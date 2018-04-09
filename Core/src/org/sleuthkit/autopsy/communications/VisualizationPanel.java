/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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

import com.google.common.eventbus.Subscribe;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxMorphing;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.util.mxUndoManager;
import com.mxgraph.util.mxUndoableEdit;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.jdesktop.layout.GroupLayout;
import org.jdesktop.layout.LayoutStyle;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A panel that goes in the Visualize tab of the Communications Visualization
 * Tool. Hosts an JGraphX mxGraphComponent that implements the communications
 * network visualization and a MessageBrowser for viewing details of
 * communications.
 *
 * The Lookup provided by getLookup will be proxied by the lookup of the
 * CVTTopComponent when this tab is active allowing for context sensitive
 * actions to work correctly.
 */
final public class VisualizationPanel extends JPanel implements Lookup.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(VisualizationPanel.class.getName());
    private static final String BASE_IMAGE_PATH = "/org/sleuthkit/autopsy/communications/images";
    static final private ImageIcon unlockIcon
            = new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_unlocked.png"));
    static final private ImageIcon lockIcon
            = new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_locked.png"));

    @NbBundle.Messages("VisualizationPanel.cancelButton.text=Cancel")
    private static final String CANCEL = Bundle.VisualizationPanel_cancelButton_text();

    private final ExplorerManager vizEM = new ExplorerManager();
    private final ExplorerManager gacEM = new ExplorerManager();
    private final ProxyLookup proxyLookup;
    private Frame windowAncestor;

    private CommunicationsManager commsManager;
    private CommunicationsFilter currentFilter;

    private final mxGraphComponent graphComponent;
    private final CommunicationsGraph graph;

    private final mxUndoManager undoManager = new mxUndoManager();
    private final mxRubberband rubberband; //NOPMD  We keep a referenec as insurance to prevent garbage collection

    private final mxFastOrganicLayout fastOrganicLayout;
    private final mxCircleLayout circleLayout;
    private final mxOrganicLayout organicLayout;
    private final mxHierarchicalLayout hierarchicalLayout;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private SwingWorker<?, ?> worker;
    private final PinnedAccountModel pinnedAccountModel = new PinnedAccountModel();
    private final LockedVertexModel lockedVertexModel = new LockedVertexModel();

    public VisualizationPanel() {
        initComponents();

        graph = new CommunicationsGraph(pinnedAccountModel, lockedVertexModel);

        fastOrganicLayout = new mxFastOrganicLayoutImpl(graph);
        circleLayout = new mxCircleLayoutImpl(graph);
        organicLayout = new mxOrganicLayoutImpl(graph);
        hierarchicalLayout = new mxHierarchicalLayoutImpl(graph);

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoExtend(true);
        graphComponent.setAutoScroll(true);
        graphComponent.setAutoscrolls(true);
        graphComponent.setConnectable(false);
        graphComponent.setDragEnabled(false);
        graphComponent.setKeepSelectionVisibleOnZoom(true);
        graphComponent.setOpaque(true);
        graphComponent.setToolTips(true);
        graphComponent.setBackground(Color.WHITE);
        borderLayoutPanel.add(graphComponent, BorderLayout.CENTER);

        //install rubber band other handlers
        rubberband = new mxRubberband(graphComponent);

        lockedVertexModel.registerhandler(this);

        final mxEventSource.mxIEventListener scaleListener = (Object sender, mxEventObject evt)
                -> zoomLabel.setText(DecimalFormat.getPercentInstance().format(graph.getView().getScale()));
        graph.getView().addListener(mxEvent.SCALE, scaleListener);
        graph.getView().addListener(mxEvent.SCALE_AND_TRANSLATE, scaleListener);

        graphComponent.getGraphControl().addMouseWheelListener(new MouseAdapter() {
            /**
             * Translate mouse wheel events into zooming.
             *
             * @param event The MouseWheelEvent
             */
            @Override
            public void mouseWheelMoved(final MouseWheelEvent event) {
                super.mouseWheelMoved(event);
                if (event.getPreciseWheelRotation() > 0) {
                    graphComponent.zoomIn();
                } else if (event.getPreciseWheelRotation() < 0) {
                    graphComponent.zoomOut();
                }
            }
        });

        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            /**
             * Right click handler: show context menu.
             *
             * @param event The MouseEvent
             */
            @Override
            public void mouseClicked(final MouseEvent event) {
                super.mouseClicked(event);
                if (SwingUtilities.isRightMouseButton(event)) {
                    final mxCell cellAt = (mxCell) graphComponent.getCellAt(event.getX(), event.getY());
                    if (cellAt != null && cellAt.isVertex()) {
                        final JPopupMenu jPopupMenu = new JPopupMenu();
                        final AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) cellAt.getValue();

                        Set<mxCell> selectedVertices
                                = Stream.of(graph.getSelectionModel().getCells())
                                        .map(mxCell.class::cast)
                                        .filter(mxCell::isVertex)
                                        .collect(Collectors.toSet());

                        if (lockedVertexModel.isVertexLocked(cellAt)) {
                            jPopupMenu.add(new JMenuItem(new UnlockAction(selectedVertices)));
                        } else {
                            jPopupMenu.add(new JMenuItem(new LockAction(selectedVertices)));
                        }
                        if (pinnedAccountModel.isAccountPinned(adiKey)) {
                            jPopupMenu.add(UnpinAccountsAction.getInstance().getPopupPresenter());
                        } else {
                            jPopupMenu.add(PinAccountsAction.getInstance().getPopupPresenter());
                            jPopupMenu.add(ResetAndPinAccountsAction.getInstance().getPopupPresenter());
                        }
                        jPopupMenu.show(graphComponent.getGraphControl(), event.getX(), event.getY());
                    }
                }
            }

        });

        final MessageBrowser messageBrowser = new MessageBrowser(vizEM, gacEM);

        splitPane.setRightComponent(messageBrowser);

        proxyLookup = new ProxyLookup(
                ExplorerUtils.createLookup(vizEM, getActionMap()),
                messageBrowser.getLookup()
        );

        //feed selection to explorermanager
        graph.getSelectionModel().addListener(mxEvent.CHANGE, new SelectionListener());
        final mxEventSource.mxIEventListener undoListener = (Object sender, mxEventObject evt)
                -> undoManager.undoableEditHappened((mxUndoableEdit) evt.getProperty("edit"));

        graph.getModel().addListener(mxEvent.UNDO, undoListener);
        graph.getView().addListener(mxEvent.UNDO, undoListener);
    }

    @Override
    public Lookup getLookup() {
        return proxyLookup;
    }

    @Subscribe
    void handle(LockedVertexModel.VertexLockEvent event) {
        final Set<mxCell> vertices = event.getVertices();
        mxGraphView view = graph.getView();
        vertices.forEach(vertex -> {
            final mxCellState state = view.getState(vertex, true);
            view.updateLabel(state);
            view.updateLabelBounds(state);
            view.updateBoundingBox(state);
            graphComponent.redraw(state);
        });
    }

    @Subscribe
    void handle(final CVTEvents.UnpinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        pinnedAccountModel.unpinAccount(pinEvent.getAccountDeviceInstances());
        graph.clear();
        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();
    }

    @Subscribe
    void handle(final CVTEvents.PinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        if (pinEvent.isReplace()) {
            graph.resetGraph();
        }
        pinnedAccountModel.pinAccount(pinEvent.getAccountDeviceInstances());
        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();
    }

    @Subscribe
    void handle(final CVTEvents.FilterChangeEvent filterChangeEvent) {
        graph.getModel().beginUpdate();
        graph.clear();
        currentFilter = filterChangeEvent.getNewFilter();
        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void rebuildGraph() {
        if (pinnedAccountModel.isEmpty()) {
            borderLayoutPanel.remove(graphComponent);
            borderLayoutPanel.add(placeHolderPanel, BorderLayout.CENTER);
            repaint();
        } else {
            borderLayoutPanel.remove(placeHolderPanel);
            borderLayoutPanel.add(graphComponent, BorderLayout.CENTER);
            if (worker != null) {
                worker.cancel(true);
            }

            final CancelationListener cancelationListener = new CancelationListener();
            final ModalDialogProgressIndicator progress = new ModalDialogProgressIndicator(windowAncestor, "Loading Visualization", new String[]{CANCEL}, CANCEL, cancelationListener);
            worker = graph.rebuild(progress, commsManager, currentFilter);
            cancelationListener.configure(worker, progress);
            worker.addPropertyChangeListener((final PropertyChangeEvent evt) -> {
                if (worker.isDone()) {
                    if (worker.isCancelled()) {
                        graph.resetGraph();
                        rebuildGraph();
                    } else {
                        morph(fastOrganicLayout);
                    }
                }
            });

            worker.execute();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        windowAncestor = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);

        try {
            commsManager = Case.getOpenCase().getSleuthkitCase().getCommunicationsManager();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Can't get CommunicationsManager when there is no case open.", ex);
        }

        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), evt -> {
            graph.getModel().beginUpdate();
            try {
                graph.resetGraph();
            } finally {
                graph.getModel().endUpdate();
            }
            if (evt.getNewValue() == null) {
                commsManager = null;
            } else {
                Case currentCase = (Case) evt.getNewValue();
                try {
                    commsManager = currentCase.getSleuthkitCase().getCommunicationsManager();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex);
                }
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        splitPane = new JSplitPane();
        borderLayoutPanel = new JPanel();
        placeHolderPanel = new JPanel();
        jTextArea1 = new JTextArea();
        toolbar = new JPanel();
        jLabel1 = new JLabel();
        hierarchyLayoutButton = new JButton();
        fastOrganicLayoutButton = new JButton();
        organicLayoutButton = new JButton();
        circleLayoutButton = new JButton();
        jSeparator1 = new JToolBar.Separator();
        zoomOutButton = new JButton();
        zoomInButton = new JButton();
        zoomActualButton = new JButton();
        fitZoomButton = new JButton();
        jLabel2 = new JLabel();
        zoomLabel = new JLabel();
        clearVizButton = new JButton();
        jSeparator2 = new JToolBar.Separator();

        setLayout(new BorderLayout());

        splitPane.setDividerLocation(800);
        splitPane.setResizeWeight(0.5);

        borderLayoutPanel.setLayout(new BorderLayout());

        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jTextArea1.text")); // NOI18N
        jTextArea1.setBackground(new Color(240, 240, 240));

        GroupLayout placeHolderPanelLayout = new GroupLayout(placeHolderPanel);
        placeHolderPanel.setLayout(placeHolderPanelLayout);
        placeHolderPanelLayout.setHorizontalGroup(placeHolderPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(placeHolderPanelLayout.createSequentialGroup()
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jTextArea1, GroupLayout.PREFERRED_SIZE, 424, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        placeHolderPanelLayout.setVerticalGroup(placeHolderPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(placeHolderPanelLayout.createSequentialGroup()
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jTextArea1, GroupLayout.PREFERRED_SIZE, 47, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        borderLayoutPanel.add(placeHolderPanel, BorderLayout.CENTER);

        jLabel1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jLabel1.text")); // NOI18N

        hierarchyLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.hierarchyLayoutButton.text")); // NOI18N
        hierarchyLayoutButton.setFocusable(false);
        hierarchyLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        hierarchyLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        hierarchyLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                hierarchyLayoutButtonActionPerformed(evt);
            }
        });

        fastOrganicLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fastOrganicLayoutButton.text")); // NOI18N
        fastOrganicLayoutButton.setFocusable(false);
        fastOrganicLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fastOrganicLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fastOrganicLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fastOrganicLayoutButtonActionPerformed(evt);
            }
        });

        organicLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.organicLayoutButton.text")); // NOI18N
        organicLayoutButton.setFocusable(false);
        organicLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        organicLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        organicLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                organicLayoutButtonActionPerformed(evt);
            }
        });

        circleLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.circleLayoutButton.text")); // NOI18N
        circleLayoutButton.setFocusable(false);
        circleLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        circleLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        circleLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                circleLayoutButtonActionPerformed(evt);
            }
        });

        jSeparator1.setOrientation(SwingConstants.VERTICAL);

        zoomOutButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-out-red.png"))); // NOI18N
        zoomOutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomOutButton.text")); // NOI18N
        zoomOutButton.setFocusable(false);
        zoomOutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomOutButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomOutButton.toolTipText")); // NOI18N
        zoomOutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomOutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });

        zoomInButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-in-green.png"))); // NOI18N
        zoomInButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomInButton.text")); // NOI18N
        zoomInButton.setFocusable(false);
        zoomInButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomInButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomInButton.toolTipText")); // NOI18N
        zoomInButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomInButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomInButtonActionPerformed(evt);
            }
        });

        zoomActualButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-actual.png"))); // NOI18N
        zoomActualButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomActualButton.text")); // NOI18N
        zoomActualButton.setFocusable(false);
        zoomActualButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomActualButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomActualButton.toolTipText")); // NOI18N
        zoomActualButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomActualButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomActualButtonActionPerformed(evt);
            }
        });

        fitZoomButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-fit.png"))); // NOI18N
        fitZoomButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fitZoomButton.text")); // NOI18N
        fitZoomButton.setFocusable(false);
        fitZoomButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fitZoomButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fitZoomButton.toolTipText")); // NOI18N
        fitZoomButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fitZoomButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fitZoomButtonActionPerformed(evt);
            }
        });

        jLabel2.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jLabel2.text")); // NOI18N

        zoomLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomLabel.text")); // NOI18N

        clearVizButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/broom.png"))); // NOI18N
        clearVizButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.clearVizButton.text_1")); // NOI18N
        clearVizButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                clearVizButtonActionPerformed(evt);
            }
        });

        jSeparator2.setOrientation(SwingConstants.VERTICAL);

        GroupLayout toolbarLayout = new GroupLayout(toolbar);
        toolbar.setLayout(toolbarLayout);
        toolbarLayout.setHorizontalGroup(toolbarLayout.createParallelGroup(GroupLayout.LEADING)
            .add(toolbarLayout.createSequentialGroup()
                .addContainerGap()
                .add(clearVizButton)
                .add(3, 3, 3)
                .add(jSeparator1, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                .add(5, 5, 5)
                .add(jLabel1)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(fastOrganicLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(organicLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(hierarchyLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(circleLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(jSeparator2, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(jLabel2)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomLabel)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomOutButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomInButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomActualButton, GroupLayout.PREFERRED_SIZE, 33, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(fitZoomButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(12, Short.MAX_VALUE))
        );
        toolbarLayout.setVerticalGroup(toolbarLayout.createParallelGroup(GroupLayout.LEADING)
            .add(toolbarLayout.createSequentialGroup()
                .add(3, 3, 3)
                .add(toolbarLayout.createParallelGroup(GroupLayout.CENTER)
                    .add(jLabel1, GroupLayout.PREFERRED_SIZE, 25, GroupLayout.PREFERRED_SIZE)
                    .add(hierarchyLayoutButton)
                    .add(fastOrganicLayoutButton)
                    .add(organicLayoutButton)
                    .add(circleLayoutButton)
                    .add(jSeparator1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(zoomOutButton)
                    .add(zoomInButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(zoomActualButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(fitZoomButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel2)
                    .add(zoomLabel)
                    .add(clearVizButton)
                    .add(jSeparator2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .add(3, 3, 3))
        );

        borderLayoutPanel.add(toolbar, BorderLayout.PAGE_START);

        splitPane.setLeftComponent(borderLayoutPanel);

        add(splitPane, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void fitZoomButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fitZoomButtonActionPerformed
        fitGraph();
    }//GEN-LAST:event_fitZoomButtonActionPerformed

    private void zoomActualButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomActualButtonActionPerformed
        graphComponent.zoomActual();
    }//GEN-LAST:event_zoomActualButtonActionPerformed

    private void zoomInButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        graphComponent.zoomIn();
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void zoomOutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        graphComponent.zoomOut();
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void circleLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_circleLayoutButtonActionPerformed
        morph(circleLayout);
    }//GEN-LAST:event_circleLayoutButtonActionPerformed

    private void organicLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_organicLayoutButtonActionPerformed
        applyOrganicLayout(10);
    }//GEN-LAST:event_organicLayoutButtonActionPerformed

    private void fastOrganicLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fastOrganicLayoutButtonActionPerformed
        morph(fastOrganicLayout);
    }//GEN-LAST:event_fastOrganicLayoutButtonActionPerformed

    private void hierarchyLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_hierarchyLayoutButtonActionPerformed
        morph(hierarchicalLayout);
    }//GEN-LAST:event_hierarchyLayoutButtonActionPerformed

    private void clearVizButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_clearVizButtonActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        graph.getModel().beginUpdate();
        pinnedAccountModel.clear();
        graph.clear();
        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();
        setCursor(Cursor.getDefaultCursor());

    }//GEN-LAST:event_clearVizButtonActionPerformed

    private void applyOrganicLayout(int iterations) {
        organicLayout.setMaxIterations(iterations);
        morph(organicLayout);
    }

    private void fitGraph() {
        graphComponent.zoomTo(1, true);
        mxPoint translate = graph.getView().getTranslate();
        if (translate == null || Double.isNaN(translate.getX()) || Double.isNaN(translate.getY())) {
            translate = new mxPoint();
        }

        mxRectangle boundsForCells = graph.getCellBounds(graph.getDefaultParent(), true, true, true);
        if (boundsForCells == null || Double.isNaN(boundsForCells.getWidth()) || Double.isNaN(boundsForCells.getHeight())) {
            boundsForCells = new mxRectangle(0, 0, 1, 1);
        }
        final mxPoint mxPoint = new mxPoint(translate.getX() - boundsForCells.getX(), translate.getY() - boundsForCells.getY());

        graph.cellsMoved(graph.getChildCells(graph.getDefaultParent()), mxPoint.getX(), mxPoint.getY(), false, false);

        boundsForCells = graph.getCellBounds(graph.getDefaultParent(), true, true, true);
        if (boundsForCells == null || Double.isNaN(boundsForCells.getWidth()) || Double.isNaN(boundsForCells.getHeight())) {
            boundsForCells = new mxRectangle(0, 0, 1, 1);
        }

        final Dimension size = graphComponent.getSize();
        final double widthFactor = size.getWidth() / boundsForCells.getWidth();

        graphComponent.zoom(widthFactor);

    }

    @NbBundle.Messages({"Visualization.computingLayout=Computing Layout"})
    private void morph(mxIGraphLayout layout) {
        // layout using morphing
        graph.getModel().beginUpdate();

        CancelationListener cancelationListener = new CancelationListener();
        ModalDialogProgressIndicator progress = new ModalDialogProgressIndicator(windowAncestor,
                Bundle.Visualization_computingLayout(), new String[]{CANCEL}, CANCEL, cancelationListener);
        SwingWorker<Void, Void> morphWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                progress.start(Bundle.Visualization_computingLayout());
                layout.execute(graph.getDefaultParent());
                if (isCancelled()) {
                    progress.finish();
                    return null;
                }
                mxMorphing morph = new mxMorphing(graphComponent, 20, 1.2, 20) {
                    @Override
                    public void updateAnimation() {
                        fireEvent(new mxEventObject(mxEvent.EXECUTE));
                        super.updateAnimation(); //To change body of generated methods, choose Tools | Templates.
                    }

                };
                morph.addListener(mxEvent.EXECUTE, (Object sender, mxEventObject evt) -> {
                    if (isCancelled()) {
                        morph.stopAnimation();
                    }
                });
                morph.addListener(mxEvent.DONE, (Object sender, mxEventObject event) -> {
                    graph.getModel().endUpdate();
                    if (isCancelled()) {
                        undoManager.undo();
                    } else {
                        fitGraph();
                    }
                    progress.finish();
                });

                morph.startAnimation();
                return null;

            }
        };
        cancelationListener.configure(morphWorker, progress);
        morphWorker.execute();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JPanel borderLayoutPanel;
    private JButton circleLayoutButton;
    private JButton clearVizButton;
    private JButton fastOrganicLayoutButton;
    private JButton fitZoomButton;
    private JButton hierarchyLayoutButton;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JToolBar.Separator jSeparator1;
    private JToolBar.Separator jSeparator2;
    private JTextArea jTextArea1;
    private JButton organicLayoutButton;
    private JPanel placeHolderPanel;
    private JSplitPane splitPane;
    private JPanel toolbar;
    private JButton zoomActualButton;
    private JButton zoomInButton;
    private JLabel zoomLabel;
    private JButton zoomOutButton;
    // End of variables declaration//GEN-END:variables

    /**
     * Listens to graph selection model and updates ExplorerManager to reflect
     * changes in selection.
     */
    final private class SelectionListener implements mxEventSource.mxIEventListener {

        @SuppressWarnings("unchecked")
        @Override
        public void invoke(Object sender, mxEventObject evt) {
            Object[] selectionCells = graph.getSelectionCells();
            Node rootNode = Node.EMPTY;
            Node[] selectedNodes = new Node[0];
            if (selectionCells.length > 0) {
                mxICell[] selectedCells = Arrays.asList(selectionCells).toArray(new mxCell[selectionCells.length]);
                HashSet<Content> relationshipSources = new HashSet<>();
                HashSet<AccountDeviceInstanceKey> adis = new HashSet<>();
                for (mxICell cell : selectedCells) {
                    if (cell.isEdge()) {
                        mxICell source = (mxICell) graph.getModel().getTerminal(cell, true);
                        AccountDeviceInstanceKey account1 = (AccountDeviceInstanceKey) source.getValue();
                        mxICell target = (mxICell) graph.getModel().getTerminal(cell, false);
                        AccountDeviceInstanceKey account2 = (AccountDeviceInstanceKey) target.getValue();
                        try {
                            final List<Content> relationshipSources1 = commsManager.getRelationshipSources(
                                    account1.getAccountDeviceInstance(),
                                    account2.getAccountDeviceInstance(),
                                    currentFilter);
                            relationshipSources.addAll(relationshipSources1);
                        } catch (TskCoreException tskCoreException) {
                            logger.log(Level.SEVERE, " Error getting relationsips....", tskCoreException);
                        }
                    } else if (cell.isVertex()) {
                        adis.add((AccountDeviceInstanceKey) cell.getValue());
                    }
                }

                rootNode = SelectionNode.createFromAccountsAndRelationships(relationshipSources, adis, currentFilter, commsManager);
                selectedNodes = new Node[]{rootNode};
            }
            vizEM.setRootContext(rootNode);
            try {
                vizEM.setSelectedNodes(selectedNodes);
            } catch (PropertyVetoException ex) {
                logger.log(Level.SEVERE, "Selection vetoed.", ex);
            }
        }
    }

    final private class mxFastOrganicLayoutImpl extends mxFastOrganicLayout {

        mxFastOrganicLayoutImpl(mxGraph graph) {
            super(graph);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                    || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x,y variable names are standard
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }
    }

    final private class mxCircleLayoutImpl extends mxCircleLayout {

        mxCircleLayoutImpl(mxGraph graph) {
            super(graph);
            setResetEdges(true);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                    || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x,y variable names are standard
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }
    }

    final private class mxOrganicLayoutImpl extends mxOrganicLayout {

        mxOrganicLayoutImpl(mxGraph graph) {
            super(graph);
            setResetEdges(true);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                    || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x,y variable names are standard
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }
    }

    final private class mxHierarchicalLayoutImpl extends mxHierarchicalLayout {

        mxHierarchicalLayoutImpl(mxGraph graph) {
            super(graph);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                    || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x,y variable names are standard
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }
    }

    /**
     * Listener that closes a ModalDialogProgreessIndicator when invoked.
     */
    final private class CancelationListener implements ActionListener {

        private Future<?> cancellable;
        private ModalDialogProgressIndicator progress;

        void configure(Future<?> cancellable, ModalDialogProgressIndicator progress) {
            this.cancellable = cancellable;
            this.progress = progress;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            progress.setCancelling("Cancelling...");
            cancellable.cancel(true);
            progress.finish();
        }
    }

    /**
     * Action that un-locks the selected vertices.
     */
    @NbBundle.Messages({
        "VisualizationPanel.unlockAction.singularText=Unlock Selected Account",
        "VisualizationPanel.unlockAction.pluralText=Unlock Selected Accounts",})
    private final class UnlockAction extends AbstractAction {

        private final Set<mxCell> selectedVertices;

        UnlockAction(Set<mxCell> selectedVertices) {
            super(selectedVertices.size() > 1 ? Bundle.VisualizationPanel_unlockAction_pluralText() : Bundle.VisualizationPanel_unlockAction_singularText(),
                    unlockIcon);
            this.selectedVertices = selectedVertices;
        }

        @Override

        public void actionPerformed(final ActionEvent event) {
            lockedVertexModel.unlock(selectedVertices);
        }
    }

    /**
     * Action that locks the selected vertices.
     */
    @NbBundle.Messages({
        "VisualizationPanel.lockAction.singularText=Lock Selected Account",
        "VisualizationPanel.lockAction.pluralText=Lock Selected Accounts"})
    private final class LockAction extends AbstractAction {

        private final Set<mxCell> selectedVertices;

        LockAction(Set<mxCell> selectedVertices) {
            super(selectedVertices.size() > 1 ? Bundle.VisualizationPanel_lockAction_pluralText() : Bundle.VisualizationPanel_lockAction_singularText(),
                    lockIcon);
            this.selectedVertices = selectedVertices;
        }

        @Override
        public void actionPerformed(final ActionEvent event) {
            lockedVertexModel.lock(selectedVertices);
        }
    }
}
