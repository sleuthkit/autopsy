/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory.NoSuchEventBusException;
import org.sleuthkit.autopsy.directorytree.ExplorerNodeActionVisitor;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.autopsy.directorytree.FileSystemDetailsAction;
import org.sleuthkit.datamodel.Tag;

/**
 * This class is used to represent the "Node" for the volume. Its child is the
 * root directory of a file system
 */
public class VolumeNode extends AbstractContentNode<Volume> {

    private static final Logger logger = Logger.getLogger(VolumeNode.class.getName());
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.CONTENT_CHANGED);

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     *
     * @param vol Volume to get the name of
     *
     * @return short name for the Volume
     */
    static String nameForVolume(Volume vol) {
        return "vol" + Long.toString(vol.getAddr()); //NON-NLS
    }

    /**
     *
     * @param vol underlying Content instance
     */
    public VolumeNode(Volume vol) {
        super(vol);

        // set name, display name, and icon
        String volName = nameForVolume(vol);
        long end = vol.getStart() + (vol.getLength() - 1);
        String tempVolName = volName + " (" + vol.getDescription() + ": " + vol.getStart() + "-" + end + ")";

        // If this is a pool volume use a custom display name
        try {
            if (vol.getParent() != null
                    && vol.getParent().getParent() instanceof Pool) {
                // Pool volumes are not contiguous so printing a range of blocks is inaccurate
                tempVolName = volName + " (" + vol.getDescription() + ": " + vol.getStart() + ")";
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error looking up parent(s) of volume with obj ID = " + vol.getId(), ex);
        }
        this.setDisplayName(tempVolName);

        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/vol-icon.png"); //NON-NLS
        // Listen for ingest events so that we can detect new added files (e.g. carved)
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, pcl);
        // Listen for case events so that we can detect when case is closed
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
    }

    private void removeListeners() {
        IngestManager.getInstance().removeIngestModuleEventListener(pcl);
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
    }

    /*
     * This property change listener refreshes the tree when a new file is
     * carved out of the unallocated space of this volume.
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
                        // Is this new carved file for this data source?
                        if (newContent.getDataSource().getId() == getContent().getDataSource().getId()) {
                            // Find the volume (if any) associated with the new content and
                            // trigger a refresh if it matches the volume wrapped by this node.
                            while ((parent = parent.getParent()) != null) {
                                if (parent.getId() == getContent().getId()) {
                                    BaseChildFactory.post(getName(), new BaseChildFactory.RefreshKeysEvent());
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (TskCoreException ex) {
                // Do nothing.
            } catch (NoSuchEventBusException ex) {
                logger.log(Level.WARNING, eventType, ex);
            }
        } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
            if (evt.getNewValue() == null) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                removeListeners();
            }
        }
    };

    /**
     * Right click action for volume node
     *
     * @param popup
     *
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new FileSystemDetailsAction(content));
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "VolumeNode.getActions.viewInNewWin.text"), this));
        actionsList.addAll(ExplorerNodeActionVisitor.getActions(content));
        actionsList.add(null);
        actionsList.addAll(Arrays.asList(super.getActions(true)));

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.name.desc"),
                this.getDisplayName()));
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.id.name"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.id.displayName"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.id.desc"),
                content.getAddr()));
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.startSector.name"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.startSector.displayName"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.startSector.desc"),
                content.getStart()));
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.lenSectors.name"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.lenSectors.displayName"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.lenSectors.desc"),
                content.getLength()));
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.description.name"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.description.displayName"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.description.desc"),
                content.getDescription()));
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.flags.name"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.flags.displayName"),
                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.flags.desc"),
                content.getFlagsAsString()));

        return sheet;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return DisplayableItemNode.FILE_PARENT_NODE_KEY;
    }

    /**
     * Reads and returns a list of all tags associated with this content node.
     *
     * Null implementation of an abstract method.
     *
     * @return list of tags associated with the node.
     */
    @Override
    protected List<Tag> getAllTagsFromDatabase() {
        return new ArrayList<>();
    }

}
