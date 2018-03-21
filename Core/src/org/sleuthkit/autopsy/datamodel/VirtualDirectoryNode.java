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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Node for a virtual directory
 */
public class VirtualDirectoryNode extends SpecialDirectoryNode {

    private static final Logger logger = Logger.getLogger(VirtualDirectoryNode.class.getName());
    //prefix for special VirtualDirectory root nodes grouping local files
    public final static String LOGICAL_FILE_SET_PREFIX = "LogicalFileSet"; //NON-NLS

    public static String nameForVirtualDirectory(VirtualDirectory ld) {
        return ld.getName();
    }

    public VirtualDirectoryNode(VirtualDirectory ld) {
        super(ld);

        this.setDisplayName(nameForVirtualDirectory(ld));

        String name = ld.getName();

        //set icon for name, special case for logical file set
        if (ld.isDataSource()) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-virtual.png"); //TODO NON-NLS
        }
    }
    
    @Override
    @NbBundle.Messages({"VirtualDirectoryNode.createSheet.size.name=Size (Bytes)",
        "VirtualDirectoryNode.createSheet.size.displayName=Size (Bytes)",
        "VirtualDirectoryNode.createSheet.size.desc=Size of the data source in bytes.",
        "VirtualDirectoryNode.createSheet.type.name=Type",
        "VirtualDirectoryNode.createSheet.type.displayName=Type",
        "VirtualDirectoryNode.createSheet.type.desc=Type of the image.",
        "VirtualDirectoryNode.createSheet.type.text=Logical File Set",
        "VirtualDirectoryNode.createSheet.timezone.name=Timezone",
        "VirtualDirectoryNode.createSheet.timezone.displayName=Timezone",
        "VirtualDirectoryNode.createSheet.timezone.desc=Timezone of the image",
        "VirtualDirectoryNode.createSheet.deviceId.name=Device ID",
        "VirtualDirectoryNode.createSheet.deviceId.displayName=Device ID",
        "VirtualDirectoryNode.createSheet.deviceId.desc=Device ID of the image"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(),
                        "VirtualDirectoryNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.createSheet.name.desc"),
                getName()));

        if (!this.content.isDataSource()) {
            Map<String, Object> map = new LinkedHashMap<>();
            fillPropertyMap(map, getContent());

            final String NO_DESCR = NbBundle.getMessage(this.getClass(), "VirtualDirectoryNode.createSheet.noDesc");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                ss.put(new NodeProperty<>(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
            }
            addTagProperty(ss);
        } else {
            ss.put(new NodeProperty<>(Bundle.VirtualDirectoryNode_createSheet_type_name(),
                    Bundle.VirtualDirectoryNode_createSheet_type_displayName(),
                    Bundle.VirtualDirectoryNode_createSheet_type_desc(),
                    Bundle.VirtualDirectoryNode_createSheet_type_text()));
            ss.put(new NodeProperty<>(Bundle.VirtualDirectoryNode_createSheet_size_name(),
                    Bundle.VirtualDirectoryNode_createSheet_size_displayName(),
                    Bundle.VirtualDirectoryNode_createSheet_size_desc(),
                    this.content.getSize()));
            try (SleuthkitCase.CaseDbQuery query = Case.getOpenCase().getSleuthkitCase().executeQuery("SELECT time_zone FROM data_source_info WHERE obj_id = " + this.content.getId())) {
                ResultSet timeZoneSet = query.getResultSet();
                if (timeZoneSet.next()) {
                    ss.put(new NodeProperty<>(Bundle.VirtualDirectoryNode_createSheet_timezone_name(),
                            Bundle.VirtualDirectoryNode_createSheet_timezone_displayName(),
                            Bundle.VirtualDirectoryNode_createSheet_timezone_desc(),
                            timeZoneSet.getString("time_zone")));
                }
            } catch (SQLException | TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Failed to get time zone for the following image: " + this.content.getId(), ex);
            }
            try (SleuthkitCase.CaseDbQuery query = Case.getOpenCase().getSleuthkitCase().executeQuery("SELECT device_id FROM data_source_info WHERE obj_id = " + this.content.getId());) {
                ResultSet deviceIdSet = query.getResultSet();
                if (deviceIdSet.next()) {
                    ss.put(new NodeProperty<>(Bundle.VirtualDirectoryNode_createSheet_deviceId_name(),
                            Bundle.VirtualDirectoryNode_createSheet_deviceId_displayName(),
                            Bundle.VirtualDirectoryNode_createSheet_deviceId_desc(),
                            deviceIdSet.getString("device_id")));
                }
            } catch (SQLException | TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Failed to get device id for the following image: " + this.content.getId(), ex);
            }

        }

        return s;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }
}
