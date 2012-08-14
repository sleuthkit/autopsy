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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.directorytree.HashSearchProvider;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FsContent;

/**
 * Searches for FsContent Files with the same MD5 hash as the given Node's
 * FsContent's MD5 hash. This action should only be available from Nodes with
 * specific Content attached; it is manually programmed into a Node's available actions.
 */
public class HashDbSearchAction extends CallableSystemAction implements HashSearchProvider {

    private static final InitializeContentVisitor initializeCV = new InitializeContentVisitor();
    private FsContent fsContent;

    private static HashDbSearchAction instance = null;

    HashDbSearchAction() {
        super();
    }
    
    public static HashDbSearchAction getDefault() {
        if(instance == null){
            instance = new HashDbSearchAction();
        }
        return instance;
    }

    @Override
    public void search(Node contentNode) {
        Content tempContent = contentNode.getLookup().lookup(Content.class);
        this.fsContent = tempContent.accept(initializeCV);
        performAction();
    }

    /**
     * Returns the FsContent if it is supported, otherwise null. It should 
     * realistically never return null or a Directory, only a File.
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
     * Find all files with the same MD5 hash as this' fsContent. fsContent should
     * be previously set by calling the search function, which in turn calls performAction.
     */
    @Override
    public void performAction() {
        // Make sure all files have an md5 hash
        if(HashDbSearcher.allFilesMd5Hashed()) {
            doSearch();
        // and if not, warn the user
        } else if(HashDbSearcher.countFilesMd5Hashed() > 0) {
            Object selected = JOptionPane.showConfirmDialog(null, "Not all files have MD5 hashes. "
                    + "Search results will be incomplete.\n"
                    + "Would you like to search anyway?", "File Search by MD5 Hash", JOptionPane.YES_NO_OPTION);
            if(selected.equals(JOptionPane.YES_OPTION)) {
                doSearch();
            }
        } else {
                JOptionPane.showMessageDialog(null, "No files currently have an MD5 hash.",
                        "File Search by MD5 Hash", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void doSearch() {
        // Get the map of hashes to FsContent and send it to the manager
        List<FsContent> files = HashDbSearcher.findFilesByMd5(fsContent.getMd5Hash());
        for(int i=0; i<files.size(); i++) {
            // If they are the same file, remove it from the list
            if(files.get(i).equals(fsContent)) {
                files.remove(i);
            }
        }
        if(!files.isEmpty()) {
            Map<String, List<FsContent>> map = new LinkedHashMap<String, List<FsContent>>();
            map.put(fsContent.getMd5Hash(), files);
            HashDbSearchManager man = new HashDbSearchManager(map);
            man.execute();
        } else {
            JOptionPane.showMessageDialog(null, "No other files with the same MD5 hash were found.");
        }
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
