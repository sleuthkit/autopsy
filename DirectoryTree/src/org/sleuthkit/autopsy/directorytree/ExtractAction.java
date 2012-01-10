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

import java.awt.event.ActionEvent;
import javax.swing.JFileChooser;
import java.io.File;
import java.awt.Component;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FsContent;

/**
 * Exports files and folders
 */
public final class ExtractAction extends AbstractAction {

    private static final InitializeContentVisitor initializeCV = new InitializeContentVisitor();
    private FsContent fsContent;

    public ExtractAction(String title, Node contentNode) {
        super(title);
        Content tempContent = contentNode.getLookup().lookup(Content.class);

        this.fsContent = tempContent.accept(initializeCV);
        this.setEnabled(fsContent != null);
    }

    /**
     * Returns the FsContent if it is supported, otherwise null
     */
    private static class InitializeContentVisitor extends ContentVisitor.Default<FsContent> {

        @Override
        public FsContent visit(org.sleuthkit.datamodel.File f) {
            return f;
        }

        @Override
        public FsContent visit(Directory dir) {
            return ContentUtils.isDotDirectory(dir) ? null : dir;
        }

        @Override
        protected FsContent defaultVisit(Content cntnt) {
            return null;
        }
    }

    /**
     * Asks user to choose destination, then extracts file/directory to 
     * destination (recursing on directories)
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(this.getClass());

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(this.fsContent.getName()));
        int returnValue = fc.showSaveDialog((Component) e.getSource());

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File destination = fc.getSelectedFile();

            // check that it's okay to overwrite existing file
            if (destination.exists()) {
                int choice = JOptionPane.showConfirmDialog(
                        (Component) e.getSource(),
                        "Destination file already exists, it will be overwritten.",
                        "Destination already exists!",
                        JOptionPane.OK_CANCEL_OPTION);

                if (choice != JOptionPane.OK_OPTION) {
                    return;
                }

                if (!destination.delete()) {
                    JOptionPane.showMessageDialog(
                            (Component) e.getSource(),
                            "Couldn't delete existing file.");
                }
            }

            ExtractFscContentVisitor.extract(fsContent, destination);
            if(fsContent.isDir())
                JOptionPane.showMessageDialog((Component) e.getSource(), "Directory extracted.");
            else if(fsContent.isFile()){
                JOptionPane.showMessageDialog((Component) e.getSource(), "File extracted.");
            }
        }
    }
}
