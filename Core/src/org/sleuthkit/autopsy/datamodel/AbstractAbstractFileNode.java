/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract node that encapsulates AbstractFile data
 *
 * @param <T> type of the AbstractFile to encapsulate
 */
public abstract class AbstractAbstractFileNode<T extends AbstractFile> extends AbstractContentNode<T> {

    private static final Logger LOGGER = Logger.getLogger(AbstractAbstractFileNode.class.getName());

    /**
     * @param <T>          type of the AbstractFile data to encapsulate
     * @param abstractFile file to encapsulate
     */
    AbstractAbstractFileNode(T abstractFile) {
        super(abstractFile);
    }

    // Note: this order matters for the search result, changed it if the order of property headers on the "KeywordSearchNode"changed
    public static enum AbstractFilePropertyType {

        NAME {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.nameColLbl");
                    }
                },
        LOCATION {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.locationColLbl");
                    }
                },
        MOD_TIME {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.modifiedTimeColLbl");
                    }
                },
        CHANGED_TIME {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.changeTimeColLbl");
                    }
                },
        ACCESS_TIME {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.accessTimeColLbl");
                    }
                },
        CREATED_TIME {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.createdTimeColLbl");
                    }
                },
        SIZE {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.sizeColLbl");
                    }
                },
        FLAGS_DIR {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.flagsDirColLbl");
                    }
                },
        FLAGS_META {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.flagsMetaColLbl");
                    }
                },
        MODE {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.modeColLbl");
                    }
                },
        USER_ID {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.useridColLbl");
                    }
                },
        GROUP_ID {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.groupidColLbl");
                    }
                },
        META_ADDR {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.metaAddrColLbl");
                    }
                },
        ATTR_ADDR {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.attrAddrColLbl");
                    }
                },
        TYPE_DIR {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.typeDirColLbl");
                    }
                },
        TYPE_META {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.typeMetaColLbl");
                    }
                },
        KNOWN {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.knownColLbl");
                    }
                },
        HASHSETS {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.inHashsetsColLbl");
                    }
                },
        MD5HASH {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.md5HashColLbl");
                    }
                },
        ObjectID {
                    @Override
                    public String toString() {
                        return NbBundle.getMessage(this.getClass(), "AbstractAbstractFileNode.objectId");

                    }
                },
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map     map with preserved ordering, where property names/values
     *                are put
     * @param content to extract properties from
     */
    public static void fillPropertyMap(Map<String, Object> map, AbstractFile content) {

        String path = "";
        try {
            path = content.getUniquePath();
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Except while calling Content.getUniquePath() on {0}", content); //NON-NLS
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
        map.put(AbstractFilePropertyType.HASHSETS.toString(), getHashSetHitsForFile(content));
        map.put(AbstractFilePropertyType.MD5HASH.toString(), content.getMd5Hash() == null ? "" : content.getMd5Hash());
        map.put(AbstractFilePropertyType.ObjectID.toString(), content.getId());
    }

    static String getContentDisplayName(AbstractFile file) {
        String name = file.getName();
        switch (name) {
            case "..":
                return DirectoryNode.DOTDOTDIR;

            case ".":
                return DirectoryNode.DOTDIR;
            default:
                return name;
        }
    }

    @SuppressWarnings("deprecation")
    private static String getHashSetHitsForFile(AbstractFile content) {
        try {
            return StringUtils.join(content.getHashSetNames(), ", ");
        } catch (TskCoreException tskCoreException) {
            LOGGER.log(Level.WARNING, "Error getting hashset hits: ", tskCoreException); //NON-NLS
            return "";
        }
    }
}
