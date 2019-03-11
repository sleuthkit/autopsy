/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionCancelledException;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import static org.sleuthkit.autopsy.casemodule.CaseMetadata.getFileExtension;
import org.sleuthkit.autopsy.casemodule.StartupWindowProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

@NbBundle.Messages({
    "MultiCaseKeywordSearchNode.properties.case=Case",
    "MultiCaseKeywordSearchNode.properties.caseDirectory=Case Directory",
    "MultiCaseKeywordSearchNode.properties.dataSource=Data Source",
    "MultiCaseKeywordSearchNode.properties.sourceType=Keyword Hit Source Type",
    "MultiCaseKeywordSearchNode.properties.source=Keyword Hit Source",
    "MultiCaseKeywordSearchNode.properties.path=Keyword Hit Source Path"
})

/**
 * A root node containing child nodes of the results of a multi-case keyword
 * Search.
 */
class MultiCaseKeywordSearchNode extends AbstractNode {

    private static final Logger LOGGER = Logger.getLogger(MultiCaseKeywordSearchNode.class.getName());

    /**
     * Construct a new MultiCaseKeywordSearchNode
     *
     * @param resultList the list of KeywordSearchHits which will be the
     *                   children of this node.
     */
    MultiCaseKeywordSearchNode(Collection<SearchHit> resultList) {
        super(new MultiCaseKeywordSearchChildren(resultList));
    }

    /**
     * A factory for creating children of the MultiCaseKeywordSearchNode.
     */
    static class MultiCaseKeywordSearchChildren extends Children.Keys<SearchHit> {
        private final Collection<SearchHit> resultList;

        /**
         * Construct a new MultiCaseKeywordSearchChildren
         *
         * @param resultList the list of KeywordSearchHits which will be used to
         *                   construct the children.
         */
        MultiCaseKeywordSearchChildren(Collection<SearchHit> resultList) {
            this.resultList = resultList;
        }

         @Override
        protected void addNotify() {
            super.addNotify();
            setKeys(resultList);
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            setKeys(Collections.emptyList());
        }
       
        @Override
        protected Node[] createNodes(SearchHit t) {
            return new Node[]{new SearchHitNode(t)};
        }

        @Override
        public Object clone() {
            return super.clone();
        }

    }

    /**
     * A leaf node which represents a hit for the multi-case keyword search.
     */
    static final class SearchHitNode extends AbstractNode {

        private final SearchHit searchHit;

        /**
         * Construct a new SearchHitNode
         *
         * @param kwsHit the KeywordSearchHit which will be represented by this
         *               node.
         */
        SearchHitNode(SearchHit kwsHit) {
            super(Children.LEAF);
            searchHit = kwsHit;
            super.setName(searchHit.getCaseDisplayName());
            setDisplayName(searchHit.getCaseDisplayName());
        }

        @Override
        public Action getPreferredAction() {
            return new OpenCaseAction(getCasePath());
        }

        /**
         * Get the path to the case directory
         *
         * @return the path to the case directory for the KeywordSearchHit
         *         represented by this node
         */
        private String getCasePath() {
            return searchHit.getCaseDirectoryPath();
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(Bundle.MultiCaseKeywordSearchNode_properties_case(), Bundle.MultiCaseKeywordSearchNode_properties_case(), Bundle.MultiCaseKeywordSearchNode_properties_case(),
                    searchHit.getCaseDisplayName()));
            ss.put(new NodeProperty<>(Bundle.MultiCaseKeywordSearchNode_properties_caseDirectory(), Bundle.MultiCaseKeywordSearchNode_properties_caseDirectory(), Bundle.MultiCaseKeywordSearchNode_properties_caseDirectory(),
                    searchHit.getCaseDirectoryPath()));
            ss.put(new NodeProperty<>(Bundle.MultiCaseKeywordSearchNode_properties_dataSource(), Bundle.MultiCaseKeywordSearchNode_properties_dataSource(), Bundle.MultiCaseKeywordSearchNode_properties_dataSource(),
                    searchHit.getDataSourceName()));
            ss.put(new NodeProperty<>(Bundle.MultiCaseKeywordSearchNode_properties_path(), Bundle.MultiCaseKeywordSearchNode_properties_path(), Bundle.MultiCaseKeywordSearchNode_properties_path(),
                    searchHit.getSourcePath()));
            ss.put(new NodeProperty<>(Bundle.MultiCaseKeywordSearchNode_properties_sourceType(), Bundle.MultiCaseKeywordSearchNode_properties_sourceType(), Bundle.MultiCaseKeywordSearchNode_properties_sourceType(),
                    searchHit.getSourceType().getDisplayName()));
            ss.put(new NodeProperty<>(Bundle.MultiCaseKeywordSearchNode_properties_source(), Bundle.MultiCaseKeywordSearchNode_properties_source(), Bundle.MultiCaseKeywordSearchNode_properties_source(),
                    searchHit.getSourceName()));
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            actions.add(new OpenCaseAction(getCasePath()));
            actions.add(new CopyResultAction(searchHit));
            return actions.toArray(new Action[actions.size()]);
        }
    }

    @NbBundle.Messages({"MultiCaseKeywordSearchNode.copyResultAction.text=Copy to clipboard"})
    /**
     * Put the contents of the selected row in the clipboard in the same tab
     * seperated format as pressing ctrl+c.
     */
    private static class CopyResultAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        SearchHit result;

        /**
         * Construct a new CopyResultAction
         */
        CopyResultAction(SearchHit selectedResult) {
            super(Bundle.MultiCaseKeywordSearchNode_copyResultAction_text());
            result = selectedResult;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            StringSelection resultSelection = new StringSelection(result.getCaseDisplayName()+ "\t"
                    + result.getCaseDirectoryPath() + "\t"
                    + result.getDataSourceName() + "\t"
                    + result.getSourceType().getDisplayName() + "\t"
                    + result.getSourceName() + "\t"
                    + result.getSourcePath() + "\t");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(resultSelection, resultSelection);
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }

    }

    @NbBundle.Messages({"MultiCaseKeywordSearchNode.OpenCaseAction.text=Open Case"})
    /**
     * Action to open the case associated with the selected node.
     */
    private static class OpenCaseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final String caseDirPath;

        /**
         * Finds the path to the .aut file for the specified case directory.
         *
         * @param caseDirectory the directory to check for a .aut file
         *
         * @return the path to the first .aut file found in the directory
         *
         * @throws CaseActionException if there was an issue finding a .aut file
         */
        private static String findAutFile(String caseDirectory) throws CaseActionException {
            File caseFolder = Paths.get(caseDirectory).toFile();
            if (caseFolder.exists()) {
                /*
                 * Search for '*.aut' files.
                 */
                File[] fileArray = caseFolder.listFiles();
                if (fileArray == null) {
                    throw new CaseActionException("No files found in case directory");
                }
                String autFilePath = null;
                for (File file : fileArray) {
                    String name = file.getName().toLowerCase();
                    if (autFilePath == null && name.endsWith(getFileExtension())) {
                        return file.getAbsolutePath();
                    }
                }
                throw new CaseActionException("No .aut files found in case directory");
            }
            throw new CaseActionException("Case directory was not found");
        }

        /**
         * Construct a new open case action
         *
         * @param path the path to the case directory for the case to open
         */
        OpenCaseAction(String path) {
            super(Bundle.MultiCaseKeywordSearchNode_OpenCaseAction_text());
            caseDirPath = path;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            StartupWindowProvider.getInstance().close();
            new Thread(
                    () -> {
                        try {
                            Case.openAsCurrentCase(findAutFile(caseDirPath));
                        } catch (CaseActionException ex) {
                            if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                                LOGGER.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseDirPath), ex); //NON-NLS
                                MessageNotifyUtil.Message.error(ex.getCause().getLocalizedMessage());
                            }
                        }
                    }
            ).start();
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }

    }
}
