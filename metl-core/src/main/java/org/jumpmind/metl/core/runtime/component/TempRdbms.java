package org.jumpmind.metl.core.runtime.component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.h2.Driver;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.ResettableBasicDataSource;
import org.jumpmind.metl.core.model.Component;
import org.jumpmind.metl.core.model.DataType;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttribute;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.runtime.ControlMessage;
import org.jumpmind.metl.core.runtime.LogLevel;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.MisconfiguredException;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;
import org.jumpmind.properties.TypedProperties;

public class TempRdbms extends AbstractRdbmsComponentRuntime  {

    public static String IN_MEMORY_DB = "in.memory.db";

    int rowsPerMessage = 10000;

    boolean inMemoryDb = true;

    IDatabasePlatform databasePlatform;

    RdbmsWriter databaseWriter;

    String databaseName;
    
    List<String> sqls;
    
    int rowReadDuringHandle;

    @Override
    protected void start() {
        TypedProperties properties = getTypedProperties();
        sqls = getSqlStatements(true);

        this.inMemoryDb = properties.is(IN_MEMORY_DB);
        this.rowsPerMessage = properties.getInt(ROWS_PER_MESSAGE);
        Component comp = context.getFlowStep().getComponent();
        comp.setOutputModel(comp.getInputModel());
        Model inputModel = context.getFlowStep().getComponent().getInputModel();
        if (inputModel == null) {
            throw new MisconfiguredException("The input model is not set and it is required");
        }
    }

    
    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        createDatabase();
        loadIntoDatabase(inputMessage);
        if (unitOfWorkBoundaryReached) {
        	query(callback);
        }
    }


    protected void query(ISendMessageCallback callback) {
        RdbmsReader reader = new RdbmsReader();
        reader.setDataSource(databasePlatform.getDataSource());
        reader.setContext(context);
        reader.setComponentDefinition(componentDefinition);
        reader.setRowsPerMessage(rowsPerMessage);
        reader.setThreadNumber(threadNumber);
        
        reader.setSqls(sqls);
        reader.handle(new ControlMessage(this.context.getFlowStep().getId()), callback, false);
        info("Sent %d records", reader.getRowReadDuringHandle());

        ResettableBasicDataSource ds = databasePlatform.getDataSource();
        log(LogLevel.INFO, "Closing ds: %s", ds.getUrl());
        ds.close();

        if (!inMemoryDb) {
            try {
                Files.list(Paths.get(System.getProperty("h2.baseDir"))).filter(path -> path.toFile().getName().startsWith(databaseName))
                        .forEach(path -> deleteDatabaseFile(path.toFile()));
            } catch (IOException e) {
                log.warn("Failed to delete file", e);
            }
        }
        
        databasePlatform = null;
        databaseName = null;
        databaseWriter = null;
    }

    
    protected void deleteDatabaseFile(File file) {
        log(LogLevel.INFO, "Deleting database file: %s", file.getName());
        FileUtils.deleteQuietly(file);
    }

    
    protected void loadIntoDatabase(Message message) {
        if (databaseWriter == null) {
            databaseWriter = new RdbmsWriter();
            databaseWriter.setDatabasePlatform(databasePlatform);
            databaseWriter.setComponentDefinition(componentDefinition);
            databaseWriter.setReplaceRows(true);
            databaseWriter.setContext(context);
            databaseWriter.setThreadNumber(threadNumber);
        }
        databaseWriter.handle(message, null, false);
    }

    protected void createDatabase() {
        if (databasePlatform == null) {
            ResettableBasicDataSource ds = new ResettableBasicDataSource();
            ds.setDriverClassName(Driver.class.getName());
            ds.setMaxActive(1);
            ds.setInitialSize(1);
            ds.setMinIdle(1);
            ds.setMaxIdle(1);
            databaseName = UUID.randomUUID().toString();
            if (inMemoryDb) {
                ds.setUrl("jdbc:h2:mem:" + databaseName);
            } else {
                ds.setUrl("jdbc:h2:file:./" + databaseName);
            }
            databasePlatform = JdbcDatabasePlatformFactory.createNewPlatformInstance(ds, new SqlTemplateSettings(), true, false);

            Model inputModel = context.getFlowStep().getComponent().getInputModel();
            List<ModelEntity> entities = inputModel.getModelEntities();
            for (ModelEntity entity : entities) {
                Table table = new Table();
                table.setName(entity.getName());
                List<ModelAttribute> attributes = entity.getModelAttributes();
                for (ModelAttribute attribute : attributes) {
                    DataType dataType = attribute.getDataType();
                    Column column = new Column(attribute.getName());
                    if (dataType.isNumeric()) {
                        column.setTypeCode(Types.DECIMAL);
                    } else if (dataType.isBoolean()) {
                        column.setTypeCode(Types.BOOLEAN);
                    } else if (dataType.isTimestamp()) {
                        column.setTypeCode(Types.TIMESTAMP);
                    } else if (dataType.isBinary()) {
                        column.setTypeCode(Types.BLOB);
                    } else {
                        column.setTypeCode(Types.LONGVARCHAR);
                    }

                    column.setPrimaryKey(attribute.isPk());
                    table.addColumn(column);
                }
                log(LogLevel.INFO, "Creating table: " + table.getName() + "  on db: " + databasePlatform.getDataSource().toString());
                
                // H2 database platform sets names to upper case by default unless 
                // the database name is mixed case. For our purposes, we 
                // always want the name to be case insensitive in the logical model.
                alterCaseToMatchLogicalCase(table);
                
                databasePlatform.createTables(false, false, table);
            }

            log(LogLevel.INFO, "Creating databasePlatform with the following url: %s", ds.getUrl());
        }
    }
    
    private void alterCaseToMatchLogicalCase(Table table) {
        table.setName(table.getName().toUpperCase());

        Column[] columns = table.getColumns();
        for (Column column : columns) {
            column.setName(column.getName().toUpperCase());
        }

        IIndex[] indexes = table.getIndices();
        for (IIndex index : indexes) {
            index.setName(index.getName().toUpperCase());

            IndexColumn[] indexColumns = index.getColumns();
            for (IndexColumn indexColumn : indexColumns) {
                indexColumn.setName(indexColumn.getName().toUpperCase());
            }
        }
    }

    @Override
    public boolean supportsStartupMessages() {
        return false;
    }

}