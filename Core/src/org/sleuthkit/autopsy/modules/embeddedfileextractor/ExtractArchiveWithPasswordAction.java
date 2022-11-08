/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.awt.event.ActionEvent;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.coreutils.Logger;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * An action that will allow the user to enter a password for archive file and
 * unpack its contents.
 *
 */
public class ExtractArchiveWithPasswordAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ExtractArchiveWithPasswordAction.class.getName());
    private final AbstractFile archiveFile;

    /**
     * Create an action that will allow the user to enter an password and then
     * use the entered password to extract contents of a password protected
     * archive.
     *
     * @param file the password protected archive file to extract
     */
    @Messages({"ExtractArchiveWithPasswordAction.name.text=Unzip contents with password", "ExtractArchiveWithPasswordAction.prompt.text=Enter Password",
        "ExtractArchiveWithPasswordAction.prompt.title=Enter Password",
        "ExtractArchiveWithPasswordAction.extractFailed.title=Failed to Unpack Files, with Password",
        "# {0} - archiveFile",
        "ExtractArchiveWithPasswordAction.progress.text=Unpacking contents of archive: {0}"})
    public ExtractArchiveWithPasswordAction(AbstractFile file) {
        super(Bundle.ExtractArchiveWithPasswordAction_name_text());
        archiveFile = file;
    }
  
    @Override
    public void actionPerformed(ActionEvent e) {
        String password = getPassword(Bundle.ExtractArchiveWithPasswordAction_prompt_title(), "");
        if (password != null) {
            ExtractAndIngestWorker extractWorker = new ExtractAndIngestWorker(password, archiveFile);
            extractWorker.execute();
        }
    }

    private String getPassword(String title, String oldPassword) {
        String password = null;
        Object inputValue = JOptionPane.showInputDialog(WindowManager.getDefault().getMainWindow(), Bundle.ExtractArchiveWithPasswordAction_prompt_text(),
                title, JOptionPane.PLAIN_MESSAGE, null, null, oldPassword);
        if (inputValue != null) {
            password = (String) inputValue;
        }
        return password;
    }

    /**
     * SwingWorker which attempts to extract contents of archive file with a
     * password and if successful proceeds to let the user run ingest on the
     * contents.
     */
    private class ExtractAndIngestWorker extends SwingWorker<Boolean, Void> {

        private final AbstractFile archive;
        private String password;
        private final ModalDialogProgressIndicator progress = new ModalDialogProgressIndicator(WindowManager.getDefault().getMainWindow(), "Extracting Archive");

        /**
         * Construct an ExtractAndIngestWorker
         * 
         * @param pass - the password to initially attempt using
         * @param file - the password protected archive file to extract the contents of
         */
        private ExtractAndIngestWorker(String pass, AbstractFile file) {
            archive = file;
            password = pass;
        }

        @Override
        protected Boolean doInBackground() {
            boolean done = false;
            try {
                String moduleDirRelative = Paths.get(Case.getCurrentCaseThrows().getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getOutputFolderName()).toString();
                String moduleDirAbsolute = Paths.get(Case.getCurrentCaseThrows().getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getOutputFolderName()).toString();
                /*
                 * Construct a file type detector.
                 */
                progress.start(Bundle.ExtractArchiveWithPasswordAction_progress_text(archive.getName()));
                FileTypeDetector fileTypeDetector;
                try {
                    fileTypeDetector = new FileTypeDetector();
                } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                    return false;
                }
                try {
                    SevenZipExtractor extractor = new SevenZipExtractor(null, fileTypeDetector, moduleDirRelative, moduleDirAbsolute, new FileTaskExecutor(null));
                    done = extractor.unpack(archive, new ConcurrentHashMap<>(), password);
                } catch (SevenZipNativeInitializationException ex) {
                    IngestServices.getInstance().postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), "Unable to extract file with password", password));
                    logger.log(Level.INFO, "Unable to extract file with password", ex);
                    return done;
                }
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error getting open case unable to perform extraction action", ex);
            } finally {
                progress.finish();
            }
            return done;
        }

        @Override
        protected void done() {
            boolean done = false;
            try {
                done = get();
                while (!done) {
                    password = getPassword(Bundle.ExtractArchiveWithPasswordAction_extractFailed_title(), password);
                    if (password == null) {
                        //allow them to cancel if they don't know the correct password
                        return;
                    }
                    done = doInBackground();
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "Unable to extract archive successfully", ex);
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Execution Exception: Unable to extract archive successfully", ex.getCause());
            }
            if (done) {
                RunIngestModulesAction runIngest = new RunIngestModulesAction(archive);
                runIngest.actionPerformed(null);
            }
        }
    }
}
