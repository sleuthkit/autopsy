/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
import com.mxgraph.view.mxGraph;
import java.awt.BorderLayout;
import java.awt.Color;
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
import static java.util.Collections.singleton;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.datamodel.AccountDeviceInstance;
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
@NbBundle.Messages("VisualizationPanel.cancelButton.text=Cancel")
final public class VisualizationPanel extends JPanel implements Lookup.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(VisualizationPanel.class.getName());
    private static final String BASE_IMAGE_PATH = "/org/sleuthkit/autopsy/communications/images";
    static final private ImageIcon pinIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/marker--pin.png"));
    static final private ImageIcon addPinIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/marker--plus.png"));
    static final private ImageIcon unpinIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/marker--minus.png"));
    static final private ImageIcon unlockIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_unlocked.png"));
    static final private ImageIcon lockIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_locked.png"));

    private static final String CANCEL = Bundle.VisualizationPanel_cancelButton_text();

    private final ExplorerManager vizEM = new ExplorerManager();
    private final ExplorerManager gacEM = new ExplorerManager();
    private final ProxyLookup proxyLookup = new ProxyLookup(
            ExplorerUtils.createLookup(gacEM, getActionMap()),
            ExplorerUtils.createLookup(vizEM, getActionMap()));

    private Frame windowAncestor;

    private CommunicationsManager commsManager;
    private CommunicationsFilter currentFilter;

    private final mxGraphComponent graphComponent;
    private final CommunicationsGraph graph;

    protected mxUndoManager undoManager = new mxUndoManager();
    private final mxRubberband rubberband;
    private final mxFastOrganicLayout fastOrganicLayout;
    private final mxCircleLayout circleLayout;
    private final mxOrganicLayout organicLayout;
    private final mxHierarchicalLayout hierarchicalLayout;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private SwingWorker<?, ?> worker;
    private final PinnedAccountModel pinnedAccountModel;
    private final LockedVertexModel lockedVertexModel;

    public VisualizationPanel() {
        initComponents();
        graph = new CommunicationsGraph();
        pinnedAccountModel = graph.getPinnedAccountModel();
        lockedVertexModel = graph.getLockedVertexModel();

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

        //install rubber band selection handler
        rubberband = new mxRubberband(graphComponent);

        final mxEventSource.mxIEventListener scaleListener = (Object sender, mxEventObject evt) ->
                zoomLabel.setText(DecimalFormat.getPercentInstance().format(graph.getView().getScale()));
        graph.getView().addListener(mxEvent.SCALE, scaleListener);
        graph.getView().addListener(mxEvent.SCALE_AND_TRANSLATE, scaleListener);

        //right click handler
        graphComponent.getGraphControl().addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(final MouseWheelEvent e) {
                super.mouseWheelMoved(e);
                if (e.getPreciseWheelRotation() > 0) {
                    graphComponent.zoomIn();
                } else if (e.getPreciseWheelRotation() < 0) {
                    graphComponent.zoomOut();
                }
            }
        });

        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (SwingUtilities.isRightMouseButton(e)) {
                    final mxCell cellAt = (mxCell) graphComponent.getCellAt(e.getX(), e.getY());
                    if (cellAt != null && cellAt.isVertex()) {
                        final JPopupMenu jPopupMenu = new JPopupMenu();
                        final AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) cellAt.getValue();

                        if (lockedVertexModel.isVertexLocked(cellAt)) {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("UnLock " + cellAt.getId(), unlockIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    lockedVertexModel.unlockVertex(cellAt);
                                }
                            }));
                        } else {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Lock " + cellAt.getId(), lockIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    lockedVertexModel.lockVertex(cellAt);
                                }
                            }));
                        }
                        if (pinnedAccountModel.isAccountPinned(adiKey)) {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Unpin " + cellAt.getId(), unpinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handleUnPinEvent(new CVTEvents.UnpinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue())));
                                }
                            }));
                        } else {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Pin " + cellAt.getId(), addPinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handlePinEvent(new CVTEvents.PinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue()), false));
                                }
                            }));
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Pin only " + cellAt.getId(), pinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handlePinEvent(new CVTEvents.PinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue()), true));
                                }
                            }));
                        }
                        jPopupMenu.show(graphComponent.getGraphControl(), e.getX(), e.getY());
                    }
                }
            }
        });

        splitPane.setRightComponent(new MessageBrowser(vizEM, gacEM));

        //feed selection to explorermanager
        graph.getSelectionModel().addListener(null, new SelectionListener());
        final mxEventSource.mxIEventListener undoListener = (Object sender, mxEventObject evt) ->
                undoManager.undoableEditHappened((mxUndoableEdit) evt.getProperty("edit"));

        graph.getModel().addListener(mxEvent.UNDO, undoListener);
        graph.getView().addListener(mxEvent.UNDO, undoListener);
    }

    @Override

    public Lookup getLookup() {
        return proxyLookup;
    }

    @Subscribe
    void handleUnPinEvent(CVTEvents.UnpinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        pinnedAccountModel.unpinAccount(pinEvent.getAccountDeviceInstances());
        graph.clear();
        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();

    }

    @Subscribe
    void handlePinEvent(CVTEvents.PinAccountsEvent pinEvent) {
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
    void handleFilterEvent(CVTEvents.FilterChangeEvent filterChangeEvent) {

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

            CancelationListener cancelationListener = new CancelationListener();
            ModalDialogProgressIndicator progress = new ModalDialogProgressIndicator(windowAncestor, "Loading Visualization", new String[]{CANCEL}, CANCEL, cancelationListener);
            worker = graph.rebuild(progress, commsManager, currentFilter);
            cancelationListener.configure(worker, progress);
            worker.addPropertyChangeListener((final PropertyChangeEvent evt) -> {
                if (worker.isDone()) {
                    if (worker.isCancelled()) {
                        graph.resetGraph();
                        rebuildGraph();
                    } else if (graph.getModel().getChildCount(graph.getDefaultParent()) < 64) {
                        applyOrganicLayout(10);
                    } else {
                        JOptionPane.showMessageDialog(VisualizationPanel.this,
                                "Too many accounts, layout aborted.",
                                "Autopsy",
                                JOptionPane.WARNING_MESSAGE);
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
            commsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Can't get CommunicationsManager when there is no case open.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex);

        }

        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), evt -> {
            graph.getModel().beginUpdate();
            try {
                graph.resetGraph();
            } finally {
                graph.getModel().endUpdate();
            }
            if (evt.getNewValue() != null) {
                Case currentCase = (Case) evt.getNewValue();
                try {
                    commsManager = currentCase.getSleuthkitCase().getCommunicationsManager();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex);
                }
            } else {
                commsManager = null;
            }

        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
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

        setLayout(new BorderLayout());

        splitPane.setDividerLocation(800);
        splitPane.setResizeWeight(0.5);

        borderLayoutPanel.setLayout(new BorderLayout());

        jTextArea1.setBackground(new Color(240, 240, 240));
        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jTextArea1.text")); // NOI18N

        GroupLayout placeHolderPanelLayout = new GroupLayout(placeHolderPanel);
        placeHolderPanel.setLayout(placeHolderPanelLayout);
        placeHolderPanelLayout.setHorizontalGroup(placeHolderPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, placeHolderPanelLayout.createSequentialGroup()
                .addContainerGap(208, Short.MAX_VALUE)
                .add(jTextArea1, GroupLayout.PREFERRED_SIZE, 372, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(209, Short.MAX_VALUE))
        );
        placeHolderPanelLayout.setVerticalGroup(placeHolderPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, placeHolderPanelLayout.createSequentialGroup()
                .addContainerGap(213, Short.MAX_VALUE)
                .add(jTextArea1, GroupLayout.PREFERRED_SIZE, 43, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(214, Short.MAX_VALUE))
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

        zoomOutButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-out-red.png"))); // NOI18N
        zoomOutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomOutButton.text")); // NOI18N
        zoomOutButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomOutButton.toolTipText")); // NOI18N
        zoomOutButton.setFocusable(false);
        zoomOutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomOutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomOutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });

        zoomInButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-in-green.png"))); // NOI18N
        zoomInButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomInButton.text")); // NOI18N
        zoomInButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomInButton.toolTipText")); // NOI18N
        zoomInButton.setFocusable(false);
        zoomInButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomInButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomInButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomInButtonActionPerformed(evt);
            }
        });

        zoomActualButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-actual.png"))); // NOI18N
        zoomActualButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomActualButton.text")); // NOI18N
        zoomActualButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomActualButton.toolTipText")); // NOI18N
        zoomActualButton.setFocusable(false);
        zoomActualButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomActualButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomActualButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomActualButtonActionPerformed(evt);
            }
        });

        fitZoomButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-fit.png"))); // NOI18N
        fitZoomButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fitZoomButton.text")); // NOI18N
        fitZoomButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fitZoomButton.toolTipText")); // NOI18N
        fitZoomButton.setFocusable(false);
        fitZoomButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fitZoomButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fitZoomButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fitZoomButtonActionPerformed(evt);
            }
        });

        jLabel2.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jLabel2.text")); // NOI18N

        zoomLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomLabel.text")); // NOI18N

        GroupLayout toolbarLayout = new GroupLayout(toolbar);
        toolbar.setLayout(toolbarLayout);
        toolbarLayout.setHorizontalGroup(toolbarLayout.createParallelGroup(GroupLayout.LEADING)
            .add(toolbarLayout.createSequentialGroup()
                .add(3, 3, 3)
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
                .add(jSeparator1, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomOutButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomInButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomActualButton, GroupLayout.PREFERRED_SIZE, 33, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(fitZoomButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jLabel2)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomLabel)
                .add(27, 27, 27))
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
                    .add(zoomLabel))
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

        Dimension size = graphComponent.getSize();
        double widthFactor = size.getWidth() / boundsForCells.getWidth();

        graphComponent.zoom(widthFactor);

    }

    private void morph(mxIGraphLayout layout) {
        // layout using morphing
        graph.getModel().beginUpdate();

        CancelationListener cancelationListener = new CancelationListener();
        ModalDialogProgressIndicator progress = new ModalDialogProgressIndicator(windowAncestor, "Computing layout", new String[]{CANCEL}, CANCEL, cancelationListener);
        SwingWorker<Void, Void> morphWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                progress.start("Computing layout");
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
    private JButton fastOrganicLayoutButton;
    private JButton fitZoomButton;
    private JButton hierarchyLayoutButton;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JToolBar.Separator jSeparator1;
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
                HashSet<AccountDeviceInstance> adis = new HashSet<>();
                for (mxICell cell : selectedCells) {
                    if (cell.isEdge()) {
                        relationshipSources.addAll((Set<Content>) cell.getValue());
                    } else if (cell.isVertex()) {
                        adis.add(((AccountDeviceInstanceKey) cell.getValue()).getAccountDeviceInstance());
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
        public mxRectangle setVertexLocation(Object vertex, double x, double y) {
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
        public mxRectangle setVertexLocation(Object vertex, double x, double y) {
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
        public mxRectangle setVertexLocation(Object vertex, double x, double y) {
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
        public mxRectangle setVertexLocation(Object vertex, double x, double y) {
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }
    }

    private class CancelationListener implements ActionListener {

        private Future<?> cancellable;
        private ModalDialogProgressIndicator progress;

        void configure(Future<?> cancellable, ModalDialogProgressIndicator progress) {
            this.cancellable = cancellable;
            this.progress = progress;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            progress.setCancelling("Cancelling...");
            cancellable.cancel(true);
            progress.finish();
        }

    }
}
