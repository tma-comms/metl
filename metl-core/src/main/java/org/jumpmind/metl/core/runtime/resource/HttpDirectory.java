/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.core.runtime.resource;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.codec.binary.Base64;
import org.jumpmind.exception.IoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.oauth.signature.HMAC_SHA1;
import com.sun.jersey.oauth.signature.OAuthParameters;
import com.sun.jersey.oauth.signature.OAuthRequest;
import com.sun.jersey.oauth.signature.OAuthSecrets;
import com.sun.jersey.oauth.signature.OAuthSignature;

public class HttpDirectory implements IDirectory {

    final Logger log = LoggerFactory.getLogger(getClass());

    public static final String HTTP_METHOD_GET = "GET";
    public static final String HTTP_METHOD_PUT = "PUT";
    public static final String HTTP_METHOD_POST = "POST";

    public static final String SECURITY_NONE = "None";
    public static final String SECURITY_BASIC = "Basic Auth";
    public static final String SECURITY_TOKEN = "Token Auth";
    public static final String SECURITY_OAUTH_10 = "OAuth 1.0";

    String url;
    String httpMethod;
    String contentType;
    String security;
    String username;
    String password;
    String token;
    String oa1ConsumerSecret;
    String oa1ConsumerKey;
    String oa1SignatureMethod;
    String oa1Version;
    String oa1Realm;
    String oa1Token;
    String oa1TokenSecret;    
    int timeout;
    int contentLength;

    public HttpDirectory(String url, String httpMethod, String contentType, int timeout, String security,
            String username, String password, String token,
            String oa1ConsumerKey, String oa1ConsumerSecret, String oa1Token, String oa1TokenSecret,
            String oa1Version, String oa1SignatureMethod, String oa1Realm) {
        this.url = url;
        this.httpMethod = httpMethod;
        this.contentType = contentType;
        this.timeout = timeout;
        this.security = security;
        this.username = username;
        this.password = password;
        this.token = token;
        this.oa1ConsumerKey = oa1ConsumerKey;
        this.oa1ConsumerSecret = oa1ConsumerSecret;
        this.oa1Token = oa1Token;
        this.oa1TokenSecret = oa1TokenSecret;
        this.oa1Version = oa1Version;
        this.oa1SignatureMethod = oa1SignatureMethod;
        this.oa1Realm = oa1Realm;
    }
    
    @Override
    public FileInfo listFile(String relativePath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileInfo listFile(String relativePath, boolean closeSession) {
        return listFile(relativePath);
    }
    
    @Override
    public List<FileInfo> listFiles(String... relativePaths) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileInfo> listFiles(boolean closeSession, String... relativePaths) {
        return listFiles(relativePaths);
    }
    
    @Override
    public void copyToDir(String fromFilePath, String toDirPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyToDir(String fromFilePath, String toDirPath, boolean closeSession) {
        copyToDir(fromFilePath, toDirPath);
    }
    
    @Override
    public void moveToDir(String fromFilePath, String toDirPath) {
        throw new UnsupportedOperationException();
    }    

    @Override
    public void moveToDir(String fromFilePath, String toDirPath, boolean closeSession) {
        moveToDir(fromFilePath, toDirPath);
    }
    
    @Override
    public void copyFile(String fromFilePath, String toFilePath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyFile(String fromFilePath, String toFilePath, boolean closeSession) {
        copyFile(fromFilePath, toFilePath);
    }
    
    @Override
    public void moveFile(String fromFilePath, String toFilePath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveFile(String fromFilePath, String toFilePath, boolean closeSession) {
        moveFile(fromFilePath, toFilePath);
    }
    
    @Override
    public boolean renameFile(String fromFilePath, String toFilePath) {
        throw new UnsupportedOperationException();
    }   

    @Override
    public boolean renameFile(String fromFilePath, String toFilePath, boolean closeSession) {
        return renameFile(fromFilePath, toFilePath);
    } 
    
    @Override
    public InputStream getInputStream(String relativePath, boolean mustExist) {
        return getInputStream(relativePath, null, null);
    }
    
    public InputStream getInputStream(String relativePath, Map<String,String> headers, 
            Map<String,String> parameters) {
        try {
            HttpURLConnection httpConnection = buildHttpUrlConnection(relativePath, headers, parameters);
            int responseCode = httpConnection.getResponseCode();
            if (responseCode == 200) {
                String type = httpConnection.getContentEncoding();
                InputStream in = httpConnection.getInputStream();
                if (!isBlank(type) && type.equals("gzip")) {
                    in = new GZIPInputStream(in);
                }
                return in;
            } else {
                throw new IoException("Received an unexpected response code of " + responseCode);
            }

        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    @Override
    public InputStream getInputStream(String relativePath, boolean mustExist, boolean closeSession) {
        return getInputStream(relativePath, mustExist);
    }

    @Override
    public OutputStream getOutputStream(String relativePath, boolean mustExist) {
        return getOutputStream(relativePath, null, null);
    }
    
    public OutputStream getOutputStream(String relativePath, Map<String,String> headers,
            Map<String,String> parameters) {
        HttpURLConnection httpUrlConnection = buildHttpUrlConnection(relativePath, headers, parameters);
        return new HttpOutputStream(httpUrlConnection);        
    }

    @Override
    public OutputStream getOutputStream(String relativePath, boolean mustExist, boolean closeSession, boolean append) {
        return getOutputStream(relativePath, mustExist);
    }

    protected HttpURLConnection buildHttpUrlConnection(String relativePath, Map<String,String> headers,
            Map<String,String> parameters) {
        try {
            String fullUrl = url;
            if (isNotBlank(relativePath)) {
                fullUrl += relativePath;
            }
            HttpURLConnection httpUrlConnection = (HttpURLConnection) new URL(fullUrl).openConnection();
            setBasicAuthIfNeeded(httpUrlConnection);
            setOAuth10IfNeeded(httpUrlConnection, headers, parameters);
            if (isNotBlank(contentType)) {
                httpUrlConnection.setRequestProperty("Content-Type", contentType);
            }
            if (headers != null) {
                for(String key:headers.keySet()) {
                    httpUrlConnection.setRequestProperty(key, headers.get(key));
                }
            }
            httpUrlConnection.setConnectTimeout(timeout);
            httpUrlConnection.setReadTimeout(timeout);
            httpUrlConnection.setRequestMethod(httpMethod);
            httpUrlConnection.setDoOutput(true);
            httpUrlConnection.setDoInput(true);            
            return httpUrlConnection;
        } catch (Exception e) {
            throw new IoException(e);
        }
    }

    protected void setOAuth10IfNeeded(HttpURLConnection conn, Map<String,String> headers,
            Map<String,String> parameters) {
        if (SECURITY_OAUTH_10.equals(security)) {
            OAuthParameters parms = new OAuthParameters();
            parms.setConsumerKey(oa1ConsumerKey);
            parms.setSignatureMethod(HMAC_SHA1.NAME);
            parms.setVersion(oa1Version);
            parms.setRealm(oa1Realm);
            parms.setNonce();
            parms.setTimestamp();
            parms.setToken(oa1Token);            
            OAuthSecrets secrets = new OAuthSecrets();
            secrets.setConsumerSecret(oa1ConsumerSecret);
            secrets.setTokenSecret(oa1TokenSecret);
            OAuthReq req = new OAuthReq(headers, parameters);
            try {        
                String signature = OAuthSignature.generate(req, parms, secrets);
                String authHdr = String.format("OAuth realm=\"%s\",oauth_consumer_key=\"%s\","
                        + "oauth_token=\"%s\",oauth_signature_method=\"%s\","
                        + "oauth_timestamp=\"%s\",oauth_nonce=\"%s\","
                        + "oauth_version=\"%s\",oauth_signature=\"%s\"", 
                        oa1Realm, oa1ConsumerKey, oa1Token, oa1SignatureMethod,
                        parms.getTimestamp(), parms.getNonce(),
                        oa1Version, signature);
                conn.setRequestProperty("Authorization", authHdr);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }
    
    protected void setBasicAuthIfNeeded(HttpURLConnection conn) {
        if (SECURITY_BASIC.equals(security)) {
            String userpassword = String.format("%s:%s", username, password);
            String encodedAuthorization = new String(Base64.encodeBase64(userpassword.getBytes()));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
        } else if (SECURITY_TOKEN.equals(security)) {
        	conn.setRequestProperty("Authorization", "Bearer " + token);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresContentLength() {
        return false;
    }

    @Override
    public void setContentLength(int length) {
        this.contentLength = length;
    }

    @Override
    public boolean supportsInputStream() {
        return true;
    }

    @Override
    public boolean supportsOutputStream() {
        return true;
    }

    @Override
    public boolean delete(String relativePath) {
        return false;
    }

    @Override
    public boolean delete(String relativePath, boolean closeSession) {
        return delete(relativePath);
    }

    @Override
    public boolean supportsDelete() {
        return false;
    }
    
    @Override
    public String toString() {
        return url;
    }

    @Override
    public void connect() {
    }
    
    private class OAuthReq implements OAuthRequest {

        Map<String,String> headers;
        Map<String,String> parameters;
        
        public OAuthReq(Map<String,String> headers, Map<String,String> parameters) {
            this.headers = headers;
            this.parameters = parameters;
        }
        
        @Override
        public String getRequestMethod() {
            return httpMethod;
        }

        @Override
        public URL getRequestURL() {
            try {
                return new URL(url);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }

        @Override
        public Set<String> getParameterNames() {
            return parameters.keySet();
        }

        @Override
        public List<String> getParameterValues(String name) {
            List<String>parmValues = new ArrayList<String>();
            parmValues.add(parameters.get(name));
            return parmValues;
        }

        @Override
        public List<String> getHeaderValues(String name) {
            List<String>headerValues = new ArrayList<String>();
            headerValues.add(parameters.get(name));
            return headerValues;
            
        }

        @Override
        public void addHeaderValue(String name, String value) throws IllegalStateException {
            headers.put(name, value);
        }
    }
}
