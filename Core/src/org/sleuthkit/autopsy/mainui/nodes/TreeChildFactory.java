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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler.Refresher;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;

/**
 * Factory for populating tree with results.
 */
public abstract class TreeChildFactory<T> extends ChildFactory.Detachable<Long> implements Refresher {

    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST
            = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);

    private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);

    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        String eventType = evt.getPropertyName();
        if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
            // case was closed. Remove listeners so that we don't get called with a stale case handle
            if (evt.getNewValue() == null) {
                removeNotify();
            }
        } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
            /**
             * This is a stop gap measure until a different way of handling the
             * closing of cases is worked out. Currently, remote events may be
             * received for a case that is already closed.
             */
            try {
                Case.getCurrentCaseThrows();
                refresh(false);
            } catch (NoCurrentCaseException notUsed) {
                /**
                 * Case is closed, do nothing.
                 */
            }
        }
    };

    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

    private final Map<Long, TreeNode<T>> typeNodeMap = new MapMaker().weakValues().makeMap();
    private TreeResultsDTO<? extends T> curResults = null;
    private Map<Long, TreeItemDTO<? extends T>> idMapping = new HashMap<>();

    @Override
    protected boolean createKeys(List<Long> toPopulate) {
        if (curResults == null) {
            try {
                updateData();
            } catch (IllegalArgumentException | ExecutionException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching keys", ex);
                return false;
            }
        }

        // update existing cached nodes
        List<Long> curResultIds = new ArrayList<>();
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
    protected Node createNodeForKey(Long treeItemId) {
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

    @Override
    public void refresh() {
        update();
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
        refreshThrottler.registerForIngestModuleEvents();
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
    }

    /**
     * Unregister listeners for autopsy events.
     */
    private void unregisterListeners() {
        refreshThrottler.unregisterEventListener();
        IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
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
}
