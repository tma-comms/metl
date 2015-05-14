package org.jumpmind.symmetric.is.core.runtime.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.is.core.model.SettingDefinition;
import org.jumpmind.symmetric.is.core.model.SettingDefinition.Type;
import org.jumpmind.symmetric.is.core.runtime.EntityData;
import org.jumpmind.symmetric.is.core.runtime.LogLevel;
import org.jumpmind.symmetric.is.core.runtime.Message;
import org.jumpmind.symmetric.is.core.runtime.flow.IMessageTarget;
import org.jumpmind.symmetric.is.core.runtime.resource.ResourceCategory;
import org.jumpmind.util.FormatUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ComponentDefinition(
        typeName = SqlWriter.TYPE,
        category = ComponentCategory.WRITER,
        iconImage = "dbwriter.png",
        inputMessage = MessageType.ANY,
        outgoingMessage = MessageType.ANY,
        resourceCategory = ResourceCategory.DATASOURCE,
        inputOutputModelsMatch = true)
public class SqlWriter extends AbstractDbComponent {

    private static final String ON_SUCCESS = "ON SUCCESS";

    private static final String PER_MESSAGE = "PER MESSAGE";

    private static final String PER_ENTITY = "PER ENTITY";

    public static final String TYPE = "Sql Writer";

    @SettingDefinition(order = 10, required = true, type = Type.MULTILINE_TEXT, label = "Sql")
    public final static String SQL = "sql";

    @SettingDefinition(order = 0, required = false, type = Type.CHOICE, choices = { PER_MESSAGE,
            ON_SUCCESS, PER_ENTITY }, defaultValue = PER_MESSAGE, label = "Run When")
    public final static String RUN_WHEN = "run.when";

    List<String> sqls;

    String runWhen = PER_MESSAGE;

    @Override
    protected void start() {
        applySettings();
        if (getResourceRuntime() == null) {
            throw new IllegalStateException("This component requires a data source");
        }
    }

    @Override
    public void handle(final Message inputMessage, final IMessageTarget messageTarget) {
        getComponentStatistics().incrementInboundMessages();
        for (String sql : this.sqls) {
            final String sqlToExecute = FormatUtils.replaceTokens(sql,
                    context.getFlowParametersAsString(), true);
            NamedParameterJdbcTemplate template = getJdbcTemplate();
            Map<String, Object> params = new HashMap<String, Object>(
                    context.getFlowParametersAsString());
            if (runWhen.equals(PER_MESSAGE)) {
                int count = template.update(sqlToExecute, params);
                getComponentStatistics().incrementNumberEntitiesProcessed(count);
            } else if (runWhen.equals(PER_ENTITY)) {
                List<EntityData> datas = inputMessage.getPayload();
                for (EntityData entityData : datas) {
                    params.putAll(getComponent().toRow(entityData));
                    int count = template.update(sqlToExecute, params);
                    getComponentStatistics().incrementNumberEntitiesProcessed(count);
                }
            }
        }
        getComponentStatistics().incrementOutboundMessages();
        messageTarget.put(inputMessage.copy(getFlowStepId()));
    }

    @Override
    public void flowCompleted() {
        if (runWhen.equals(ON_SUCCESS)) {
            NamedParameterJdbcTemplate template = getJdbcTemplate();
            for (String sql : this.sqls) {
                String sqlToExecute = sql;
                Map<String, Object> params = new HashMap<String, Object>();
                sqlToExecute = FormatUtils.replaceTokens(sql, context.getFlowParametersAsString(),
                        true);
                params.putAll(context.getFlowParameters());
                log(LogLevel.INFO, "Executing the following sql after a successful completion: "
                        + sqlToExecute);
                int count = template.update(sqlToExecute, params);
                getComponentStatistics().incrementNumberEntitiesProcessed(count);
            }
        }
    }

    protected void applySettings() {
        TypedProperties properties = getComponent().toTypedProperties(getSettingDefinitions(false));
        sqls = getSqlStatements(properties.get(SQL));
        runWhen = properties.get(RUN_WHEN, PER_MESSAGE);
    }

}