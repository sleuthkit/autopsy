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

    // Note: this order matters for the search result, changed it if the order of property headers on the "KeywordSearchNode"changed
    public static enum FsContentPropertyType {

        NAME {
            @Override
            public String toString() {
                return "Name";
            }
        },
        LOCATION {
            @Override
            public String toString() {
                return "Location";
            }
        },
        MOD_TIME {
            @Override
            public String toString() {
                return "Mod. Time";
            }
        },
        CHANGED_TIME {
            @Override
            public String toString() {
                return "Change Time";
            }
        },
        ACCESS_TIME {
            @Override
            public String toString() {
                return "Access Time";
            }
        },
        CREATED_TIME {
            @Override
            public String toString() {
                return "Created Time";
            }
        },
        SIZE {
            @Override
            public String toString() {
                return "Size";
            }
        },
        FLAGS_DIR {
            @Override
            public String toString() {
                return "Flags(Dir)";
            }
        },
        FLAGS_META {
            @Override
            public String toString() {
                return "Flags(Meta)";
            }
        },
        MODE {
            @Override
            public String toString() {
                return "Mode";
            }
        },
        USER_ID {
            @Override
            public String toString() {
                return "UserID";
            }
        },
        GROUP_ID {
            @Override
            public String toString() {
                return "GroupID";
            }
        },
        META_ADDR {
            @Override
            public String toString() {
                return "Meta Addr.";
            }
        },
        ATTR_ADDR {
            @Override
            public String toString() {
                return "Attr. Addr.";
            }
        },
        TYPE_DIR {
            @Override
            public String toString() {
                return "Type(Dir)";
            }
        },
        TYPE_META {
            @Override
            public String toString() {
                return "Type(Meta)";
            }
        },
        KNOWN {
            @Override
            public String toString() {
                return "Known";
            }
        },
        MD5HASH {
            @Override
            public String toString() {
                return "MD5 Hash";
            }
        }
    }
    
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
        this.setDisplayName(AbstractFsContentNode.getFsContentName(fsContent));
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

        FsContentPropertyType[] fsTypes = FsContentPropertyType.values();
        final int FS_PROPS_LEN = fsTypes.length;
        final String NO_DESCR = "no description";
        for (int i = 0; i < FS_PROPS_LEN; ++i) {
            final FsContentPropertyType propType = FsContentPropertyType.values()[i];
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
        
        map.put(FsContentPropertyType.NAME.toString(), getFsContentName(content));
        map.put(FsContentPropertyType.LOCATION.toString(), path);
        map.put(FsContentPropertyType.MOD_TIME.toString(), ContentUtils.getStringTime(content.getMtime(), content));
        map.put(FsContentPropertyType.CHANGED_TIME.toString(), ContentUtils.getStringTime(content.getCtime(), content));
        map.put(FsContentPropertyType.ACCESS_TIME.toString(), ContentUtils.getStringTime(content.getAtime(), content));
        map.put(FsContentPropertyType.CREATED_TIME.toString(), ContentUtils.getStringTime(content.getCrtime(), content));
        map.put(FsContentPropertyType.SIZE.toString(), content.getSize());
        map.put(FsContentPropertyType.FLAGS_DIR.toString(), content.getDirFlagAsString());
        map.put(FsContentPropertyType.FLAGS_META.toString(), content.getMetaFlagsAsString());
        map.put(FsContentPropertyType.MODE.toString(), content.getModesAsString());
        map.put(FsContentPropertyType.USER_ID.toString(), content.getUid());
        map.put(FsContentPropertyType.GROUP_ID.toString(), content.getGid());
        map.put(FsContentPropertyType.META_ADDR.toString(), content.getMetaAddr());
        map.put(FsContentPropertyType.ATTR_ADDR.toString(), Long.toString(content.getAttrType().getValue()) + "-" + Long.toString(content.getAttrId()));
        map.put(FsContentPropertyType.TYPE_DIR.toString(), content.getDirType().getLabel());
        map.put(FsContentPropertyType.TYPE_META.toString(), content.getMetaType().toString());
        map.put(FsContentPropertyType.KNOWN.toString(), content.getKnown().getName());
        map.put(FsContentPropertyType.MD5HASH.toString(), content.getMd5Hash() == null ? "" : content.getMd5Hash());
    }

    static String getFsContentName(FsContent fsContent) {
        String name = fsContent.getName();
        if (name.equals("..")) {
            name = DirectoryNode.DOTDOTDIR;
        } else if (name.equals(".")) {
            name = DirectoryNode.DOTDIR;
        }
        return name;
    }
}
