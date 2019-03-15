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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

public class NetworkUtils {
    
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

        //get the domain part from host (last 2)
        StringTokenizer tok = new StringTokenizer(host, ".");
        StringBuilder hostB = new StringBuilder();
        int toks = tok.countTokens();

        for (int count = 0; count < toks; ++count) {
            String part = tok.nextToken();
            int diff = toks - count;
            if (diff < 3) {
                hostB.append(part);
            }
            if (diff == 2) {
                hostB.append(".");
            }
        }
        
        
        String base = hostB.toString();
        // verify there are no special characters in there
        if (base.matches(".*[~`!@#$%^&\\*\\(\\)\\+={}\\[\\];:\\?<>,/ ].*")) {
            return "";
        }
        
        //verify that the base domain actually has a '.', details JIRA-4609
        if(!base.contains(".")) {
            return "";
        }
        
        return base;
    }

    /**
     * Attempt to extract the domain from a URL.
     * Will start by using the built-in URL class, and if that fails will
     * try to extract it manually.
     * 
     * @param urlString The URL to extract the domain from
     * @return empty string if no domain name was found
     */
    public static String extractDomain(String urlString) {
        if (urlString == null) {
            return "";
        }
        String result = "";

        try {
            URL url = new URL(urlString);
            result = url.getHost();
        } catch (MalformedURLException ex) {
            //do not log if not a valid URL - we will try to extract it ourselves
        }

        //was not a valid URL, try a less picky method
        if (result == null || result.trim().isEmpty()) {
            return getBaseDomain(urlString);
        }
        return result;
    }
    
}
