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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

final class CommunicationsGraph extends mxGraph {

    private static final Logger logger = Logger.getLogger(CommunicationsGraph.class.getName());
    private static final URL MARKER_PIN_URL = CommunicationsGraph.class.getResource("/org/sleuthkit/autopsy/communications/images/marker--pin.png");
    private static final URL LOCK_URL = CommunicationsGraph.class.getResource("/org/sleuthkit/autopsy/communications/images/lock_large_locked.png");

    /**
     * mustache.java template
     */
    private final static Mustache labelMustache;

    static {
        InputStream templateStream = CommunicationsGraph.class.getResourceAsStream("/org/sleuthkit/autopsy/communications/Vertex_Label_template.html");
        labelMustache = new DefaultMustacheFactory().compile(new InputStreamReader(templateStream), "Vertex_Label");
    }

    static final private mxStylesheet mxStylesheet = new mxStylesheet();
    private final Set<AccountDeviceInstanceKey> pinnedAccountDevices = new HashSet<>();
    private final Set<mxCell> lockedVertices = new HashSet<>();

    private final Map<String, mxCell> nodeMap = new HashMap<>();
    private final Multimap<Content, mxCell> edgeMap = MultimapBuilder.hashKeys().hashSetValues().build();

    static {
        //initialize defaul vertex properties
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_PERIMETER, mxConstants.PERIMETER_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_FONTCOLOR, "000000");

        //initialize defaul edge properties
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, true);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_PERIMETER_SPACING, 0);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_STARTARROW, mxConstants.NONE);
    }

    CommunicationsGraph() {
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
        final StringWriter stringWriter = new StringWriter();
        HashMap<String, Object> scopes = new HashMap<>();

        Object value = getModel().getValue(cell);
        if (value instanceof AccountDeviceInstanceKey) {
            final AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) value;

            scopes.put("accountName", adiKey.getAccountDeviceInstance().getAccount().getTypeSpecificID());
            scopes.put("size", Math.round(Math.log(adiKey.getMessageCount()) + 5));
            scopes.put("iconFileName", CommunicationsGraph.class.getResource("/org/sleuthkit/autopsy/communications/images/"
                    + Utils.getIconFileName(adiKey.getAccountDeviceInstance().getAccount().getAccountType())));
            scopes.put("pinned", pinnedAccountDevices.contains(adiKey));
            scopes.put("MARKER_PIN_URL", MARKER_PIN_URL);
            scopes.put("locked", lockedVertices.contains((mxCell) cell));
            scopes.put("LOCK_URL", LOCK_URL);

            labelMustache.execute(stringWriter, scopes);

            return stringWriter.toString();
        } else {
            return "";
        }
    }

    @Override
    public String getToolTipForCell(Object cell) {
        final StringWriter stringWriter = new StringWriter();
        HashMap<String, Object> scopes = new HashMap<>();

        Object value = getModel().getValue(cell);
        if (value instanceof AccountDeviceInstanceKey) {
            final AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) value;

            scopes.put("accountName", adiKey.getAccountDeviceInstance().getAccount().getTypeSpecificID());
            scopes.put("size", 12);// Math.round(Math.log(adiKey.getMessageCount()) + 5));
            scopes.put("iconFileName", CommunicationsGraph.class.getResource("/org/sleuthkit/autopsy/communications/images/"
                    + Utils.getIconFileName(adiKey.getAccountDeviceInstance().getAccount().getAccountType())));
            scopes.put("pinned", pinnedAccountDevices.contains(adiKey));
            scopes.put("MARKER_PIN_URL", MARKER_PIN_URL);
            scopes.put("locked", lockedVertices.contains((mxCell) cell));
            scopes.put("LOCK_URL", LOCK_URL);

            labelMustache.execute(stringWriter, scopes);

            return stringWriter.toString();
        } else {
            return ((mxICell) cell).getId();
        }
    }

    /**
     * Unpin the given accounts from the graph. Pinned accounts will always be
     * shown regardless of the filter state. Furthermore, accounts with
     * relationships that pass the filters will also be shown.
     *
     * @param accountDeviceInstances The accounts to unpin.
     */
    void unpinAccount(ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances) {
        pinnedAccountDevices.removeAll(accountDeviceInstances);
    }

    /**
     * Pin the given accounts to the graph. Pinned accounts will always be shown
     * regardless of the filter state. Furthermore, accounts with relationships
     * that pass the filters will also be shown.
     *
     * @param accountDeviceInstances The accounts to pin.
     */
    void pinAccount(ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances) {
        pinnedAccountDevices.addAll(accountDeviceInstances);
    }

    /**
     * Lock the given vertex so that applying a layout algorithm doesn't move
     * it. The user can still manually position the vertex.
     *
     * @param vertex The vertex to lock.
     */
    void lockVertex(mxCell vertex) {
        lockedVertices.add(vertex);
        getView().clear(vertex, true, true);
        getView().validate();
    }

    /**
     * Lock the given vertex so that applying a layout algorithm can move it.
     *
     * @param vertex The vertex to unlock.
     */
    void unlockVertex(mxCell vertex) {
        lockedVertices.remove(vertex);

        final mxCellState state = getView().getState(vertex, true);
        getView().updateLabel(state);
        getView().updateLabelBounds(state);
        getView().updateBoundingBox(state);
    }

    SwingWorker<?, ?> rebuild(ProgressIndicator progress, CommunicationsManager commsManager, CommunicationsFilter currentFilter) {
        return new RebuildWorker(progress, commsManager, currentFilter);
    }

    void resetGraph() {
        clear();
        getView().setScale(1);
        pinnedAccountDevices.clear();
        lockedVertices.clear();
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
                    Math.random() * 400,
                    Math.random() * 400,
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
            edge = (mxCell) insertEdge(getDefaultParent(), edgeName, hashSet, vertex1, vertex2,
                    "strokeWidth=" + (Math.log(hashSet.size()) + 1));
        } else {
            edge = (mxCell) edgesBetween[0];
            ((Collection<Content>) edge.getValue()).addAll(relSources);
            edge.setStyle("strokeWidth=" + (Math.log(((Collection) edge.getValue()).size()) + 1));
        }
        return edge;
    }

    /**
     * Are there any accounts in this graph? If there are no pinned accounts the
     * graph will be empty.
     *
     * @return True if this graph is empty.
     */
    boolean isEmpty() {
        return pinnedAccountDevices.isEmpty();
    }

    boolean isVertexLocked(mxCell vertex) {
        return lockedVertices.contains(vertex);

    }

    /**
     * SwingWorker that loads the accounts and edges for this graph according to
     * the pinned accounts and the current filters.
     */
    private class RebuildWorker extends SwingWorker<Void, Void> {

        private final ProgressIndicator progress;
        private final CommunicationsManager commsManager;
        private final CommunicationsFilter currentFilter;

        RebuildWorker(ProgressIndicator progress, CommunicationsManager commsManager, CommunicationsFilter currentFilter) {
            this.progress = progress;
            this.currentFilter = currentFilter;
            this.commsManager = commsManager;

        }

        @Override
        protected Void doInBackground() throws Exception {
            progress.start("Loading accounts");
//            progress.switchToDeterminate("Loading accounts", 0,pinnedAccountDevices.size());
            int i = 0;
            try {
                /**
                 * set to keep track of accounts related to pinned accounts
                 */
                Set<AccountDeviceInstanceKey> relatedAccounts = new HashSet<>();
                for (AccountDeviceInstanceKey adiKey : pinnedAccountDevices) {
                    if (isCancelled()) {
                        break;
                    }
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
                        if (isCancelled()) {
                            break;
                        }
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
                logger.log(Level.SEVERE, "Error building graph visualization. ", ex);
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Graph visualization cancelled");
            } finally {
                progress.finish();
            }
        }
    }
}
