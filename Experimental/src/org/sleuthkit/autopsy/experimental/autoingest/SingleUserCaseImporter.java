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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import static javax.security.auth.callback.ConfirmationCallback.OK_CANCEL_OPTION;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.SingleUserCaseConverter;
import org.sleuthkit.autopsy.casemodule.SingleUserCaseConverter.ImportCaseData;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

public class SingleUserCaseImporter implements Runnable {

    private static final String AIM_LOG_FILE_NAME = "auto_ingest_log.txt"; //NON-NLS
    static final String CASE_IMPORT_LOG_FILE = "case_import_log.txt"; //NON-NLS
    private static final String DOTAUT = ".aut"; //NON-NLS
    private static final String SEP = System.getProperty("line.separator");
    private static final String logDateFormat = "yyyy/MM/dd HH:mm:ss"; //NON-NLS
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(logDateFormat);
    private final Object threadWaitNotifyLock = new Object();
    private final ImportDoneCallback notifyOnComplete;
    private final Path baseImageInput;
    private final Path baseCaseInput;
    private final Path baseImageOutput;
    private final Path baseCaseOutput;
    private final boolean copyImages;
    private final boolean deleteCase;
    private String oldCaseName = null;
    private String newCaseName = null;
    private int userAnswer = 0;
    private PrintWriter writer;

    public SingleUserCaseImporter(String baseImageInput, String baseCaseInput, String baseImageOutput, String baseCaseOutput, boolean copyImages, boolean deleteCase, ImportDoneCallback callback) {
        this.baseImageInput = Paths.get(baseImageInput);
        this.baseCaseInput = Paths.get(baseCaseInput);
        this.baseImageOutput = Paths.get(baseImageOutput);
        this.baseCaseOutput = Paths.get(baseCaseOutput);
        this.copyImages = copyImages;
        this.deleteCase = deleteCase;
        this.notifyOnComplete = callback;
    }

    /**
     * This causes iteration over all .aut files in the baseCaseInput path,
     * calling SingleUserCaseConverter.importCase() for each one.
     */
    public void importCases() throws Exception {
        openLog(baseCaseOutput.toFile());
        log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.StartingBatch")
                + baseCaseInput.toString() + " "
                + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to")
                + " " + baseCaseOutput.toString()); //NON-NLS

        // iterate for .aut files
        FindDotAutFolders dotAutFolders = new FindDotAutFolders();
        try {
            Path walked = Files.walkFileTree(baseCaseInput, dotAutFolders);
        } catch (IOException ex) {
            log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.ErrorFindingAutFiles") + " " + ex.getMessage()); //NON-NLS
        }

        ArrayList<ImportCaseData> ableToProcess = new ArrayList<>();
        ArrayList<ImportCaseData> unableToProcess = new ArrayList<>();

        SingleUserCaseConverter scc = new SingleUserCaseConverter();

        // validate we can convert the .aut file, one by one
        for (FoundAutFile f : dotAutFolders.getCandidateList()) {
            this.oldCaseName = f.getPath().getFileName().toString();

            // Test image output folder for uniqueness, find a unique folder for it if we can
            File specificOutputFolder = baseImageOutput.resolve(oldCaseName).toFile();
            String newImageName = oldCaseName;
            if (specificOutputFolder.exists()) {
                // Not unique. add numbers before timestamp to specific image output name
                String timeStamp = TimeStampUtils.getTimeStampOnly(oldCaseName);
                newImageName = TimeStampUtils.removeTimeStamp(oldCaseName);
                int number = 1;
                String temp = ""; //NON-NLS
                while (specificOutputFolder.exists()) {
                    if (number == Integer.MAX_VALUE) {
                        // It never became unique, so give up.
                        throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.NonUniqueOutputFolder") + newImageName); //NON-NLS
                    }
                    temp = newImageName + "_" + Integer.toString(number) + timeStamp; //NON-NLS
                    specificOutputFolder = baseImageOutput.resolve(temp).toFile();
                    ++number;
                }
                newImageName = temp;
            }
            Path imageOutput = baseImageOutput.resolve(newImageName);
            imageOutput.toFile().mkdirs(); // Create image output folder

            // Test case output folder for uniqueness, find a unique folder for it if we can
            specificOutputFolder = baseCaseOutput.resolve(oldCaseName).toFile();
            newCaseName = oldCaseName;
            if (specificOutputFolder.exists()) {
                // not unique. add numbers before timestamp to specific case output name
                String timeStamp = TimeStampUtils.getTimeStampOnly(oldCaseName); //NON-NLS
                newCaseName = TimeStampUtils.removeTimeStamp(oldCaseName);
                int number = 1;
                String temp = ""; //NON-NLS
                while (specificOutputFolder.exists()) {
                    if (number == Integer.MAX_VALUE) {
                        // It never became unique, so give up.
                        throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.NonUniqueOutputFolder") + newCaseName); //NON-NLS
                    }
                    temp = newCaseName + "_" + Integer.toString(number) + timeStamp; //NON-NLS
                    specificOutputFolder = baseCaseOutput.resolve(temp).toFile();
                    ++number;
                }
                newCaseName = temp;
            }
            Path caseOutput = baseCaseOutput.resolve(newCaseName);
            caseOutput.toFile().mkdirs(); // Create case output folder

            /**
             * Test if the input path has a corresponding image input folder and
             * no repeated case names in the path. If both of these conditions
             * are true, we can process this case, otherwise not.
             */
            // Check that there is an image folder if they are trying to copy it
            boolean canProcess = true;
            Path imageInput = null;
            String relativeCaseName = TimeStampUtils.removeTimeStamp(baseCaseInput.relativize(f.getPath()).toString());
            Path testImageInputsFromOldCase = Paths.get(baseImageInput.toString(), relativeCaseName);
            if (copyImages) {
                if (!testImageInputsFromOldCase.toFile().isDirectory()) {
                    // Mark that we are unable to process this item
                    canProcess = false;
                } else {
                    imageInput = testImageInputsFromOldCase;
                }
                if (imageInput == null) {
                    throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.SourceImageMissing") + " " + f.getPath()); //NON-NLS
                }

                // If case name is in the image path, it causes bad things to happen with the parsing. Test for this.
                for (int x = 0; x < imageInput.getNameCount(); ++x) {
                    if (oldCaseName.toLowerCase().equals(imageInput.getName(x).toString().toLowerCase())) {
                        // Mark that we are unable to process this item
                        canProcess = false;
                    }
                }
            } else {
                imageInput = testImageInputsFromOldCase;
            }

            // Create an Import Case Data object for this case
            SingleUserCaseConverter.ImportCaseData icd = scc.new ImportCaseData(
                    imageInput,
                    f.getPath(),
                    imageOutput,
                    caseOutput,
                    oldCaseName,
                    newCaseName,
                    f.getAutFile().toString(),
                    f.getFolderName().toString(),
                    copyImages,
                    deleteCase);

            if (canProcess) {
                ableToProcess.add(icd);
            } else {
                unableToProcess.add(icd);
            }
        }

        // Create text to be populated in the confirmation dialog
        StringBuilder casesThatWillBeProcessed = new StringBuilder();
        StringBuilder casesThatWillNotBeProcessed = new StringBuilder();

        casesThatWillBeProcessed.append(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.WillImport")).append(SEP); // NON-NLS
        if (ableToProcess.isEmpty()) {
            casesThatWillBeProcessed.append(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.None")).append(SEP); // NON-NLS
        } else {
            for (ImportCaseData i : ableToProcess) {
                casesThatWillBeProcessed.append(i.getCaseInputFolder().toString()).append(SEP);
            }
        }

        if (!unableToProcess.isEmpty()) {
            casesThatWillNotBeProcessed.append(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.WillNotImport")).append(SEP); // NON-NLS
            for (ImportCaseData i : unableToProcess) {
                casesThatWillNotBeProcessed.append(i.getCaseInputFolder().toString()).append(SEP);
            }
        }

        JTextArea jta = new JTextArea(casesThatWillBeProcessed.toString() + SEP + casesThatWillNotBeProcessed.toString());
        jta.setEditable(false);
        JScrollPane jsp = new JScrollPane(jta) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(700, 480);
            }
        };

        // Show confirmation dialog
        SwingUtilities.invokeLater(() -> {
            userAnswer = JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                    jsp,
                    NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.ContinueWithImport"), // NON-NLS
                    OK_CANCEL_OPTION);
            synchronized (threadWaitNotifyLock) {
                threadWaitNotifyLock.notify();
            }
        });

        // Wait while the user handles the confirmation dialog
        synchronized (threadWaitNotifyLock) {
            try {
                threadWaitNotifyLock.wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(SingleUserCaseImporter.class.getName()).log(Level.SEVERE, "Threading Issue", ex); //NON-NLS
                throw new Exception(ex);
            }
        }

        // If the user wants to proceed, do so.
        if (userAnswer == JOptionPane.OK_OPTION) {
            boolean result = true; // if anything went wrong, result becomes false.
            // Feed .aut files in one by one for processing
            for (ImportCaseData i : ableToProcess) {
                try {
                    log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.StartedProcessing")
                            + i.getCaseInputFolder()
                            + " " + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to") + " "
                            + i.getCaseOutputFolder()); //NON-NLS
                    SingleUserCaseConverter.importCase(i);
                    handleAutoIngestLog(i);
                    log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.FinishedProcessing")
                            + i.getCaseInputFolder()
                            + " " + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to") + " "
                            + i.getCaseOutputFolder()); //NON-NLS

                } catch (Exception ex) {
                    log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.FailedToComplete")
                            + i.getCaseInputFolder()
                            + " " + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to") + " "
                            + i.getCaseOutputFolder() + " " + ex.getMessage()); //NON-NLS
                    result = false;
                }
            }

            log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.CompletedBatch")
                    + baseCaseInput.toString()
                    + " " + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to") + " "
                    + baseCaseOutput.toString()); //NON-NLS

            closeLog();
            if (notifyOnComplete != null) {
                notifyOnComplete.importDoneCallback(result, ""); // NON-NLS
            }
        } else {
            // The user clicked cancel. Abort.
            log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.AbortingBatch")
                    + baseCaseInput.toString()
                    + " " + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to") + " "
                    + baseCaseOutput.toString()); //NON-NLS

            closeLog();
            if (notifyOnComplete != null) {
                notifyOnComplete.importDoneCallback(false, NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.Cancelled")); // NON-NLS
            }
        }
    }

    @Override
    public void run() {
        try {
            importCases();
        } catch (Exception ex) {
            log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.FailedToComplete")
                    + baseCaseInput.toString()
                    + " " + NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.to") + " "
                    + baseCaseOutput.toString()
                    + " " + ex.getMessage()); //NON-NLS

            closeLog();
            if (notifyOnComplete != null) {
                notifyOnComplete.importDoneCallback(false, ex.getMessage()); // NON-NLS
            }
        }
    }

    /**
     * Move the Auto Ingest log if we can
     *
     * @param icd the Import Case Data structure detailing where the files are
     */
    void handleAutoIngestLog(ImportCaseData icd) {
        try {
            Path source = icd.getCaseInputFolder().resolve(AIM_LOG_FILE_NAME);
            Path destination = icd.getCaseOutputFolder().resolve(AIM_LOG_FILE_NAME);

            if (source.toFile().exists()) {
                FileUtils.copyFile(source.toFile(), destination.toFile());
            }

            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(destination.toString(), true)))) {
                out.println(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.ImportedAsMultiUser") + new Date()); //NON-NLS
            } catch (IOException e) {
                // If unable to log it, no problem, move on
            }

            File oldIngestLog = Paths.get(icd.getCaseOutputFolder().toString(), NetworkUtils.getLocalHostName(), AIM_LOG_FILE_NAME).toFile();
            if (oldIngestLog.exists()) {
                oldIngestLog.delete();
            }
        } catch (Exception ex) {
            // If unable to copy Auto Ingest log, no problem, move on   
        }
    }

    /**
     * Open the case import log in the base output folder.
     *
     * @param location holds the path to the log file
     */
    private void openLog(File location) {
        location.mkdirs();
        File logFile = Paths.get(location.toString(), CASE_IMPORT_LOG_FILE).toFile();
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, logFile.exists())), true);
        } catch (IOException ex) {
            writer = null;
            Logger.getLogger(SingleUserCaseImporter.class.getName()).log(Level.WARNING, "Error opening log file " + logFile.toString(), ex); //NON-NLS
        }
    }

    /**
     * Log a message to the case import log in the base output folder.
     *
     * @param message the message to log.
     */
    private void log(String message) {
        if (writer != null) {
            writer.println(String.format("%s %s", simpleDateFormat.format((Date.from(Instant.now()).getTime())), message)); //NON-NLS
        }
    }

    /**
     * Close the case import log
     */
    private void closeLog() {
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Extend SimpleFileVisitor to find all the cases to process based upon
     * presence of .aut files.
     */
    private class FindDotAutFolders extends SimpleFileVisitor<Path> {

        private final ArrayList<FoundAutFile> candidateList;

        public FindDotAutFolders() {
            this.candidateList = new ArrayList<>();
        }

        /**
         * Handle comparing .aut file and containing folder names without
         * timestamps on either one. It strips them off if they exist.
         *
         * @param directory the directory we are currently visiting.
         * @param attrs     file attributes.
         *
         * @return CONTINUE if we want to carry on, SKIP_SUBTREE if we've found
         *         a .aut file, precluding searching any deeper into this
         *         folder.
         *
         * @throws IOException
         */
        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
            // Find all files that end in .aut
            File[] dotAutFiles = directory.toFile().listFiles((File dir, String name) -> name.toLowerCase().endsWith(DOTAUT));
            for (File specificFile : dotAutFiles) {
                // If the case name ends in a timestamp, strip it off
                String sanitizedCaseName = specificFile.getName();
                sanitizedCaseName = TimeStampUtils.removeTimeStamp(sanitizedCaseName);

                // If the folder ends in a timestamp, strip it off
                String sanitizedFolderName = TimeStampUtils.removeTimeStamp(directory.getFileName().toString());

                // If file and folder match, found leaf node case
                if (sanitizedCaseName.toLowerCase().startsWith(sanitizedFolderName.toLowerCase())) {
                    candidateList.add(new FoundAutFile(directory, Paths.get(sanitizedCaseName), Paths.get(sanitizedFolderName)));
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            // If no matching .aut files, continue to traverse subfolders
            return FileVisitResult.CONTINUE;
        }

        /**
         * Returns the list of folders we've found that need to be looked at for
         * possible import from single-user to multi-user cases.
         *
         * @return the candidateList
         */
        public ArrayList<FoundAutFile> getCandidateList() {
            return candidateList;
        }
    }

    /**
     * This class holds information about .aut files that have been found by the
     * FileWalker.
     */
    public class FoundAutFile {

        private final Path path;
        private final Path autFile;
        private final Path folderName;

        public FoundAutFile(Path path, Path autFile, Path folderName) {
            this.path = path;
            this.autFile = autFile;
            this.folderName = folderName;
        }

        Path getPath() {
            return this.path;
        }

        Path getAutFile() {
            return this.autFile;
        }

        Path getFolderName() {
            return this.folderName;
        }
    }
}
