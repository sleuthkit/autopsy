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
import org.apache.commons.validator.routines.DomainValidator;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;

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
     * Attempt to manually extract the domain from a URL.
     *
     * @param url
     * @return empty string if no domain could be found
     */
    private static String getBaseDomain(String url) {
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

        String base = host;
        try {
            base = DomainTokenizer.getInstance().getDomain(host);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load resources for domain categorization.", ex);
        }

        // verify there are no special characters in there
        if (base.matches(".*[~`!@#$%^&\\*\\(\\)\\+={}\\[\\];:\\?<>,/ ].*")) {
            return "";
        }

        //verify that the base domain actually has a '.', details JIRA-4609
        if (!base.contains(".")) {
            return "";
        }

        return base;
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
        
        String result = (StringUtils.isNotBlank(urlHost))
                ? getBaseDomain(urlHost)
                : getBaseDomain(urlString);

        // if there is a valid url host, get base domain from that host
        // otherwise use urlString and parse the domain
        DomainValidator validator = DomainValidator.getInstance(true);
        if (validator.isValid(result)) {
            return result;
        } else {
            final String validIpAddressRegex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
            if (result.matches(validIpAddressRegex)) {
                return result;
            } else {
                return "";
            }
        }
    }

}
