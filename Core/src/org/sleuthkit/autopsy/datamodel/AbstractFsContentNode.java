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
import java.util.logging.Level;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Abstract class that implements the commonality between File and Directory
 * Nodes (same properties).
 * 
 * TODO type bounds should be  T extends AbstractFile after fields/methods are factored up to AbstractFile
 */
public abstract class AbstractFsContentNode<T extends FsContent> extends AbstractAbstractFileNode<T> {
    
    private static Logger logger = Logger.getLogger(AbstractFsContentNode.class.getName());

    
    private boolean directoryBrowseMode;
    public static final String HIDE_PARENT = "hide_parent";

    AbstractFsContentNode(T fsContent) {
        this(fsContent, true);
    }

    /**
     * Constructor
     *
     * @param fsContent the fsContent
     * @param directoryBrowseMode how the user caused this node to be created:
     * if by browsing the image contents, it is true. If by selecting a file
     * filter (e.g. 'type' or 'recent'), it is false
     */
    AbstractFsContentNode(T fsContent, boolean directoryBrowseMode) {
        super(fsContent);
        this.setDisplayName(AbstractAbstractFileNode.getContentDisplayName(fsContent));
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
        fillPropertyMap(map, content);

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

    /**
     * Fill map with FsContent properties
     * 
     * TODO change to accept AbstractFile after datamodel refactor
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param content to extract properties from
     */
    public static void fillPropertyMap(Map<String, Object> map, FsContent content) {
        
        String path = "";
        try {
            path = content.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + content);
        }
        
        map.put(AbstractFilePropertyType.NAME.toString(), AbstractAbstractFileNode.getContentDisplayName(content));
        map.put(AbstractFilePropertyType.LOCATION.toString(), path);
        map.put(AbstractFilePropertyType.MOD_TIME.toString(), ContentUtils.getStringTime(content.getMtime(), content));
        map.put(AbstractFilePropertyType.CHANGED_TIME.toString(), ContentUtils.getStringTime(content.getCtime(), content));
        map.put(AbstractFilePropertyType.ACCESS_TIME.toString(), ContentUtils.getStringTime(content.getAtime(), content));
        map.put(AbstractFilePropertyType.CREATED_TIME.toString(), ContentUtils.getStringTime(content.getCrtime(), content));
        map.put(AbstractFilePropertyType.SIZE.toString(), content.getSize());
        map.put(AbstractFilePropertyType.FLAGS_DIR.toString(), content.getDirFlagAsString());
        map.put(AbstractFilePropertyType.FLAGS_META.toString(), content.getMetaFlagsAsString());
        map.put(AbstractFilePropertyType.MODE.toString(), content.getModesAsString());
        map.put(AbstractFilePropertyType.USER_ID.toString(), content.getUid());
        map.put(AbstractFilePropertyType.GROUP_ID.toString(), content.getGid());
        map.put(AbstractFilePropertyType.META_ADDR.toString(), content.getMetaAddr());
        map.put(AbstractFilePropertyType.ATTR_ADDR.toString(), Long.toString(content.getAttrType().getValue()) + "-" + Long.toString(content.getAttrId()));
        map.put(AbstractFilePropertyType.TYPE_DIR.toString(), content.getDirType().getLabel());
        map.put(AbstractFilePropertyType.TYPE_META.toString(), content.getMetaType().toString());
        map.put(AbstractFilePropertyType.KNOWN.toString(), content.getKnown().getName());
        map.put(AbstractFilePropertyType.MD5HASH.toString(), content.getMd5Hash() == null ? "" : content.getMd5Hash());
    }

   
}
