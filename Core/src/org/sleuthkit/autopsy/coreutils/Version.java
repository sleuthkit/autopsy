/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility to get version and build settings set at build time
 */
public class Version {

    private static Properties versionProperties;

    private Version() {
    }

    public enum Type {

        RELEASE, DEVELOPMENT;
    }

    private static void loadVersionProperty() {
        if (versionProperties != null) {
            return;
        }

        versionProperties = new Properties();
        try {
            InputStream inputStream = Version.class.getResourceAsStream("Version.properties"); //NON-NLS
            versionProperties.load(inputStream);
        } catch (IOException e) {
            versionProperties = null;
        }

    }

    private static String getVersionProperty(String property) {
        loadVersionProperty();
        if (versionProperties == null) {
            return "";
        } else {
            return versionProperties.getProperty(property);
        }
    }

    /**
     * Get the application version as set at build time
     *
     * @return application version string
     */
    public static String getVersion() {
        return getVersionProperty("app.version");
    }

    /**
     * Get the application name as set at build time
     *
     * @return the application name string
     */
    public static String getName() {
        return getVersionProperty("app.name");
    }

    /**
     * Get the application build type as set at build time
     *
     * @return the application build type
     */
    public static Version.Type getBuildType() {
        String valueProp = getVersionProperty("build.type");
        return Type.valueOf(valueProp);
    }

    public static String getJavaRuntimeVersion() {
        return System.getProperty("java.runtime.version");
    }

    public static String getNetbeansBuild() {
        return System.getProperty("netbeans.buildnumber");
    }

    public static String getNetbeansProductVersion() {
        return System.getProperty("netbeans.productversion");
    }
}
