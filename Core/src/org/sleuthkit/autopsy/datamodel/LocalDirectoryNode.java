/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.FileSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.LocalDirectory;

/**
 * Node for a local directory
 */
public class LocalDirectoryNode extends AbstractAbstractFileNode<LocalDirectory> {

    private static final Logger logger = Logger.getLogger(VirtualDirectoryNode.class.getName());

    public static String nameForLocalDir(LocalDirectory ld) {
        return ld.getName();
    }

    public LocalDirectoryNode(LocalDirectory ld) {
        super(ld);

        this.setDisplayName(nameForLocalDir(ld));
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/credit-card.png"); //NON-NLS

    }

    /**
     * Right click action for this node
     *
     * @param popup
     *
     * @return
     */
    @Override
    @NbBundle.Messages({"LocalDirectoryNode.action.runIngestMods.text=Run Ingest Modules",
        "LocalDirectoryNode.getActions.viewInNewWin.text=View in New Window"
    })
    public Action[] getActions(boolean popup) {
        List<Action> actions = new ArrayList<>();
        for (Action a : super.getActions(true)) {
            actions.add(a);
        }

        actions.add(new NewWindowViewAction(
                Bundle.LocalDirectoryNode_action_runIngestMods_text(), this));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(new FileSearchAction(
                Bundle.ImageNode_getActions_openFileSearchByAttr_text()));
        actions.add(new RunIngestModulesAction(Collections.<Content>singletonList(content)));
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions.toArray(new Action[0]);
    }

    @Override
    @Messages({"LocalDirectoryNode.createSheet.size.name=Size (Bytes)",
        "LocalDirectoryNode.createSheet.size.displayName=Size (Bytes)",
        "LocalDirectoryNode.createSheet.size.desc=Size of the data source in bytes.",
        "LocalDirectoryNode.createSheet.type.name=Type",
        "LocalDirectoryNode.createSheet.type.displayName=Type",
        "LocalDirectoryNode.createSheet.type.desc=Type of the image.",
        "LocalDirectoryNode.createSheet.type.text=Logical File Set",
        "LocalDirectoryNode.createSheet.timezone.name=Timezone",
        "LocalDirectoryNode.createSheet.timezone.displayName=Timezone",
        "LocalDirectoryNode.createSheet.timezone.desc=Timezone of the image",
        "LocalDirectoryNode.createSheet.deviceId.name=Device ID",
        "LocalDirectoryNode.createSheet.deviceId.displayName=Device ID",
        "LocalDirectoryNode.createSheet.deviceId.desc=Device ID of the image",
        "LocalDirectoryNode.createSheet.name.name=Name",
        "LocalDirectoryNode.createSheet.name.displayName=Name",
        "LocalDirectoryNode.createSheet.name.desc=no description",
        "LocalDirectoryNode.createSheet.noDesc=no description"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(Bundle.LocalDirectoryNode_createSheet_name_name(),
                Bundle.LocalDirectoryNode_createSheet_name_displayName(),
                Bundle.LocalDirectoryNode_createSheet_name_desc(),
                getName()));

        if (!this.content.isDataSource()) {
            Map<String, Object> map = new LinkedHashMap<>();
            fillPropertyMap(map, content);

            final String NO_DESCR = Bundle.LocalDirectoryNode_createSheet_noDesc();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                ss.put(new NodeProperty<>(entry.getKey(), entry.getKey(), NO_DESCR, entry.getValue()));
            }
            addTagProperty(ss);
        } else {
            ss.put(new NodeProperty<>(Bundle.LocalDirectoryNode_createSheet_type_name(),
                    Bundle.LocalDirectoryNode_createSheet_type_displayName(),
                    Bundle.LocalDirectoryNode_createSheet_type_desc(),
                    Bundle.LocalDirectoryNode_createSheet_type_text()));
            ss.put(new NodeProperty<>(Bundle.LocalDirectoryNode_createSheet_size_name(),
                    Bundle.LocalDirectoryNode_createSheet_size_displayName(),
                    Bundle.LocalDirectoryNode_createSheet_size_desc(),
                    this.content.getSize()));
            try (SleuthkitCase.CaseDbQuery query = Case.getCurrentCase().getSleuthkitCase().executeQuery("SELECT time_zone FROM data_source_info WHERE obj_id = " + this.content.getId())) {
                ResultSet timeZoneSet = query.getResultSet();
                if (timeZoneSet.next()) {
                    ss.put(new NodeProperty<>(Bundle.LocalDirectoryNode_createSheet_timezone_name(),
                            Bundle.LocalDirectoryNode_createSheet_timezone_displayName(),
                            Bundle.LocalDirectoryNode_createSheet_timezone_desc(),
                            timeZoneSet.getString("time_zone")));
                }
            } catch (SQLException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get time zone for the following image: " + this.content.getId(), ex);
            }
            try (SleuthkitCase.CaseDbQuery query = Case.getCurrentCase().getSleuthkitCase().executeQuery("SELECT device_id FROM data_source_info WHERE obj_id = " + this.content.getId());) {
                ResultSet deviceIdSet = query.getResultSet();
                if (deviceIdSet.next()) {
                    ss.put(new NodeProperty<>(Bundle.LocalDirectoryNode_createSheet_deviceId_name(),
                            Bundle.LocalDirectoryNode_createSheet_deviceId_displayName(),
                            Bundle.LocalDirectoryNode_createSheet_deviceId_desc(),
                            deviceIdSet.getString("device_id")));
                }
            } catch (SQLException | TskCoreException ex) {
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

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    /**
     * Convert meta flag long to user-readable string / label
     *
     * @param metaFlag to convert
     *
     * @return string formatted meta flag representation
     */
    public static String metaFlagToString(short metaFlag) {

        String result = "";

        short allocFlag = TskData.TSK_FS_META_FLAG_ENUM.ALLOC.getValue();
        short unallocFlag = TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.getValue();

        if ((metaFlag & allocFlag) == allocFlag) {
            result = TskData.TSK_FS_META_FLAG_ENUM.ALLOC.toString();
        }
        if ((metaFlag & unallocFlag) == unallocFlag) {
            result = TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.toString();
        }

        return result;
    }

    @Override
    public String getItemType() {
        // use content.isDataSource if different column settings are desired
        return DisplayableItemNode.FILE_PARENT_NODE_KEY;
    }
}
