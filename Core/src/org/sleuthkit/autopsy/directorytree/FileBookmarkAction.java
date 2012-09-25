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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.Bookmarks;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action on a file that bookmarks a file and reloads the bookmark view.
 * Supports bookmarking of a fs file, directory and layout file and layout
 * directory (virtual files/dirs for unalloc content) 
 * 
 * TODO add use enters description and hierarchy (TSK_TAG_NAME with slashes)
 */
public class FileBookmarkAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(FileBookmarkAction.class.getName());
    //content to bookmark (AbstractFile)
    private AbstractFile bookmarkFile;
    private final InitializeBookmarkFileV initializer = new InitializeBookmarkFileV();

    FileBookmarkAction(String title, Node contentNode) {
        super(title);
        Content content = contentNode.getLookup().lookup(Content.class);

        bookmarkFile = content.accept(initializer);
        this.setEnabled(bookmarkFile != null);
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
        public AbstractFile visit(org.sleuthkit.datamodel.LayoutDirectory ld) {
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
        if (doBookmarkFile(bookmarkFile)) {
            refreshView();
        }
    }

    private void refreshView() {
        DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
        //viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
    }

    private boolean doBookmarkFile(AbstractFile file) {
        try {
            //TODO popup a dialog and allow user to enter description
            //and optional bookmark name (TSK_TAG_NAME) with slashes representating hierarchy
            //should always start with FILE_BOOKMARK_TAG_NAME           
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
            List<BlackboardAttribute> attrs = new ArrayList<BlackboardAttribute>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", Bookmarks.FILE_BOOKMARK_TAG_NAME);
            BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID(),
                    "", "No Description");
            attrs.add(attr1);
            attrs.add(attr2);
            bookArt.addAttributes(attrs);
            return true;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Could not create a bookmark for a file: " + file, ex);
        }

        return false;


    }
}
