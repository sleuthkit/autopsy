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
import java.util.stream.Collectors;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;

/**
 * Abstract class that implements the commonality between File and Directory
 * Nodes (same properties).
 *
 * @param <T> extends AbstractFile
 */
public abstract class AbstractFsContentNode<T extends AbstractFile> extends AbstractAbstractFileNode<T> {

    private static Logger logger = Logger.getLogger(AbstractFsContentNode.class.getName());

    private boolean directoryBrowseMode;
    public static final String HIDE_PARENT = "hide_parent"; //NON-NLS

    AbstractFsContentNode(T fsContent) {
        this(fsContent, true);
    }

    /**
     * Constructor
     *
     * @param content             the content
     * @param directoryBrowseMode how the user caused this node to be created:
     *                            if by browsing the image contents, it is true.
     *                            If by selecting a file filter (e.g. 'type' or
     *                            'recent'), it is false
     */
    AbstractFsContentNode(T content, boolean directoryBrowseMode) {
        super(content);
        this.setDisplayName(AbstractAbstractFileNode.getContentDisplayName(content));
        this.directoryBrowseMode = directoryBrowseMode;
    }

    public boolean getDirectoryBrowseMode() {
        return directoryBrowseMode;
    }

    @Override
    @NbBundle.Messages("AbstractFsContentNode.noDesc.text=no description")
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        List<ContentTag> tags = getContentTagsFromDatabase();
        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, getContent());
        sheetSet.put(new NodeProperty<>("Name",
                "Name",
                "Name",
                getName()));
        addCommentProperty(sheetSet, tags);
        final String NO_DESCR = Bundle.AbstractFsContentNode_noDesc_text();
        for (AbstractFilePropertyType propType : AbstractFilePropertyType.values()) {
            final String propString = propType.toString();
            sheetSet.put(new NodeProperty<>(propString, propString, NO_DESCR, map.get(propString)));
        }
        if (directoryBrowseMode) {
            sheetSet.put(new NodeProperty<>(HIDE_PARENT, HIDE_PARENT, HIDE_PARENT, HIDE_PARENT));
        }

        // add tags property to the sheet
        addTagProperty(sheetSet, tags);

        return sheet;
    }

}
