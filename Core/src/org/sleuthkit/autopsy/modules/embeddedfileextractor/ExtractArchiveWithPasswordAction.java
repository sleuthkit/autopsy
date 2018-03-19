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
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
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

    private final AbstractFile archiveFile;

    /**
     * Create an action that will allow the user to enter an password and then
     * use the entered password to extract contents of a password protected
     * archive.
     *
     * @param file the password protected archive file to extract
     */
    public ExtractArchiveWithPasswordAction(AbstractFile file) {
        super("Unzip contents with password");
        archiveFile = file;
    }
    
    @Messages({"ExtractArchiveWithPasswordAction.promt.text=Enter Password",
            "ExtractArchiveWithPasswordAction.promt.title=Enter Password",
            "ExtractArchiveWithPasswordAction.extractFailed.title=Failed to Unpack Files, with Password"})
    @Override
    public void actionPerformed(ActionEvent e) {
        String moduleDirRelative = Paths.get(Case.getCurrentCase().getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        String moduleDirAbsolute = Paths.get(Case.getCurrentCase().getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        boolean done = false;
        String password = "";
        String title = Bundle.ExtractArchiveWithPasswordAction_promt_title();
        while (!done) {
            Object inputValue = JOptionPane.showInputDialog(null, Bundle.ExtractArchiveWithPasswordAction_promt_text(), 
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
                    return;
                    //WJS-TODO should this throw exception
                }
                try {
                    SevenZipExtractor extractor = new SevenZipExtractor(null, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
                    done = extractor.unpack(archiveFile, password);
                    if (done == false) {
                        title = Bundle.ExtractArchiveWithPasswordAction_extractFailed_title();
                    }
                } catch (SevenZipNativeInitializationException ex) {
                    IngestServices.getInstance().postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), "Unable to extract file with password", password));
                }
            }
        }
    }
}
