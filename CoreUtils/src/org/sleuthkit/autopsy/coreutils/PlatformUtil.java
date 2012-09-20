/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;

/**
 *
 * Platform utilities
 */
public class PlatformUtil {

    private static String javaPath = null;

    /**
     * Get root path where the application is installed
     *
     * @return absolute path string to the install root dir
     */
    public static String getInstallPath() {
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false);
        File rootPath = coreFolder.getParentFile().getParentFile();
        return rootPath.getAbsolutePath();
    }

    /**
     * Get root path where the application modules are installed
     *
     * @return absolute path string to the install modules root dir, or null if
     * not found
     */
    public static String getInstallModulesPath() {
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false);

        File rootPath = coreFolder.getParentFile();
        String modulesPath = rootPath.getAbsolutePath() + File.separator + "modules";
        File modulesPathF = new File(modulesPath);
        if (modulesPathF.exists() && modulesPathF.isDirectory()) {
            return modulesPath;
        } else {
            rootPath = rootPath.getParentFile();
            modulesPath = rootPath.getAbsolutePath() + File.separator + "modules";
            modulesPathF = new File(modulesPath);
            if (modulesPathF.exists() && modulesPathF.isDirectory()) {
                return modulesPath;
            } else {
                return null;
            }
        }

    }

    /**
     * Get root path where the user modules are installed
     *
     * @return absolute path string to the install modules root dir, or null if
     * not found
     */
    public static String getUserModulesPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + "modules";
    }

    /**
     * get file path to the java executable binary use embedded java if
     * available, otherwise use system java in PATH no validation is done if
     * java exists in PATH
     *
     * @return file path to java binary
     */
    public synchronized static String getJavaPath() {
        if (javaPath != null) {
            return javaPath;
        }

        File jrePath = new File(getInstallPath() + File.separator + "jre6");

        if (jrePath != null && jrePath.exists() && jrePath.isDirectory()) {
            System.out.println("Embedded jre6 directory found in: " + jrePath.getAbsolutePath());
            javaPath = jrePath.getAbsolutePath() + File.separator + "bin" + File.separator + "java";
        } else {
            //else use system installed java in PATH env variable
            javaPath = "java";

        }

        System.out.println("Using java binary path: " + javaPath);


        return javaPath;
    }

    /**
     * Get user directory where application wide user settings, cache, temp
     * files are stored
     *
     * @return File object representing user directory
     */
    public static File getUserDirectory() {
        return Places.getUserDirectory();
    }

    public static String getLogDirectory() {
        return Places.getUserDirectory().getAbsolutePath() + "/var/log/";
    }

    public static String getDefaultPlatformFileEncoding() {
        return System.getProperty("file.encoding");
    }

    public static String getDefaultPlatformCharset() {
        return Charset.defaultCharset().name();
    }

    public static String getLogFileEncoding() {
        return Charset.forName("UTF-8").name();
    }

    /**
     * Utility to extract a resource file to a user directory, if it does not
     * exist
     *
     * @param resourceClass class in the same package as the resourceFile to
     * extract
     * @param resourceFile resource file name to extract
     * @return true if extracted, false otherwise (if file already exists)
     * @throws IOException exception thrown if extract the file failed for IO
     * reasons
     */
    public static boolean extractResourceToUserDir(final Class resourceClass, final String resourceFile) throws IOException {
        final File userDir = getUserDirectory();

        final File resourceFileF = new File(userDir + File.separator + resourceFile);
        if (resourceFileF.exists()) {
            return false;
        }

        InputStream inputStream = resourceClass.getResourceAsStream(resourceFile);

        OutputStream out = null;
        InputStream in = null;
        try {

            in = new BufferedInputStream(inputStream);
            OutputStream outFile = new FileOutputStream(resourceFileF);
            out = new BufferedOutputStream(outFile);
            int readBytes = 0;
            while ((readBytes = in.read()) != -1) {
                out.write(readBytes);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        }
        return true;
    }
}
