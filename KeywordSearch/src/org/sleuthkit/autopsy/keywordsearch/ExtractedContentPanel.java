/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JTextPane;
import javax.swing.SizeRequirements;
import javax.swing.SwingWorker;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.HTMLFactory;
import javax.swing.text.html.InlineView;
import javax.swing.text.html.ParagraphView;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TextUtil;

/**
 * Panel displays HTML content sent to ExtractedContentViewer, and provides a
 * combo-box to select between multiple sources.
 */
class ExtractedContentPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(ExtractedContentPanel.class.getName());
    private String contentName;

    ExtractedContentPanel() {
        initComponents();
        setSources("", new ArrayList<>());
        hitPreviousButton.setEnabled(false);
        hitNextButton.setEnabled(false);

        /*
         * This appears to be an attempt to modify the wrapping behavior of the
         * extractedTextPane taken form this website:
         * http://java-sl.com/tip_html_letter_wrap.html.
         */
        HTMLEditorKit editorKit = new HTMLEditorKit() {
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
                                    if (r == null) {
                                        r = new SizeRequirements();
                                    }
                                    float pref = layoutPool.getPreferredSpan(axis);
                                    float min = layoutPool.getMinimumSpan(axis);
                                    // Don't include insets, Box.getXXXSpan will include them. 
                                    r.minimum = (int) min;
                                    r.preferred = Math.max(r.minimum, (int) pref);
                                    r.maximum = Integer.MAX_VALUE;
                                    r.alignment = 0.5f;
                                    return r;
                                }
                            };
                        }
                        return v;
                    }
                };
            }
        };
        /*
         * set font size manually in an effort to get fonts in this panel to
         * look similar to what is in the 'String View' content viewer.
         */
        editorKit.getStyleSheet().addRule("body {font-size: 8.5px;}"); //NON-NLS
        extractedTextPane.setEditorKit(editorKit);

        sourceComboBox.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                refreshCurrentMarkup();
            }
        });
        extractedTextPane.setComponentPopupMenu(rightClickMenu);
        copyMenuItem.addActionListener(actionEvent -> extractedTextPane.copy());
        selectAllMenuItem.addActionListener(actionEvent -> extractedTextPane.selectAll());
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
        jScrollPane1 = new javax.swing.JScrollPane();
        extractedTextPane = new javax.swing.JTextPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        sourceComboBox = new javax.swing.JComboBox<>();
        jLabel1 = new javax.swing.JLabel();
        pageOfLabel = new javax.swing.JLabel();
        pageButtonsLabel = new javax.swing.JLabel();
        pageTotalLabel = new javax.swing.JLabel();
        pagesLabel = new javax.swing.JLabel();
        pageNextButton = new javax.swing.JButton();
        pagePreviousButton = new javax.swing.JButton();
        pageCurLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        hitLabel = new javax.swing.JLabel();
        hitButtonsLabel = new javax.swing.JLabel();
        hitNextButton = new javax.swing.JButton();
        hitOfLabel = new javax.swing.JLabel();
        hitTotalLabel = new javax.swing.JLabel();
        hitPreviousButton = new javax.swing.JButton();
        hitCountLabel = new javax.swing.JLabel();
        jSeparator2 = new javax.swing.JSeparator();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setPreferredSize(new java.awt.Dimension(100, 58));

        jScrollPane1.setBackground(new java.awt.Color(255, 255, 255));
        jScrollPane1.setPreferredSize(new java.awt.Dimension(640, 29));

        extractedTextPane.setEditable(false);
        extractedTextPane.setAutoscrolls(false);
        extractedTextPane.setInheritsPopupMenu(true);
        extractedTextPane.setMaximumSize(new java.awt.Dimension(2000, 2000));
        extractedTextPane.setPreferredSize(new java.awt.Dimension(600, 29));
        jScrollPane1.setViewportView(extractedTextPane);

        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane2.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        jScrollPane2.setPreferredSize(new java.awt.Dimension(600, 100));

        jPanel1.setMinimumSize(new java.awt.Dimension(0, 0));
        jPanel1.setPreferredSize(new java.awt.Dimension(600, 81));

        sourceComboBox.setModel(new javax.swing.DefaultComboBoxModel<org.sleuthkit.autopsy.keywordsearch.IndexedText>());
        sourceComboBox.setMaximumSize(new java.awt.Dimension(150, 32767));
        sourceComboBox.setMinimumSize(new java.awt.Dimension(150, 20));
        sourceComboBox.setPreferredSize(new java.awt.Dimension(150, 20));

        jLabel1.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.jLabel1.text")); // NOI18N

        pageOfLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageOfLabel.text")); // NOI18N

        pageButtonsLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageButtonsLabel.text")); // NOI18N

        pageTotalLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pageTotalLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageTotalLabel.text")); // NOI18N

        pagesLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagesLabel.text")); // NOI18N

        pageNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward.png"))); // NOI18N
        pageNextButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageNextButton.text")); // NOI18N
        pageNextButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pageNextButton.setBorderPainted(false);
        pageNextButton.setContentAreaFilled(false);
        pageNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_disabled.png"))); // NOI18N
        pageNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        pageNextButton.setPreferredSize(new java.awt.Dimension(23, 23));

        pagePreviousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back.png"))); // NOI18N
        pagePreviousButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagePreviousButton.text")); // NOI18N
        pagePreviousButton.setActionCommand(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagePreviousButton.actionCommand")); // NOI18N
        pagePreviousButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pagePreviousButton.setBorderPainted(false);
        pagePreviousButton.setContentAreaFilled(false);
        pagePreviousButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_disabled.png"))); // NOI18N
        pagePreviousButton.setMargin(new java.awt.Insets(2, 0, 2, 0));

        pageCurLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pageCurLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageCurLabel.text")); // NOI18N

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        hitLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitLabel.text")); // NOI18N
        hitLabel.setToolTipText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitLabel.toolTipText")); // NOI18N

        hitButtonsLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitButtonsLabel.text")); // NOI18N

        hitNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward.png"))); // NOI18N
        hitNextButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitNextButton.text")); // NOI18N
        hitNextButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hitNextButton.setBorderPainted(false);
        hitNextButton.setContentAreaFilled(false);
        hitNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_disabled.png"))); // NOI18N
        hitNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        hitNextButton.setPreferredSize(new java.awt.Dimension(23, 23));
        hitNextButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_hover.png"))); // NOI18N

        hitOfLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitOfLabel.text")); // NOI18N

        hitTotalLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hitTotalLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitTotalLabel.text")); // NOI18N
        hitTotalLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        hitTotalLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        hitTotalLabel.setPreferredSize(new java.awt.Dimension(18, 14));

        hitPreviousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back.png"))); // NOI18N
        hitPreviousButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitPreviousButton.text")); // NOI18N
        hitPreviousButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hitPreviousButton.setBorderPainted(false);
        hitPreviousButton.setContentAreaFilled(false);
        hitPreviousButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_disabled.png"))); // NOI18N
        hitPreviousButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        hitPreviousButton.setPreferredSize(new java.awt.Dimension(23, 23));
        hitPreviousButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_hover.png"))); // NOI18N

        hitCountLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hitCountLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitCountLabel.text")); // NOI18N
        hitCountLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        hitCountLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        hitCountLabel.setPreferredSize(new java.awt.Dimension(18, 14));

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 600, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(hitLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(hitCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(hitOfLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(hitTotalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(hitButtonsLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(hitPreviousButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, 0)
                    .addComponent(hitNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(18, 18, 18)
                    .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(18, 18, 18)
                    .addComponent(pagesLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(pageCurLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(pageOfLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(pageTotalLabel)
                    .addGap(18, 18, 18)
                    .addComponent(pageButtonsLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(pagePreviousButton)
                    .addGap(0, 0, 0)
                    .addComponent(pageNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(18, 18, 18)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(sourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap()))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 81, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(hitPreviousButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel1)
                        .addComponent(hitNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pageButtonsLabel)
                        .addComponent(pagePreviousButton)
                        .addComponent(pageNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(sourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pagesLabel)
                        .addComponent(hitLabel)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pageCurLabel)
                        .addComponent(pageOfLabel)
                        .addComponent(hitCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pageTotalLabel)
                        .addComponent(hitOfLabel)
                        .addComponent(hitTotalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(hitButtonsLabel))
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {hitButtonsLabel, hitCountLabel, hitLabel, hitNextButton, hitOfLabel, hitPreviousButton, hitTotalLabel, jLabel1, jSeparator1, jSeparator2, pageButtonsLabel, pageCurLabel, pageNextButton, pageOfLabel, pagePreviousButton, pageTotalLabel, pagesLabel, sourceComboBox});

        jScrollPane2.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JTextPane extractedTextPane;
    private javax.swing.JLabel hitButtonsLabel;
    private javax.swing.JLabel hitCountLabel;
    private javax.swing.JLabel hitLabel;
    private javax.swing.JButton hitNextButton;
    private javax.swing.JLabel hitOfLabel;
    private javax.swing.JButton hitPreviousButton;
    private javax.swing.JLabel hitTotalLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
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
    void setSources(String contentName, List<IndexedText> sources) {
        this.contentName = contentName;
        setPanelText(null, false);

        sourceComboBox.removeAllItems();
        sources.forEach(sourceComboBox::addItem);
        if (false == sources.isEmpty()) {
            sourceComboBox.setSelectedIndex(0);
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
        text = StringUtils.defaultString(text);

        if (detectDirection) {
            //detect text direction using first 1024 chars and set it
            //get first up to 1024 chars, strip <pre> tag and unescape html to get the string on which to detect
            final int len = text.length();
            final int prefixLen = "<pre>".length(); //NON-NLS
            if (len > prefixLen) {
                final int maxOrientChars = Math.min(len, 1024);
                final String orientDetectText = EscapeUtil.unEscapeHtml(text.substring(prefixLen, maxOrientChars));
                ComponentOrientation direction = TextUtil.getTextDirection(orientDetectText);
                //logger.log(Level.INFO, "ORIENTATION LEFT TO RIGHT: " + direction.isLeftToRight());
                extractedTextPane.applyComponentOrientation(direction);
            } else {
                extractedTextPane.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            }
        } else {
            extractedTextPane.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }

        extractedTextPane.setText(text);
        extractedTextPane.setCaretPosition(0);
    }

    void scrollToAnchor(String anchor) {
        extractedTextPane.scrollToReference(anchor);
    }

    /**
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
     *
     * @param current, current page to update the display with
     */
    void updateCurrentPageDisplay(int current) {
        pageCurLabel.setText(Integer.toString(current));
    }

    /**
     *
     * @param total total number of pages to update the display with
     */
    void updateTotalPageslDisplay(int total) {
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
            updateTotalPageslDisplay(0);
            return;
        }

        updateCurrentPageDisplay(source.getCurrentPage());
        int totalPages = source.getNumberPages();
        updateTotalPageslDisplay(totalPages);

        if (totalPages == 1) {
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
