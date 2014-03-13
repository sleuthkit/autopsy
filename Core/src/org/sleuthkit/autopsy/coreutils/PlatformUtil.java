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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.swing.filechooser.FileSystemView;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.ptql.ProcessFinder;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Platform utilities
 */
public class PlatformUtil {

    private static String javaPath = null;
    public static final String OS_NAME_UNKNOWN = NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.nameUnknown");
    public static final String OS_VERSION_UNKNOWN = NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.verUnknown");
    public static final String OS_ARCH_UNKNOWN = NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.archUnknown");
    private static volatile long pid = -1;
    private static volatile Sigar sigar = null;
    private static volatile MemoryMXBean memoryManager = null;

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

        File jrePath = new File(getInstallPath() + File.separator + "jre");

        if (jrePath != null && jrePath.exists() && jrePath.isDirectory()) {
            System.out.println(
                    NbBundle.getMessage(PlatformUtil.class,
                                        "PlatformUtil.jrePath.jreDir.msg",
                                        jrePath.getAbsolutePath()));
            javaPath = jrePath.getAbsolutePath() + File.separator + "bin" + File.separator + "java";
        } else {
            //else use system installed java in PATH env variable
            javaPath = "java";

        }

        System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.jrePath.usingJavaPath.msg", javaPath));


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
     * Get RCP project dirs 
     * @return 
     */
    public static List<String> getProjectsDirs() {
        List<String> ret = new ArrayList<String>();
        String projectDir = System.getProperty("netbeans.dirs");
        if (projectDir == null) {
            return ret;
        }
        String [] split = projectDir.split(";");
        if (split == null || split.length == 0) {
            return ret;
        }
        for (String path : split) {
            ret.add(path);
        }
         
         return ret;
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
    public static <T> boolean extractResourceToUserConfigDir(final Class<T> resourceClass, final String resourceFile) throws IOException {
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
     * Get a list of all physical drives attached to the client's machine. Error
     * threshold of 4 non-existent physical drives before giving up.
     *
     * @return list of physical drives
     */
    public static List<LocalDisk> getPhysicalDrives() {
        List<LocalDisk> drives = new ArrayList<LocalDisk>();
        // Windows drives
        if (PlatformUtil.isWindowsOS()) {
            int n = 0;
            int breakCount = 0;
            while (true) {
                String path = "\\\\.\\PhysicalDrive" + n;
                if (canReadDrive(path)) {
                    try {
                        drives.add(new LocalDisk("Drive " + n, path, SleuthkitJNI.findDeviceSize(path)));
                    } catch (TskCoreException ex) {
                        // Don't add the drive because we can't read the size
                    }
                    n++;
                } else {
                    if (breakCount > 4) { // Give up after 4 non-existent drives
                        break;
                    }
                    breakCount++;
                    n++;
                }
            }
            // Linux drives
        } else {
            File dev = new File("/dev/");
            File[] files = dev.listFiles();
            for (File f : files) {
                String name = f.getName();
                if ((name.contains("hd") || name.contains("sd")) && f.canRead() && name.length() == 3) {
                    String path = "/dev/" + name;
                    if (canReadDrive(path)) {
                        try {
                            drives.add(new LocalDisk(path, path, SleuthkitJNI.findDeviceSize(path)));
                        } catch (TskCoreException ex) {
                            // Don't add the drive because we can't read the size
                        }
                    }
                }
            }

        }
        return drives;
    }

    /**
     * Get a list all all the local drives and partitions on the client's
     * machine.
     *
     * @return list of local drives and partitions
     */
    public static List<LocalDisk> getPartitions() {
        List<LocalDisk> drives = new ArrayList<LocalDisk>();
        FileSystemView fsv = FileSystemView.getFileSystemView();
        if (PlatformUtil.isWindowsOS()) {
            File[] f = File.listRoots();
            for (int i = 0; i < f.length; i++) {
                String name = fsv.getSystemDisplayName(f[i]);
                // Check if it is a drive, readable, and not mapped to the network
                if (f[i].canRead() && !name.contains("\\\\") && (fsv.isDrive(f[i]) || fsv.isFloppyDrive(f[i]))) {
                    String path = f[i].getPath();
                    String diskPath = "\\\\.\\" + path.substring(0, path.length() - 1);
                    if (canReadDrive(diskPath)) {
                        drives.add(new LocalDisk(fsv.getSystemDisplayName(f[i]), diskPath, f[i].getTotalSpace()));
                    }
                }
            }
        } else {
            File dev = new File("/dev/");
            File[] files = dev.listFiles();
            for (File f : files) {
                String name = f.getName();
                if ((name.contains("hd") || name.contains("sd")) && f.canRead() && name.length() == 4) {
                    String path = "/dev/" + name;
                    if (canReadDrive(path)) {
                        drives.add(new LocalDisk(path, path, f.getTotalSpace()));
                    }
                }
            }
        }
        return drives;
    }

    /**
     * Are we able to read this drive? Usually related to admin permissions.
     *
     * For all drives and partitions, we are using Java's ability to read the
     * first byte of a drive to determine if TSK would be able to read the drive
     * during the add image process. This returns whether the drive is readable
     * or not far faster than validating if TSK can open the drive. We are
     * assuming the results are almost exactly the same.
     *
     * @param diskPath path to the disk we want to read
     * @return true if we successfully read the first byte
     * @throws IOException if we fail to read
     */
    private static boolean canReadDrive(String diskPath) {
        BufferedInputStream br = null;
        try {
            File tmp = new File(diskPath);
            br = new BufferedInputStream(new FileInputStream(tmp));
            int b = br.read();
            return b != -1;
        } catch (IOException ex) {
            return false;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     * Query and get PID of this process
     *
     * @return PID of this process or -1 if it couldn't be determined
     */
    public static synchronized long getPID() {

        if (pid != -1) {
            return pid;
        }

        try {
            if (sigar == null) {
                sigar = org.sleuthkit.autopsy.corelibs.SigarLoader.getSigar();
            }
            if (sigar != null) {
                pid = sigar.getPid();
            } else {
                System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getPID.sigarNotInit.msg"));
            }
        } catch (Exception e) {
            System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getPID.gen.msg", e.toString()));
        }
        return pid;

    }

    /**
     * Query and get PID of another java process
     *
     * @param sigarSubQuery a sigar subquery to identify a unique java process among
     * other java processes, for example, by class name, use:
     * Args.*.eq=org.jboss.Main more examples here:
     * http://support.hyperic.com/display/SIGAR/PTQL
     *
     * @return PID of a java process or -1 if it couldn't be determined
     */
    public static synchronized long getJavaPID(String sigarSubQuery) {
        long jpid = -1;
        final String sigarQuery = "State.Name.sw=java," + sigarSubQuery;
        try {
            if (sigar == null) {
                sigar = org.sleuthkit.autopsy.corelibs.SigarLoader.getSigar();
            }
            if (sigar != null) {
                ProcessFinder finder = new ProcessFinder(sigar);
                jpid = finder.findSingleProcess(sigarQuery);
            } else {
                System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getJavaPID.sigarNotInit.msg"));
            }
        } catch (Exception e) {
            System.out.println(
                    NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getJavaPID.gen.msg", sigarQuery, e.toString()));
        }
        return jpid;

    }

    /**
     * Query and get PIDs of another java processes matching a query
     *
     * @param sigarSubQuery a sigar subquery to identify a java processes among other
     * java processes, for example, by class name, use: Args.*.eq=org.jboss.Main
     * more examples here: http://support.hyperic.com/display/SIGAR/PTQL
     *
     * @return array of PIDs of a java processes matching the query or null if
     * it couldn't be determined
     */
    public static synchronized long[] getJavaPIDs(String sigarSubQuery) {
        long[] jpids = null;
        final String sigarQuery = "State.Name.sw=java," + sigarSubQuery;
        try {
            if (sigar == null) {
                sigar = org.sleuthkit.autopsy.corelibs.SigarLoader.getSigar();
            }
            if (sigar != null) {
                ProcessFinder finder = new ProcessFinder(sigar);
                jpids = finder.find(sigarQuery);
            } else {
                System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getJavaPIDs.sigarNotInit"));
            }
        } catch (Exception e) {
            System.out.println(
                    NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getJavaPIDs.gen.msg", sigarQuery, e.toString()));
        }
        return jpids;

    }

    /**
     * Kill a process by PID by sending signal to it using Sigar
     *
     * @param pid pid of the process to kill
     */
    public static synchronized void killProcess(long pid) {
        try {
            if (sigar == null) {
                sigar = org.sleuthkit.autopsy.corelibs.SigarLoader.getSigar();
            }
            if (sigar != null) {
                sigar.kill(pid, 9);
            } else {
                System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.killProcess.sigarNotInit.msg"));
            }
        } catch (Exception e) {
            System.out.println(
                    NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.killProcess.gen.msg", pid, e.toString()));
        }

    }

    /**
     * Query and return virtual memory used by the process
     *
     * @return virt memory used in bytes or -1 if couldn't be queried
     */
    public static synchronized long getProcessVirtualMemoryUsed() {
        long pid = getPID();
        long virtMem = -1;

        try {
            if (sigar == null) {
                sigar = org.sleuthkit.autopsy.corelibs.SigarLoader.getSigar();
            }

            if (sigar == null || pid == -1) {
                System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getProcVmUsed.sigarNotInit.msg"));
                return -1;
            }
            virtMem = sigar.getProcMem(pid).getSize();
        } catch (Exception e) {
            System.out.println(NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.getProcVmUsed.gen.msg", e.toString()));
        }

        return virtMem;
    }

    /**
     * Return formatted string with Jvm heap and non-heap memory usage
     *
     * @return formatted string with jvm memory usage
     */
    public static String getJvmMemInfo() {
        synchronized (PlatformUtil.class) {
            if (memoryManager == null) {
                memoryManager = ManagementFactory.getMemoryMXBean();
            }
        }
        final MemoryUsage heap = memoryManager.getHeapMemoryUsage();
        final MemoryUsage nonHeap = memoryManager.getNonHeapMemoryUsage();

        return NbBundle.getMessage(PlatformUtil.class,
                                   "PlatformUtil.getJvmMemInfo.usageText",
                                   heap.toString(), nonHeap.toString());
    }

    /**
     * Return formatted string with physical memory usage
     *
     * @return formatted string with physical memory usage
     */
    public static String getPhysicalMemInfo() {
        final Runtime runTime = Runtime.getRuntime();
        final long maxMemory = runTime.maxMemory();
        final long totalMemory = runTime.totalMemory();
        final long freeMemory = runTime.freeMemory();
        return NbBundle.getMessage(PlatformUtil.class,
                                   "PlatformUtil.getPhysicalMemInfo.usageText",
                                   Long.toString(maxMemory), Long.toString(totalMemory), Long.toString(freeMemory));
    }

    /**
     * Return formatted string with all memory usage (jvm, physical, native)
     *
     * @return formatted string with all memory usage info
     */
    public static String getAllMemUsageInfo() {
//        StringBuilder sb = new StringBuilder();
//        sb.append(PlatformUtil.getPhysicalMemInfo()).append("\n");
//        sb.append(PlatformUtil.getJvmMemInfo()).append("\n");
//        sb.append("Process Virtual Memory: ").append(PlatformUtil.getProcessVirtualMemoryUsed());
//        return sb.toString();
        return NbBundle.getMessage(PlatformUtil.class,
                                   "PlatformUtil.getAllMemUsageInfo.usageText",
                                   PlatformUtil.getPhysicalMemInfo(), PlatformUtil.getJvmMemInfo(),
                                   PlatformUtil.getProcessVirtualMemoryUsed());
    }
}
