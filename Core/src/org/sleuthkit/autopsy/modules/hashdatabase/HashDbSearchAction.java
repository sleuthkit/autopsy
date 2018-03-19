/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.directorytree.HashSearchProvider;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.VirtualDirectory;

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
        public AbstractFile visit(org.sleuthkit.datamodel.LocalFile lf) {
            return lf;
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

        @Override
        public AbstractFile visit(LayoutFile lf) {
            // layout files do not have times
            return lf;
        }

        @Override
        public AbstractFile visit(SlackFile f) {
            return f;
        }

        @Override
        public AbstractFile visit(VirtualDirectory dir) {
            return ContentUtils.isDotDirectory(dir) ? null : dir;
        }
    }

    /**
     * Find all files with the same MD5 hash as this' file. file should be
     * previously set by calling the search function, which in turn calls
     * performAction.
     */
    @Override
    @NbBundle.Messages ({
        "HashDbSearchAction.noOpenCase.errMsg=No open case available."
    })
    public void performAction() {
        // Make sure at least 1 file has an md5 hash
        try {
        if (file != null && HashDbSearcher.countFilesMd5Hashed() > 0) {
            doSearch();
        } else {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbSearchAction.dlgMsg.noFilesHaveMD5Calculated"),
                    NbBundle.getMessage(this.getClass(), "HashDbSearchAction.dlgMsg.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
        } catch (NoCurrentCaseException ex) {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.HashDbSearchAction_noOpenCase_errMsg(),
                    NbBundle.getMessage(this.getClass(), "HashDbSearchAction.dlgMsg.title"),
                    JOptionPane.ERROR_MESSAGE);            
        }
    }

    private void doSearch() {
        HashDbSearchThread hashThread = new HashDbSearchThread(file);
        hashThread.execute();
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "HashDbSearchAction.getName.text");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
