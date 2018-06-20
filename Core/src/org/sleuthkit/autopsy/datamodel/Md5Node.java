/*
 * 
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
package org.sleuthkit.autopsy.datamodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonfilesearch.FileInstanceMetadata;
import org.sleuthkit.autopsy.commonfilesearch.Md5Metadata;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a common files match - two or more files which appear to be the
 * same file and appear as children of this node.  This node will simply contain
 * the MD5 of the matched files, the data sources those files were found within,
 * and a count of the instances represented by the md5.
 */
public class Md5Node extends DisplayableItemNode {
    
    private static final Logger LOGGER = Logger.getLogger(Md5Node.class.getName());    
    
    private final String md5Hash;
    private final int commonFileCount;
    private final String dataSources;

    /**
     * Create a Match node whose children will all have this object in common.
     * @param data the common feature, and the children
     */
    public Md5Node(Md5Metadata data) {
        super(Children.create(
                new FileInstanceNodeFactory(data), true));
        
        this.commonFileCount = data.size();
        this.dataSources = String.join(", ", data.getDataSources());
        this.md5Hash = data.getMd5();
        
        this.setDisplayName(this.md5Hash);
    }

    /**
     * How many files are in common?  This will be the number of children.
     * @return int
     */
    int getCommonFileCount() {
        return this.commonFileCount;
    }

    /**
     * Datasources where these matches occur.
     * @return string delimited list of sources
     */
    String getDataSources() {
        return this.dataSources;
    }

    /**
     * MD5 which is common to these matches
     * @return string md5 hash
     */
    public String getMd5() {
        return this.md5Hash;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, this);

        final String NO_DESCR = Bundle.AbstractFsContentNode_noDesc_text();
        for (Md5Node.CommonFileParentPropertyType propType : Md5Node.CommonFileParentPropertyType.values()) {
            final String propString = propType.toString();
            sheetSet.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }

        return sheet;
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param node The item to get properties for.
     */
    static private void fillPropertyMap(Map<String, Object> map, Md5Node node) {
        //map.put(CommonFileParentPropertyType.Case.toString(), "");
        map.put(CommonFileParentPropertyType.DataSource.toString(), node.getDataSources());
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    /**
     * Child generator for <code>FileInstanceNode</code> of <code>Md5Node</code>.
     */
    static class FileInstanceNodeFactory extends ChildFactory<FileInstanceMetadata> {

        private final Md5Metadata descendants;

        FileInstanceNodeFactory(Md5Metadata descendants) {
            this.descendants = descendants;
        }

        @Override
        protected Node createNodeForKey(FileInstanceMetadata file) {
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                SleuthkitCase tskDb = currentCase.getSleuthkitCase();
                AbstractFile abstractFile = tskDb.findAllFilesWhere(String.format("obj_id in (%s)", file.getObjectId())).get(0);
                
                return new FileInstanceNode(abstractFile, file.getDataSourceName());
            } catch (NoCurrentCaseException | TskCoreException ex) {
                LOGGER.log(Level.SEVERE, String.format("Unable to create node for file with obj_id: %s.", new Object[]{file.getObjectId()}), ex);
            }
            return null;
        }

        @Override
        protected boolean createKeys(List<FileInstanceMetadata> list) {            
            list.addAll(this.descendants.getMetadata());
            return true;
        }
    }

    @NbBundle.Messages({
        "CommonFileParentPropertyType.fileColLbl=File",
        "CommonFileParentPropertyType.instanceColLbl=Instance Count",
        "CommonFileParentPropertyType.caseColLbl=Case",
        "CommonFileParentPropertyType.dataSourceColLbl=Data Source"})
    public enum CommonFileParentPropertyType {

        DataSource(Bundle.CommonFileParentPropertyType_dataSourceColLbl());

        final private String displayString;

        private CommonFileParentPropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return this.displayString;
        }
    }
}
