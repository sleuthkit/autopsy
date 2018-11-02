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
package org.sleuthkit.autopsy.commonfilesearch;

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

    /**
     * Create a node which can be used in a multilayer tree table and is based
     * on an <code>AbstractFile</code>.
     *
     * @param fsContent the file which is being represented by this node
     * @param caseName the name of the case
     * @param dataSource the datasource which contains the file
     * 
     */
    public CaseDBCommonAttributeInstanceNode(AbstractFile fsContent, String caseName, String dataSource) {
        super(fsContent, false);
        this.caseName = caseName;
        this.dataSource = dataSource;
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
                NbBundle.getMessage(AbstractAbstractFileNode.class,  "AbstractAbstractFileNode.nameColLbl"),
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.score.name"), 
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.comment.name"), 
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.count.name"), 
                NbBundle.getMessage(AbstractAbstractFileNode.class,  "AbstractAbstractFileNode.mimeType")));
        
        for(Property<?> p : sheetSet.getProperties()) {
            if(!keepProps.contains(p.getName())){
                sheetSet.remove(p.getName());
            }
        }
        final String NO_DESCR = Bundle.CommonFilesSearchResultsViewerTable_noDescText();
        
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, this.getDataSource()));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), NO_DESCR, caseName));
        
        return sheet;
    }
}