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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
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
public abstract class TreeChildFactory<T> extends ChildFactory.Detachable<Object> implements Comparator<T> {

    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());

    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        if (evt.getNewValue() instanceof DAOEvent) {
            DAOAggregateEvent aggEvt = (DAOAggregateEvent) evt.getNewValue();
            for (DAOEvent daoEvt : aggEvt.getEvents()) {
                if (daoEvt instanceof TreeEvent) {
                    TreeEvent treeEvt = (TreeEvent) daoEvt;
                    TreeItemDTO<? extends T> item = getInvalidatedChild(treeEvt);
                    if (item != null) {
                        if (treeEvt.isRefreshRequired()) {
                            update();
                            break;
                        } else {
                            updateNodeData(item);
                        }
                    }
                }
            }
        }
    };

    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, MainDAO.getInstance().getTreeEventsManager());

    private final Map<Object, TreeNode<T>> typeNodeMap = new MapMaker().weakValues().makeMap();
    private final Object resultsUpdateLock = new Object();

    private TreeResultsDTO<? extends T> curResults = null;
    private List<TreeItemDTO<? extends T>> curItemsList = new ArrayList<>();
    private Map<Object, TreeItemDTO<? extends T>> idMapping = new HashMap<>();

    @Override
    protected boolean createKeys(List<Object> toPopulate) {
        List<TreeItemDTO<? extends T>> itemsList;
        synchronized (resultsUpdateLock) {
            if (curResults == null) {
                try {
                    updateData();
                } catch (IllegalArgumentException | ExecutionException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching keys", ex);
                    return false;
                }
            }
            itemsList = curItemsList;
        }

        // update existing cached nodes
        List<Object> curResultIds = new ArrayList<>();
        for (TreeItemDTO<? extends T> dto : itemsList) {
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
     * Updates an individual item in the display list.
     *
     * @param item The added item.
     */
    protected void updateNodeData(TreeItemDTO<? extends T> item) {
        TreeNode<T> cachedTreeNode = this.typeNodeMap.get(item.getId());
        if (cachedTreeNode == null) {
            synchronized (resultsUpdateLock) {
                // add to id mapping
                this.idMapping.put(item.getId(), item);

                // insert in sorted position
                int insertIndex = 0;
                for (; insertIndex < this.curItemsList.size(); insertIndex++) {
                    if (this.compare(item.getTypeData(), this.curItemsList.get(insertIndex).getTypeData()) < 0) {
                        break;
                    }
                }
                this.curItemsList.add(insertIndex, item);
            }
            this.refresh(false);
        } else {
            cachedTreeNode.update(item);
        }
    }

    /**
     * Updates local data by fetching data from the DAO's.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    protected void updateData() throws IllegalArgumentException, ExecutionException {
        synchronized (resultsUpdateLock) {
            this.curResults = getChildResults();
            Map<Object, TreeItemDTO<? extends T>> idMapping = new HashMap<>();
            List<TreeItemDTO<? extends T>> curItemsList = new ArrayList<>();
            for (TreeItemDTO<? extends T> item : this.curResults.getItems()) {
                idMapping.put(item.getId(), item);
                curItemsList.add(item);
            }

            this.idMapping = idMapping;
            this.curItemsList = curItemsList;
        }
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
        typeNodeMap.clear();

        synchronized (resultsUpdateLock) {
            curResults = null;
            this.curItemsList.clear();
            idMapping.clear();
        }
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
        unregisterListeners();
        disposeResources();
        super.removeNotify();
    }

    @Override
    protected void finalize() throws Throwable {
        unregisterListeners();
        disposeResources();
        super.finalize();
    }

    @Override
    protected void addNotify() {
        registerListeners();
        super.addNotify();
    }
    
    public static <T> TreeItemDTO<T> getUpdatedTreeData(TreeItemDTO<T> original, T updatedData) {
        return new TreeItemDTO<>(
                original.getTypeId(),
                updatedData,
                original.getId(),
                original.getDisplayName(),
                original.getDisplayCount());
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

    protected abstract TreeItemDTO<? extends T> getInvalidatedChild(TreeEvent daoEvt);
}
