/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Node that wraps CaseDBCommonAttributeInstance to represent a file instance
 * stored in the CaseDB.
 */
public class CaseDBCommonAttributeInstanceNode extends FileNode {

    private final String caseName;
    private final String dataSource;
    private final String value;
    private final AbstractCommonAttributeInstance.NODE_TYPE nodeType;

    /**
     * Create a node which can be used in a multilayer tree table and is based
     * on an <code>AbstractFile</code>.
     *
     * @param fsContent  the file which is being represented by this node
     * @param caseName   the name of the case
     * @param dataSource the datasource which contains the file
     * @param value      the value that the correlation attribute was matched on
     * @param nodeType   the type of node to display columns for
     *
     */
    public CaseDBCommonAttributeInstanceNode(AbstractFile fsContent, String caseName, String dataSource, String value, AbstractCommonAttributeInstance.NODE_TYPE nodeType) {
        super(fsContent, false);
        this.caseName = caseName;
        this.dataSource = dataSource;
        this.nodeType = nodeType;
        this.value = value;
    }

    @Override
    public boolean isLeafTypeNode() {
        //Not used atm - could maybe be leveraged for better use in Children objects
        return true;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public String getCase() {
        return this.caseName;
    }

    public String getDataSource() {
        return this.dataSource;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        Set<String> keepProps = new HashSet<>(Arrays.asList(
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"),
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.score.name"),
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.comment.name"),
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.count.name"),
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType")));

        for (Property<?> p : sheetSet.getProperties()) {
            if (!keepProps.contains(p.getName())) {
                sheetSet.remove(p.getName());
            }
        }
        final String NO_DESCR = Bundle.CommonFilesSearchResultsViewerTable_noDescText();
        //add different columns for complete information depending on how nodes are structured in results
        if (nodeType == AbstractCommonAttributeInstance.NODE_TYPE.COUNT_NODE) {
             sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, this.getContent().getParentPath()));
            sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, this.getDataSource()));
            sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl(), Bundle.CommonFilesSearchResultsViewerTable_caseColLbl(), NO_DESCR, caseName));
        } else if (nodeType == AbstractCommonAttributeInstance.NODE_TYPE.CASE_NODE) {
             sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_localPath(), Bundle.CommonFilesSearchResultsViewerTable_localPath(), NO_DESCR, this.getContent().getParentPath()));
            sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), NO_DESCR, this.value));
        }
        return sheet;
    }
}
