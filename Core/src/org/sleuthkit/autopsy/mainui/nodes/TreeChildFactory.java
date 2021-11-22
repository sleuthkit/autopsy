/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import com.google.common.collect.MapMaker;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;

/**
 * Factory for populating tree with results.
 */
public abstract class TreeChildFactory<T> extends ChildFactory.Detachable<Object> {

    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());

    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        if (evt.getNewValue() instanceof DAOEvent) {
            DAOAggregateEvent aggEvt = (DAOAggregateEvent) evt.getNewValue();
            for (DAOEvent daoEvt : aggEvt.getEvents()) {
                if (daoEvt instanceof TreeEvent) {
                    TreeEvent treeEvt = (TreeEvent) daoEvt;
                    if (isChildInvalidating(treeEvt.getDaoEvent())) {
                        try {
                            if (treeEvt.isDeterminate()) {
                                updateData();   
                            } else {
                                showIndeterminate(treeEvt);
                            }
                        } catch (ExecutionException ex) {
                            logger.log(Level.WARNING, "An error occurred while updating the data for this factory of type: " + this.getClass().getName(), ex);
                        }
                        break;
                    }
                }
            }
        }
    };

    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, MainDAO.getInstance().getTreeEventsManager());

    private final Map<Object, TreeNode<T>> typeNodeMap = new MapMaker().weakValues().makeMap();
    private TreeResultsDTO<? extends T> curResults = null;
    private Map<Object, TreeItemDTO<? extends T>> idMapping = new HashMap<>();

    @Override
    protected boolean createKeys(List<Object> toPopulate) {
        if (curResults == null) {
            try {
                updateData();
            } catch (IllegalArgumentException | ExecutionException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching keys", ex);
                return false;
            }
        }

        // update existing cached nodes
        List<Object> curResultIds = new ArrayList<>();
        for (TreeItemDTO<? extends T> dto : curResults.getItems()) {
            TreeNode<T> currentlyCached = typeNodeMap.get(dto.getId());
            if (currentlyCached != null) {
                currentlyCached.update(dto);
            }
            curResultIds.add(dto.getId());
        }

        toPopulate.addAll(curResultIds);
        return true;
    }

    @Override
    protected Node createNodeForKey(Object treeItemId) {
        return typeNodeMap.computeIfAbsent(treeItemId, (id) -> {
            TreeItemDTO<? extends T> itemData = idMapping.get(id);
            // create new node if data for node exists.  otherwise, return null.
            return itemData == null
                    ? null
                    : createNewNode(itemData);
        });
    }

    /**
     * Updates local data by fetching data from the DAO's.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    protected void updateData() throws IllegalArgumentException, ExecutionException {
        this.curResults = getChildResults();
        this.idMapping = curResults.getItems().stream()
                .collect(Collectors.toMap(item -> item.getId(), item -> item, (item1, item2) -> item1));

    }

    /**
     * Fetches child view from the database and updates the tree.
     */
    public void update() {
        try {
            updateData();
        } catch (IllegalArgumentException | ExecutionException ex) {
            logger.log(Level.WARNING, "An error occurred while fetching keys", ex);
            return;
        }
        this.refresh(false);
    }

    /**
     * Dispose resources associated with this factory.
     */
    private void disposeResources() {
        curResults = null;
        typeNodeMap.clear();
        idMapping.clear();
    }

    /**
     * Register listeners for autopsy events.
     */
    private void registerListeners() {
        MainDAO.getInstance().getTreeEventsManager().addPropertyChangeListener(weakPcl);
    }

    /**
     * Unregister listeners for autopsy events.
     */
    private void unregisterListeners() {
        // GVDTODO this may not be necessary due to the weak listener's ability to unregister itself
        MainDAO.getInstance().getTreeEventsManager().removePropertyChangeListener(weakPcl);
    }

    @Override
    protected void removeNotify() {
        disposeResources();
        unregisterListeners();
        super.removeNotify();
    }

    @Override
    protected void finalize() throws Throwable {
        disposeResources();
        unregisterListeners();
        super.finalize();
    }

    @Override
    protected void addNotify() {
        registerListeners();
        super.addNotify();
    }

    /**
     * Creates a TreeNode given the tree item data.
     *
     * @param rowData The tree item data.
     *
     * @return The generated tree node.
     */
    protected abstract TreeNode<T> createNewNode(TreeItemDTO<? extends T> rowData);

    /**
     * Fetches data from the database to populate this part of the tree.
     *
     * @return The data.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    protected abstract TreeResultsDTO<? extends T> getChildResults() throws IllegalArgumentException, ExecutionException;

    protected abstract boolean isChildInvalidating(DAOEvent daoEvt);
}
