/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.filechooser.FileSystemView;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
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

    private static final String PYTHON_MODULES_SUBDIRECTORY = "python_modules"; //NON-NLS
    private static final String CLASSIFIERS_SUBDIRECTORY = "object_detection_classifiers"; //NON-NLS
    private static final String OCR_LANGUAGE_SUBDIRECTORY = "ocr_language_packs"; //NON-NLS
    private static final String OCR_LANGUAGE_PACK_EXT = "traineddata";
    private static String javaPath = null;
    public static final String OS_NAME_UNKNOWN = NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.nameUnknown");
    public static final String OS_VERSION_UNKNOWN = NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.verUnknown");
    public static final String OS_ARCH_UNKNOWN = NbBundle.getMessage(PlatformUtil.class, "PlatformUtil.archUnknown");
    private static volatile long pid = -1;
    private static volatile MemoryMXBean memoryManager = null;

    /**
     * Get root path where the application is installed
     *
     * @return absolute path string to the install root dir
     */
    public static String getInstallPath() {
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false); //NON-NLS
        File rootPath = coreFolder.getParentFile().getParentFile();
        return rootPath.getAbsolutePath();
    }

    /**
     * Get root path where the application modules are installed
     *
     * @return absolute path string to the install modules root dir, or null if
     *         not found
     */
    public static String getInstallModulesPath() {
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false); //NON-NLS

        File rootPath = coreFolder.getParentFile();
        String modulesPath = rootPath.getAbsolutePath() + File.separator + "modules"; //NON-NLS
        File modulesPathF = new File(modulesPath);
        if (modulesPathF.exists() && modulesPathF.isDirectory()) {
            return modulesPath;
        } else {
            rootPath = rootPath.getParentFile();
            modulesPath = rootPath.getAbsolutePath() + File.separator + "modules"; //NON-NLS
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
     *         not found
     */
    public static String getUserModulesPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + "modules"; //NON-NLS
    }

    /**
     * Get root path where the user Python modules are installed.
     *
     * @return Absolute path to the Python modules root directory.
     */
    public static String getUserPythonModulesPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + PYTHON_MODULES_SUBDIRECTORY;
    }

    /**
     * Get root path where the user's Ocr language packs are stored.
     *
     * @return Absolute path to the Ocr language packs root directory.
     */
    public static String getOcrLanguagePacksPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + OCR_LANGUAGE_SUBDIRECTORY;
    }

    /**
     * Get the names of the language packs installed at the user directory.
     *
     * @return List of language packs base names
     */
    public static List<String> getOcrLanguagePacks() {
        File languagePackRootDir = new File(getOcrLanguagePacksPath());

        List<String> languagePacks = new ArrayList<>();
        for (File languagePack : languagePackRootDir.listFiles()) {
            String fileExt = FilenameUtils.getExtension(languagePack.getName());
            if (!languagePack.isDirectory() && OCR_LANGUAGE_PACK_EXT.equals(fileExt)) {
                String packageName = FilenameUtils.getBaseName(languagePack.getName());
                languagePacks.add(packageName);
            }
        }

        return languagePacks;
    }

    /**
     * Get root path where the user's object detection classifiers are stored.
     *
     * @return Absolute path to the object detection classifiers root directory.
     */
    public static String getObjectDetectionClassifierPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + CLASSIFIERS_SUBDIRECTORY;
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

        // by default, use Java that came with Autopsy
        File jrePath = new File(getInstallPath() + File.separator + "jre");
        if (jrePath.exists() && jrePath.isDirectory()) {
            System.out.println(
                    NbBundle.getMessage(PlatformUtil.class,
                            "PlatformUtil.jrePath.jreDir.msg",
                            jrePath.getAbsolutePath()));
            javaPath = jrePath.getAbsolutePath() + File.separator + "bin" + File.separator + "java"; //NON-NLS
        } else if (System.getProperty("java.home") != null && !(System.getProperty("java.home").isEmpty())) {
            // if OS knows where Java is located
            return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"; //NON-NLS
        } else {
            //else use system installed java in PATH env variable
            javaPath = "java"; //NON-NLS
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
     *
     * @return
     */
    public static List<String> getProjectsDirs() {
        List<String> ret = new ArrayList<>();
        String projectDir = System.getProperty("netbeans.dirs");
        if (projectDir == null) {
            return ret;
        }
        String[] split = projectDir.split(";");
        if (split == null || split.length == 0) {
            return ret;
        }
        ret.addAll(Arrays.asList(split));

        return ret;
    }

    /**
     * Get user config directory path
     *
     * @return Get user config directory path string
     */
    public static String getUserConfigDirectory() {
        return Places.getUserDirectory() + File.separator + "config"; //NON-NLS
    }

    /**
     * Get module config directory path
     *
     * @return Get module config directory path string
     */
    public static String getModuleConfigDirectory() {
        return Paths.get(getUserConfigDirectory(), "ModuleConfig").toString();
    }

    /**
     * Get log directory path
     *
     * @return Get log directory path string
     */
    public static String getLogDirectory() {
        return Places.getUserDirectory().getAbsolutePath() + File.separator
                + "var" + File.separator + "log" + File.separator; //NON-NLS
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
     * @param resourceClass    class in the same package as the resourceFile to
     *                         extract
     * @param resourceFileName Name of the resource file to extract
     * @param overWrite        true to overwrite an existing resource
     *
     * @return true if extracted, false otherwise (if file already exists)
     *
     * @throws IOException exception thrown if extract the file failed for IO
     *                     reasons
     */
    public static <T> boolean extractResourceToUserConfigDir(final Class<T> resourceClass, final String resourceFileName, boolean overWrite) throws IOException {
        Path resourceFilePath = Paths.get(getUserConfigDirectory(), resourceFileName);
        final File resourceFile = resourceFilePath.toFile();
        if (resourceFile.exists() && !overWrite) {
            return false;
        }

        InputStream inputStream = resourceClass.getResourceAsStream(resourceFileName);
        if (null == inputStream) {
            return false;
        }

        resourceFile.getParentFile().mkdirs();
        try (InputStream in = new BufferedInputStream(inputStream)) {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(resourceFile))) {
                int readBytes;
                while ((readBytes = in.read()) != -1) {
                    out.write(readBytes);
                }
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
        return System.getProperty("os.name", OS_NAME_UNKNOWN); //NON-NLS
    }

    /**
     * Get operating system version, or OS_VERSION_UNKNOWN
     *
     * @return OS version string
     */
    public static String getOSVersion() {
        return System.getProperty("os.version", OS_VERSION_UNKNOWN); //NON-NLS
    }

    /**
     * Get OS arch details, or OS_ARCH_UNKNOWN
     *
     * @return OS arch string
     */
    public static String getOSArch() {
        return System.getProperty("os.arch", OS_ARCH_UNKNOWN); //NON-NLS
    }

    /**
     * Check if running on Windows OS
     *
     * @return true if running on Windows OS
     */
    public static boolean isWindowsOS() {
        return PlatformUtil.getOSName().toLowerCase().contains("windows"); //NON-NLS
    }

    /**
     * Convert file path (quote) for OS specific
     *
     * @param origFilePath
     *
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
     * Attempts to determine whether the operating system is a 64-bit operating
     * system. May not be completely reliable for non-Windows operating systems.
     *
     * @return True if the operating system is definitely a 64-bit operating
     *         system, false otherwise.
     */
    public static boolean is64BitOS() {
        if (System.getProperty("os.name").contains("Windows")) { //NON-NLS
            return (System.getenv("ProgramFiles(x86)") != null); //NON-NLS
        } else {
            return (System.getProperty("os.arch").contains("64")); //NON-NLS
        }
    }

    /**
     * Attempts to determine whether the JVM is 64-bit or 32-bit. May not be
     * completely reliable for non-Windows operating systems.
     *
     * @return True if the JVM is 64-bit. False otherwise.
     */
    public static boolean is64BitJVM() {
        return (System.getProperty("sun.arch.data.model").equals("64"));
    }

    /**
     * Get a list of all physical drives attached to the client's machine. Error
     * threshold of 4 non-existent physical drives before giving up.
     *
     * @return list of physical drives
     */
    public static List<LocalDisk> getPhysicalDrives() {
        List<LocalDisk> drives = new ArrayList<>();
        // Windows drives
        if (PlatformUtil.isWindowsOS()) {
            int n = 0;
            int breakCount = 0;
            while (true) {
                String path = "\\\\.\\PhysicalDrive" + n; //NON-NLS
                if (canReadDrive(path)) {
                    try {
                        drives.add(new LocalDisk("Drive " + n, path, SleuthkitJNI.findDeviceSize(path))); //NON-NLS
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
                if ((name.contains("hd") || name.contains("sd") || name.contains("disk")) && f.canRead() && name.length() <= 5) { //NON-NLS
                    String path = "/dev/" + name; //NON-NLS
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
        List<LocalDisk> drives = new ArrayList<>();
        FileSystemView fsv = FileSystemView.getFileSystemView();
        if (PlatformUtil.isWindowsOS()) {
            File[] f = File.listRoots();
            for (File f1 : f) {
                String name = fsv.getSystemDisplayName(f1);
                // Check if it is a drive, readable, and not mapped to the network
                if (f1.canRead() && !name.contains("\\\\") && (fsv.isDrive(f1) || fsv.isFloppyDrive(f1))) {
                    String path = f1.getPath();
                    String diskPath = "\\\\.\\" + path.substring(0, path.length() - 1);
                    if (canReadDrive(diskPath)) {
                        drives.add(new LocalDisk(fsv.getSystemDisplayName(f1), diskPath, f1.getTotalSpace()));
                    }
                }
            }
        } else {
            File dev = new File("/dev/");
            File[] files = dev.listFiles();
            for (File f : files) {
                String name = f.getName();
                if ((name.contains("hd") || name.contains("sd") || name.contains("disk")) && f.canRead() && name.length() <= 7) { //NON-NLS
                    String path = "/dev/" + name; //NON-NLS
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
     *
     * @return true if we successfully read the first byte
     *
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
        // taken from https://stackoverflow.com/a/7303433/2375948
        return ProcessHandle.current().pid();
    }

    /**
     * Query and get PID of another java process
     *
     * @param sigarSubQuery a sigar subquery to identify a unique java process
     *                      among other java processes, for example, by class
     *                      name, use: Args.*.eq=org.jboss.Main more examples
     *                      here: http://support.hyperic.com/display/SIGAR/PTQL
     *
     * @return PID of a java process or -1 if it couldn't be determined
     */
    public static synchronized long getJavaPID(String sigarSubQuery) {
        long[] pids = getJavaPIDs(sigarSubQuery);
        return pids == null || pids.length < 1
                ? -1
                : pids[0];
    }
    
    /**
     * Performs a simple conversion of a sql like statement to regex replacing
     * '%' and '_' in a like statement with regex equivalents.
     *
     * @param originalLikeStatement The original like statement.
     * @return The equivalent regex string.
     */
    private static String convertSqlLikeToRegex(String originalLikeStatement) {
        if (originalLikeStatement == null) {
            return "";
        }

        Map<Character, String> likeEscapeSequences = new HashMap<>() {
            {
                put('%', ".*");
                put('_', ".");
            }
        };

        String regexQuoted = Pattern.quote(originalLikeStatement);
        char[] charArr = regexQuoted.toCharArray();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < charArr.length; i++) {
            char curChar = charArr[i];
            String regexReplacement = likeEscapeSequences.get(curChar);
            if (regexReplacement == null) {
                sb.append(curChar);
            } else {
                Character nextChar = charArr.length > i + 1 ? charArr[i + 1] : null;
                if (nextChar != null && curChar == nextChar) {
                    sb.append(curChar);
                    i++;
                } else {
                    sb.append(regexReplacement);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Query and get PIDs of another java processes matching a query
     *
     * @param argsSubQuery A like query for command line arguments
     *
     * @return array of PIDs of a java processes matching the query or null if
     *         it couldn't be determined
     */
    public static synchronized long[] getJavaPIDs(String argsSubQuery) {
        try {
        if (isWindowsOS()) {
            
            ProcessBuilder pb = new ProcessBuilder("wmic process where \"name='java.exe' AND commandline LIKE '%" + argsSubQuery + "%'\" get ProcessID");
            String output = IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
            String[] lines = output.split("\\r?\\n");
            
            return Stream.of(lines).skip(1).map(ln -> {
                if (ln == null || ln.trim().isEmpty()) {
                    return null;
                }
                
                try {
                    return Long.parseLong(ln.trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
            })
                    .filter(num -> num != null)
                    .mapToLong(l -> l)
                    .toArray();

        } else {
            String sigarRegexQuery = convertSqlLikeToRegex(argsSubQuery);
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "ps -ef | grep -E 'java.*" + sigarRegexQuery + ".*'");
            String output = IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
            List<String> lines = Arrays.asList(output.split("\\r?\\n"));
            
            if (lines.size() > 0) {
                // ignore last one as it will be the same as this command
                lines.remove(lines.size() - 1);
            }
            
            return lines.stream().skip(1).map(ln -> {
                if (ln == null || ln.trim().isEmpty()) {
                    return null;
                }
                
                ln = ln.trim();
                
                String[] pieces = ln.split("\\s*");
                if (pieces.length < 2) {
                    return null;
                }
                
                try {
                    return Long.parseLong(pieces[1]);
                } catch (NumberFormatException ex) {
                    return null;
                }
            })
                    .filter(num -> num != null)
                    .mapToLong(l -> l)
                    .toArray();
        }
        } catch (IOException ex) {
            System.out.println("An exception occurred while fetching java pids with query: " + argsSubQuery + " with IO Exception: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Kill a process by PID by sending signal to it using Sigar
     *
     * @param pid pid of the process to kill
     */
    public static synchronized void killProcess(long pid) {
        String cmd = isWindowsOS()
                ? "taskkill /F /PID " + pid
                : "kill " + pid;

        try {
            Runtime.getRuntime().exec(cmd);
        } catch (IOException ex) {
            System.out.println("An exception occurred while killing process pid: " + pid);
            ex.printStackTrace();
        }
    }

    /**
     * Query and return virtual memory used by the process
     *
     * @return virt memory used in bytes or -1 if couldn't be queried
     */
    public static synchronized long getProcessVirtualMemoryUsed() {
        // taken from https://stackoverflow.com/a/17376879/2375948
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
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
        return NbBundle.getMessage(PlatformUtil.class,
                "PlatformUtil.getAllMemUsageInfo.usageText",
                PlatformUtil.getPhysicalMemInfo(), PlatformUtil.getJvmMemInfo(),
                PlatformUtil.getProcessVirtualMemoryUsed());
    }
}
