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
import org.sleuthkit.datamodel.FsContent;

/**
 * Abstract class that implements the commonality between File and Directory
 * Nodes (same properties).
 */
public abstract class AbstractFsContentNode<T extends FsContent> extends AbstractContentNode<T> {

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
    }

    AbstractFsContentNode(T fsContent) {
        super(fsContent);
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

        return s;
    }

    /**
     * Fill map with FsContent properties
     * @param map, with preserved ordering, where property names/values are put
     * @param content to extract properties from
     */
    public static void fillPropertyMap(Map<String, Object> map, FsContent content) {
        map.put(FsContentPropertyType.NAME.toString(), content.getName());
        map.put(FsContentPropertyType.LOCATION.toString(), DataConversion.getformattedPath(ContentUtils.getDisplayPath(content), 0));
        map.put(FsContentPropertyType.MOD_TIME.toString(), content.getMtimeAsDate());
        map.put(FsContentPropertyType.CHANGED_TIME.toString(), content.getCtimeAsDate());
        map.put(FsContentPropertyType.ACCESS_TIME.toString(), content.getAtimeAsDate());
        map.put(FsContentPropertyType.CREATED_TIME.toString(), content.getCrtimeAsDate());
        map.put(FsContentPropertyType.SIZE.toString(), content.getSize());
        map.put(FsContentPropertyType.FLAGS_DIR.toString(), content.getDirFlagsAsString());
        map.put(FsContentPropertyType.FLAGS_META.toString(), content.getMetaFlagsAsString());
        map.put(FsContentPropertyType.MODE.toString(), content.getModeAsString());
        map.put(FsContentPropertyType.USER_ID.toString(), content.getUid());
        map.put(FsContentPropertyType.GROUP_ID.toString(), content.getGid());
        map.put(FsContentPropertyType.META_ADDR.toString(), content.getMeta_addr());
        map.put(FsContentPropertyType.ATTR_ADDR.toString(), Long.toString(content.getAttr_type()) + "-" + Long.toString(content.getAttr_id()));
        map.put(FsContentPropertyType.TYPE_DIR.toString(), content.getDirTypeAsString());
        map.put(FsContentPropertyType.TYPE_META.toString(), content.getMetaTypeAsString());
        map.put(FsContentPropertyType.KNOWN.toString(), content.getKnown().getName());
    }
}
