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
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
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
import org.sleuthkit.datamodel.AccountPair;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Implementation of mxGraph customized for our use in the CVT visualize mode.
 * Acts as the primary entry point into the JGraphX API.
 */
final class CommunicationsGraph extends mxGraph {

    private static final Logger logger = Logger.getLogger(CommunicationsGraph.class.getName());
    private static final URL MARKER_PIN_URL = CommunicationsGraph.class.getResource("/org/sleuthkit/autopsy/communications/images/marker--pin.png");
    private static final URL LOCK_URL = CommunicationsGraph.class.getResource("/org/sleuthkit/autopsy/communications/images/lock_large_locked.png");

    /* mustache.java template */
    private final static Mustache labelMustache;

    static {
        final InputStream templateStream = CommunicationsGraph.class.getResourceAsStream("/org/sleuthkit/autopsy/communications/Vertex_Label_template.html");
        labelMustache = new DefaultMustacheFactory().compile(new InputStreamReader(templateStream), "Vertex_Label");
    }

    /* Style sheet for default vertex and edge styles. These are initialized in
     * the static block below. */
    static final private mxStylesheet mxStylesheet = new mxStylesheet();

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

    /** Map from type specific account identifier to mxCell(vertex). */
    private final Map<String, mxCell> nodeMap = new HashMap<>();

    /** Map from relationship source (Content) to mxCell (edge). */
    private final Multimap<Content, mxCell> edgeMap = MultimapBuilder.hashKeys().hashSetValues().build();
    private final LockedVertexModel lockedVertexModel;

    private final PinnedAccountModel pinnedAccountModel;

    CommunicationsGraph(PinnedAccountModel pinnedAccountModel, LockedVertexModel lockedVertexModel) {
        super(mxStylesheet);
        this.pinnedAccountModel = pinnedAccountModel;
        this.lockedVertexModel = lockedVertexModel;
        //set fixed properties of graph.
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

    /**
     * Get the LockedVertexModel
     *
     * @return the LockedVertexModel
     */
    LockedVertexModel getLockedVertexModel() {
        return lockedVertexModel;
    }

    PinnedAccountModel getPinnedAccountModel() {
        return pinnedAccountModel;
    }

    void clear() {
        nodeMap.clear();
        edgeMap.clear();
        removeCells(getChildVertices(getDefaultParent()));
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
            scopes.put("iconFileName", CommunicationsGraph.class.getResource(Utils.getIconFilePath(adiKey.getAccountDeviceInstance().getAccount().getAccountType())));
            scopes.put("pinned", pinnedAccountModel.isAccountPinned(adiKey));
            scopes.put("MARKER_PIN_URL", MARKER_PIN_URL);
            scopes.put("locked", lockedVertexModel.isVertexLocked((mxCell) cell));
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
            scopes.put("relationships", 12);// Math.round(Math.log(adiKey.getMessageCount()) + 5));
            scopes.put("iconFileName", CommunicationsGraph.class.getResource(Utils.getIconFilePath(adiKey.getAccountDeviceInstance().getAccount().getAccountType())));
            scopes.put("pinned", pinnedAccountModel.isAccountPinned(adiKey));
            scopes.put("MARKER_PIN_URL", MARKER_PIN_URL);
            scopes.put("locked", lockedVertexModel.isVertexLocked((mxCell) cell));
            scopes.put("LOCK_URL", LOCK_URL);
            scopes.put("device_id", adiKey.getAccountDeviceInstance().getDeviceId());

            labelMustache.execute(stringWriter, scopes);

            return stringWriter.toString();
        } else {
            final mxICell edge = (mxICell) cell;
            final long count = (long) edge.getValue();
            return "<html>" + edge.getId() + "<br>" + count + (count == 1 ? " relationship" : " relationships") + "</html>";
        }
    }

    SwingWorker<?, ?> rebuild(ProgressIndicator progress, CommunicationsManager commsManager, CommunicationsFilter currentFilter) {
        return new RebuildWorker(progress, commsManager, currentFilter);
    }

    void resetGraph() {
        clear();
        getView().setScale(1);
        pinnedAccountModel.clear();
        lockedVertexModel.clear();
    }

    private mxCell getOrCreateVertex(AccountDeviceInstanceKey accountDeviceInstanceKey) {
        final AccountDeviceInstance accountDeviceInstance = accountDeviceInstanceKey.getAccountDeviceInstance();
        final String name = accountDeviceInstance.getAccount().getTypeSpecificID();

        final mxCell vertex = nodeMap.computeIfAbsent(name + accountDeviceInstance.getDeviceId(), vertexName -> {
            double size = Math.sqrt(accountDeviceInstanceKey.getMessageCount()) + 10;

            mxCell newVertex = (mxCell) insertVertex(
                    getDefaultParent(),
                    name, accountDeviceInstanceKey,
                    Math.random() * 400,
                    Math.random() * 400,
                    size,
                    size);
            return newVertex;
        });
        return vertex;
    }

    @SuppressWarnings("unchecked")
    private mxCell addOrUpdateEdge(long relSources, AccountDeviceInstanceKey account1, AccountDeviceInstanceKey account2) {
        mxCell vertex1 = getOrCreateVertex(account1);
        mxCell vertex2 = getOrCreateVertex(account2);
        Object[] edgesBetween = getEdgesBetween(vertex1, vertex2);
        mxCell edge;
        if (edgesBetween.length == 0) {
            final String edgeName = vertex1.getId() + " - " + vertex2.getId();
            edge = (mxCell) insertEdge(getDefaultParent(), edgeName, relSources, vertex1, vertex2,
                    "strokeWidth=" + (Math.log(relSources) + 1));
        } else {
            edge = (mxCell) edgesBetween[0];
            edge.setStyle("strokeWidth=" + (Math.log(relSources) + 1));
        }
        return edge;
    }

    /**
     * SwingWorker that loads the accounts and edges for this graph according to
     * the pinned accounts and the current filters.
     */
    private class RebuildWorker extends SwingWorker<Void, Void> {

        private final ProgressIndicator progressIndicator;
        private final CommunicationsManager commsManager;
        private final CommunicationsFilter currentFilter;

        RebuildWorker(ProgressIndicator progress, CommunicationsManager commsManager, CommunicationsFilter currentFilter) {
            this.progressIndicator = progress;
            this.currentFilter = currentFilter;
            this.commsManager = commsManager;

        }

        @Override
        protected Void doInBackground() {
            progressIndicator.start("Loading accounts");
            int progressCounter = 0;
            try {
                /**
                 * set to keep track of accounts related to pinned accounts
                 */
                final Map<AccountDeviceInstance, AccountDeviceInstanceKey> relatedAccounts = new HashMap<>();
                for (final AccountDeviceInstanceKey adiKey : pinnedAccountModel.getPinnedAccounts()) {
                    if (isCancelled()) {
                        break;
                    }
                    final List<AccountDeviceInstance> relatedAccountDeviceInstances
                            = commsManager.getRelatedAccountDeviceInstances(adiKey.getAccountDeviceInstance(), currentFilter);
                    relatedAccounts.put(adiKey.getAccountDeviceInstance(), adiKey);
                    getOrCreateVertex(adiKey);

                    //get accounts related to pinned account
                    for (final AccountDeviceInstance relatedADI : relatedAccountDeviceInstances) {
                        final long adiRelationshipsCount = commsManager.getRelationshipSourcesCount(relatedADI, currentFilter);
                        final AccountDeviceInstanceKey relatedADIKey = new AccountDeviceInstanceKey(relatedADI, currentFilter, adiRelationshipsCount);
                        relatedAccounts.put(relatedADI, relatedADIKey); //store related accounts
                    }
                    progressIndicator.progress(++progressCounter);
                }

                Set<AccountDeviceInstance> accounts = relatedAccounts.keySet();

                Map<AccountPair, Long> relationshipCounts = commsManager.getRelationshipCountsPairwise(accounts, currentFilter);

                int total = relationshipCounts.size();
                int progress = 0;
                String progressText = "";
                progressIndicator.switchToDeterminate("", 0, total);
                for (Map.Entry<AccountPair, Long> entry : relationshipCounts.entrySet()) {
                    Long count = entry.getValue();
                    AccountPair relationshipKey = entry.getKey();
                    AccountDeviceInstanceKey account1 = relatedAccounts.get(relationshipKey.getFirst());
                    AccountDeviceInstanceKey account2 = relatedAccounts.get(relationshipKey.getSecond());

                    if (pinnedAccountModel.isAccountPinned(account1)
                            || pinnedAccountModel.isAccountPinned(account2)) {
                        mxCell addEdge = addOrUpdateEdge(count, account1, account2);
                        progressText = addEdge.getId();
                    }
                    progressIndicator.progress(progressText, progress++);
                }
            } catch (TskCoreException tskCoreException) {
                logger.log(Level.SEVERE, "Error", tskCoreException);
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
                progressIndicator.finish();
            }
        }
    }
}
