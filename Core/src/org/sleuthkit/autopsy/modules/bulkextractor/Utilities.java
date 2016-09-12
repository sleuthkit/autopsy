/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.bulkextractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModule;

/**
 * Utility methods for the Bulk Extractor ingest module.
 */
@NbBundle.Messages({
    "Utilities.unsupportedOS.message=Bulk Extractor Module is only supported for Windows platforms.",
    "Utilities.missingBulkExtractor.message=Unable to locate Bulk Extractor executable.",
    "Utilities.cannotExecuteBulkExtractor.message=Unable to execute Bulk Extractor.",
    "# {0} - output directory name", "Utilities.cannotCreateOutputDir.message.with.param=Unable to create output directory: {0}."
})
final class Utilities {

    private static final String OUTPUT_SUB_DIRECTORY = "BulkExtractor"; // NON-NLS
    private static final String REPORT_NAME_BASE = "Bulk Extractor Scan"; // NON-NLS
    private static final String BULK_EXTRACTOR_DIR = "bulk_extractor_1_5_3"; // NON-NLS
    private static final String BULK_EXTRACTOR_VERSION = "1.5.3"; // NON-NLS
    private static final String BULK_EXTRACTOR_32_BIT_DIR = "32-bit"; // NON-NLS
    private static final String BULK_EXTRACTOR_64_BIT_DIR = "64-bit"; // NON-NLS
    private static final String BULK_EXTRACTOR_WINDOWS_EXE = "bulk_extractor.exe"; // NON-NLS

    /**
     * Gets the ingest module name.
     *
     * @return A name string.
     */
    static String getModuleName() {
        return NbBundle.getMessage(BulkExtractorIngestModuleFactory.class, "Utilities.moduleName");
    }
    
    /**
     * Gets the version of the module 
     * @return Version string
     */
    static String getVersion() {
        return BULK_EXTRACTOR_VERSION;
    }

    /**
     * Creates a report name based on the name of file presumed to be scanned by
     * Bulk Extractor.
     *
     * @param scannedFileName The name of the file.
     *
     * @return The report name string.
     */
    static String getReportName(String scannedFileName) {
        return scannedFileName + " " + Utilities.REPORT_NAME_BASE;
    }

    /**
     * Creates the output directory for this module for the current case and
     * data source, if it does not already exist.
     *
     * @return The absolute path of the output directory.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    synchronized static Path createOutputDirectoryForDataSource(String subDirName) throws IngestModule.IngestModuleException {
        Path path = Paths.get(Case.getCurrentCase().getModuleDirectory(), Utilities.OUTPUT_SUB_DIRECTORY, subDirName);
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException ex) {
            // No worries.
        } catch (IOException | SecurityException | UnsupportedOperationException ex) {
            throw new IngestModule.IngestModuleException(Bundle.Utilities_cannotCreateOutputDir_message_with_param(path.toString()), ex);
        }
        return path;
    }

    /**
     * Creates a path for an output subdirectory to contain the results of
     * scanning a file with Bulk Extractor.
     *
     * @param rootOutputDirPath The root output directory path
     * @param objectId          The object id of the file to be scanned.
     *
     * @return The path.
     */
    static Path getOutputSubdirectoryPath(Path rootOutputDirPath, long objectId) {
        StringBuilder nameBuilder = new StringBuilder(Long.toString(objectId));
        nameBuilder.append("_"); // NON-NLS
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS");  // NON-NLS
        Date date = new Date();
        nameBuilder.append(dateFormat.format(date));
        return Paths.get(rootOutputDirPath.toAbsolutePath().toString(), nameBuilder.toString());
    }

    /**
     * Locates the Bulk Extractor executable.
     *
     * @return The path of the executable.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    static Path locateBulkExtractorExecutable() throws IngestModule.IngestModuleException {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
            throw new IngestModule.IngestModuleException(Bundle.Utilities_unsupportedOS_message());
        }

        // Build the expected path to either the 32-bit or 64-bit version of the 
        // Bulk Extractor executable.
        final File beRoot = InstalledFileLocator.getDefault().locate(Utilities.BULK_EXTRACTOR_DIR, Utilities.class.getPackage().getName(), false);

        Path executablePath;
        if (PlatformUtil.is64BitOS()) {
            executablePath = Paths.get(
                    beRoot.getAbsolutePath(),
                    Utilities.BULK_EXTRACTOR_64_BIT_DIR,
                    Utilities.BULK_EXTRACTOR_WINDOWS_EXE);
        } else {
            executablePath = Paths.get(
                    beRoot.getAbsolutePath(),
                    Utilities.BULK_EXTRACTOR_32_BIT_DIR,
                    Utilities.BULK_EXTRACTOR_WINDOWS_EXE);
        }

        // Make sure the executable exists at the expected location and that it  
        // can be run.
        File bulkExtractor = executablePath.toFile();
        if (null == bulkExtractor || !bulkExtractor.exists()) {
            throw new IngestModule.IngestModuleException(Bundle.Utilities_missingBulkExtractor_message());
        }
        if (!bulkExtractor.canExecute()) {
            throw new IngestModule.IngestModuleException(Bundle.Utilities_cannotExecuteBulkExtractor_message());
        }

        return executablePath;
    }

    /**
     * Runs the Bulk Extractor executable.
     *
     * @param bulkExtractorPath The path to the Bulk Extractor executable.
     * @param outputDirPath     The path to the module output directory.
     * @param inputFilePath     The path to the input file.
     * @param terminator
     *
     * @return The exit value of the subprocess used to run Bulk Extractor.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    static int runBulkExtractor(Path bulkExtractorPath, Path outputDirPath, Path inputFilePath, ProcessTerminator terminator) throws SecurityException, IOException, InterruptedException {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(bulkExtractorPath.toAbsolutePath().toString());
        commandLine.add("-e");
        commandLine.add("facebook");
        commandLine.add("-o");
        commandLine.add(outputDirPath.toAbsolutePath().toString());
        commandLine.add(inputFilePath.toAbsolutePath().toString());
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);

        // redirect BE stdout and stderr to txt files
        Path logFileName = Paths.get(outputDirPath.getParent().toString(), outputDirPath.getFileName().toString() + "_out.txt");
        File logFile = new File(logFileName.toString());
        Path errFileName = Paths.get(outputDirPath.getParent().toString(), outputDirPath.getFileName().toString() + "_err.txt");
        File errFile = new File(errFileName.toString());
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

        return ExecUtil.execute(processBuilder, terminator);
    }

    /**
     * Determines whether or not a directory is empty.
     *
     * @param directoryPath The path to the directory to inspect.
     *
     * @return True if the directory is empty, false otherwise.
     *
     * @throws IllegalArgumentException
     * @throws IOException
     */
    static boolean isDirectoryEmpty(final Path directoryPath) throws IllegalArgumentException, IOException {
        if (!Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("The directoryPath argument must be a directory path"); // NON-NLS
        }
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directoryPath)) {
            return !dirStream.iterator().hasNext();
        }
    }

}
