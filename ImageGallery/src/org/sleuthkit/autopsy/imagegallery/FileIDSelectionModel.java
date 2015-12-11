/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Exceptions;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Singleton that manages set of fileIds, as well as last selected fileID.
 *
 * NOTE: When we had synchronization on selected and lastSelectedProp we got
 * deadlocks with the tiles during selection
 *
 * TODO: should this be singleton? selections are only within a single group
 * now... -jm
 */
public class FileIDSelectionModel {

    private static final Logger LOGGER = Logger.getLogger(FileIDSelectionModel.class.getName());

    private static FileIDSelectionModel instance;

    private final ObservableSet<Long> selected = FXCollections.observableSet();

    private final ReadOnlyObjectWrapper<Long> lastSelectedProp = new ReadOnlyObjectWrapper<>();

    public static synchronized FileIDSelectionModel getInstance() {
        if (instance == null) {
            instance = new FileIDSelectionModel();
        }
        return instance;
    }

    public FileIDSelectionModel() {
        super();
        selected.addListener((Observable observable) -> {

            Set<Long> fileIDs = ImmutableSet.copyOf(selected);
            SwingUtilities.invokeLater(() -> {

                ArrayList<FileNode> fileNodes = new ArrayList<>();
                for (Long id : fileIDs) {
                    try {
                        fileNodes.add(new FileNode(ImageGalleryController.getDefault().getSleuthKitCase().getAbstractFileById(id)));
                    } catch (TskCoreException ex) {
                    }
                }
                FileNode[] fileNodeArray = fileNodes.stream().toArray(FileNode[]::new);

                Children.Array children = new Children.Array();
                children.add(fileNodeArray);

                ImageGalleryTopComponent etc = (ImageGalleryTopComponent) WindowManager.getDefault().findTopComponent(ImageGalleryTopComponent.PREFERRED_ID);

                etc.getExplorerManager().setRootContext(new AbstractNode(children));
                try {
                    etc.getExplorerManager().setSelectedNodes(fileNodeArray);
                } catch (PropertyVetoException ex) {
                    Exceptions.printStackTrace(ex);
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

    public ObservableSet<Long> getSelected() {
        return selected;
    }

    public void clearAndSelectAll(ObservableList<Long> ids) {
        clearAndSelectAll(ids.toArray(new Long[ids.size()]));
    }
}
