/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.awt.event.ActionEvent;
import java.nio.file.Paths;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author wschaefer
 */
public class ExtractArchiveWithPasswordAction extends AbstractAction {

    AbstractFile archiveFile;

    public ExtractArchiveWithPasswordAction(AbstractFile file) {
        super("Unzip contents with password");
        archiveFile = file;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String moduleDirRelative = Paths.get(Case.getCurrentCase().getModuleOutputDirectoryRelativePath(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        String moduleDirAbsolute = Paths.get(Case.getCurrentCase().getModuleDirectory(), EmbeddedFileExtractorModuleFactory.getModuleName()).toString();
        boolean done = false;
        String password = "";
        String prompt = "Enter Password:";
        String title = "Enter Password";
        while (!done) {
            Object inputValue = JOptionPane.showInputDialog(null,  prompt, title, JOptionPane.PLAIN_MESSAGE, null, null, password);
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
                    //throw new Exception(Bundle.CannotRunFileTypeDetection(), ex);
                }
                try {
                    SevenZipExtractor extractor = new SevenZipExtractor(null, fileTypeDetector, moduleDirRelative, moduleDirAbsolute);
                    done = extractor.unpack(archiveFile, password);
                    if (done == false){
                        title = "Password Failed To Unpack Files";
                        prompt = "Enter Another Password:";
                    }
                } catch (SevenZipNativeInitializationException ex) {
                    IngestServices.getInstance().postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), "Unable to extract file with password", password));
                }
            }
        }
    }

}
