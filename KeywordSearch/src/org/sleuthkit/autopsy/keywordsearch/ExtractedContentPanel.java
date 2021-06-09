/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.ComponentOrientation;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SizeRequirements;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.HTMLFactory;
import javax.swing.text.html.InlineView;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.StyleSheet;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.guiutils.WrapLayout;

/**
 * Panel displays HTML content sent to ExtractedContentViewer, and provides a
 * combo-box to select between multiple sources.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class ExtractedContentPanel extends javax.swing.JPanel implements ResizableTextPanel {

    private static final Logger logger = Logger.getLogger(ExtractedContentPanel.class.getName());
    
    // set font as close as possible to default
    private static final Font DEFAULT_FONT = UIManager.getDefaults().getFont("Label.font");
    
    private static final long serialVersionUID = 1L;
    private String contentName;
    private int curSize;
    
    private final StyleSheet styleSheet;
    private final HTMLEditorKit editorKit;
    private String lastKnownAnchor = null;

    ExtractedContentPanel() {
        initComponents();
        additionalInit();
        hitPreviousButton.setEnabled(false);
        hitNextButton.setEnabled(false);

        /*
         * This appears to be an attempt to modify the wrapping behavior of the
         * extractedTextPane taken form this website:
         * http://java-sl.com/tip_html_letter_wrap.html.
         */
        editorKit = new HTMLEditorKit() {
            private static final long serialVersionUID = 1L;

            @Override
            public ViewFactory getViewFactory() {

                return new HTMLFactory() {
                    @Override
                    public View create(Element e) {
                        View v = super.create(e);
                        if (v instanceof InlineView) {
                            return new InlineView(e) {
                                @Override
                                public int getBreakWeight(int axis, float pos, float len) {
                                    return GoodBreakWeight;
                                }

                                @Override
                                public View breakView(int axis, int p0, float pos, float len) {
                                    if (axis == View.X_AXIS) {
                                        checkPainter();
                                        int p1 = getGlyphPainter().getBoundedPosition(this, p0, pos, len);
                                        if (p0 == getStartOffset() && p1 == getEndOffset()) {
                                            return this;
                                        }
                                        return createFragment(p0, p1);
                                    }
                                    return this;
                                }
                            };
                        } else if (v instanceof ParagraphView) {
                            return new ParagraphView(e) {
                                @Override
                                protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
                                    SizeRequirements requirements = r;
                                    if (requirements == null) {
                                        requirements = new SizeRequirements();
                                    }
                                    float pref = layoutPool.getPreferredSpan(axis);
                                    float min = layoutPool.getMinimumSpan(axis);
                                    // Don't include insets, Box.getXXXSpan will include them. 
                                    requirements.minimum = (int) min;
                                    requirements.preferred = Math.max(requirements.minimum, (int) pref);
                                    requirements.maximum = Integer.MAX_VALUE;
                                    requirements.alignment = 0.5f;
                                    return requirements;
                                }
                            };
                        }
                        return v;
                    }
                };
            }
        };
        // set new style sheet to clear default styles
        styleSheet = editorKit.getStyleSheet();
        
        sourceComboBox.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                refreshCurrentMarkup();
            }
        });
        extractedTextPane.setComponentPopupMenu(rightClickMenu);
        
        copyMenuItem.addActionListener(actionEvent -> extractedTextPane.copy());
        selectAllMenuItem.addActionListener(actionEvent -> extractedTextPane.selectAll());
        
        // TextZoomPanel could not be directly instantiated in Swing WYSIWYG editor 
        // (because it was package private, couldn't use constructor, etc.)
        // so it was identified as a JPanel for the WYSIWYG.  This function is called for
        // initial setup so the font size of this panel as well as the font size indicated
        // in the TextZoomPanel are correct
        SwingUtilities.invokeLater(() -> {
            if (zoomPanel instanceof TextZoomPanel)
                ((TextZoomPanel) this.zoomPanel).resetSize();
        });
        
        setSources("", new ArrayList<>());
    }
    
    
    private void setStyleSheetSize(StyleSheet styleSheet, int size) {
        styleSheet.addRule(
                "body { font-family:\"" + DEFAULT_FONT.getFamily() + "\"; font-size:" + size + "pt; } " +
                "pre { font-family:\"" + DEFAULT_FONT.getFamily() + "\"; font-size:" + size + "pt; } "
        );
    }
    
    
    @Override
    public int getTextSize() {
        return curSize;
    }

    @Override
    public void setTextSize(int newSize) {
        curSize = newSize;

        String curText = extractedTextPane.getText();
        
        setStyleSheetSize(styleSheet, curSize);
        
        editorKit.setStyleSheet(styleSheet);
        extractedTextPane.setEditorKit(editorKit);

        extractedTextPane.setText(curText);
        if (lastKnownAnchor != null)
            scrollToAnchor(lastKnownAnchor);
    }
    
    
    private void additionalInit() {
        // use wrap layout for better component wrapping
        WrapLayout layout = new WrapLayout(0,5);
        layout.setOppositeAligned(Arrays.asList(textSourcePanel));
        controlPanel.setLayout(layout);
    }
    

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rightClickMenu = new javax.swing.JPopupMenu();
        copyMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        controlPanel = new javax.swing.JPanel();
        javax.swing.JPanel pagePanel = new javax.swing.JPanel();
        pagesLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerSmall1 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        pageCurLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerSmall2 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        pageOfLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerSmall3 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        pageTotalLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerSmall4 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        pageButtonsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerMed1 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        pagePreviousButton = new javax.swing.JButton();
        pageNextButton = new javax.swing.JButton();
        javax.swing.Box.Filler fillerSmall6 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        jSeparator2 = new javax.swing.JSeparator();
        javax.swing.JPanel matchesPanel = new javax.swing.JPanel();
        hitLabel = new javax.swing.JLabel();
        hitCountLabel = new javax.swing.JLabel();
        hitOfLabel = new javax.swing.JLabel();
        hitTotalLabel = new javax.swing.JLabel();
        hitButtonsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerMed2 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        hitPreviousButton = new javax.swing.JButton();
        hitNextButton = new javax.swing.JButton();
        javax.swing.Box.Filler fillerSmall11 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        jSeparator3 = new javax.swing.JSeparator();
        javax.swing.JPanel zoomPanelWrapper = new javax.swing.JPanel();
        zoomPanel = new TextZoomPanel(this);
        javax.swing.Box.Filler fillerSmall14 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        jSeparator4 = new javax.swing.JSeparator();
        textSourcePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        javax.swing.Box.Filler fillerSmall12 = new javax.swing.Box.Filler(new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 0), new java.awt.Dimension(5, 32767));
        sourceComboBox = new javax.swing.JComboBox<>();
        extractedScrollPane = new javax.swing.JScrollPane();
        extractedTextPane = new javax.swing.JTextPane();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setMinimumSize(new java.awt.Dimension(250, 0));
        setPreferredSize(new java.awt.Dimension(250, 58));
        setLayout(new java.awt.BorderLayout());

        controlPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        pagePanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        pagesLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagesLabel.text")); // NOI18N
        pagePanel.add(pagesLabel);
        pagePanel.add(fillerSmall1);

        pageCurLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pageCurLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageCurLabel.text")); // NOI18N
        pagePanel.add(pageCurLabel);
        pagePanel.add(fillerSmall2);

        pageOfLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageOfLabel.text")); // NOI18N
        pagePanel.add(pageOfLabel);
        pagePanel.add(fillerSmall3);

        pageTotalLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pageTotalLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageTotalLabel.text")); // NOI18N
        pagePanel.add(pageTotalLabel);
        pagePanel.add(fillerSmall4);

        pageButtonsLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageButtonsLabel.text")); // NOI18N
        pagePanel.add(pageButtonsLabel);
        pagePanel.add(fillerMed1);

        pagePreviousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back.png"))); // NOI18N
        pagePreviousButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagePreviousButton.text")); // NOI18N
        pagePreviousButton.setActionCommand(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagePreviousButton.actionCommand")); // NOI18N
        pagePreviousButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pagePreviousButton.setBorderPainted(false);
        pagePreviousButton.setContentAreaFilled(false);
        pagePreviousButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_disabled.png"))); // NOI18N
        pagePreviousButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        pagePanel.add(pagePreviousButton);

        pageNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward.png"))); // NOI18N
        pageNextButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageNextButton.text")); // NOI18N
        pageNextButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pageNextButton.setBorderPainted(false);
        pageNextButton.setContentAreaFilled(false);
        pageNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_disabled.png"))); // NOI18N
        pageNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        pageNextButton.setPreferredSize(new java.awt.Dimension(23, 23));
        pagePanel.add(pageNextButton);
        pagePanel.add(fillerSmall6);

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator2.setMaximumSize(new java.awt.Dimension(2, 25));
        jSeparator2.setMinimumSize(new java.awt.Dimension(2, 25));
        jSeparator2.setPreferredSize(new java.awt.Dimension(2, 25));
        pagePanel.add(jSeparator2);

        controlPanel.add(pagePanel);

        matchesPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        hitLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitLabel.text")); // NOI18N
        hitLabel.setToolTipText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitLabel.toolTipText")); // NOI18N
        matchesPanel.add(hitLabel);

        hitCountLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hitCountLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitCountLabel.text")); // NOI18N
        hitCountLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        hitCountLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        hitCountLabel.setPreferredSize(new java.awt.Dimension(18, 14));
        matchesPanel.add(hitCountLabel);

        hitOfLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitOfLabel.text")); // NOI18N
        matchesPanel.add(hitOfLabel);

        hitTotalLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hitTotalLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitTotalLabel.text")); // NOI18N
        hitTotalLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        hitTotalLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        hitTotalLabel.setPreferredSize(new java.awt.Dimension(18, 14));
        matchesPanel.add(hitTotalLabel);

        hitButtonsLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitButtonsLabel.text")); // NOI18N
        matchesPanel.add(hitButtonsLabel);
        matchesPanel.add(fillerMed2);

        hitPreviousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back.png"))); // NOI18N
        hitPreviousButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitPreviousButton.text")); // NOI18N
        hitPreviousButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hitPreviousButton.setBorderPainted(false);
        hitPreviousButton.setContentAreaFilled(false);
        hitPreviousButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_disabled.png"))); // NOI18N
        hitPreviousButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        hitPreviousButton.setPreferredSize(new java.awt.Dimension(23, 23));
        hitPreviousButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_hover.png"))); // NOI18N
        matchesPanel.add(hitPreviousButton);

        hitNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward.png"))); // NOI18N
        hitNextButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitNextButton.text")); // NOI18N
        hitNextButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hitNextButton.setBorderPainted(false);
        hitNextButton.setContentAreaFilled(false);
        hitNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_disabled.png"))); // NOI18N
        hitNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        hitNextButton.setPreferredSize(new java.awt.Dimension(23, 23));
        hitNextButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_hover.png"))); // NOI18N
        matchesPanel.add(hitNextButton);
        matchesPanel.add(fillerSmall11);

        jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator3.setMaximumSize(new java.awt.Dimension(2, 25));
        jSeparator3.setMinimumSize(new java.awt.Dimension(2, 25));
        jSeparator3.setName(""); // NOI18N
        jSeparator3.setPreferredSize(new java.awt.Dimension(2, 25));
        matchesPanel.add(jSeparator3);

        controlPanel.add(matchesPanel);

        zoomPanelWrapper.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        zoomPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        zoomPanelWrapper.add(zoomPanel);
        zoomPanel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.AccessibleContext.accessibleName")); // NOI18N

        zoomPanelWrapper.add(fillerSmall14);

        jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator4.setMaximumSize(new java.awt.Dimension(2, 25));
        jSeparator4.setMinimumSize(new java.awt.Dimension(2, 25));
        jSeparator4.setName(""); // NOI18N
        jSeparator4.setPreferredSize(new java.awt.Dimension(2, 25));
        zoomPanelWrapper.add(jSeparator4);

        controlPanel.add(zoomPanelWrapper);

        textSourcePanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));

        jLabel1.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.jLabel1.text")); // NOI18N
        textSourcePanel.add(jLabel1);
        textSourcePanel.add(fillerSmall12);

        sourceComboBox.setModel(new javax.swing.DefaultComboBoxModel<org.sleuthkit.autopsy.keywordsearch.IndexedText>());
        sourceComboBox.setMaximumSize(new java.awt.Dimension(150, 32767));
        sourceComboBox.setMinimumSize(new java.awt.Dimension(150, 25));
        sourceComboBox.setPreferredSize(new java.awt.Dimension(150, 25));
        textSourcePanel.add(sourceComboBox);

        controlPanel.add(textSourcePanel);

        add(controlPanel, java.awt.BorderLayout.NORTH);

        extractedScrollPane.setBackground(new java.awt.Color(255, 255, 255));
        extractedScrollPane.setPreferredSize(new java.awt.Dimension(640, 29));

        extractedTextPane.setEditable(false);
        extractedTextPane.setAutoscrolls(false);
        extractedTextPane.setInheritsPopupMenu(true);
        extractedTextPane.setMaximumSize(new java.awt.Dimension(2000, 2000));
        extractedTextPane.setPreferredSize(new java.awt.Dimension(600, 29));
        extractedScrollPane.setViewportView(extractedTextPane);

        add(extractedScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlPanel;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JScrollPane extractedScrollPane;
    private javax.swing.JTextPane extractedTextPane;
    private javax.swing.JLabel hitButtonsLabel;
    private javax.swing.JLabel hitCountLabel;
    private javax.swing.JLabel hitLabel;
    private javax.swing.JButton hitNextButton;
    private javax.swing.JLabel hitOfLabel;
    private javax.swing.JButton hitPreviousButton;
    private javax.swing.JLabel hitTotalLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JLabel pageButtonsLabel;
    private javax.swing.JLabel pageCurLabel;
    private javax.swing.JButton pageNextButton;
    private javax.swing.JLabel pageOfLabel;
    private javax.swing.JButton pagePreviousButton;
    private javax.swing.JLabel pageTotalLabel;
    private javax.swing.JLabel pagesLabel;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JComboBox<org.sleuthkit.autopsy.keywordsearch.IndexedText> sourceComboBox;
    private javax.swing.JPanel textSourcePanel;
    private javax.swing.JPanel zoomPanel;
    // End of variables declaration//GEN-END:variables

    void refreshCurrentMarkup() {
        setMarkup(getSelectedSource());
    }

    /**
     * Set the available sources (selects the first source in the list by
     * default)
     *
     * @param contentName The name of the content to be displayed
     * @param sources     A list of IndexedText that have different 'views' of
     *                    the content.
     */
    final void setSources(String contentName, List<IndexedText> sources) {
        this.lastKnownAnchor = null;
        this.contentName = contentName;
        setPanelText(null, false);

        sourceComboBox.removeAllItems();
        sources.forEach(sourceComboBox::addItem);
        if (false == sources.isEmpty()) {
            sourceComboBox.setEnabled(true);
            sourceComboBox.setSelectedIndex(0);
        } else {
            sourceComboBox.setEnabled(false);
        }
    }

    /**
     * Get the source selected in the combo box
     *
     * @return currently selected Source
     */
    public IndexedText getSelectedSource() {
        return (IndexedText) sourceComboBox.getSelectedItem();
    }

    private void setPanelText(String text, boolean detectDirection) {
        String safeText = StringUtils.defaultString(text);

        if (detectDirection) {
            //detect text direction using first 1024 chars and set it
            //get first up to 1024 chars, strip <pre> tag and unescape html to get the string on which to detect
            final int len = safeText.length();
            final int prefixLen = "<pre>".length(); //NON-NLS
            if (len > prefixLen) {
                final int maxOrientChars = Math.min(len, 1024);
                final String orientDetectText = EscapeUtil.unEscapeHtml(safeText.substring(prefixLen, maxOrientChars));
                ComponentOrientation direction = TextUtil.getTextDirection(orientDetectText);
                extractedTextPane.applyComponentOrientation(direction);
            } else {
                extractedTextPane.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            }
        } else {
            extractedTextPane.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }

        // refresh style
        setStyleSheetSize(styleSheet, curSize);
        extractedTextPane.setText(safeText);
        extractedTextPane.setCaretPosition(0);
    }

    void scrollToAnchor(String anchor) {
        lastKnownAnchor = anchor;
        extractedTextPane.scrollToReference(anchor);
    }

    /**
     * Update the value displayed as the current match
     *
     * @param current, current hit to update the display with
     */
    void updateCurrentMatchDisplay(int current) {
        if (current == 0) {
            hitCountLabel.setText("-");
        } else {
            hitCountLabel.setText(Integer.toString(current));
        }
    }

    /**
     * Update the value dispalyed for the total number of matches
     *
     * @param total total number of hits to update the display with
     */
    void updateTotaMatcheslDisplay(int total) {
        if (total == 0) {
            hitTotalLabel.setText("-");
        } else {
            hitTotalLabel.setText(Integer.toString(total));
        }
    }

    /**
     * Update the value displayed for the current page
     *
     * @param current, current page to update the display with
     */
    void updateCurrentPageDisplay(int current) {
        pageCurLabel.setText(Integer.toString(current));
    }

    /**
     * Update the value displayed for the total pages
     *
     * @param total total number of pages to update the display with
     */
    void updateTotalPagesDisplay(int total) {
        pageTotalLabel.setText(Integer.toString(total));
    }

    /**
     * Reset the display including the current/total pages and the current/total
     * hits
     */
    void resetDisplay() {
        setSources("", new ArrayList<>());
        hitTotalLabel.setText("-");
        hitCountLabel.setText("-");
        pageCurLabel.setText("-");
        pageTotalLabel.setText("-");
    }

    /**
     * enable previous match control
     *
     * @param enable whether to enable or disable
     */
    void enablePrevMatchControl(boolean enable) {
        hitPreviousButton.setEnabled(enable);
    }

    /**
     * enable next match control
     *
     * @param enable whether to enable or disable
     */
    void enableNextMatchControl(boolean enable) {
        hitNextButton.setEnabled(enable);
    }

    void addPrevMatchControlListener(ActionListener l) {
        hitPreviousButton.addActionListener(l);
    }

    void addNextMatchControlListener(ActionListener l) {
        hitNextButton.addActionListener(l);
    }

    /**
     * enable previous oage control
     *
     * @param enable whether to enable or disable
     */
    void enablePrevPageControl(boolean enable) {
        pagePreviousButton.setEnabled(enable);
    }

    /**
     * enable next page control
     *
     * @param enable whether to enable or disable
     */
    void enableNextPageControl(boolean enable) {
        pageNextButton.setEnabled(enable);
    }

    void addPrevPageControlListener(ActionListener l) {
        pagePreviousButton.addActionListener(l);
    }

    void addNextPageControlListener(ActionListener l) {
        pageNextButton.addActionListener(l);
    }

    void addSourceComboControlListener(ActionListener l) {
        sourceComboBox.addActionListener(l);
    }

    /**
     * Update page and search controls for selected source
     *
     * @param source the selected source
     */
    void updateControls(IndexedText source) {
        updatePageControls(source);
        updateSearchControls(source);
    }

    /**
     * update page controls given the selected source
     *
     * @param source selected source
     */
    void updatePageControls(IndexedText source) {
        if (source == null) {
            enableNextPageControl(false);
            enablePrevPageControl(false);
            updateCurrentPageDisplay(0);
            updateTotalPagesDisplay(0);
            return;
        }

        updateCurrentPageDisplay(source.getCurrentPage());
        int totalPages = source.getNumberPages();
        updateTotalPagesDisplay(totalPages);

        if (totalPages < 2) {
            enableNextPageControl(false);
            enablePrevPageControl(false);
        } else {
            enableNextPageControl(source.hasNextPage());
            enablePrevPageControl(source.hasPreviousPage());
        }
    }

    /**
     * update search controls given the selected source
     *
     * @param source selected source
     */
    void updateSearchControls(IndexedText source) {
        //setup search controls
        if (source != null && source.isSearchable()) {
            updateCurrentMatchDisplay(source.currentItem());
            updateTotaMatcheslDisplay(source.getNumberHits());
            enableNextMatchControl(source.hasNextItem() || source.hasNextPage());
            enablePrevMatchControl(source.hasPreviousItem() || source.hasPreviousPage());
        } else {
            enableNextMatchControl(false);
            enablePrevMatchControl(false);
            updateCurrentMatchDisplay(0);
            updateTotaMatcheslDisplay(0);
        }
    }

    /**
     * Scroll to current (first) hit after SetMarkupWorker worker completed
     *
     * @param source
     */
    private void scrollToCurrentHit(final IndexedText source) {
        if (source == null || !source.isSearchable()) {
            return;
        }

        //scrolling required invokeLater to enqueue in EDT
        EventQueue.invokeLater(() -> scrollToAnchor(source.getAnchorPrefix() + source.currentItem()));

    }

    /**
     * Gets and sets new markup (i.e. based on user choose keyword hits or pure
     * text). Updates GUI in GUI thread and gets markup in background thread. To
     * be invoked from GUI thread only.
     */
    @NbBundle.Messages("ExtractedContentPanel.setMarkup.panelTxt=<span style='font-style:italic'>Loading text... Please wait</span>")
    private void setMarkup(IndexedText source) {
        setPanelText(Bundle.ExtractedContentPanel_setMarkup_panelTxt(), false);
        new SetMarkupWorker(contentName, source).execute();
    }

    /**
     * Swingworker to get markup source content String from Solr in background
     * thread and then set the panel text in the EDT Helps not to block the UI
     * while content from Solr is retrieved.
     */
    private final class SetMarkupWorker extends SwingWorker<String, Void> {

        private final String contentName;

        private final IndexedText source;

        private ProgressHandle progress;

        SetMarkupWorker(String contentName, IndexedText source) {
            this.contentName = contentName;
            this.source = source;
        }

        @Override
        @NbBundle.Messages({"# {0} - Content name",
            "ExtractedContentPanel.SetMarkup.progress.loading=Loading text for {0}"})
        protected String doInBackground() throws Exception {
            progress = ProgressHandle.createHandle(Bundle.ExtractedContentPanel_SetMarkup_progress_loading(contentName));
            progress.start();
            progress.switchToIndeterminate();

            return source.getText();
        }

        @Override
        protected void done() {
            super.done();
            progress.finish();

            // see if there are any errors
            try {
                String markup = get();
                if (markup != null) {
                    setPanelText(markup, true);
                } else {
                    setPanelText("", false);
                }
            } catch (InterruptedException | CancellationException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Error getting marked up text", ex); //NON-NLS
                setPanelText(Bundle.IndexedText_errorMessage_errorGettingText(), true);
            }

            updateControls(source);
            scrollToCurrentHit(source);
        }
    }
}
