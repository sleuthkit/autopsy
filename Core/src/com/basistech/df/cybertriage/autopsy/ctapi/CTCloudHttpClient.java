/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.basistech.df.cybertriage.autopsy.ctapi;

import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.client.WinHttpClients;

/**
 * Actually makes the http requests to CT cloud.
 */
public class CTCloudHttpClient {

    private static final CTCloudHttpClient instance = new CTCloudHttpClient();
    private static final Logger LOGGER = Logger.getLogger(CTCloudHttpClient.class.getName());

    private static final List<String> DEFAULT_SCHEME_PRIORITY
            = new ArrayList<>(Arrays.asList(
                    AuthSchemes.SPNEGO,
                    AuthSchemes.KERBEROS,
                    AuthSchemes.NTLM,
                    AuthSchemes.CREDSSP,
                    AuthSchemes.DIGEST,
                    AuthSchemes.BASIC));

    private static final int CONNECTION_TIMEOUT_MS = 58 * 1000; // milli sec

    public static CTCloudHttpClient getInstance() {
        return instance;
    }

    private final ObjectMapper mapper = ObjectMapperUtil.getInstance().getDefaultObjectMapper();
    private final SSLContext sslContext;
    private String hostName = null;

    private CTCloudHttpClient() {
        // leave as null for now unless we want to customize this at a later date
        this.sslContext = null;
    }

    private ProxySettingArgs getProxySettings() {
        if (StringUtils.isBlank(hostName)) {
            try {
                hostName = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException ex) {
                LOGGER.log(Level.WARNING, "An error occurred while fetching the hostname", ex);
            }
        }

        int proxyPort = 0;
        if (StringUtils.isNotBlank(ProxySettings.getHttpPort())) {
            try {
                proxyPort = Integer.parseInt(ProxySettings.getHttpsPort());
            } catch (NumberFormatException ex) {
                LOGGER.log(Level.WARNING, "Unable to convert port to integer");
            }
        }

        return new ProxySettingArgs(
                ProxySettings.getProxyType() != ProxySettings.DIRECT_CONNECTION,
                hostName,
                ProxySettings.getHttpsHost(),
                proxyPort,
                ProxySettings.getAuthenticationUsername(),
                ProxySettings.getAuthenticationPassword(),
                null
        );
    }
    
    public <O> O doPost(String urlPath, Object jsonBody, Class<O> classType) throws CTCloudException {
        return doPost(urlPath, Collections.emptyMap(), jsonBody, classType);
    }

    public <O> O doPost(String urlPath, Map<String, String> urlReqParams, Object jsonBody, Class<O> classType) throws CTCloudException {
        String url = Constants.CT_CLOUD_SERVER + urlPath;
        try {

            LOGGER.log(Level.INFO, "initiating http connection to ctcloud server");
            try (CloseableHttpClient httpclient = createConnection(getProxySettings(), sslContext)) {
                URIBuilder builder = new URIBuilder(url);
                
                if (!MapUtils.isEmpty(urlReqParams)) {
                    for (Entry<String, String> e : urlReqParams.entrySet()) {
                        String key = e.getKey();
                        String value = e.getValue();
                        if (StringUtils.isNotBlank(key) || StringUtils.isNotBlank(value)) {
                            builder.addParameter(key, value);
                        }
                    }
                }

                URI postURI = builder.build();
                HttpPost postRequest = new HttpPost(postURI);

                
                configureRequestTimeout(postRequest);
                postRequest.setHeader("Content-type", "application/json");

                if (jsonBody != null) {
                    String requestBody = mapper.writeValueAsString(jsonBody);
                    if (StringUtils.isNotBlank(requestBody)) {
                        HttpEntity entity = new StringEntity(requestBody, "UTF-8");
                        postRequest.setEntity(entity);
                    }
                }

                LOGGER.log(Level.INFO, "initiating http post request to ctcloud server " + postRequest.getURI());
                try (CloseableHttpResponse response = httpclient.execute(postRequest)) {

                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        LOGGER.log(Level.INFO, "Response Received. - Status OK");
                        // Parse Response
                        if (classType != null) {
                            HttpEntity entity = response.getEntity();
                            String entityStr = EntityUtils.toString(entity);
                            O respObj = mapper.readValue(entityStr, classType);
                            return respObj;
                        } else {
                            return null;
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "Response Received. - Status Error {}", response.getStatusLine());
                        handleNonOKResponse(response, "");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error when parsing response from CyberTriage Cloud", ex);
                    throw new CTCloudException(CTCloudException.parseUnknownException(ex), ex);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "IO Exception raised when connecting to  CT Cloud using " + url, ex);
            throw new CTCloudException(CTCloudException.ErrorCode.NETWORK_ERROR, ex);
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.WARNING, "Wrong URL syntax for CT Cloud " + url, ex);
            throw new CTCloudException(CTCloudException.ErrorCode.UNKNOWN, ex);
        }

        return null;
    }
    
    public void doFileUploadPost(String urlPath, String fileName, InputStream fileIs) throws CTCloudException {
         
        try (CloseableHttpClient httpclient = createConnection(getProxySettings(), sslContext)) {
            HttpPost post = new HttpPost(urlPath);
            configureRequestTimeout(post);
            
            post.addHeader("Connection", "keep-alive");
            
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody(
                    "file",
                    fileIs,
                    ContentType.APPLICATION_OCTET_STREAM,
                    fileName
            );
            
            HttpEntity multipart = builder.build();
            post.setEntity(multipart);
            
            try (CloseableHttpResponse response = httpclient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
                    LOGGER.log(Level.INFO, "Response Received. - Status OK");
                } else {
                    LOGGER.log(Level.WARNING, MessageFormat.format("Response Received. - Status Error {0}", response.getStatusLine()));
                    handleNonOKResponse(response, fileName);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "IO Exception raised when connecting to Reversing Labs for file content upload ", ex);
            throw new CTCloudException(CTCloudException.ErrorCode.NETWORK_ERROR, ex);
        }
    }

    /**
     * A generic way to handle the HTTP response - when the response code is NOT
     * 200 OK.
     *
     * @param response
     * @param fileName - used only for logging.
     * @throws MalwareScannerException
     * @throws IOException
     */
    private void handleNonOKResponse(CloseableHttpResponse response, String fileName) throws CTCloudException, IOException {
        LOGGER.log(Level.WARNING, MessageFormat.format(
                "Response code {0}. Message Body {1}",
                response.getStatusLine().getStatusCode(),
                EntityUtils.toString(response.getEntity())));

        switch (response.getStatusLine().getStatusCode()) {

            case HttpStatus.SC_BAD_REQUEST:
                //400: Bad request	=> Unsupported HTTP method or invalid http request (e.g., empty body).
                throw new CTCloudException(CTCloudException.ErrorCode.BAD_REQUEST);
            case HttpStatus.SC_UNAUTHORIZED:
                //401 Invalid API key	=> An invalid API key, or no API key, has been provided
                throw new CTCloudException(CTCloudException.ErrorCode.INVALID_KEY);
            case HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED:
                // 407 Proxy server authentication required.
                throw new CTCloudException(CTCloudException.ErrorCode.PROXY_UNAUTHORIZED);
            case HttpStatus.SC_FORBIDDEN:
                throw new CTCloudException(CTCloudException.ErrorCode.UN_AUTHORIZED);
            case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                //500 Internal error	Server temporarily unavailable; please try again later. If the issue persists, please contact RL.
                throw new CTCloudException(CTCloudException.ErrorCode.TEMP_UNAVAILABLE);
            case HttpStatus.SC_SERVICE_UNAVAILABLE:
                //503 Server is too busy. Try again later.
                //503 Failed to request scan. Try again later.	The server is currently unable to handle the request due to a temporary overloading or maintenance of the server. If the issue persists, please contact RL.
                throw new CTCloudException(CTCloudException.ErrorCode.TEMP_UNAVAILABLE);
            case HttpStatus.SC_GATEWAY_TIMEOUT:
                throw new CTCloudException(CTCloudException.ErrorCode.GATEWAY_TIMEOUT);
            default:
                String returnData = EntityUtils.toString(response.getEntity());
                LOGGER.log(Level.WARNING, MessageFormat.format("upload response content for {0}:\n {1}", fileName, returnData));
                throw new CTCloudException(CTCloudException.ErrorCode.NETWORK_ERROR);
        }
    }

    /**
     * NOTE That this is not a perfect solution as timeouts set this way does
     * not terminate a connection forcefully after a specified interval. so if
     * there is data streaming in from the server at a small speed the
     * connection will be kept open.
     *
     * @param request
     */
    private void configureRequestTimeout(HttpRequestBase request) {
        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
                .setConnectTimeout(CONNECTION_TIMEOUT_MS)
                .setSocketTimeout(CONNECTION_TIMEOUT_MS)
                .build();
        request.setConfig(config);
    }

    /**
     * Creates and returns a CloseableHttpClient SYSTEM and MANUAL looks up from
     * runtime proxy config settings. These are updated accordingly from the
     * Proxy Config UI. This allows us to keep the CreateConnection call fairly
     * simple and not have to deal with the System Proxy settings and such.
     *
     * @return
     */
    private static CloseableHttpClient createConnection(ProxySettingArgs proxySettings, SSLContext sslContext) {
        HttpClientBuilder builder = getHttpClientBuilder(proxySettings);

        if (sslContext != null) {
            builder.setSSLContext(sslContext);
        }
        return builder.build();
    }

    private static HttpClientBuilder getHttpClientBuilder(ProxySettingArgs proxySettings) {

        if (proxySettings.isSystemOrManualProxy()) {

            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    LOGGER.info("Requesting Password Authentication...");
                    return super.getPasswordAuthentication();
                }
            });

            HttpClientBuilder builder = null;
            HttpHost proxyHost = null;
            CredentialsProvider proxyCredsProvider = null;
            RequestConfig config = null;

            if (Objects.nonNull(proxySettings.getProxyHostname()) && proxySettings.getProxyPort() > 0) {
                proxyHost = new HttpHost(proxySettings.getProxyHostname(), proxySettings.getProxyPort());

                proxyCredsProvider = getProxyCredentialsProvider(proxySettings);
                if (StringUtils.isNotBlank(proxySettings.getAuthScheme())) {
                    if (!DEFAULT_SCHEME_PRIORITY.get(0).equalsIgnoreCase(proxySettings.getAuthScheme())) {
                        DEFAULT_SCHEME_PRIORITY.removeIf(s -> s.equalsIgnoreCase(proxySettings.getAuthScheme()));
                        DEFAULT_SCHEME_PRIORITY.add(0, proxySettings.getAuthScheme());
                    }
                }
                config = RequestConfig.custom().setProxyPreferredAuthSchemes(DEFAULT_SCHEME_PRIORITY).build();
            }

            if (Objects.isNull(proxyCredsProvider) && WinHttpClients.isWinAuthAvailable()) {
                builder = WinHttpClients.custom();
                builder.useSystemProperties();
                LOGGER.log(Level.WARNING, "Using Win HTTP Client");
            } else {
                builder = HttpClients.custom();
                builder.setDefaultRequestConfig(config);
                if (Objects.nonNull(proxyCredsProvider)) { // make sure non null proxycreds before setting it 
                    builder.setDefaultCredentialsProvider(proxyCredsProvider);
                }
                LOGGER.log(Level.WARNING, "Using default http client");
            }
            if (Objects.nonNull(proxyHost)) {
                builder.setProxy(proxyHost);
                LOGGER.log(Level.WARNING, MessageFormat.format("Using proxy {0}", proxyHost));
            }

            return builder;
        } else {
            return HttpClients.custom();
        }
    }

    /**
     * Returns a CredentialsProvider for proxy, if one is configured.
     *
     * @return CredentialsProvider, if a proxy is configured with credentials,
     * null otherwise
     */
    private static CredentialsProvider getProxyCredentialsProvider(ProxySettingArgs proxySettings) {
        CredentialsProvider proxyCredsProvider = null;
        if (proxySettings.isSystemOrManualProxy()) {
            if (StringUtils.isNotBlank(proxySettings.getProxyUserId())) {
                if (null != proxySettings.getProxyPassword() && proxySettings.getProxyPassword().length > 0) { // Password will be blank for KERBEROS / NEGOTIATE schemes.
                    proxyCredsProvider = new SystemDefaultCredentialsProvider();
                    String userId = proxySettings.getProxyUserId();
                    String domain = null;
                    if (userId.contains("\\")) {
                        domain = userId.split("\\\\")[0];
                        userId = userId.split("\\\\")[1];
                    }
                    String workStation = proxySettings.getHostName();
                    proxyCredsProvider.setCredentials(new AuthScope(proxySettings.getProxyHostname(), proxySettings.getProxyPort()),
                            new NTCredentials(userId, new String(proxySettings.getProxyPassword()), workStation, domain));
                }
            }
        }

        return proxyCredsProvider;
    }

    private static class ProxySettingArgs {

        private final boolean systemOrManualProxy;
        private final String hostName;
        private final String proxyHostname;
        private final int proxyPort;
        private final String proxyUserId;
        private final char[] proxyPassword;
        private final String authScheme;

        ProxySettingArgs(boolean systemOrManualProxy, String hostName, String proxyHostname, int proxyPort, String proxyUserId, char[] proxyPassword, String authScheme) {
            this.systemOrManualProxy = systemOrManualProxy;
            this.hostName = hostName;
            this.proxyHostname = proxyHostname;
            this.proxyPort = proxyPort;
            this.proxyUserId = proxyUserId;
            this.proxyPassword = proxyPassword;
            this.authScheme = authScheme;
        }

        boolean isSystemOrManualProxy() {
            return systemOrManualProxy;
        }

        String getHostName() {
            return hostName;
        }

        String getProxyHostname() {
            return proxyHostname;
        }

        int getProxyPort() {
            return proxyPort;
        }

        String getProxyUserId() {
            return proxyUserId;
        }

        char[] getProxyPassword() {
            return proxyPassword;
        }

        public String getAuthScheme() {
            return authScheme;
        }
    }
}
