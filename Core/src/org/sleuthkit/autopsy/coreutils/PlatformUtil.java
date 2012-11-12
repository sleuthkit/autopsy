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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.swing.filechooser.FileSystemView;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.LocalDisk;
import org.sleuthkit.datamodel.SleuthkitJNI;


/**
 *
 * Platform utilities
 */
public class PlatformUtil {

    private static String javaPath = null;
    public static final String OS_NAME_UNKNOWN = "unknown";
    public static final String OS_VERSION_UNKNOWN = "unknown";
    public static final String OS_ARCH_UNKNOWN = "unknown";

    
           
            

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
            javaPath = getOSFilePath(javaPath);

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

    /**
     * Get user config directory path
     *
     * @return Get user config directory path string
     */
    public static String getUserConfigDirectory() {
        return Places.getUserDirectory() + File.separator + "config";
    }

    /**
     * Get log directory path
     *
     * @return Get log directory path string
     */
    public static String getLogDirectory() {
        return Places.getUserDirectory().getAbsolutePath() + File.separator
                + "var" + File.separator + "log" + File.separator;
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
     * Utility to extract a resource file to a user configuration directory, if
     * it does not exist - useful for setting up default configurations.
     *
     * @param resourceClass class in the same package as the resourceFile to
     * extract
     * @param resourceFile resource file name to extract
     * @return true if extracted, false otherwise (if file already exists)
     * @throws IOException exception thrown if extract the file failed for IO
     * reasons
     */
    public static boolean extractResourceToUserConfigDir(final Class resourceClass, final String resourceFile) throws IOException {
        final File userDir = new File(getUserConfigDirectory());

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

    /**
     * Get operating system name, or OS_NAME_UNKNOWN
     *
     * @return OS name string
     */
    public static String getOSName() {
        return System.getProperty("os.name", OS_NAME_UNKNOWN);
    }

    /**
     * Get operating system version, or OS_VERSION_UNKNOWN
     *
     * @return OS version string
     */
    public static String getOSVersion() {
        return System.getProperty("os.version", OS_VERSION_UNKNOWN);
    }

    /**
     * Get OS arch details, or OS_ARCH_UNKNOWN
     *
     * @return OS arch string
     */
    public static String getOSArch() {
        return System.getProperty("os.arch", OS_ARCH_UNKNOWN);
    }

    /**
     * Check if running on Windows OS
     *
     * @return true if running on Windows OS
     */
    public static boolean isWindowsOS() {
        return PlatformUtil.getOSName().toLowerCase().contains("windows");
    }

    /**
     * Convert file path (quote) for OS specific
     *
     * @param origFilePath
     * @return converted file path
     */
    public static String getOSFilePath(String origFilePath) {
        if (isWindowsOS()) {
            return "\"" + origFilePath + "\"";
        } else {
            return origFilePath;
        }
    }
    
    /**
     * Get a list of all physical drives attached to the client's machine.
     * Error threshold of 4 non-existent physical drives before giving up.
     * 
     * @return list of physical drives
     */
    public static List<LocalDisk> getPhysicalDrives() {
        List<LocalDisk> drives = new ArrayList<LocalDisk>();
        // Windows drives
        if(PlatformUtil.isWindowsOS()) {
            int n = 0;
            int breakCount = 0;
            BufferedInputStream br = null;
            while(true) {
                try {
                    File tmp = new File("\\\\.\\PhysicalDrive" + n);
                    br = new BufferedInputStream(new FileInputStream(tmp));
                    int b = br.read();
                    if(b!=-1) {
                        String path = "\\\\.\\PhysicalDrive" + n;
                        drives.add(new LocalDisk("Storage Device " + n, path, SleuthkitJNI.findDeviceSize(path)));
                        n++;
                    }
                } catch(Exception ex) {
                    if(breakCount > 4) { // Give up after 4 non-existent drives
                        break;
                    }
                    breakCount++;
                    n++;
                } finally {
                    try {
                        if(br != null) {
                            br.close();
                        }
                    } catch (IOException ex) {
                    }
                }
            }
        // Linux drives
        } else {
            File dev = new File("/dev/");
            File[] files = dev.listFiles();
            for(File f: files) {
                String name = f.getName();
                if((name.contains("hd") || name.contains("sd")) && f.canRead()) {
                    drives.add(new LocalDisk("/dev/" + name, "/dev/" + name, f.getTotalSpace()));
                }
            }
            
        }
        return drives;
    }
    
    /**
     * Get a list all all the local drives and partitions on the client's machine.
     * 
     * @return list of local drives and partitions
     */
    public static List<LocalDisk> getPartitions() {
        List<LocalDisk> drives = new ArrayList<LocalDisk>();
        FileSystemView fsv = FileSystemView.getFileSystemView();
        if(PlatformUtil.isWindowsOS()) {
            File[] f = File.listRoots();
            for (int i = 0; i < f.length; i++) {
                String name = fsv.getSystemDisplayName(f[i]);
                // Check if it is a drive, readable, and not mapped to the network
                if(f[i].canRead() && !name.contains("\\\\") && (fsv.isDrive(f[i]) || fsv.isFloppyDrive(f[i]))) {
                    String path = f[i].getPath();
                    String diskPath = "\\\\.\\" + path.substring(0, path.length()-1);
                    drives.add(new LocalDisk(fsv.getSystemDisplayName(f[i]), diskPath, f[i].getTotalSpace()));
                }
            }
        }
        return drives;
    }
}
