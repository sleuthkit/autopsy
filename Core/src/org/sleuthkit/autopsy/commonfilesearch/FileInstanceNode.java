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

import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Used by the Common Files search feature to encapsulate instances of a given 
 MD5s matched in the search.  These nodes will be children of <code>Md5Node</code>s.
 */
public class FileInstanceNode extends FileNode {
    
    private final String dataSource;

    /**
     * Create a node which can be used in a multilayer tree table and is based
     * on an <code>AbstractFile</code>.
     * 
     * @param fsContent
     * @param dataSource 
     */
    public FileInstanceNode(AbstractFile fsContent, String dataSource) {
        super(fsContent);
        this.dataSource = dataSource;
        
        this.setDisplayName(fsContent.getName());
    }

    @Override
    public boolean isLeafTypeNode(){
        //Not used atm - could maybe be leveraged for better use in Children objects
        return true;
    }
    
    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    String getDataSource() {
        return this.dataSource;
    }

    @NbBundle.Messages({"FileInstanceNode.createSheet.noDescription= "})
    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        //Map<String, Object> map = new LinkedHashMap<>();
        //fillPropertyMap(map, this);

        final String NO_DESCR = Bundle.FileInstanceNode_createSheet_noDescription();
        
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), NO_DESCR, this.getContent().getName()));
        //sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), NO_DESCR, ""));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), NO_DESCR, this.getContent().getParentPath()));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), NO_DESCR, getHashSetHitsForFile(this.getContent())));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), NO_DESCR, this.getDataSource()));
        sheetSet.put(new NodeProperty<>(Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), NO_DESCR, StringUtils.defaultString(this.getContent().getMIMEType())));

        this.addTagProperty(sheetSet);

        return sheet;
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param node The item to get properties for.
     */
//    static private void fillPropertyMap(Map<String, Object> map, FileInstanceNode node) {
//
//        map.put(CommonFilePropertyType.Files.toString(), node.getContent().getName());
//        map.put(CommonFilePropertyType.Instances.toString(), "");
//        map.put(CommonFilePropertyType.ParentPath.toString(), node.getContent().getParentPath());
//        map.put(CommonFilePropertyType.HashsetHits.toString(), getHashSetHitsForFile(node.getContent()));
//        map.put(CommonFilePropertyType.DataSource.toString(), node.getDataSource());
//        map.put(CommonFilePropertyType.MimeType.toString(), StringUtils.defaultString(node.getContent().getMIMEType()));
//        map.put(CommonFilePropertyType.Tags.toString(), "");
//    }
//
//    /**
//     * Encapsulates the columns to be displayed for reach row represented by an 
//     * instance of this object.
//     */
//    public enum CommonFilePropertyType {
//
//        Files(Bundle.CommonFilesSearchResultsViewerTable_filesColLbl()),
//        Instances(Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl()),
//        ParentPath(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl()),
//        HashsetHits(Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl()),
//        DataSource(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl()),
//        MimeType(Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl()),
//        Tags(Bundle.CommonFilesSearchResultsViewerTable_tagsColLbl1());
//
//        final private String displayString;
//
//        private CommonFilePropertyType(String displayString) {
//            this.displayString = displayString;
//        }
//
//        @Override
//        public String toString() {
//            return displayString;
//        }
//    }
}
