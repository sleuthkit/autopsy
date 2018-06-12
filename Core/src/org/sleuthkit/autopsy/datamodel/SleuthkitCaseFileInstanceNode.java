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
package org.sleuthkit.autopsy.datamodel;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Used by the Common Files search feature to encapsulate instances of a given 
 * MD5s matched in the search.  These nodes will be children of <code>Md5Node</code>s.
 * 
 * Use this type for files which are in the current case.  Contrast with 
 * <code>CentralRepositoryFileInstanceNode</code> which should be used when the 
 * FileInstance was found in some case not presently open in Autopsy, but present 
 * in the Central Repository.
 */
public class SleuthkitCaseFileInstanceNode extends FileNode {
    
    private final String dataSource;

    public SleuthkitCaseFileInstanceNode(AbstractFile fsContent, String dataSource) {
        super(fsContent);
        this.content = fsContent;
        this.dataSource = dataSource;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public AbstractFile getContent() {
        return this.content;
    }

    String getDataSource() {
        return this.dataSource;
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
        for (SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType propType : SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.values()) {
            final String propString = propType.toString();
            final Object property = map.get(propString);
            final NodeProperty<Object> nodeProperty = new NodeProperty<>(propString, propString, NO_DESCR, property);
            sheetSet.put(nodeProperty);
        }

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
    static private void fillPropertyMap(Map<String, Object> map, SleuthkitCaseFileInstanceNode node) {

        map.put(SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.File.toString(), node.getName());
        map.put(SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.ParentPath.toString(), node.getContent().getParentPath());
        map.put(SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.HashsetHits.toString(), getHashSetHitsForFile(node.getContent()));
        map.put(SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.Case.toString(), "TODO");
        map.put(SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.DataSource.toString(), node.getDataSource());
        map.put(SleuthkitCaseFileInstanceNode.SleuthkitCaseFileInstanceNodePropertyType.MimeType.toString(), StringUtils.defaultString(node.content.getMIMEType()));
    }

    /**
     * Encapsulates the columns to be displayed for reach row represented by an 
     * instance of this object.
     */
    @NbBundle.Messages({
        "SleuthkitCaseFileInstanceNodePropertyType.fileColLbl=File",
        "SleuthkitCaseFileInstanceNodePropertyType.pathColLbl=Parent Path",
        "SleuthkitCaseFileInstanceNodePropertyType.hashsetHitsColLbl=Hash Set Hits",
        "SleuthkitCaseFileInstanceNodePropertyType.caseColLbl=Case",
        "SleuthkitCaseFileInstanceNodePropertyType.dataSourceColLbl=Data Source",
        "SleuthkitCaseFileInstanceNodePropertyType.mimeTypeColLbl=MIME Type"
    })
    public enum SleuthkitCaseFileInstanceNodePropertyType {

        File(Bundle.SleuthkitCaseFileInstanceNodePropertyType_fileColLbl()),
        ParentPath(Bundle.SleuthkitCaseFileInstanceNodePropertyType_pathColLbl()),
        HashsetHits(Bundle.SleuthkitCaseFileInstanceNodePropertyType_hashsetHitsColLbl()),
        Case(Bundle.SleuthkitCaseFileInstanceNodePropertyType_caseColLbl()),
        DataSource(Bundle.SleuthkitCaseFileInstanceNodePropertyType_dataSourceColLbl()),
        MimeType(Bundle.SleuthkitCaseFileInstanceNodePropertyType_mimeTypeColLbl());

        final private String displayString;

        private SleuthkitCaseFileInstanceNodePropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }
}
