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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.logging.Log;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author jantonius
 */
public class ExternalViewerAction extends AbstractAction {

    private byte[] content;
    private Content contentObject;
    private String fileName;
    private String extension;
    // for error handling
    private JPanel caller;
    private String className = this.getClass().toString();

    /** the constructor */
    public ExternalViewerAction(String title, Node fileNode) {
        super(title);
        this.contentObject = fileNode.getLookup().lookup(Content.class);

        long size = contentObject.getSize();
        String fullFileName = fileNode.getDisplayName();
        if (fullFileName.contains(".") && size > 0) {
            String tempFileName = fullFileName.substring(0, fullFileName.indexOf("."));
            String tempExtension = fullFileName.substring(fullFileName.indexOf("."));
            this.fileName = tempFileName;
            this.extension = tempExtension;
        } else {
            this.fileName = fullFileName;
            this.extension = "";
            this.setEnabled(false); //TODO: fix this later (right now only extract a file with extension)
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(this.getClass());

        try {
            // @@@ Thing to do: maybe throw confirmation first???

            // the menu should be disabled if we can't read the content (for example: on zero-sized file).
            // Therefore, it should never throw the TSKException.
            try {
                this.content = contentObject.read(0, contentObject.getSize());
            } catch (TskException ex) {
                Logger.getLogger(this.className).log(Level.WARNING, "Error: can't read the content of the file.", ex);
            }

            // Get the temp folder path of the case
            String tempPath = Case.getCurrentCase().getTempDirectory();
            tempPath = tempPath + File.separator + this.fileName + this.extension;

            // create the temporary file
            File file = new File(tempPath);
            if (file.exists()) {
                file.delete();
            }

            file.createNewFile();

            // convert char to byte
            byte[] dataSource = new byte[content.length];
            for (int i = 0; i < content.length; i++) {
                dataSource[i] = (byte) content[i];
            }

            FileOutputStream fos = new FileOutputStream(file);
            //fos.write(dataSource);
            fos.write(dataSource);
            fos.close();

            try {
                Desktop.getDesktop().open(file);
            } catch (IOException ex) {
                // if can't open the file, throw the error saying: "File type not supported."
                JOptionPane.showMessageDialog(caller, "Error: File type not supported.\n \nDetail: \n" + ex.getMessage() + " (at " + className + ").", "Error", JOptionPane.ERROR_MESSAGE);
            }

            // delete the file on exit
            file.deleteOnExit();

        } catch (IOException ex) {
            // throw an error here
            Logger.getLogger(this.className).log(Level.WARNING, "Error: can't open the external viewer for this file.", ex);
        }

    }
}
