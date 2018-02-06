/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

final class mxGraphImpl extends mxGraph {

    private static final Logger logger = Logger.getLogger(mxGraphImpl.class.getName());
    private static final URL MARKER_PIN_URL = VisualizationPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/marker--pin.png");

    static final private mxStylesheet mxStylesheet = new mxStylesheet();
    private final HashSet<AccountDeviceInstanceKey> pinnedAccountDevices = new HashSet<>();
    private final HashSet<AccountDeviceInstanceKey> lockedAccountDevices = new HashSet<>();

    private final Map<String, mxCell> nodeMap = new HashMap<>();
    private final Multimap<Content, mxCell> edgeMap = MultimapBuilder.hashKeys().hashSetValues().build();

    static {
        //initialize defaul cell (Vertex and/or Edge)  properties
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_PERIMETER, mxConstants.PERIMETER_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_FONTCOLOR, "000000");
//        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_WHITE_SPACE, "wrap");

        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, true);
//        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_OPACITY, 50        );
//        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_ROUNDED, true);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_PERIMETER_SPACING, 0);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_STARTARROW, mxConstants.NONE);
    }

    public mxGraphImpl() {
        super(mxStylesheet);
        setAutoSizeCells(true);
        setCellsCloneable(false);
        setDropEnabled(false);
        setCellsCloneable(false);
        setCellsEditable(false);
        setCellsResizable(false);
        setCellsMovable(true);
        setCellsDisconnectable(false);
        setConnectableEdges(false);
        setDisconnectOnMove(false);
        setEdgeLabelsMovable(false);
        setVertexLabelsMovable(false);
        setAllowDanglingEdges(false);
        setCellsBendable(true);
        setKeepEdgesInBackground(true);
        setResetEdgesOnMove(true);
        setHtmlLabels(true);
    }

    @Override
    public boolean isCellMovable(Object cell) {
        final mxICell mxCell = (mxICell) cell;
        if (mxCell.isEdge()) {
            return super.isCellMovable(cell);
        } else {
            return super.isCellMovable(cell)
                    && false == lockedAccountDevices.contains((AccountDeviceInstanceKey) mxCell.getValue());
        }
    }

    void clear() {
        nodeMap.clear();
        edgeMap.clear();
        removeCells(getChildVertices(getDefaultParent()));
    }

    boolean isAccountPinned(AccountDeviceInstanceKey account) {
        return pinnedAccountDevices.contains(account);
    }

    @Override
    public String convertValueToString(Object cell) {
        Object value = getModel().getValue(cell);
        if (value instanceof AccountDeviceInstanceKey) {
            final AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) value;
            final String accountName = adiKey.getAccountDeviceInstance().getAccount().getTypeSpecificID();
            String iconFileName = Utils.getIconFileName(adiKey.getAccountDeviceInstance().getAccount().getAccountType());
            String label = "<img src=\""
                    + VisualizationPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/" + iconFileName)
                    + "\">" + accountName;
            if (pinnedAccountDevices.contains(adiKey)) {
                label += "<img src=\"" + MARKER_PIN_URL + "\">";
            }
            return "<span>" + label + "</span>";
        } else {
            return "";
        }
    }

    @Override
    public String getToolTipForCell(Object cell) {
        return ((mxICell) cell).getId();
    }

    void unpinAccount(ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances) {
        pinnedAccountDevices.removeAll(accountDeviceInstances);
    }

    void pinAccount(ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances) {
        pinnedAccountDevices.addAll(accountDeviceInstances);
    }

    void lockAccount(AccountDeviceInstanceKey accountDeviceInstance) {
        lockedAccountDevices.add(accountDeviceInstance);
    }

    void unlockAccount(AccountDeviceInstanceKey accountDeviceInstance) {
        lockedAccountDevices.remove(accountDeviceInstance);
    }

    SwingWorker<?, ?> rebuild(ProgressIndicator progress, CommunicationsManager commsManager, CommunicationsFilter currentFilter) {

        return new SwingWorkerImpl(progress, commsManager, currentFilter);
    }

    void resetGraph() {
        clear();
        pinnedAccountDevices.clear();
        lockedAccountDevices.clear();
    }

    private mxCell getOrCreateVertex(AccountDeviceInstanceKey accountDeviceInstanceKey) {
        final AccountDeviceInstance accountDeviceInstance = accountDeviceInstanceKey.getAccountDeviceInstance();
        final String name =// accountDeviceInstance.getDeviceId() + ":"                +
                accountDeviceInstance.getAccount().getTypeSpecificID();

        final mxCell vertex = nodeMap.computeIfAbsent(name, vertexName -> {
            double size = Math.sqrt(accountDeviceInstanceKey.getMessageCount()) + 10;

            mxCell newVertex = (mxCell) insertVertex(
                    getDefaultParent(),
                    vertexName, accountDeviceInstanceKey,
                    Math.random() * getView().getGraphBounds().getWidth(),
                    Math.random() * getView().getGraphBounds().getHeight(),
                    size,
                    size);
            return newVertex;
        });
        final mxCellState state = getView().getState(vertex, true);

        getView().updateLabel(state);
        getView().updateLabelBounds(state);
        getView().updateBoundingBox(state);

        return vertex;
    }

    @SuppressWarnings("unchecked")
    private mxCell addEdge(Collection<Content> relSources, AccountDeviceInstanceKey account1, AccountDeviceInstanceKey account2) {
        mxCell vertex1 = getOrCreateVertex(account1);
        mxCell vertex2 = getOrCreateVertex(account2);
        Object[] edgesBetween = getEdgesBetween(vertex1, vertex2);
        mxCell edge;
        if (edgesBetween.length == 0) {
            final String edgeName = vertex1.getId() + " <-> " + vertex2.getId();
            final HashSet<Content> hashSet = new HashSet<>(relSources);
            //            edgeMap.put(relSource, edge);
            edge = (mxCell) insertEdge(getDefaultParent(), edgeName, hashSet, vertex1, vertex2,
                    "strokeWidth=" + Math.sqrt(hashSet.size()));
        } else {
            edge = (mxCell) edgesBetween[0];
            ((Collection<Content>) edge.getValue()).addAll(relSources);
            edge.setStyle("strokeWidth=" + Math.sqrt(((Collection) edge.getValue()).size()));
        }
        return edge;
    }

    boolean hasPinnedAccounts() {
        return pinnedAccountDevices.isEmpty() == false;  }

    class SwingWorkerImpl extends SwingWorker<Void, Void> {

        private final ProgressIndicator progress;
        private final CommunicationsManager commsManager;
        private final CommunicationsFilter currentFilter;

        SwingWorkerImpl(ProgressIndicator progress, CommunicationsManager commsManager, CommunicationsFilter currentFilter) {
            this.progress = progress;
            this.currentFilter = currentFilter;
            this.commsManager = commsManager;
        }

        @Override
        protected Void doInBackground() throws Exception {
            progress.start("Loading accounts", pinnedAccountDevices.size());
            int i = 0;
            try {
                /**
                 * set to keep track of accounts related to pinned accounts
                 */
                Set<AccountDeviceInstanceKey> relatedAccounts = new HashSet<>();
                for (AccountDeviceInstanceKey adiKey : pinnedAccountDevices) {
                    List<AccountDeviceInstance> relatedAccountDeviceInstances =
                            commsManager.getRelatedAccountDeviceInstances(adiKey.getAccountDeviceInstance(), currentFilter);
                    relatedAccounts.add(adiKey);
                    //get accounts related to pinned account
                    for (AccountDeviceInstance relatedADI : relatedAccountDeviceInstances) {
//                            handle.progress(1);
                        long adiRelationshipsCount = commsManager.getRelationshipSourcesCount(relatedADI, currentFilter);
                        final AccountDeviceInstanceKey relatedADIKey = new AccountDeviceInstanceKey(relatedADI, currentFilter, adiRelationshipsCount);
                        relatedAccounts.add(relatedADIKey); //store related accounts
                    }
                    progress.progress(++i);
                }

                //for each pair of related accounts add edges if they are related o each other.
                // this is O(n^2) in the number of related accounts!!!
                List<AccountDeviceInstanceKey> relatedAccountsList = new ArrayList<>(relatedAccounts);
                progress.switchToDeterminate("", 0, relatedAccountsList.size());
                for (i = 0; i < relatedAccountsList.size(); i++) {
                    AccountDeviceInstanceKey adiKey1 = relatedAccountsList.get(i);
                    for (int j = i; j < relatedAccountsList.size(); j++) {

                        AccountDeviceInstanceKey adiKey2 = relatedAccountsList.get(j);
                        List<Content> relationships = commsManager.getRelationshipSources(
                                adiKey1.getAccountDeviceInstance(),
                                adiKey2.getAccountDeviceInstance(),
                                currentFilter);
                        if (relationships.size() > 0) {
                            mxCell addEdge = addEdge(relationships, adiKey1, adiKey2);
                            progress.progress(addEdge.getId());
                        }
                    }
                    progress.progress(i);
                }
            } catch (TskCoreException tskCoreException) {
                logger.log(Level.SEVERE, "Error", tskCoreException);
            } finally {
            }
            return null;
        }

        @Override
        protected void done() {
            super.done();
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                progress.finish();
            }
        }
    }
}
