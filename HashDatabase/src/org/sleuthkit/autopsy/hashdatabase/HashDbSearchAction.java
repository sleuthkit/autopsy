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
package org.sleuthkit.autopsy.hashdatabase;

import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.directorytree.HashSearchProvider;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FsContent;

/**
 * Searches for FsContent Files with the same MD5 hash as the given Node's
 * FsContent's MD5 hash. This action should only be available from Nodes with
 * specific Content attached; it is manually programmed into a Node's available
 * actions.
 */
public class HashDbSearchAction extends CallableSystemAction implements HashSearchProvider {

    private static final InitializeContentVisitor initializeCV = new InitializeContentVisitor();
    private AbstractFile file;
    private static HashDbSearchAction instance = null;

    HashDbSearchAction() {
        super();
    }

    public static HashDbSearchAction getDefault() {
        if (instance == null) {
            instance = new HashDbSearchAction();
        }
        return instance;
    }

    @Override
    public void search(Node contentNode) {
        Content tempContent = contentNode.getLookup().lookup(Content.class);
        this.file = tempContent.accept(initializeCV);
        performAction();
    }

    /**
     * Returns the FsContent if it is supported, otherwise null. It should
     * realistically never return null or a Directory, only a File.
     */
    private static class InitializeContentVisitor extends ContentVisitor.Default<AbstractFile> {

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.File f) {
            return f;
        }

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.DerivedFile df) {
            return df;
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

    /**
     * Find all files with the same MD5 hash as this' file. file should be
     * previously set by calling the search function, which in turn calls
     * performAction.
     */
    @Override
    public void performAction() {
        // Make sure at least 1 file has an md5 hash
        if (HashDbSearcher.countFilesMd5Hashed() > 0) {
            doSearch();
        } else {
            JOptionPane.showMessageDialog(null, "No files currently have an MD5 hash calculated, run HashDB ingest first.",
                    "File Search by MD5 Hash", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSearch() {
        HashDbSearchThread hashThread = new HashDbSearchThread(file);
        hashThread.execute();
    }

    @Override
    public String getName() {
        return "Hash Search";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
