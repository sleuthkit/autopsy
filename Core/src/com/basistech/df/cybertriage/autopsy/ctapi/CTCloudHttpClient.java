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
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
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
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.ssl.SSLInitializationException;
import org.netbeans.core.ProxySettings;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * Makes the http requests to CT cloud.
 *
 * NOTE: regarding proxy settings, the host and port are handled by the
 * NbProxySelector. Any proxy authentication is handled by NbAuthenticator which
 * is installed at startup (i.e. NbAuthenticator.install). See
 * GeneralOptionsModel.testHttpConnection to see how the general options panel
 * tests the connection.
 */
class CTCloudHttpClient {

    private static final Logger LOGGER = Logger.getLogger(CTCloudHttpClient.class.getName());
    private static final String HOST_URL = Version.getBuildType() == Version.Type.RELEASE ? Constants.CT_CLOUD_SERVER : Constants.CT_CLOUD_DEV_SERVER;
    private static final String NB_PROXY_SELECTOR_NAME = "org.netbeans.core.NbProxySelector";

    private static final int CONNECTION_TIMEOUT_MS = 58 * 1000; // milli sec

    private static final CTCloudHttpClient instance = new CTCloudHttpClient();

    public static CTCloudHttpClient getInstance() {
        return instance;
    }

    private final ObjectMapper mapper = ObjectMapperUtil.getInstance().getDefaultObjectMapper();
    private final SSLContext sslContext;
    private final ProxySelector proxySelector;

    private CTCloudHttpClient() {
        // leave as null for now unless we want to customize this at a later date
        this.sslContext = createSSLContext();
        this.proxySelector = getProxySelector();
    }

    private static URI getUri(String host, String path, Map<String, String> urlReqParams) throws URISyntaxException {
        String url = host + path;
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

        return builder.build();
    }

    public <O> O doPost(String urlPath, Object jsonBody, Class<O> classType) throws CTCloudException {
        return doPost(urlPath, Collections.emptyMap(), jsonBody, classType);
    }

    public <O> O doPost(String urlPath, Map<String, String> urlReqParams, Object jsonBody, Class<O> classType) throws CTCloudException {

        URI postURI = null;
        try {
            postURI = getUri(HOST_URL, urlPath, urlReqParams);
            LOGGER.log(Level.INFO, "initiating http connection to ctcloud server");
            try (CloseableHttpClient httpclient = createConnection(proxySelector, sslContext)) {

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
            LOGGER.log(Level.WARNING, "IO Exception raised when connecting to  CT Cloud using " + postURI, ex);
            throw new CTCloudException(CTCloudException.ErrorCode.NETWORK_ERROR, ex);
        } catch (SSLInitializationException ex) {
            LOGGER.log(Level.WARNING, "No such algorithm exception raised when creating SSL connection for  CT Cloud using " + postURI, ex);
            throw new CTCloudException(CTCloudException.ErrorCode.NETWORK_ERROR, ex);
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.WARNING, "Wrong URL syntax for CT Cloud " + postURI, ex);
            throw new CTCloudException(CTCloudException.ErrorCode.UNKNOWN, ex);
        }

        return null;
    }

    public void doFileUploadPost(String fullUrlPath, String fileName, InputStream fileIs) throws CTCloudException {
        URI postUri;
        try {
            postUri = new URI(fullUrlPath);
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.WARNING, "Wrong URL syntax for CT Cloud " + fullUrlPath, ex);
            throw new CTCloudException(CTCloudException.ErrorCode.UNKNOWN, ex);
        }

        try (CloseableHttpClient httpclient = createConnection(proxySelector, sslContext)) {
            LOGGER.log(Level.INFO, "initiating http post request to ctcloud server " + fullUrlPath);
            HttpPost post = new HttpPost(postUri);
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
        } catch (SSLInitializationException ex) {
            LOGGER.log(Level.WARNING, "SSL exception raised when connecting to Reversing Labs for file content upload ", ex);
            throw new CTCloudException(CTCloudException.ErrorCode.NETWORK_ERROR, ex);
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
     * Get ProxySelector present (favoring NbProxySelector if present).
     *
     * @return The found ProxySelector or null.
     */
    private static ProxySelector getProxySelector() {
        Collection<? extends ProxySelector> selectors = Lookup.getDefault().lookupAll(ProxySelector.class);
        return (selectors != null ? selectors.stream() : Stream.empty())
                .filter(s -> s != null)
                .map(s -> (ProxySelector) s)
                .sorted((a, b) -> {
                    String aName = a.getClass().getCanonicalName();
                    String bName = b.getClass().getCanonicalName();
                    boolean aIsNb = aName.equalsIgnoreCase(NB_PROXY_SELECTOR_NAME);
                    boolean bIsNb = bName.equalsIgnoreCase(NB_PROXY_SELECTOR_NAME);
                    if (aIsNb == bIsNb) {
                        return StringUtils.compareIgnoreCase(aName, bName);
                    } else {
                        return aIsNb ? -1 : 1;
                    }
                })
                .findFirst()
                // TODO take this out to remove proxy selector logging
                .map(s -> new LoggingProxySelector(s))
                .orElse(null);
    }

    /**
     * Create an SSLContext object using our in-memory keystore.
     *
     * @return
     */
    private static SSLContext createSSLContext() {
        LOGGER.log(Level.INFO, "Creating custom SSL context");
        try {

            // I'm not sure how much of this is really necessary to set up, but it works
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            KeyManager[] keyManagers = getKeyManagers();
            TrustManager[] trustManagers = getTrustManagers();
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException ex) {
            LOGGER.log(Level.SEVERE, "Error creating SSL context", ex);
            return null;
        }
    }

    // jvm default key manager
    // based in part on this: https://stackoverflow.com/questions/1793979/registering-multiple-keystores-in-jvm/16229909
    private static KeyManager[] getKeyManagers() {
        LOGGER.log(Level.INFO, "Using default algorithm to create trust store: " + KeyManagerFactory.getDefaultAlgorithm());
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(null, null);
            return kmf.getKeyManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
            LOGGER.log(Level.SEVERE, "Error getting KeyManagers", ex);
            return new KeyManager[0];
        }

    }

    // jvm default trust store
    // based in part on this: https://stackoverflow.com/questions/1793979/registering-multiple-keystores-in-jvm/16229909
    private static TrustManager[] getTrustManagers() {
        try {
            LOGGER.log(Level.INFO, "Using default algorithm to create trust store: " + TrustManagerFactory.getDefaultAlgorithm());
            TrustManagerFactory tmf
                    = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];

            return new TrustManager[]{tm};
        } catch (KeyStoreException | NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, "Error getting TrustManager", ex);
            return new TrustManager[0];
        }
    }

    /**
     * Creates a connection to CT Cloud with the given arguments.
     *
     * @param proxySelector The proxy selector.
     * @param sslContext The ssl context or null.
     * @return The connection to CT Cloud.
     */
    private static CloseableHttpClient createConnection(ProxySelector proxySelector, SSLContext sslContext) throws SSLInitializationException {
        HttpClientBuilder builder;

        if (ProxySettings.getProxyType() != ProxySettings.DIRECT_CONNECTION
                && StringUtils.isBlank(ProxySettings.getAuthenticationUsername()) 
                && ArrayUtils.isEmpty(ProxySettings.getAuthenticationPassword())
                && WinHttpClients.isWinAuthAvailable()) {

            builder = WinHttpClients.custom();
            builder.useSystemProperties();
            LOGGER.log(Level.WARNING, "Using Win HTTP Client");
        } else {
            builder = HttpClients.custom();
            // builder.setDefaultRequestConfig(config);
            LOGGER.log(Level.WARNING, "Using default http client");
        }

        if (sslContext != null) {
            builder.setSSLContext(sslContext);
        }

        if (proxySelector != null) {
            builder.setRoutePlanner(new SystemDefaultRoutePlanner(proxySelector));
        }

        return builder.build();
    }

    private static class LoggingProxySelector extends ProxySelector {

        private final ProxySelector delegate;

        public LoggingProxySelector(ProxySelector delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Proxy> select(URI uri) {
            List<Proxy> selectedProxies = delegate.select(uri);
            LOGGER.log(Level.INFO, MessageFormat.format("Proxy selected for {0} are {1}", uri, selectedProxies));
            return selectedProxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            LOGGER.log(Level.WARNING, MessageFormat.format("Connection failed connecting to {0} socket address {1}", uri, sa), ioe);
            delegate.connectFailed(uri, sa, ioe);
        }

    }
}
