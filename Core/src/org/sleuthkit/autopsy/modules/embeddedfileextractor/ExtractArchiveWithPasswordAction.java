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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.coreutils.Logger;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
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
    @Messages({"ExtractArchiveWithPasswordAction.name.text=Unzip contents with password"})
    public ExtractArchiveWithPasswordAction(AbstractFile file) {
        super(Bundle.ExtractArchiveWithPasswordAction_name_text());
        archiveFile = file;
    }
    
    @Messages({"ExtractArchiveWithPasswordAction.prompt.text=Enter Password",
            "ExtractArchiveWithPasswordAction.prompt.title=Enter Password",
            "ExtractArchiveWithPasswordAction.extractFailed.title=Failed to Unpack Files, with Password"})
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            String moduleDirRelative = Paths.get(Case.getOpenCase().getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
            String moduleDirAbsolute = Paths.get(Case.getOpenCase().getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
            boolean done = false;
            String password = "";
            String title = Bundle.ExtractArchiveWithPasswordAction_prompt_title();
            while (!done) {
                Object inputValue = JOptionPane.showInputDialog(null, Bundle.ExtractArchiveWithPasswordAction_prompt_text(),
                        title, JOptionPane.PLAIN_MESSAGE, null, null, password);
                if (inputValue == null) {
                    done = true;
                } else {
                    password = (String) inputValue;
                    /*
                     * Construct a file type detector.
                     */
                    FileTypeDetector fileTypeDetector;
                    try {
                        fileTypeDetector = new FileTypeDetector();
                    } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                        logger.log(Level.SEVERE, "Unable to construct file type detector", ex);
                        return;
                    }
                    try {
                        SevenZipExtractor extractor = new SevenZipExtractor(null, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
                        done = extractor.unpack(archiveFile, password);
                        if (done == false) {
                            title = Bundle.ExtractArchiveWithPasswordAction_extractFailed_title();
                        }
                    } catch (SevenZipNativeInitializationException ex) {
                        IngestServices.getInstance().postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), "Unable to extract file with password", password));
                        logger.log(Level.INFO, "Unable to extract file with password", ex);
                    }
                }
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error getting open case unable to perform extraction action", ex);
        }
    }
}
