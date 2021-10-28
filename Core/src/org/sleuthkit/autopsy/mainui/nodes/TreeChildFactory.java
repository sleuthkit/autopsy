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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;

/**
 * Factory for populating tree with results.
 */
public abstract class TreeChildFactory<T> extends ChildFactory.Detachable<TreeItemDTO<? extends T>> {

    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());

    private final Map<TreeItemDTO<? extends T>, TreeNode<T>> typeNodeMap = new HashMap<>();
    private TreeResultsDTO<? extends T> curResults = null;

    @Override
    protected boolean createKeys(List<TreeItemDTO<? extends T>> toPopulate) {
        if (curResults == null) {
            try {
                curResults = getChildResults();
            } catch (IllegalArgumentException | ExecutionException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching keys", ex);
                return false;
            }
        }

        Set<TreeItemDTO<? extends T>> resultRows = new HashSet<>(curResults.getItems());

        // remove no longer present
        Set<TreeItemDTO<? extends T>> toBeRemoved = new HashSet<>(typeNodeMap.keySet());
        toBeRemoved.removeAll(resultRows);
        for (TreeItemDTO<? extends T> presentId : toBeRemoved) {
            typeNodeMap.remove(presentId);
        }

        List<TreeItemDTO<? extends T>> rowsToReturn = new ArrayList<>();
        for (TreeItemDTO<? extends T> dto : curResults.getItems()) {
            // update cached that remain
            TreeNode<T> currentlyCached = typeNodeMap.get(dto.getId());
            if (currentlyCached != null) {
                currentlyCached.update(dto);
            } else {
                // add new items
                typeNodeMap.put(dto, createNewNode(dto));
            }

            rowsToReturn.add(dto);
        }

        toPopulate.addAll(rowsToReturn);
        return true;
    }

    @Override
    protected void removeNotify() {
        curResults = null;
        typeNodeMap.clear();
        super.removeNotify();
    }

    @Override
    protected void addNotify() {
        super.addNotify();
    }

    @Override
    protected Node createNodeForKey(TreeItemDTO<? extends T> key) {
        return typeNodeMap.get(key);
    }

    /**
     * Fetches child view from the database and updates the tree.
     */
    public void update() {
        try {
            this.curResults = getChildResults();
        } catch (IllegalArgumentException | ExecutionException ex) {
            logger.log(Level.WARNING, "An error occurred while fetching keys", ex);
            return;
        }
        this.refresh(false);
    }

    /**
     * Creates a TreeNode given the tree item data.
     * @param rowData The tree item data.
     * @return The generated tree node.
     */
    protected abstract TreeNode createNewNode(TreeItemDTO<? extends T> rowData);

    /**
     * Fetches data from the database to populate this part of the tree.
     * @return The data.
     * @throws IllegalArgumentException
     * @throws ExecutionException 
     */
    protected abstract TreeResultsDTO<? extends T> getChildResults() throws IllegalArgumentException, ExecutionException;
}
