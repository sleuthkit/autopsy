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
package org.sleuthkit.autopsy.directorytree;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;

/**
 * Extracts a File object to a temporary file in the case directory, and then
 * tries to open it in the user's system with the default or user specified
 * associated application.
 */
public class ExternalViewerAction extends AbstractAction {

    private final static Logger logger = Logger.getLogger(ExternalViewerAction.class.getName());
    private org.sleuthkit.datamodel.AbstractFile fileObject;
    private String fileObjectExt;
    final static String[] EXECUTABLE_EXT = {".exe", ".dll", ".com", ".bat", ".msi", ".reg", ".scr", ".cmd"}; //NON-NLS

    public ExternalViewerAction(String title, Node fileNode) {
        super(title);
        this.fileObject = fileNode.getLookup().lookup(org.sleuthkit.datamodel.AbstractFile.class);

        long size = fileObject.getSize();
        String fileName = fileObject.getName();
        int extPos = fileName.lastIndexOf('.');

        boolean isExecutable = false;
        if (extPos != -1) {
            String extension = fileName.substring(extPos, fileName.length()).toLowerCase();
            fileObjectExt = extension;
            for (int i = 0; i < EXECUTABLE_EXT.length; ++i) {
                if (EXECUTABLE_EXT[i].equals(extension)) {
                    isExecutable = true;
                    break;
                }
            }
        } else {
            fileObjectExt = "";
        }

        // no point opening a file if it's empty, and java doesn't know how to
        // find an application for files without an extension
        // or if file is executable (for security reasons)
        // Also skip slack files since their extension is the original extension + "-slack"
        if (!(size > 0) || extPos == -1 || isExecutable || (fileNode instanceof SlackFileNode)) {
            this.setEnabled(false);
        }
    }

    @Messages({"ExternalViewerAction.actionPerformed.failure.message=Could not find a viewer for the given file.",
        "ExternalViewerAction.actionPerformed.failure.title=Open Failure"})
    @Override
    public void actionPerformed(ActionEvent e) {
        // Get the temp folder path of the case
        String tempPath = Case.getCurrentCase().getTempDirectory();
        tempPath = tempPath + File.separator + this.fileObject.getName();

        // create the temporary file
        File tempFile = new File(tempPath);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        try {
            tempFile.createNewFile();
            ContentUtils.writeToFile(fileObject, tempFile);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Can't save to temporary file.", ex); //NON-NLS
        }

        /**
         * Check if the file MIME type or extension exists in the user defined
         * settings. Otherwise open with the default associated application.
         */
        String exePath = ExternalViewerRulesManager.getInstance().getExePathForName(fileObject.getMIMEType());
        if (exePath.equals("")) {
            exePath = ExternalViewerRulesManager.getInstance().getExePathForName(fileObjectExt);
        }
        if (!exePath.equals("")) {
            Runtime runtime = Runtime.getRuntime();
            String[] s = new String[]{exePath, tempFile.getAbsolutePath()};
            try {
                runtime.exec(s);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not open the specified viewer for the given file: " + tempFile.getName(), ex); //NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.ExternalViewerAction_actionPerformed_failure_message(), Bundle.ExternalViewerAction_actionPerformed_failure_title(), JOptionPane.ERROR_MESSAGE);
            }
        } else {
            try {
                Desktop.getDesktop().open(tempFile);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not find a viewer for the given file: " + tempFile.getName(), ex); //NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.ExternalViewerAction_actionPerformed_failure_message(), Bundle.ExternalViewerAction_actionPerformed_failure_title(), JOptionPane.ERROR_MESSAGE);
            }
        }
        // delete the file on exit
        tempFile.deleteOnExit();
    }
}
