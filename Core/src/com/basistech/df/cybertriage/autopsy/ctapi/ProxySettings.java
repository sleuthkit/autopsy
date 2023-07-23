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



import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.netbeans.api.keyring.Keyring;
import org.openide.util.*;
import org.openide.util.lookup.ServiceProvider;

/**
 * Taken from https://raw.githubusercontent.com/apache/netbeans/master/platform/o.n.core/src/org/netbeans/core/ProxySettings.java
 * @author Jiri Rechtacek
 */
public class ProxySettings {
    
    public static final String PROXY_HTTP_HOST = "proxyHttpHost";   // NOI18N
    public static final String PROXY_HTTP_PORT = "proxyHttpPort";   // NOI18N
    public static final String PROXY_HTTPS_HOST = "proxyHttpsHost"; // NOI18N
    public static final String PROXY_HTTPS_PORT = "proxyHttpsPort"; // NOI18N
    public static final String PROXY_SOCKS_HOST = "proxySocksHost"; // NOI18N
    public static final String PROXY_SOCKS_PORT = "proxySocksPort"; // NOI18N
    public static final String NOT_PROXY_HOSTS = "proxyNonProxyHosts";  // NOI18N
    public static final String PROXY_TYPE = "proxyType";                // NOI18N
    public static final String USE_PROXY_AUTHENTICATION = "useProxyAuthentication"; // NOI18N
    public static final String PROXY_AUTHENTICATION_USERNAME = "proxyAuthenticationUsername";   // NOI18N
    public static final String PROXY_AUTHENTICATION_PASSWORD = "proxyAuthenticationPassword";   // NOI18N
    public static final String USE_PROXY_ALL_PROTOCOLS = "useProxyAllProtocols";    // NOI18N
    public static final String DIRECT = "DIRECT";   // NOI18N
    public static final String PAC = "PAC";     // NOI18N
    
    public static final String SYSTEM_PROXY_HTTP_HOST = "systemProxyHttpHost";      // NOI18N
    public static final String SYSTEM_PROXY_HTTP_PORT = "systemProxyHttpPort";      // NOI18N
    public static final String SYSTEM_PROXY_HTTPS_HOST = "systemProxyHttpsHost";    // NOI18N
    public static final String SYSTEM_PROXY_HTTPS_PORT = "systemProxyHttpsPort";    // NOI18N
    public static final String SYSTEM_PROXY_SOCKS_HOST = "systemProxySocksHost";    // NOI18N
    public static final String SYSTEM_PROXY_SOCKS_PORT = "systemProxySocksPort";    // NOI18N
    public static final String SYSTEM_NON_PROXY_HOSTS = "systemProxyNonProxyHosts"; // NOI18N
    public static final String SYSTEM_PAC = "systemPAC";                            // NOI18N
    
    // Only for testing purpose (Test connection in General options panel)
    public static final String TEST_SYSTEM_PROXY_HTTP_HOST = "testSystemProxyHttpHost"; // NOI18N
    public static final String TEST_SYSTEM_PROXY_HTTP_PORT = "testSystemProxyHttpPort"; // NOI18N
    public static final String HTTP_CONNECTION_TEST_URL = "https://netbeans.apache.org";// NOI18N
    
    private static String presetNonProxyHosts;

    /** No proxy is used to connect. */
    public static final int DIRECT_CONNECTION = 0;
    
    /** Proxy setting is automatically detect in OS. */
    public static final int AUTO_DETECT_PROXY = 1; // as default
    
    /** Manually set proxy host and port. */
    public static final int MANUAL_SET_PROXY = 2;
    
    /** Proxy PAC file automatically detect in OS. */
    public static final int AUTO_DETECT_PAC = 3;
    
    /** Proxy PAC file manually set. */
    public static final int MANUAL_SET_PAC = 4;
    
    private static final Logger LOGGER = Logger.getLogger(ProxySettings.class.getName());
    
    private static Preferences getPreferences() {
        return NbPreferences.forModule (ProxySettings.class);
    }
    
    
    public static String getHttpHost () {
        return normalizeProxyHost (getPreferences ().get (PROXY_HTTP_HOST, ""));
    }
    
    public static String getHttpPort () {
        return getPreferences ().get (PROXY_HTTP_PORT, "");
    }
    
    public static String getHttpsHost () {
        if (useProxyAllProtocols ()) {
            return getHttpHost ();
        } else {
            return getPreferences ().get (PROXY_HTTPS_HOST, "");
        }
    }
    
    public static String getHttpsPort () {
        if (useProxyAllProtocols ()) {
            return getHttpPort ();
        } else {
            return getPreferences ().get (PROXY_HTTPS_PORT, "");
        }
    }
    
    public static String getSocksHost () {
        if (useProxyAllProtocols ()) {
            return getHttpHost ();
        } else {
            return getPreferences ().get (PROXY_SOCKS_HOST, "");
        }
    }
    
    public static String getSocksPort () {
        if (useProxyAllProtocols ()) {
            return getHttpPort ();
        } else {
            return getPreferences ().get (PROXY_SOCKS_PORT, "");
        }
    }
    
    public static String getNonProxyHosts () {
        String hosts = getPreferences ().get (NOT_PROXY_HOSTS, getDefaultUserNonProxyHosts ());
        return compactNonProxyHosts(hosts);
    }
    
    public static int getProxyType () {
        int type = getPreferences ().getInt (PROXY_TYPE, AUTO_DETECT_PROXY);
        if (AUTO_DETECT_PROXY == type) {
            type = ProxySettings.getSystemPac() != null ? AUTO_DETECT_PAC : AUTO_DETECT_PROXY;
        }
        return type;
    }
    
    
    public static String getSystemHttpHost() {
        return getPreferences().get(SYSTEM_PROXY_HTTP_HOST, "");
    }
    
    public static String getSystemHttpPort() {
        return getPreferences().get(SYSTEM_PROXY_HTTP_PORT, "");
    }
    
    public static String getSystemHttpsHost() {
        return getPreferences().get(SYSTEM_PROXY_HTTPS_HOST, "");
    }
    
    public static String getSystemHttpsPort() {
        return getPreferences().get(SYSTEM_PROXY_HTTPS_PORT, "");
    }
    
    public static String getSystemSocksHost() {
        return getPreferences().get(SYSTEM_PROXY_SOCKS_HOST, "");
    }
    
    public static String getSystemSocksPort() {
        return getPreferences().get(SYSTEM_PROXY_SOCKS_PORT, "");
    }
    
    public static String getSystemNonProxyHosts() {
        return getPreferences().get(SYSTEM_NON_PROXY_HOSTS, getModifiedNonProxyHosts(""));
    }
    
    public static String getSystemPac() {
        return getPreferences().get(SYSTEM_PAC, null);
    }
    
    
    public static String getTestSystemHttpHost() {
        return getPreferences().get(TEST_SYSTEM_PROXY_HTTP_HOST, "");
    }
    
    public static String getTestSystemHttpPort() {
        return getPreferences().get(TEST_SYSTEM_PROXY_HTTP_PORT, "");
    }
    
    
    public static boolean useAuthentication () {
        return getPreferences ().getBoolean (USE_PROXY_AUTHENTICATION, false);
    }
    
    public static boolean useProxyAllProtocols () {
        return getPreferences ().getBoolean (USE_PROXY_ALL_PROTOCOLS, false);
    }
    
    public static String getAuthenticationUsername () {
        return getPreferences ().get (PROXY_AUTHENTICATION_USERNAME, "");
    }
    
    public static char[] getAuthenticationPassword () {
        String old = getPreferences().get(PROXY_AUTHENTICATION_PASSWORD, null);
        if (old != null) {
            getPreferences().remove(PROXY_AUTHENTICATION_PASSWORD);
            setAuthenticationPassword(old.toCharArray());
        }
        char[] pwd = Keyring.read(PROXY_AUTHENTICATION_PASSWORD);
        return pwd != null ? pwd : new char[0];
    }
    
    public static void setAuthenticationPassword(char[] password) {
        Keyring.save(ProxySettings.PROXY_AUTHENTICATION_PASSWORD, password,
                // XXX consider including getHttpHost and/or getHttpsHost
                NbBundle.getMessage(ProxySettings.class, "ProxySettings.password.description"));    // NOI18N
    }

    public static void addPreferenceChangeListener (PreferenceChangeListener l) {
        getPreferences ().addPreferenceChangeListener (l);
    }
    
    public static void removePreferenceChangeListener (PreferenceChangeListener l) {
        getPreferences ().removePreferenceChangeListener (l);
    }
    
    private static String getPresetNonProxyHosts () {
        if (presetNonProxyHosts == null) {
            presetNonProxyHosts = System.getProperty ("http.nonProxyHosts", "");    // NOI18N
        }
        return presetNonProxyHosts;
    }
    
    private static String getDefaultUserNonProxyHosts () {
        return getModifiedNonProxyHosts (getSystemNonProxyHosts ());
    }

  
    private static String concatProxies(String... proxies) {
        StringBuilder sb = new StringBuilder();
        for (String n : proxies) {
            if (n == null) {
                continue;
            }
            n = n.trim();
            if (n.isEmpty()) {
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '|') { // NOI18N
                if (!n.startsWith("|")) {   // NOI18N
                    sb.append('|');         // NOI18N
                }
            }
            sb.append(n);
        }
        return sb.toString();
    }

    private static String getModifiedNonProxyHosts (String systemPreset) {
        String fromSystem = systemPreset.replace (";", "|").replace (",", "|"); //NOI18N
        String fromUser = getPresetNonProxyHosts () == null ? "" : getPresetNonProxyHosts ().replace (";", "|").replace (",", "|"); //NOI18N
        if (Utilities.isWindows ()) {
            fromSystem = addReguralToNonProxyHosts (fromSystem);
        }
        final String staticNonProxyHosts = NbBundle.getMessage(ProxySettings.class, "StaticNonProxyHosts"); // NOI18N
        String nonProxy = concatProxies(fromUser, fromSystem, staticNonProxyHosts); // NOI18N
        String localhost;
        try {
            localhost = InetAddress.getLocalHost().getHostName();
            if (!"localhost".equals(localhost)) { // NOI18N
                nonProxy = nonProxy + "|" + localhost; // NOI18N
            } else {
                // Avoid this error when hostname == localhost:
                // Error in http.nonProxyHosts system property:  sun.misc.REException: localhost is a duplicate
            }
        }
        catch (UnknownHostException e) {
            // OK. Sometimes a hostname is assigned by DNS, but a computer
            // is later pulled off the network. It may then produce a bogus
            // name for itself which can't actually be resolved. Normally
            // "localhost" is aliased to 127.0.0.1 anyway.
        }
        /* per Milan's agreement it's removed. See issue #89868
        try {
            String localhost2 = InetAddress.getLocalHost().getCanonicalHostName();
            if (!"localhost".equals(localhost2) && !localhost2.equals(localhost)) { // NOI18N
                nonProxy = nonProxy + "|" + localhost2; // NOI18N
            } else {
                // Avoid this error when hostname == localhost:
                // Error in http.nonProxyHosts system property:  sun.misc.REException: localhost is a duplicate
            }
        }
        catch (UnknownHostException e) {
            // OK. Sometimes a hostname is assigned by DNS, but a computer
            // is later pulled off the network. It may then produce a bogus
            // name for itself which can't actually be resolved. Normally
            // "localhost" is aliased to 127.0.0.1 anyway.
        }
         */
        return compactNonProxyHosts (nonProxy);
    }


    // avoid duplicate hosts
    private static String compactNonProxyHosts (String hosts) {
        StringTokenizer st = new StringTokenizer(hosts, ","); //NOI18N
        StringBuilder nonProxyHosts = new StringBuilder();
        while (st.hasMoreTokens()) {
            String h = st.nextToken().trim();
            if (h.length() == 0) {
                continue;
            }
            if (nonProxyHosts.length() > 0) {
                nonProxyHosts.append("|"); // NOI18N
            }
            nonProxyHosts.append(h);
        }
        st = new StringTokenizer (nonProxyHosts.toString(), "|"); //NOI18N
        Set<String> set = new HashSet<String> (); 
        StringBuilder compactedProxyHosts = new StringBuilder();
        while (st.hasMoreTokens ()) {
            String t = st.nextToken ();
            if (set.add (t.toLowerCase (Locale.US))) {
                if (compactedProxyHosts.length() > 0) {
                    compactedProxyHosts.append('|');    // NOI18N
                }
                compactedProxyHosts.append(t);
            }
        }
        return compactedProxyHosts.toString();
    }
    
    private static String addReguralToNonProxyHosts (String nonProxyHost) {
        StringTokenizer st = new StringTokenizer (nonProxyHost, "|");   // NOI18N
        StringBuilder reguralProxyHosts = new StringBuilder();
        while (st.hasMoreTokens ()) {
            String t = st.nextToken ();
            if (t.indexOf ('*') == -1) { //NOI18N
                t = t + '*'; //NOI18N
            }
            if (reguralProxyHosts.length() > 0) 
                reguralProxyHosts.append('|');  // NOI18N
            reguralProxyHosts.append(t);
        }

        return reguralProxyHosts.toString();
    }

    public static String normalizeProxyHost (String proxyHost) {
        if (proxyHost.toLowerCase (Locale.US).startsWith ("http://")) { // NOI18N
            return proxyHost.substring (7, proxyHost.length ());
        } else {
            return proxyHost;
        }
    }
    
    private static InetSocketAddress analyzeProxy(URI uri) {
        Parameters.notNull("uri", uri);     // NOI18N
        List<Proxy> proxies = ProxySelector.getDefault().select(uri);
        assert proxies != null : "ProxySelector cannot return null for " + uri;     // NOI18N
        assert !proxies.isEmpty() : "ProxySelector cannot return empty list for " + uri;    // NOI18N
        String protocol = uri.getScheme();
        Proxy p = proxies.get(0);
        if (Proxy.Type.DIRECT == p.type()) {
            // return null for DIRECT proxy
            return null;
        }
        if (protocol == null
                || ((protocol.startsWith("http") || protocol.equals("ftp")) && Proxy.Type.HTTP == p.type()) // NOI18N
                || !(protocol.startsWith("http") || protocol.equals("ftp"))) {  // NOI18N
            if (p.address() instanceof InetSocketAddress) {
                // check is
                //assert ! ((InetSocketAddress) p.address()).isUnresolved() : p.address() + " must be resolved address.";
                return (InetSocketAddress) p.address();
            } else {
                LOGGER.log(Level.INFO, p.address() + " is not instanceof InetSocketAddress but " + p.address().getClass()); // NOI18N
                return null;
            }
        } else {
            return null;
        }
    }
    
    public static void reload() {
        Reloader reloader = Lookup.getDefault().lookup(Reloader.class);
        reloader.reload();
    }

    @ServiceProvider(service = NetworkSettings.ProxyCredentialsProvider.class, position = 1000)
    public static class NbProxyCredentialsProvider extends NetworkSettings.ProxyCredentialsProvider {

        @Override
        public String getProxyHost(URI u) {
            if (getPreferences() == null) {
                return null;
            }
            InetSocketAddress sa = analyzeProxy(u);
            return sa == null ? null : sa.getHostName();
        }

        @Override
        public String getProxyPort(URI u) {
            if (getPreferences() == null) {
                return null;
            }
            InetSocketAddress sa = analyzeProxy(u);
            return sa == null ? null : Integer.toString(sa.getPort());
        }

        @Override
        protected String getProxyUserName(URI u) {
            if (getPreferences() == null) {
                return null;
            }
            return ProxySettings.getAuthenticationUsername();
        }

        @Override
        protected char[] getProxyPassword(URI u) {
            if (getPreferences() == null) {
                return null;
            }
            return ProxySettings.getAuthenticationPassword();
        }

        @Override
        protected boolean isProxyAuthentication(URI u) {
            if (getPreferences() == null) {
                return false;
            }
            return getPreferences().getBoolean(USE_PROXY_AUTHENTICATION, false);
        }

    }
    
    /** A bridge between <code>o.n.core</code> and <code>core.network</code>.
     * An implementation of this class brings a facility to reload Network Proxy Settings
     * from underlying OS.
     * The module <code>core.network</code> provides a implementation which may be accessible
     * via <code>Lookup.getDefault()</code>. It's not guaranteed any implementation is found on all distribution. 
     * 
     * @since 3.40
     */
    public abstract static class Reloader {
        
        /** Reloads Network Proxy Settings from underlying system.
         *
         */
        public abstract void reload();
    }
}
