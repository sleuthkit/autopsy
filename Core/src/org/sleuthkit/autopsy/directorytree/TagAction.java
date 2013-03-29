/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.nodes.Node;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;

/**
 * Action on a file or artifact that adds a tag and
 * reloads the directory tree. Supports tagging of AbstractFiles and
 * BlackboardArtifacts.
 *
 * TODO add use enters description and hierarchy (TSK_TAG_NAME with slashes)
 */
public class TagAction extends AbstractAction implements Presenter.Popup {

    private static final Logger logger = Logger.getLogger(TagAction.class.getName());
    private JMenu tagMenu;
    private final InitializeBookmarkFileV initializer = new InitializeBookmarkFileV();

    public TagAction(Node contentNode) {
        AbstractFile file = contentNode.getLookup().lookup(AbstractFile.class);
        if (file != null) {
            tagMenu = new TagMenu(file);
            return;
        }
        
        BlackboardArtifact bba = contentNode.getLookup().lookup(BlackboardArtifact.class);
        if (bba != null) {
            tagMenu = new TagMenu(bba);
            return;
        }
        
        logger.log(Level.SEVERE, "Tried to create a " + TagAction.class.getName()
                + " using a Node whose lookup did not contain an AbstractFile or a BlackboardArtifact.");
    }

    public TagAction(AbstractFile file) {
        tagMenu = new TagMenu(file);
    }
    
    public TagAction(BlackboardArtifact bba) {
        tagMenu = new TagMenu(bba);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return tagMenu;
    }

    /**
     * Returns the FsContent if it is supported, otherwise null
     */
    private static class InitializeBookmarkFileV extends ContentVisitor.Default<AbstractFile> {

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.File f) {
            return f;
        }

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.LayoutFile lf) {
            return lf;
        }
        
        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.DerivedFile lf) {
            return lf;
        }

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.VirtualDirectory ld) {
            return ld;
        }

        @Override
        public AbstractFile visit(Directory dir) {
            return ContentUtils.isDotDirectory(dir) ? null : dir;
        }

        @Override
        protected AbstractFile defaultVisit(Content cntnt) {
            return null;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Do nothing - this action should never be performed
        // Submenu actions are invoked instead
    }
}