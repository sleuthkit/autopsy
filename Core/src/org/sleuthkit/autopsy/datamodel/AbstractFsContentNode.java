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
package org.sleuthkit.autopsy.datamodel;

import java.util.LinkedHashMap;
import java.util.Map;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Abstract class that implements the commonality between File and Directory
 * Nodes (same properties).
 * 
 */
public abstract class AbstractFsContentNode<T extends AbstractFile> extends AbstractAbstractFileNode<T> {
    
    private static Logger logger = Logger.getLogger(AbstractFsContentNode.class.getName());

    
    private boolean directoryBrowseMode;
    public static final String HIDE_PARENT = "hide_parent";

    AbstractFsContentNode(T fsContent) {
        this(fsContent, true);
    }

    /**
     * Constructor
     *
     * @param content the content
     * @param directoryBrowseMode how the user caused this node to be created:
     * if by browsing the image contents, it is true. If by selecting a file
     * filter (e.g. 'type' or 'recent'), it is false
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
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        AbstractAbstractFileNode.fillPropertyMap(map, content);

        AbstractFilePropertyType[] fsTypes = AbstractFilePropertyType.values();
        final int FS_PROPS_LEN = fsTypes.length;
        final String NO_DESCR = "no description";
        for (int i = 0; i < FS_PROPS_LEN; ++i) {
            final AbstractFilePropertyType propType = AbstractFilePropertyType.values()[i];
            final String propString = propType.toString();
            ss.put(new NodeProperty(propString, propString, NO_DESCR, map.get(propString)));
        }
        if (directoryBrowseMode) {
            ss.put(new NodeProperty(HIDE_PARENT, HIDE_PARENT, HIDE_PARENT, HIDE_PARENT));
        }

        return s;
    }

   
   
}
