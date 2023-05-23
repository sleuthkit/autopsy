/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
import com.mxgraph.util.mxCellRenderer;
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
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.Notifications;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.communications.relationships.RelationshipBrowser;
import org.sleuthkit.autopsy.communications.relationships.SelectionInfo;
import org.sleuthkit.autopsy.communications.snapshot.CommSnapShotReportWriter;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.guiutils.WrapLayout;
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
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final public class VisualizationPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(VisualizationPanel.class.getName());
    private static final String BASE_IMAGE_PATH = "/org/sleuthkit/autopsy/communications/images";
    static final private ImageIcon unlockIcon
            = new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_unlocked.png"));
    static final private ImageIcon lockIcon
            = new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_locked.png"));

    @NbBundle.Messages("VisualizationPanel.cancelButton.text=Cancel")
    private static final String CANCEL = Bundle.VisualizationPanel_cancelButton_text();

    private Frame windowAncestor;

    private CommunicationsManager commsManager;
    private CommunicationsFilter currentFilter;

    private final mxGraphComponent graphComponent;
    private final CommunicationsGraph graph;

    private final mxUndoManager undoManager = new mxUndoManager();
    private final mxRubberband rubberband; //NOPMD  We keep a referenec as insurance to prevent garbage collection

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private SwingWorker<?, ?> worker;
    private final PinnedAccountModel pinnedAccountModel = new PinnedAccountModel();
    private final LockedVertexModel lockedVertexModel = new LockedVertexModel();

    private final Map<NamedGraphLayout, JButton> layoutButtons = new HashMap<>();
    private NamedGraphLayout currentLayout;
    
    private final RelationshipBrowser relationshipBrowser;

    private final StateManager stateManager;

    @NbBundle.Messages("VisalizationPanel.paintingError=Problem painting visualization.")
    public VisualizationPanel(RelationshipBrowser relationshipBrowser) {
        this.relationshipBrowser = relationshipBrowser;
        initComponents();
        //initialize invisible JFXPanel that is used to show JFXNotifications over this window.
        Platform.runLater(() -> {
            notificationsJFXPanel.setScene(new Scene(new Pane()));
        });

        graph = new CommunicationsGraph(pinnedAccountModel, lockedVertexModel);

        /*
         * custom implementation of mxGraphComponent that uses... a custom
         * implementation of mxGraphControl ... that overrides paint so we can
         * catch the NPEs we are getting and deal with them. For now that means
         * just ignoring them.
         */
        graphComponent = new mxGraphComponent(graph) {
            @Override
            protected mxGraphComponent.mxGraphControl createGraphControl() {

                return new mxGraphControl() {

                    @Override
                    public void paint(Graphics graphics) {
                        try {
                            super.paint(graphics);
                        } catch (NullPointerException ex) { //NOPMD
                            /* We can't find the underlying cause of the NPE in
                             * jgraphx, but it doesn't seem to cause any
                             * noticeable problems, so we are just logging it
                             * and moving on.
                             */
                            logger.log(Level.WARNING, "There was a NPE while painting the VisualizationPanel", ex);
                        }
                    }

                };
            }
        };
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
                -> zoomPercentLabel.setText(DecimalFormat.getPercentInstance().format(graph.getView().getScale()));
        graph.getView().addListener(mxEvent.SCALE, scaleListener);
        graph.getView().addListener(mxEvent.SCALE_AND_TRANSLATE, scaleListener);

        final GraphMouseListener graphMouseListener = new GraphMouseListener();
        graphComponent.getGraphControl().addMouseWheelListener(graphMouseListener);
        graphComponent.getGraphControl().addMouseListener(graphMouseListener);

        //feed selection to explorermanager
        graph.getSelectionModel().addListener(mxEvent.CHANGE, new SelectionListener());
        final mxEventSource.mxIEventListener undoListener = (Object sender, mxEventObject evt)
                -> undoManager.undoableEditHappened((mxUndoableEdit) evt.getProperty("edit"));

        graph.getModel().addListener(mxEvent.UNDO, undoListener);
        graph.getView().addListener(mxEvent.UNDO, undoListener);

        FastOrganicLayoutImpl fastOrganicLayout = new FastOrganicLayoutImpl(graph);

        //local method to configure layout buttons
        BiConsumer<JButton, NamedGraphLayout> configure = (layoutButton, layout) -> {
            layoutButtons.put(layout, layoutButton);
            layoutButton.addActionListener(event -> applyLayout(layout));
        };
        //configure layout buttons.
        configure.accept(fastOrganicLayoutButton, fastOrganicLayout);

        applyLayout(fastOrganicLayout);

        stateManager = new StateManager(pinnedAccountModel);

        setStateButtonsEnabled();
        
        toolbar.setLayout(new WrapLayout());
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

        setStateButtonsEnabled();
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

        setStateButtonsEnabled();
    }

    @Subscribe
    void handle(final CVTEvents.FilterChangeEvent filterChangeEvent) {
        graph.getModel().beginUpdate();
        graph.clear();
        currentFilter = filterChangeEvent.getNewFilter();
        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();

        setStateButtonsEnabled();
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
                    }
                    applyLayout(currentLayout);
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
            commsManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex); //NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Can't get CommunicationsManager when there is no case open.", ex); //NON-NLS
        }

        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), evt -> {
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
                    logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex); //NON-NLS
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
        GridBagConstraints gridBagConstraints;

        borderLayoutPanel = new JPanel();
        placeHolderPanel = new JPanel();
        jTextPane1 = new JTextPane();
        notificationsJFXPanel = new JFXPanel();
        toolbar = new JToolBar();
        backButton = new JButton();
        forwardButton = new JButton();
        jSeparator3 = new JToolBar.Separator();
        clearVizButton = new JButton();
        fastOrganicLayoutButton = new JButton();
        jSeparator2 = new JToolBar.Separator();
        zoomLabel = new JLabel();
        zoomPercentLabel = new JLabel();
        zoomOutButton = new JButton();
        fitZoomButton = new JButton();
        zoomActualButton = new JButton();
        zoomInButton = new JButton();
        jSeparator1 = new JToolBar.Separator();
        snapshotButton = new JButton();

        setLayout(new BorderLayout());

        borderLayoutPanel.setLayout(new BorderLayout());

        placeHolderPanel.setLayout(new GridBagLayout());

        jTextPane1.setEditable(false);
        jTextPane1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jTextPane1.text")); // NOI18N
        jTextPane1.setOpaque(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.anchor = GridBagConstraints.NORTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new Insets(50, 0, 0, 0);
        placeHolderPanel.add(jTextPane1, gridBagConstraints);

        borderLayoutPanel.add(placeHolderPanel, BorderLayout.CENTER);
        borderLayoutPanel.add(notificationsJFXPanel, BorderLayout.PAGE_END);

        add(borderLayoutPanel, BorderLayout.CENTER);

        toolbar.setRollover(true);

        backButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/resultset_previous.png"))); // NOI18N
        backButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.backButton.text_1")); // NOI18N
        backButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.backButton.toolTipText")); // NOI18N
        backButton.setFocusable(false);
        backButton.setHorizontalTextPosition(SwingConstants.CENTER);
        backButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                backButtonActionPerformed(evt);
            }
        });
        toolbar.add(backButton);

        forwardButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/resultset_next.png"))); // NOI18N
        forwardButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.forwardButton.text")); // NOI18N
        forwardButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.forwardButton.toolTipText")); // NOI18N
        forwardButton.setFocusable(false);
        forwardButton.setHorizontalTextPosition(SwingConstants.CENTER);
        forwardButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        forwardButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                forwardButtonActionPerformed(evt);
            }
        });
        toolbar.add(forwardButton);
        toolbar.add(jSeparator3);

        clearVizButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/broom.png"))); // NOI18N
        clearVizButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.clearVizButton.text_1")); // NOI18N
        clearVizButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.clearVizButton.toolTipText")); // NOI18N
        clearVizButton.setActionCommand(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.clearVizButton.actionCommand")); // NOI18N
        clearVizButton.setFocusable(false);
        clearVizButton.setHorizontalTextPosition(SwingConstants.CENTER);
        clearVizButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        clearVizButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                clearVizButtonActionPerformed(evt);
            }
        });
        toolbar.add(clearVizButton);

        fastOrganicLayoutButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-circle-double-135.png"))); // NOI18N
        fastOrganicLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fastOrganicLayoutButton.text")); // NOI18N
        fastOrganicLayoutButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fastOrganicLayoutButton.toolTipText")); // NOI18N
        fastOrganicLayoutButton.setFocusable(false);
        fastOrganicLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fastOrganicLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fastOrganicLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fastOrganicLayoutButtonActionPerformed(evt);
            }
        });
        toolbar.add(fastOrganicLayoutButton);
        toolbar.add(jSeparator2);

        zoomLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomLabel.text")); // NOI18N
        toolbar.add(zoomLabel);

        zoomPercentLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomPercentLabel.text")); // NOI18N
        toolbar.add(zoomPercentLabel);

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
        toolbar.add(zoomOutButton);

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
        toolbar.add(fitZoomButton);

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
        toolbar.add(zoomActualButton);

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
        toolbar.add(zoomInButton);
        toolbar.add(jSeparator1);

        snapshotButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/image.png"))); // NOI18N
        snapshotButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.snapshotButton.text_1")); // NOI18N
        snapshotButton.setToolTipText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.snapshotButton.toolTipText")); // NOI18N
        snapshotButton.setFocusable(false);
        snapshotButton.setHorizontalTextPosition(SwingConstants.CENTER);
        snapshotButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        snapshotButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                snapshotButtonActionPerformed(evt);
            }
        });
        toolbar.add(snapshotButton);

        add(toolbar, BorderLayout.NORTH);
    }// </editor-fold>//GEN-END:initComponents

    private void fitZoomButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fitZoomButtonActionPerformed
        fitGraph();
    }//GEN-LAST:event_fitZoomButtonActionPerformed

    private void zoomActualButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomActualButtonActionPerformed
        graphComponent.zoomActual();
        CVTEvents.getCVTEventBus().post(new CVTEvents.ScaleChangeEvent(graph.getView().getScale()));
    }//GEN-LAST:event_zoomActualButtonActionPerformed

    private void zoomInButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        graphComponent.zoomIn();
        CVTEvents.getCVTEventBus().post(new CVTEvents.ScaleChangeEvent(graph.getView().getScale()));
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void zoomOutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        graphComponent.zoomOut();
        CVTEvents.getCVTEventBus().post(new CVTEvents.ScaleChangeEvent(graph.getView().getScale()));
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    /**
     * Apply the given layout. The given layout becomes the current layout. The
     * layout is computed in the background.
     *
     * @param layout The layout to apply.
     */
    @NbBundle.Messages({"VisualizationPanel.computingLayout=Computing Layout",
        "# {0} - layout name",
        "VisualizationPanel.layoutFailWithLockedVertices.text={0} layout failed with locked vertices. Unlock some vertices or try a different layout.",
        "# {0} -  layout name",
        "VisualizationPanel.layoutFail.text={0} layout failed. Try a different layout."})
    private void applyLayout(NamedGraphLayout layout) {
        currentLayout = layout;
        layoutButtons.forEach((layoutKey, button)
                -> button.setFont(button.getFont().deriveFont(layoutKey == layout ? Font.BOLD : Font.PLAIN)));

        ModalDialogProgressIndicator progressIndicator = new ModalDialogProgressIndicator(windowAncestor, Bundle.VisualizationPanel_computingLayout());
        progressIndicator.start(Bundle.VisualizationPanel_computingLayout());
        graph.getModel().beginUpdate();
        try {    
            layout.execute(graph.getDefaultParent());
            fitGraph();
        } finally {
            graph.getModel().endUpdate();
            progressIndicator.finish();
        }
    }

    private void clearVizButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_clearVizButtonActionPerformed
        CVTEvents.getCVTEventBus().post(new CVTEvents.UnpinAccountsEvent(pinnedAccountModel.getPinnedAccounts()));
    }//GEN-LAST:event_clearVizButtonActionPerformed

    private void forwardButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_forwardButtonActionPerformed
        handleStateChange(stateManager.advance());
    }//GEN-LAST:event_forwardButtonActionPerformed

    private void backButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_backButtonActionPerformed
        handleStateChange(stateManager.retreat());
    }//GEN-LAST:event_backButtonActionPerformed

    /**
     * Manages the redo and undo actions.
     *
     * @param newState a CommunicationsState
     */
    private void handleStateChange(StateManager.CommunicationsState newState ){
        if(newState == null) {
            return;
        }

        // If the zoom was changed, only change the zoom.
        if(newState.isZoomChange()) {
            graph.getView().setScale(newState.getZoomValue());
            return;
        }

        // This will cause the FilterPane to update its controls
        CVTEvents.getCVTEventBus().post(new CVTEvents.StateChangeEvent(newState));
        setStateButtonsEnabled();

        graph.getModel().beginUpdate();
        graph.resetGraph();

        if(newState.getPinnedList() != null) {
            pinnedAccountModel.pinAccount(newState.getPinnedList());
        } else {
            pinnedAccountModel.clear();
        }

        currentFilter = newState.getCommunicationsFilter();

        rebuildGraph();
        // Updates the display
        graph.getModel().endUpdate();

        fitGraph();

    }

    private void setStateButtonsEnabled() {
        backButton.setEnabled(stateManager.canRetreat());
        forwardButton.setEnabled(stateManager.canAdvance());
    }

     @NbBundle.Messages({
         "VisualizationPanel_snapshot_report_failure=Snapshot report not created. An error occurred during creation."
     })
    private void snapshotButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_snapshotButtonActionPerformed
        try {
            handleSnapshotEvent();
        } catch (NoCurrentCaseException | IOException ex) {
            logger.log(Level.SEVERE, "Unable to create communications snapsot report", ex); //NON-NLS

            Platform.runLater(()
                    -> Notifications.create().owner(notificationsJFXPanel.getScene().getWindow())
                            .text(Bundle.VisualizationPanel_snapshot_report_failure())
                            .showWarning());
        } catch( TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to add report to currenct case", ex); //NON-NLS
        }
    }//GEN-LAST:event_snapshotButtonActionPerformed

    private void fastOrganicLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fastOrganicLayoutButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fastOrganicLayoutButtonActionPerformed

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
        final double heightFactor = size.getHeight() / boundsForCells.getHeight();

        graphComponent.zoom((heightFactor + widthFactor) / 2.0);
    }

    /**
     * Handle the ActionPerformed event from the Snapshot button.
     *
     * @throws NoCurrentCaseException
     * @throws IOException
     */
    @NbBundle.Messages({
        "VisualizationPanel_action_dialogs_title=Communications",
        "VisualizationPanel_module_name=Communications",
        "VisualizationPanel_action_name_text=Snapshot Report",
        "VisualizationPane_fileName_prompt=Enter name for the Communications Snapshot Report:",
        "VisualizationPane_reportName=Communications Snapshot",
        "# {0} -  default name",
        "VisualizationPane_accept_defaultName=Report name was empty. Press OK to accept default report name: {0}",
        "VisualizationPane_blank_report_title=Blank Report Name",
        "# {0} -  report name",
        "VisualizationPane_overrite_exiting=Overwrite existing report?\n{0}"
    })
    private void handleSnapshotEvent() throws NoCurrentCaseException, IOException, TskCoreException {
        Case currentCase = Case.getCurrentCaseThrows();
        Date generationDate = new Date();

        final String defaultReportName = FileUtil.escapeFileName(currentCase.getDisplayName() + " " + new SimpleDateFormat("MMddyyyyHHmmss").format(generationDate)); //NON_NLS

        final JTextField text = new JTextField(50);
        final JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(new JLabel(Bundle.VisualizationPane_fileName_prompt()));
        panel.add(text);

        text.setText(defaultReportName);

        int result = JOptionPane.showConfirmDialog(graphComponent, panel,
                Bundle.VisualizationPanel_action_dialogs_title(), JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String enteredReportName = text.getText();

            if(enteredReportName.trim().isEmpty()){
                result = JOptionPane.showConfirmDialog(graphComponent, Bundle.VisualizationPane_accept_defaultName(defaultReportName), Bundle.VisualizationPane_blank_report_title(), JOptionPane.OK_CANCEL_OPTION);
                if(result != JOptionPane.OK_OPTION) {
                    return;
                }
            }

            String reportName = StringUtils.defaultIfBlank(enteredReportName, defaultReportName);
            Path reportPath = Paths.get(currentCase.getReportDirectory(), reportName);
            if (Files.exists(reportPath)) {
                result = JOptionPane.showConfirmDialog(graphComponent, Bundle.VisualizationPane_overrite_exiting(reportName),
                        Bundle.VisualizationPanel_action_dialogs_title(), JOptionPane.OK_CANCEL_OPTION);

                if (result == JOptionPane.OK_OPTION) {
                    FileUtil.deleteFileDir(reportPath.toFile());
                    createReport(currentCase, reportName);
                }
            } else {
                createReport(currentCase, reportName);
                currentCase.addReport(reportPath.toString(), Bundle.VisualizationPanel_module_name(), reportName);

            }
        }
    }

    /**
     * Create the Snapshot Report.
     *
     * @param currentCase The current case
     * @param reportName User selected name for the report
     *
     * @throws IOException
     */
    @NbBundle.Messages({
        "VisualizationPane_DisplayName=Open Report",
        "VisualizationPane_NoAssociatedEditorMessage=There is no associated editor for reports of this type or the associated application failed to launch.",
        "VisualizationPane_MessageBoxTitle=Open Report Failure",
        "VisualizationPane_NoOpenInEditorSupportMessage=This platform (operating system) does not support opening a file in an editor this way.",
        "VisualizationPane_MissingReportFileMessage=The report file no longer exists.",
        "VisualizationPane_ReportFileOpenPermissionDeniedMessage=Permission to open the report file was denied.",
        "# {0} -  report path",
        "VisualizationPane_Report_Success=Report Successfully create at:\n{0}",
        "VisualizationPane_Report_OK_Button=OK",
        "VisualizationPane_Open_Report=Open Report",})
    private void createReport(Case currentCase, String reportName) throws IOException {

        // Create the report.
        Path reportFolderPath = Paths.get(currentCase.getReportDirectory(), reportName, Bundle.VisualizationPane_reportName()); //NON_NLS
        BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, graph.getView().getScale(), Color.WHITE, true, null);
        Path reportPath = new CommSnapShotReportWriter(currentCase, reportFolderPath, reportName, new Date(), image, currentFilter).writeReport();

        // Report success to the user and offer to open the report.
        String message = Bundle.VisualizationPane_Report_Success(reportPath.toAbsolutePath());
        String[] buttons = {Bundle.VisualizationPane_Open_Report(), Bundle.VisualizationPane_Report_OK_Button()};

        int result = JOptionPane.showOptionDialog(graphComponent, message,
                Bundle.VisualizationPanel_action_dialogs_title(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, buttons, buttons[1]);
        if (result == JOptionPane.YES_NO_OPTION) {
            try {
                Desktop.getDesktop().open(reportPath.toFile());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        Bundle.VisualizationPane_NoAssociatedEditorMessage(),
                        Bundle.VisualizationPane_MessageBoxTitle(),
                        JOptionPane.ERROR_MESSAGE);
            } catch (UnsupportedOperationException ex) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        Bundle.VisualizationPane_NoOpenInEditorSupportMessage(),
                        Bundle.VisualizationPane_MessageBoxTitle(),
                        JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        Bundle.VisualizationPane_MissingReportFileMessage(),
                        Bundle.VisualizationPane_MessageBoxTitle(),
                        JOptionPane.ERROR_MESSAGE);
            } catch (SecurityException ex) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        Bundle.VisualizationPane_ReportFileOpenPermissionDeniedMessage(),
                        Bundle.VisualizationPane_MessageBoxTitle(),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton backButton;
    private JPanel borderLayoutPanel;
    private JButton clearVizButton;
    private JButton fastOrganicLayoutButton;
    private JButton fitZoomButton;
    private JButton forwardButton;
    private JToolBar.Separator jSeparator1;
    private JToolBar.Separator jSeparator2;
    private JToolBar.Separator jSeparator3;
    private JTextPane jTextPane1;
    private JFXPanel notificationsJFXPanel;
    private JPanel placeHolderPanel;
    private JButton snapshotButton;
    private JToolBar toolbar;
    private JButton zoomActualButton;
    private JButton zoomInButton;
    private JLabel zoomLabel;
    private JButton zoomOutButton;
    private JLabel zoomPercentLabel;
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
            if (selectionCells.length > 0) {
                mxICell[] selectedCells = Arrays.asList(selectionCells).toArray(new mxCell[selectionCells.length]);
                HashSet<AccountDeviceInstance> selectedNodes = new HashSet<>();
                HashSet<SelectionInfo.GraphEdge> selectedEdges = new HashSet<>();
                for (mxICell cell : selectedCells) {
                    if (cell.isEdge()) {
                        mxICell source = (mxICell) graph.getModel().getTerminal(cell, true);
                        mxICell target = (mxICell) graph.getModel().getTerminal(cell, false);

                        selectedEdges.add(new SelectionInfo.GraphEdge(((AccountDeviceInstanceKey) source.getValue()).getAccountDeviceInstance(),
                            ((AccountDeviceInstanceKey) target.getValue()).getAccountDeviceInstance()));

                    } else if (cell.isVertex()) {
                        selectedNodes.add(((AccountDeviceInstanceKey) cell.getValue()).getAccountDeviceInstance());
                    }
                }

                relationshipBrowser.setSelectionInfo(new SelectionInfo(selectedNodes, selectedEdges, currentFilter));
            } else {
                relationshipBrowser.setSelectionInfo(new SelectionInfo(new HashSet<>(), new HashSet<>(), currentFilter));
            }
        }
    }

    /**
     * Extend mxIGraphLayout with a getDisplayName method,
     */
    private interface NamedGraphLayout extends mxIGraphLayout {

        String getDisplayName();
    }

    /**
     * Extension of mxFastOrganicLayout that ignores locked vertices.
     */
    final private class FastOrganicLayoutImpl extends mxFastOrganicLayout implements NamedGraphLayout {

        FastOrganicLayoutImpl(mxGraph graph) {
            super(graph);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                   || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x, y are standard coordinate names
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }

        @Override
        public String getDisplayName() {
            return "Fast Organic";
        }
    }

    /**
     * Extension of mxCircleLayout that ignores locked vertices.
     */
    final private class CircleLayoutImpl extends mxCircleLayout implements NamedGraphLayout {

        CircleLayoutImpl(mxGraph graph) {
            super(graph);
            setResetEdges(true);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                   || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x, y are standard coordinate names
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }

        @Override
        public String getDisplayName() {
            return "Circle";
        }
    }

    /**
     * Extension of mxOrganicLayout that ignores locked vertices.
     */
    final private class OrganicLayoutImpl extends mxOrganicLayout implements NamedGraphLayout {

        OrganicLayoutImpl(mxGraph graph) {
            super(graph);
            setResetEdges(true);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                   || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x, y are standard coordinate names
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }

        @Override
        public String getDisplayName() {
            return "Organic";
        }
    }

    /**
     * Extension of mxHierarchicalLayout that ignores locked vertices.
     */
    final private class HierarchicalLayoutImpl extends mxHierarchicalLayout implements NamedGraphLayout {

        HierarchicalLayoutImpl(mxGraph graph) {
            super(graph);
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return super.isVertexIgnored(vertex)
                   || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double x, double y) { //NOPMD x, y are standard coordinate names
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return super.setVertexLocation(vertex, x, y);
            }
        }

        @Override
        public String getDisplayName() {
            return "Hierarchical";
        }
    }

    /**
     * Listener that closses the given ModalDialogProgressIndicator and cancels
     * the future.
     */
    private class CancelationListener implements ActionListener {

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
     * Mouse Adapter for the graphComponent. Handles wheel zooming and context
     * menus.
     */
    private class GraphMouseListener extends MouseAdapter {

        /**
         * Translate mouse wheel events into zooming.
         *
         * @param event The MouseWheelEvent
         */
        @Override
        public void mouseWheelMoved(final MouseWheelEvent event) {
            super.mouseWheelMoved(event);
            if (event.getPreciseWheelRotation() < 0) {
                graphComponent.zoomIn();
            } else if (event.getPreciseWheelRotation() > 0) {
                graphComponent.zoomOut();
            }
            
            CVTEvents.getCVTEventBus().post(new CVTEvents.ScaleChangeEvent(graph.getView().getScale()));
        }

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
                    if (pinnedAccountModel.isAccountPinned(adiKey.getAccountDeviceInstance())) {
                        jPopupMenu.add(UnpinAccountsAction.getInstance().getPopupPresenter());
                    } else {
                        jPopupMenu.add(PinAccountsAction.getInstance().getPopupPresenter());
                        jPopupMenu.add(ResetAndPinAccountsAction.getInstance().getPopupPresenter());
                    }
                    jPopupMenu.show(graphComponent.getGraphControl(), event.getX(), event.getY());
                }
            }
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