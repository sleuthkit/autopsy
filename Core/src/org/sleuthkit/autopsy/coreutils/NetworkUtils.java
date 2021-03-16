/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;

public class NetworkUtils {

    private static final Logger logger = Logger.getLogger(NetworkUtils.class.getName());

    private NetworkUtils() {
    }

    /**
     * Set the host name variable. Sometimes the network can be finicky, so the
     * answer returned by getHostName() could throw an exception or be null.
     * Have it read the environment variable if getHostName() is unsuccessful.
     *
     * @return the local host name
     */
    public static String getLocalHostName() {
        String hostName = "";
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // getLocalHost().getHostName() can fail in some situations. 
            // Use environment variable if so.
            hostName = System.getenv("COMPUTERNAME"); //NON-NLS
        }
        if (hostName == null || hostName.isEmpty()) {
            hostName = System.getenv("COMPUTERNAME"); //NON-NLS
        }
        return hostName;
    }

    /**
     * Return the host in the url or empty string if no host can be determined.
     *
     * @param url The original url-like item.
     * @return The host or empty string if no host can be determined.
     */
    public static String extractHost(String url) {
        if (url == null) {
            return "";
        }

        String host = null;

        //strip protocol
        String cleanUrl = url.replaceFirst(".*:\\/\\/", "");

        //strip after slashes
        String dirToks[] = cleanUrl.split("\\/");
        if (dirToks.length > 0) {
            host = dirToks[0];
        } else {
            host = cleanUrl;
        }

        // verify there are no special characters in there
        if (host.matches(".*[~`!@#$%^&\\*\\(\\)\\+={}\\[\\];:\\?<>,/ ].*")) {
            return "";
        }

        //verify that the base domain actually has a '.', details JIRA-4609
        if (!host.contains(".")) {
            return "";
        }

        return host;
    }

    /**
     * Attempt to manually extract the domain from a URL.
     *
     * @param url
     * @return empty string if no domain could be found
     */
    private static String getBaseDomain(String url) {
        String base = extractHost(url);
        if (StringUtils.isBlank(base)) {
            return "";
        }

        try {
            return DomainTokenizer.getInstance().getDomain(base);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load resources for domain categorization.", ex);
            return "";
        }
    }

    /**
     * Attempt to extract the domain from a URL. Will start by using the
     * built-in URL class, and if that fails will try to extract it manually.
     *
     * @param urlString The URL to extract the domain from
     * @return empty string if no domain name was found
     */
    public static String extractDomain(String urlString) {
        if (urlString == null) {
            return "";
        }
        String urlHost = null;

        try {
            URL url = new URL(urlString);
            urlHost = url.getHost();
        } catch (MalformedURLException ex) {
            //do not log if not a valid URL - we will try to extract it ourselves
        }

        // if there is a valid url host, get base domain from that host
        // otherwise use urlString and parse the domain
        String result = (StringUtils.isNotBlank(urlHost))
                ? getBaseDomain(urlHost)
                : getBaseDomain(urlString);

        return result;
    }

}
