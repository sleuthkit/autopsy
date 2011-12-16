/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.logging.Log;

/**
 * Extracts a File object to a temporary file in the case directory, and then
 * tries to open it in the user's system with the default associated
 * application.
 */
public class ExternalViewerAction extends AbstractAction {

    private final static Logger logger = Logger.getLogger(ExternalViewerAction.class.getName());
    private org.sleuthkit.datamodel.File fileObject;

    public ExternalViewerAction(String title, Node fileNode) {
        super(title);
        this.fileObject = fileNode.getLookup().lookup(org.sleuthkit.datamodel.File.class);
        long size = fileObject.getSize();
        if (!(size > 0)) {
            this.setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(this.getClass());


        // the menu should be disabled if we can't read the content (for example: on zero-sized file).
        // Therefore, it should never throw the TSKException.

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
            // throw an error here
            logger.log(Level.WARNING, "Can't save to temporary file.", ex);
        }

        try {
            Desktop.getDesktop().open(tempFile);
        } catch (IOException ex) {
            // if can't open the file, throw the error saying: "File type not supported."
            logger.log(Level.WARNING, "File type not supported.", ex);
        }

        // delete the file on exit
        tempFile.deleteOnExit();
    }
}
