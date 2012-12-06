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
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;

/**
 * Action on a file or artifact that bookmarks a file and/or artifact
 * and reloads the bookmark view.
 * Supports bookmarking of a fs file, directory and layout file and layout
 * directory (virtual files/dirs for unalloc content) 
 * 
 * TODO add use enters description and hierarchy (TSK_TAG_NAME with slashes)
 */
public class BookmarkAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(BookmarkAction.class.getName());
    //content to bookmark
    private AbstractFile bookmarkFile;
    private BlackboardArtifact bookmarkArtifact;
    private final InitializeBookmarkFileV initializer = new InitializeBookmarkFileV();

    public BookmarkAction(String title, Node contentNode) {
        super(title);
        Content content = contentNode.getLookup().lookup(Content.class);

        bookmarkArtifact = null;
        bookmarkFile = content.accept(initializer);
        this.setEnabled(bookmarkFile != null);
    }
    
    public BookmarkAction(String title, Content content) {
        super(title);

        bookmarkArtifact = null;
        bookmarkFile = content.accept(initializer);
        this.setEnabled(bookmarkFile != null);
    }

    public BookmarkAction(String title, BlackboardArtifact art) {
        super(title);
        
        bookmarkArtifact = art;
        bookmarkFile = null;
        this.setEnabled(bookmarkArtifact != null);
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
        String comment = JOptionPane.showInputDialog(null, "Please enter a comment for the bookmark:", "Bookmark Comment", JOptionPane.PLAIN_MESSAGE);
        if(comment == null || comment.isEmpty()) {
            comment = "No Comment";
        }
        if(bookmarkArtifact != null) {
            Tags.createBookmark(bookmarkArtifact, comment);
        } else if(bookmarkFile != null) {
            Tags.createBookmark(bookmarkFile, comment);
        }
        
        DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();  
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
    }
}
