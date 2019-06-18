/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import com.google.common.collect.ImmutableSet;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages set of selected fileIds, as well as last selected fileID. Since some
 * actions (e.g. {@link ExtractAction} ) invoked through Image Gallery depend on
 * what is available in the Utilities.actionsGlobalContext() lookup, we maintain
 * that in sync with the local ObservableList based selection, via the
 * ImageGalleryTopComponent's ExplorerManager.
 *
 * NOTE: When we had synchronization on selected and lastSelectedProp we got
 * deadlocks with the tiles during selection
 */
public class FileIDSelectionModel {

    private static final Logger LOGGER = Logger.getLogger(FileIDSelectionModel.class.getName());

    private final ObservableSet<Long> selected = FXCollections.observableSet();

    private final ReadOnlyObjectWrapper<Long> lastSelectedProp = new ReadOnlyObjectWrapper<>();

    public FileIDSelectionModel(ImageGalleryController controller) {
        /**
         * Since some actions (e.g. {@link ExtractAction} ) invoked through
         * Image Gallery depend on what is available in the
         * Utilities.actionsGlobalContext() lookup, we maintain that in sync
         * with the local ObservableList based selection, via the
         * ImageGalleryTopComponent's ExplorerManager.
         */
        selected.addListener((Observable observable) -> {
            Set<Long> fileIDs = ImmutableSet.copyOf(selected);
            SwingUtilities.invokeLater(() -> {
                ArrayList<FileNode> fileNodes = new ArrayList<>();
                for (Long id : fileIDs) {
                    try {
                        fileNodes.add(new FileNode(controller.getCaseDatabase().getAbstractFileById(id)));
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to get abstract file by its ID", ex); //NON-NLS
                    }
                }
                FileNode[] fileNodeArray = fileNodes.stream().toArray(FileNode[]::new);
                Children.Array children = new Children.Array();
                children.add(fileNodeArray);

                ImageGalleryTopComponent etc = ImageGalleryTopComponent.getTopComponent();
                etc.getExplorerManager().setRootContext(new AbstractNode(children));
                try {
                    etc.getExplorerManager().setSelectedNodes(fileNodeArray);
                } catch (PropertyVetoException ex) {
                    LOGGER.log(Level.SEVERE, "Explorer manager selection was vetoed.", ex); //NON-NLS
                }
            });
        });
    }

    public void toggleSelection(Long id) {
        boolean contained = selected.contains(id);

        if (contained) {
            selected.remove(id);
            setLastSelected(null);
        } else {
            selected.add(id);
            setLastSelected(id);
        }
    }

    public void clearAndSelectAll(Long... ids) {
        selected.clear();
        selected.addAll(Arrays.asList(ids));
        if (ids.length > 0) {
            setLastSelected(ids[ids.length - 1]);
        } else {
            setLastSelected(null);
        }
    }

    public void clearAndSelect(Long id) {
        selected.clear();
        selected.add(id);
        setLastSelected(id);
    }

    public void select(Long id) {
        selected.add(id);
        setLastSelected(id);
    }

    public void deSelect(Long id) {
        selected.remove(id);
        setLastSelected(null);
    }

    public void clearSelection() {
        selected.clear();
        setLastSelected(null);
    }

    public boolean isSelected(Long id) {
        return selected.contains(id);
    }

    public ReadOnlyObjectProperty<Long> lastSelectedProperty() {
        return lastSelectedProp.getReadOnlyProperty();
    }

    private void setLastSelected(Long id) {
        lastSelectedProp.set(id);
    }

    /**
     * expose the list of selected ids so that clients can listen for changes
     */
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ObservableSet<Long> getSelected() {
        return selected;
    }

    public void clearAndSelectAll(ObservableList<Long> ids) {
        clearAndSelectAll(ids.toArray(new Long[ids.size()]));
    }
}
