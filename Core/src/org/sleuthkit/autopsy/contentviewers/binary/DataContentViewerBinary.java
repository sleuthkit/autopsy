/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.binary;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.exbin.bined.CodeType;
import org.exbin.bined.EditMode;
import org.exbin.bined.SelectionRange;
import org.exbin.bined.highlight.swing.HighlightNonAsciiCodeAreaPainter;
import org.exbin.bined.swing.basic.CodeArea;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.contentviewers.utils.ViewerPriority;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.corecomponents.DataContentViewerUtility;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.DataConversion;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Binary view of file contents.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 1)
public class DataContentViewerBinary extends javax.swing.JPanel implements DataContentViewer {

    private Content dataSource;
    private CodeArea codeArea = new CodeArea();
    private ValuesPanel valuesPanel;
    private JScrollPane valuesPanelScrollPane;

    private Mode mode = Mode.NO_DATA;

    private final AbstractAction cycleCodeTypesAction;
    private final JRadioButtonMenuItem binaryCodeTypeAction;
    private final JRadioButtonMenuItem octalCodeTypeAction;
    private final JRadioButtonMenuItem decimalCodeTypeAction;
    private final JRadioButtonMenuItem hexadecimalCodeTypeAction;
    private final ButtonGroup codeTypeButtonGroup;
    private DropDownButton codeTypeDropDown;

    private static final Logger logger = Logger.getLogger(DataContentViewerBinary.class.getName());

    /**
     * Creates new form DataContentViewerBinary
     */
    public DataContentViewerBinary() {
        codeTypeButtonGroup = new ButtonGroup();
        binaryCodeTypeAction = new JRadioButtonMenuItem(new AbstractAction(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.codeType.binary")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.setCodeType(CodeType.BINARY);
                updateCycleButtonState();
            }
        });
        codeTypeButtonGroup.add(binaryCodeTypeAction);
        octalCodeTypeAction = new JRadioButtonMenuItem(new AbstractAction(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.codeType.octal")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.setCodeType(CodeType.OCTAL);
                updateCycleButtonState();
            }
        });
        codeTypeButtonGroup.add(octalCodeTypeAction);
        decimalCodeTypeAction = new JRadioButtonMenuItem(new AbstractAction(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.codeType.decimal")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.setCodeType(CodeType.DECIMAL);
                updateCycleButtonState();
            }
        });
        codeTypeButtonGroup.add(decimalCodeTypeAction);
        hexadecimalCodeTypeAction = new JRadioButtonMenuItem(new AbstractAction(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.codeType.hexadecimal")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.setCodeType(CodeType.HEXADECIMAL);
                updateCycleButtonState();
            }
        });
        codeTypeButtonGroup.add(hexadecimalCodeTypeAction);
        cycleCodeTypesAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int codeTypePos = codeArea.getCodeType().ordinal();
                CodeType[] values = CodeType.values();
                CodeType next = codeTypePos + 1 >= values.length ? values[0] : values[codeTypePos + 1];
                codeArea.setCodeType(next);
                updateCycleButtonState();
            }
        };

        initComponents();
        customizeComponents();
        this.resetComponent();
    }

    private void customizeComponents() {
        codeArea.setEditMode(EditMode.READ_ONLY);
        codeArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        codeArea.setPainter(new HighlightNonAsciiCodeAreaPainter(codeArea) {
            @Override
            public void paintComponent(Graphics g) {
                try {
                    super.paintComponent(g);
                } catch (ContentBinaryData.TskReadException ex) {
                    String message = NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.textArea.errorText", ex.getPosition(), ex.getPosition() + ex.getLength());
                    if (mode == Mode.ERROR) {
                        textArea.append("\n" + message);
                    } else {
                        textArea.setText(message);
                        switchMode(Mode.ERROR);
                    }
                } catch (Exception ex) {
                    String message = NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.textArea.exceptionText", ex.getMessage());
                    if (mode == Mode.ERROR) {
                        textArea.append("\n" + message);
                    } else {
                        textArea.setText(message);
                        switchMode(Mode.ERROR);
                    }
                }
            }
        });

        valuesPanel = new ValuesPanel();
        valuesPanel.setCodeArea(codeArea);
        valuesPanel.enableUpdate();
        valuesPanelScrollPane = new JScrollPane(valuesPanel);
        valuesPanelScrollPane.setBorder(null);

        cycleCodeTypesAction.putValue(Action.SHORT_DESCRIPTION, NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.cycleCodeTypesAction.text"));
        JPopupMenu cycleCodeTypesPopupMenu = new JPopupMenu();
        cycleCodeTypesPopupMenu.add(binaryCodeTypeAction);
        cycleCodeTypesPopupMenu.add(octalCodeTypeAction);
        cycleCodeTypesPopupMenu.add(decimalCodeTypeAction);
        cycleCodeTypesPopupMenu.add(hexadecimalCodeTypeAction);
        codeTypeDropDown = new DropDownButton(cycleCodeTypesAction, cycleCodeTypesPopupMenu);
        updateCycleButtonState();
        controlToolBar.add(codeTypeDropDown, 0);

        codeColorizationToggleButton.setSelected(((HighlightNonAsciiCodeAreaPainter) codeArea.getPainter()).isNonAsciiHighlightingEnabled());

        ActionListener textAreaActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(copyMenuItem)) {
                    textArea.copy();
                } else if (jmi.equals(selectAllMenuItem)) {
                    textArea.selectAll();
                }
            }
        };
        copyMenuItem.addActionListener(textAreaActionListener);
        selectAllMenuItem.addActionListener(textAreaActionListener);

        ActionListener codeAreaActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(codeAreaCopyMenuItem)) {
                    codeArea.copy();
                } else if (jmi.equals(codeAreaCopyTextMenuItem)) {
                    SelectionRange selectionRange = codeArea.getSelection();
                    long selectionLength = selectionRange.getLength();
                    if (!selectionRange.isEmpty() && selectionLength < Integer.MAX_VALUE) {
                        byte[] selectionData = new byte[(int) selectionLength];
                        codeArea.getContentData().copyToArray(selectionRange.getStart(), selectionData, 0, (int) selectionLength);
                        String clipboardContent = DataConversion.byteArrayToHex(selectionData, (int) selectionLength, 0);
                        StringSelection selection = new StringSelection(clipboardContent);
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(selection, selection);
                    }
                } else if (jmi.equals(codeAreaGoToMenuItem)) {
                    goToButtonActionPerformed(e);
                } else if (jmi.equals(codeAreaSelectAllMenuItem)) {
                    codeArea.selectAll();
                }
            }
        };
        codeAreaCopyMenuItem.addActionListener(codeAreaActionListener);
        codeAreaCopyTextMenuItem.addActionListener(codeAreaActionListener);
        codeAreaSelectAllMenuItem.addActionListener(codeAreaActionListener);
        codeAreaGoToMenuItem.addActionListener(codeAreaActionListener);
        codeArea.setComponentPopupMenu(codeAreaPopupMenu);

        String goToPositionActionId = "go-to-position";
        ActionMap codeAreaActionMap = codeArea.getActionMap();
        codeAreaActionMap.put(goToPositionActionId, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goToButtonActionPerformed(e);
            }
        });
        InputMap codeAreaInputMap = codeArea.getInputMap();
        codeAreaInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK), goToPositionActionId);
        
        textArea.setText(NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.textArea.noDataText"));
    }
    
    private void switchMode(Mode mode) {
        if (this.mode != mode) {
            switch (this.mode) {
                case NO_DATA: {
                    remove(textAreaScrollPane);
                    break;
                }
                case DATA: {
                    remove(codeArea);
                    remove(valuesPanelScrollPane);
                    remove(toolBarPanel);
                    break;
                }
                case ERROR: {
                    remove(toolBarPanel);
                    remove(textAreaScrollPane);
                    break;
                }
            }
            this.mode = mode;
            switch (mode) {
                case NO_DATA: {
                    textArea.setText(NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.textArea.noDataText"));
                    add(textAreaScrollPane, BorderLayout.CENTER);
                    break;
                }
                case DATA: {
                    add(codeArea, BorderLayout.CENTER);
                    add(valuesPanelScrollPane, BorderLayout.EAST);
                    add(toolBarPanel, BorderLayout.NORTH);
                    break;
                }
                case ERROR: {
                    add(toolBarPanel, BorderLayout.NORTH);
                    add(textAreaScrollPane, BorderLayout.CENTER);
                    break;
                }
            }
            invalidate();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        textAreaPopupMenu = new javax.swing.JPopupMenu();
        copyMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        codeAreaPopupMenu = new javax.swing.JPopupMenu();
        codeAreaCopyMenuItem = new javax.swing.JMenuItem();
        codeAreaCopyTextMenuItem = new javax.swing.JMenuItem();
        codeAreaSelectAllMenuItem = new javax.swing.JMenuItem();
        codeAreaGoToMenuItem = new javax.swing.JMenuItem();
        toolBarPanel = new javax.swing.JPanel();
        controlToolBar = new javax.swing.JToolBar();
        refreshButton = new javax.swing.JButton();
        codeColorizationToggleButton = new javax.swing.JToggleButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        goToButton = new javax.swing.JButton();
        launchHxDButton = new javax.swing.JButton();
        textAreaScrollPane = new javax.swing.JScrollPane();
        textArea = new javax.swing.JTextArea();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.copyMenuItem.text")); // NOI18N
        textAreaPopupMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.selectAllMenuItem.text")); // NOI18N
        textAreaPopupMenu.add(selectAllMenuItem);

        codeAreaCopyMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.codeAreaCopyMenuItem.text")); // NOI18N
        codeAreaPopupMenu.add(codeAreaCopyMenuItem);

        codeAreaCopyTextMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.codeAreaCopyTextMenuItem.text")); // NOI18N
        codeAreaPopupMenu.add(codeAreaCopyTextMenuItem);

        codeAreaSelectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.codeAreaSelectAllMenuItem.text")); // NOI18N
        codeAreaPopupMenu.add(codeAreaSelectAllMenuItem);

        codeAreaGoToMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        codeAreaGoToMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.codeAreaGoToMenuItem.text")); // NOI18N
        codeAreaPopupMenu.add(codeAreaGoToMenuItem);

        controlToolBar.setBorderPainted(false);
        controlToolBar.setFocusable(false);

        refreshButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/binary/arrow_refresh.png"))); // NOI18N
        refreshButton.setToolTipText(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.refreshButton.toolTipText"));
        refreshButton.setFocusable(false);
        refreshButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        refreshButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });
        controlToolBar.add(refreshButton);

        codeColorizationToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/binary/color_swatch.png"))); // NOI18N
        codeColorizationToggleButton.setToolTipText(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.codeColorizationToggleButton.toolTipText"));
        codeColorizationToggleButton.setFocusable(false);
        codeColorizationToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        codeColorizationToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        codeColorizationToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeColorizationToggleButtonActionPerformed(evt);
            }
        });
        controlToolBar.add(codeColorizationToggleButton);
        controlToolBar.add(jSeparator1);

        goToButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/binary/bullet_go.png"))); // NOI18N
        goToButton.setToolTipText(NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.goToButton.toolTipText"));
        goToButton.setFocusable(false);
        goToButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        goToButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        goToButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goToButtonActionPerformed(evt);
            }
        });
        controlToolBar.add(goToButton);

        launchHxDButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerBinary.class, "DataContentViewerBinary.launchHxDButton.text")); // NOI18N
        launchHxDButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                launchHxDButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout toolBarPanelLayout = new javax.swing.GroupLayout(toolBarPanel);
        toolBarPanel.setLayout(toolBarPanelLayout);
        toolBarPanelLayout.setHorizontalGroup(
            toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, toolBarPanelLayout.createSequentialGroup()
                .addComponent(controlToolBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(launchHxDButton)
                .addContainerGap())
        );
        toolBarPanelLayout.setVerticalGroup(
            toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(launchHxDButton)
            .addComponent(controlToolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        launchHxDButton.setEnabled(PlatformUtil.isWindowsOS());

        setPreferredSize(new java.awt.Dimension(100, 58));
        setLayout(new java.awt.BorderLayout());

        textAreaScrollPane.setPreferredSize(new java.awt.Dimension(300, 33));

        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        textArea.setTabSize(0);
        textArea.setComponentPopupMenu(textAreaPopupMenu);
        textAreaScrollPane.setViewportView(textArea);

        add(textAreaScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void updateCycleButtonState() {
        CodeType codeType = codeArea.getCodeType();
        codeTypeDropDown.setActionText(codeType.name().substring(0, 3));
        switch (codeType) {
            case BINARY: {
                if (!binaryCodeTypeAction.isSelected()) {
                    binaryCodeTypeAction.setSelected(true);
                }
                break;
            }
            case OCTAL: {
                if (!octalCodeTypeAction.isSelected()) {
                    octalCodeTypeAction.setSelected(true);
                }
                break;
            }
            case DECIMAL: {
                if (!decimalCodeTypeAction.isSelected()) {
                    decimalCodeTypeAction.setSelected(true);
                }
                break;
            }
            case HEXADECIMAL: {
                if (!hexadecimalCodeTypeAction.isSelected()) {
                    hexadecimalCodeTypeAction.setSelected(true);
                }
                break;
            }
        }
    }

    @NbBundle.Messages({"DataContentViewerBinary.launchError=Unable to launch HxD Editor. "
        + "Please specify the HxD install location in Tools -> Options -> External Viewer",
        "DataContentViewerBinary.copyingFile=Copying file to open in HxD..."})
    private void launchHxDButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_launchHxDButtonActionPerformed
        new BackgroundFileCopyTask().execute();
    }//GEN-LAST:event_launchHxDButtonActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        if (mode != Mode.NO_DATA) {
            ((ContentBinaryData) codeArea.getContentData()).clearCache();
            if (mode == Mode.ERROR) {
                switchMode(Mode.DATA);
            }
            codeArea.repaint();
        }
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void goToButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goToButtonActionPerformed
        JDialog dialog = new JDialog(WindowManager.getDefault().getMainWindow(), true);
        dialog.setTitle(NbBundle.getMessage(this.getClass(), "GoToBinaryPanel.title"));
        GoToBinaryPanel goToPanel = new GoToBinaryPanel();
        goToPanel.setCursorPosition(codeArea.getDataPosition());
        goToPanel.setMaxPosition(codeArea.getDataSize());
        DefaultControlPanel controlPanel = new DefaultControlPanel();
        dialog.getContentPane().add(controlPanel, BorderLayout.SOUTH);
        dialog.getContentPane().add(goToPanel, BorderLayout.CENTER);
        controlPanel.setHandler((actionType) -> {
            if (actionType == DefaultControlPanel.ControlActionType.OK) {
                goToPanel.acceptInput();
                codeArea.setCaretPosition(goToPanel.getTargetPosition());
                codeArea.revealCursor();
            }

            dialog.setVisible(false);
            dialog.dispose();
        });

        String cancelName = "esc-cancel";
        String enterOk = "enter-ok";
        InputMap inputMap = dialog.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelName);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), enterOk);
        ActionMap actionMap = dialog.getRootPane().getActionMap();

        actionMap.put(cancelName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        actionMap.put(enterOk, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goToPanel.acceptInput();
                controlPanel.performOk();
            }
        });

        SwingUtilities.invokeLater(goToPanel::initFocus);
        Dimension preferredSize = goToPanel.getPreferredSize();
        dialog.getContentPane().setPreferredSize(new Dimension(preferredSize.width, preferredSize.height + controlPanel.getPreferredSize().height));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }//GEN-LAST:event_goToButtonActionPerformed

    private void codeColorizationToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_codeColorizationToggleButtonActionPerformed
        ((HighlightNonAsciiCodeAreaPainter) codeArea.getPainter()).setNonAsciiHighlightingEnabled(codeColorizationToggleButton.isSelected());
        codeArea.repaint();
    }//GEN-LAST:event_codeColorizationToggleButtonActionPerformed

    /**
     * Performs the file copying and process launching in a SwingWorker so that
     * the UI is not blocked when opening large files.
     */
    private class BackgroundFileCopyTask extends SwingWorker<Void, Void> {

        private boolean wasCancelled = false;

        @Override
        public Void doInBackground() throws InterruptedException {
            ProgressHandle progress = ProgressHandle.createHandle(Bundle.DataContentViewerBinary_copyingFile(), () -> {
                //Cancel the swing worker (which will interrupt the ContentUtils call below)
                this.cancel(true);
                wasCancelled = true;
                return true;
            });

            try {
                File HxDExecutable = new File(UserPreferences.getExternalHexEditorPath());
                if (!HxDExecutable.exists() || !HxDExecutable.canExecute()) {
                    JOptionPane.showMessageDialog(null, Bundle.DataContentViewerBinary_launchError());
                    return null;
                }

                String tempDirectory = Case.getCurrentCaseThrows().getTempDirectory();
                File tempFile = Paths.get(tempDirectory,
                        FileUtil.escapeFileName(dataSource.getId() + dataSource.getName())).toFile();

                progress.start(100);
                ContentUtils.writeToFile(dataSource, tempFile, progress, this, true);

                if (wasCancelled) {
                    tempFile.delete();
                    progress.finish();
                    return null;
                }

                try {
                    ProcessBuilder launchHxDExecutable = new ProcessBuilder();
                    launchHxDExecutable.command(String.format("\"%s\" \"%s\"",
                            HxDExecutable.getAbsolutePath(),
                            tempFile.getAbsolutePath()));
                    launchHxDExecutable.start();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Unsuccessful attempt to launch HxD", ex);
                    JOptionPane.showMessageDialog(null, Bundle.DataContentViewerBinary_launchError());
                    tempFile.delete();
                }
            } catch (NoCurrentCaseException | IOException ex) {
                logger.log(Level.SEVERE, "Unable to copy file into temp directory", ex);
                JOptionPane.showMessageDialog(null, Bundle.DataContentViewerBinary_launchError());
            }

            progress.finish();
            return null;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem codeAreaCopyMenuItem;
    private javax.swing.JMenuItem codeAreaCopyTextMenuItem;
    private javax.swing.JMenuItem codeAreaGoToMenuItem;
    private javax.swing.JPopupMenu codeAreaPopupMenu;
    private javax.swing.JMenuItem codeAreaSelectAllMenuItem;
    private javax.swing.JToggleButton codeColorizationToggleButton;
    private javax.swing.JToolBar controlToolBar;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JButton goToButton;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JButton launchHxDButton;
    private javax.swing.JButton refreshButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JTextArea textArea;
    private javax.swing.JPopupMenu textAreaPopupMenu;
    private javax.swing.JScrollPane textAreaScrollPane;
    private javax.swing.JPanel toolBarPanel;
    // End of variables declaration//GEN-END:variables

    @Messages({
        "DataContentViewerBinary_loading_text=Loading binary from file..."
    })

    @Override
    public void setNode(Node selectedNode) {
        resetComponent();

        if ((selectedNode == null)) {
            return;
        }

        Content content = DataContentViewerUtility.getDefaultContent(selectedNode);
        if (content == null) {
            switchMode(Mode.NO_DATA);
            return;
        }

        dataSource = content;
        codeArea.setContentData(new ContentBinaryData(dataSource));
        switchMode(Mode.DATA);
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerBinary.toolTip");
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerBinary();
    }

    @Override
    public void resetComponent() {
        // clear / reset the fields
        this.dataSource = null;
        codeArea.setContentData(null);
        switchMode(Mode.NO_DATA);
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }
        Content content = DataContentViewerUtility.getDefaultContent(node);
        return content != null && !(content instanceof BlackboardArtifact) && content.getSize() > 0;
    }

    @Override
    public int isPreferred(Node node) {
        return ViewerPriority.viewerPriority.LevelOne.getFlag();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    private enum Mode {
        NO_DATA,
        DATA,
        ERROR
    }
}
