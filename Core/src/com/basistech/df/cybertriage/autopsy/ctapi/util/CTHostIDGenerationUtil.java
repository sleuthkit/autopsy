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
package com.basistech.df.cybertriage.autopsy.ctapi.util;

import com.license4j.HardwareID;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Utility class to generate license hostID and Target hostID for malware scan
 *
 * @author rishwanth
 */
public class CTHostIDGenerationUtil {

    private static final Logger LOGGER = Logger.getLogger(CTHostIDGenerationUtil.class.getName());
    private static final String USER_NAME = System.getProperty("user.name");
    private static String cachedId = "";

    /**
     * Host ID Algorithm: Get MAC address from License4J. Get MD5 hash of it and
     * grab the first 16 characters of the hash. Get user name that Cyber Triage
     * is running as. MD5 hash of user name. Grab first 16 characters.
     * Concatenate them and separate with underscore. Example:
     * c84f70d1baf96420_7d7519bf21602c24
     *
     * @return
     */
    public static String generateLicenseHostID() {
        if (StringUtils.isBlank(cachedId)) {
            try {
                String hostName = StringUtils.defaultString(InetAddress.getLocalHost().getCanonicalHostName());
                String macAddressMd5 = StringUtils.isNotBlank(HardwareID.getHardwareIDFromEthernetAddress())
                        ? Md5HashUtil.getMD5MessageDigest(HardwareID.getHardwareIDFromEthernetAddress()).substring(0, 16)
                        : Md5HashUtil.getMD5MessageDigest(hostName).substring(0, 16);

                String usernameMd5 = StringUtils.isNotBlank(USER_NAME)
                        ? Md5HashUtil.getMD5MessageDigest(USER_NAME).substring(0, 16)
                        : Md5HashUtil.getMD5MessageDigest(hostName).substring(0, 16);

                cachedId = macAddressMd5 + "_" + usernameMd5;

            } catch (UnknownHostException ex) {
                LOGGER.log(Level.WARNING, "Unable to determine host name.", ex);
            }
        }

        return cachedId;
    }
}
