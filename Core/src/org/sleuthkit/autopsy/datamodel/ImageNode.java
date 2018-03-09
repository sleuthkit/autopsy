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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.ExplorerNodeActionVisitor;
import org.sleuthkit.autopsy.directorytree.FileSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * This class is used to represent the "Node" for the image. The children of
 * this node are volumes.
 */
public class ImageNode extends AbstractContentNode<Image> {

    private static final Logger logger = Logger.getLogger(ImageNode.class.getName());

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     *
     * @param i Image to get the name of
     *
     * @return short name for the Image
     */
    static String nameForImage(Image i) {
        return i.getName();
    }

    /**
     * @param img
     */
    public ImageNode(Image img) {
        super(img);

        // set name, display name, and icon
        String imgName = nameForImage(img);
        this.setDisplayName(imgName);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hard-drive-icon.jpg"); //NON-NLS

        // Listen for ingest events so that we can detect new added files (e.g. carved)
        IngestManager.getInstance().addIngestModuleEventListener(pcl);
        // Listen for case events so that we can detect when case is closed
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
    }

    private void removeListeners() {
        IngestManager.getInstance().removeIngestModuleEventListener(pcl);
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
    }

    /**
     * Right click action for this node
     *
     * @param context
     *
     * @return
     */
    @Override
    @Messages({"ImageNode.action.runIngestMods.text=Run Ingest Modules",
        "ImageNode.getActions.openFileSearchByAttr.text=Open File Search by Attributes",})
    public Action[] getActions(boolean context) {

        List<Action> actionsList = new ArrayList<>();
        for (Action a : super.getActions(true)) {
            actionsList.add(a);
        }
        actionsList.addAll(ExplorerNodeActionVisitor.getActions(content));
        actionsList.add(new FileSearchAction(
                Bundle.ImageNode_getActions_openFileSearchByAttr_text()));
        actionsList.add(new RunIngestModulesAction(Collections.<Content>singletonList(content)));
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "ImageNode.getActions.viewInNewWin.text"), this));
        return actionsList.toArray(new Action[0]);
    }

    @Override
    @Messages({"ImageNode.createSheet.size.name=Size (Bytes)",
        "ImageNode.createSheet.size.displayName=Size (Bytes)",
        "ImageNode.createSheet.size.desc=Size of the data source in bytes.",
        "ImageNode.createSheet.type.name=Type",
        "ImageNode.createSheet.type.displayName=Type",
        "ImageNode.createSheet.type.desc=Type of the image.",
        "ImageNode.createSheet.type.text=Image",
        "ImageNode.createSheet.sectorSize.name=Sector Size (Bytes)",
        "ImageNode.createSheet.sectorSize.displayName=Sector Size (Bytes)",
        "ImageNode.createSheet.sectorSize.desc=Sector size of the image in bytes.",
        "ImageNode.createSheet.md5.name=MD5 Hash",
        "ImageNode.createSheet.md5.displayName=MD5 Hash",
        "ImageNode.createSheet.md5.desc=MD5 Hash of the image",
        "ImageNode.createSheet.timezone.name=Timezone",
        "ImageNode.createSheet.timezone.displayName=Timezone",
        "ImageNode.createSheet.timezone.desc=Timezone of the image",
        "ImageNode.createSheet.deviceId.name=Device ID",
        "ImageNode.createSheet.deviceId.displayName=Device ID",
        "ImageNode.createSheet.deviceId.desc=Device ID of the image"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ImageNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "ImageNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "ImageNode.createSheet.name.desc"),
                getDisplayName()));

        ss.put(new NodeProperty<>(Bundle.ImageNode_createSheet_type_name(),
                Bundle.ImageNode_createSheet_type_displayName(),
                Bundle.ImageNode_createSheet_type_desc(),
                Bundle.ImageNode_createSheet_type_text()));

        ss.put(new NodeProperty<>(Bundle.ImageNode_createSheet_size_name(),
                Bundle.ImageNode_createSheet_size_displayName(),
                Bundle.ImageNode_createSheet_size_desc(),
                this.content.getSize()));
        ss.put(new NodeProperty<>(Bundle.ImageNode_createSheet_sectorSize_name(),
                Bundle.ImageNode_createSheet_sectorSize_displayName(),
                Bundle.ImageNode_createSheet_sectorSize_desc(),
                this.content.getSsize()));

        ss.put(new NodeProperty<>(Bundle.ImageNode_createSheet_md5_name(),
                Bundle.ImageNode_createSheet_md5_displayName(),
                Bundle.ImageNode_createSheet_md5_desc(),
                this.content.getMd5()));

        ss.put(new NodeProperty<>(Bundle.ImageNode_createSheet_timezone_name(),
                Bundle.ImageNode_createSheet_timezone_displayName(),
                Bundle.ImageNode_createSheet_timezone_desc(),
                this.content.getTimeZone()));

        try (CaseDbQuery query = Case.getOpenCase().getSleuthkitCase().executeQuery("SELECT device_id FROM data_source_info WHERE obj_id = " + this.content.getId());) {
            ResultSet deviceIdSet = query.getResultSet();
            if (deviceIdSet.next()) {
                ss.put(new NodeProperty<>(Bundle.ImageNode_createSheet_deviceId_name(),
                        Bundle.ImageNode_createSheet_deviceId_displayName(),
                        Bundle.ImageNode_createSheet_deviceId_desc(),
                        deviceIdSet.getString("device_id")));
            }
        } catch (SQLException | TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get device id for the following image: " + this.content.getId(), ex);
        }

        return s;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    /*
     * This property change listener refreshes the tree when a new file is
     * carved out of this image (i.e, the image is being treated as raw bytes
     * and was ingested by the RawDSProcessor).
     */
    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        String eventType = evt.getPropertyName();

        // See if the new file is a child of ours
        if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
            if ((evt.getOldValue() instanceof ModuleContentEvent) == false) {
                return;
            }
            ModuleContentEvent moduleContentEvent = (ModuleContentEvent) evt.getOldValue();
            if ((moduleContentEvent.getSource() instanceof Content) == false) {
                return;
            }
            Content newContent = (Content) moduleContentEvent.getSource();

            try {
                Content parent = newContent.getParent();
                if (parent != null) {
                    // Is this a new carved file?
                    if (parent.getName().equals(VirtualDirectory.NAME_CARVED)) {
                        // Was this new carved file produced from this image?
                        if (parent.getParent().getId() == getContent().getId()) {
                            Children children = getChildren();
                            if (children != null) {
                                ((ContentChildren) children).refreshChildren();
                                children.getNodesCount();
                            }
                        }
                    }
                }
            } catch (TskCoreException ex) {
                // Do nothing.
            }
        } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
            if (evt.getNewValue() == null) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                removeListeners();
            }
        }
    };

}
