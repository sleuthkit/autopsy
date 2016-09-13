/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.openide.modules.InstalledFileLocator;

/**
 * Utility methods for working with data sources.
 */
public class DataSourceUtils {

    private static final String TSK_IS_DRIVE_IMAGE_TOOL_DIR = "tsk_isImageTool";
    private static final String TSK_IS_DRIVE_IMAGE_TOOL_EXE = "tsk_isImageTool.exe";
    private static Path tskIsImageToolExePath = null;

    /**
     * Gets the path to the copy of the SleuthKit executable that is used to
     * determine whether or not a drive image has a file system. The tool is
     * installed during Autopsy installation, so it is assumed that it only
     * needs to be found on start up.
     *
     * @return The path to the executable.
     */
    private static boolean locateTskIsImageToolExecutable() {

        // check if we have previously located the tool
        if (tskIsImageToolExePath != null) {
            return true;
        }

        if (!PlatformUtil.isWindowsOS()) {
            // requires a Windows operating system to run
            return false;
        }

        final File folder = InstalledFileLocator.getDefault().locate(TSK_IS_DRIVE_IMAGE_TOOL_DIR, DataSourceUtils.class.getPackage().getName(), false);
        if (null == folder) {
            // Unable to locate SleuthKit image tool installation folder
            return false;
        }

        Path executablePath = Paths.get(folder.getAbsolutePath(), TSK_IS_DRIVE_IMAGE_TOOL_EXE);
        File executable = executablePath.toFile();
        if (!executable.exists()) {
            // Unable to locate SleuthKit image tool
            return false;
        }

        if (!executable.canExecute()) {
            // Unable to run SleuthKit image tool
            return false;
        }

        // located the tool
        tskIsImageToolExePath = executablePath;
        return true;
    }

    /**
     * Uses the installed tsk_isImageTool executable to determine whether a
     * potential data source has a file system.
     *
     * @param outputDirectoryPath The path to the folder where tsk_isImageTool
     *                            log and error output will be redirected to.
     * @param dataSourcePath      The path to the data source.
     *
     * @return True or false.
     *
     * @throws IOException if an error occurs while trying to determine if the
     *                     data source has a file system.
     */
    public static boolean imageHasFileSystem(Path outputDirectoryPath, Path dataSourcePath) throws IOException {
        Path logFileName = Paths.get(outputDirectoryPath.toString(), "tsk_isImageTool.log");
        File logFile = new File(logFileName.toString());
        Path errFileName = Paths.get(outputDirectoryPath.toString(), "tsk_isImageTool_err.log");
        File errFile = new File(errFileName.toString());

        if (!locateTskIsImageToolExecutable()) {
            // ELTODO how to let user know? throw exception? All this logic will move to JNI anyway...
            return false;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "\"" + tskIsImageToolExePath.toString() + "\"",
                "\"" + dataSourcePath + "\"");
        File directory = new File(tskIsImageToolExePath.getParent().toString());
        processBuilder.directory(directory);
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        int exitValue = ExecUtil.execute(processBuilder);
        Files.delete(logFileName);
        Files.delete(errFileName);
        return (exitValue == 0);
    }
}
