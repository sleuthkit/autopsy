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
import java.util.logging.Logger;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.directorytree.HashSearchProvider;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Searches based on file MD5 hash
 */
public class HashSearchAction extends CallableSystemAction implements HashSearchProvider {

    private static final InitializeContentVisitor initializeCV = new InitializeContentVisitor();
    private FsContent fsContent;
    private Logger logger = Logger.getLogger(HashSearchAction.class.getName());
    private Case currentCase = Case.getCurrentCase();
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();

    private static HashSearchAction instance = null;

    HashSearchAction() {
        super();
    }
    
    public static HashSearchAction getDefault() {
        if(instance == null){
            instance = new HashSearchAction();
        }
        return instance;
    }

    @Override
    public void search(Node contentNode) {
        Content tempContent = contentNode.getLookup().lookup(Content.class);
        this.fsContent = tempContent.accept(initializeCV);
        doAction();
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
    
    public void doAction() {
        performAction();
    }

    /**
     * Find all files with the selected file's MD5 hash and display them
     * as nodes.
     */
    @Override
    public void performAction() {
        // Make sure all files have an md5 hash
        if(skCase.md5HashFinished()) {
            // Get the map of hashes to FsContent and send it to the manager
            List<FsContent> files = skCase.findFilesByMd5(fsContent.getMd5Hash());
            Map<String, List<FsContent>> map = new LinkedHashMap<String, List<FsContent>>();
            map.put(fsContent.getMd5Hash(), files);
            HashDbSearchManager man = new HashDbSearchManager(map);
            man.execute();
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
