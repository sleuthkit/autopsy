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
package org.sleuthkit.autopsy.datamodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.LocalDirectory;

/**
 * Node for a local directory
 */
public class LocalDirectoryNode extends SpecialDirectoryNode {

    public static String nameForLocalDir(LocalDirectory ld) {
        return ld.getName();
    }

    public LocalDirectoryNode(LocalDirectory ld) {
        super(ld);

        this.setDisplayName(nameForLocalDir(ld));
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //NON-NLS

    }

    @Override
    @NbBundle.Messages({
        "LocalDirectoryNode.createSheet.name.name=Name",
        "LocalDirectoryNode.createSheet.name.displayName=Name",
        "LocalDirectoryNode.createSheet.name.desc=no description",
        "LocalDirectoryNode.createSheet.noDesc=no description"})
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        List<ContentTag> tags = getContentTagsFromDatabase();
        sheetSet.put(new NodeProperty<>(Bundle.LocalDirectoryNode_createSheet_name_name(),
                Bundle.LocalDirectoryNode_createSheet_name_displayName(),
                Bundle.LocalDirectoryNode_createSheet_name_desc(),
                getName()));
        CorrelationAttributeInstance correlationAttribute = getCorrelationAttributeInstance();
        addScoreProperty(sheetSet, tags);
        addCommentProperty(sheetSet, tags, correlationAttribute);
        addCountProperty(sheetSet, correlationAttribute);
        // At present, a LocalDirectory will never be a datasource - the top level of a logical
        // file set is a VirtualDirectory
        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, getContent());

        final String NO_DESCR = Bundle.LocalDirectoryNode_createSheet_noDesc();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sheetSet.put(new NodeProperty<>(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
        }

        return sheet;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
