/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JMenuItem;
import javax.swing.SizeRequirements;
import javax.swing.SwingWorker;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.InlineView;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.HTMLFactory;
import javax.swing.text.html.StyleSheet;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.TextUtil;

/**
 * Panel displays HTML content sent to ExtractedContentViewer, and provides a
 * combo-box to select between multiple sources.
 */
class ExtractedContentPanel extends javax.swing.JPanel {

    private static Logger logger = Logger.getLogger(ExtractedContentPanel.class.getName());

    ExtractedContentPanel() {
        initComponents();

        initControls();

        customizeComponents();

    }

    private void customizeComponents() {
        
        HTMLEditorKit editorKit = new HTMLEditorKit() {
            @Override
            public ViewFactory getViewFactory() {

                return new HTMLFactory() {
                    public View create(Element e) {
                        View v = super.create(e);
                        if (v instanceof InlineView) {
                            return new InlineView(e) {
                                public int getBreakWeight(int axis, float pos, float len) {
                                    return GoodBreakWeight;
                                }

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
        
        // set font size manually in an effort to get fonts in this panel to look
        // similar to what is in the 'String View' content viewer.
        StyleSheet ss = editorKit.getStyleSheet();
        ss.addRule("body {font-size: 8.5px;}");
        
        extractedTextPane.setEditorKit(editorKit);

        sourceComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    MarkupSource source = (MarkupSource) e.getItem();
                    setMarkup(source);
                }
            }
        });


        setSources(new ArrayList<MarkupSource>());

        extractedTextPane.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(copyMenuItem)) {
                    extractedTextPane.copy();
                } else if (jmi.equals(selectAllMenuItem)) {
                    extractedTextPane.selectAll();
                }
            }
        };
        copyMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);
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
        sourceComboBox = new javax.swing.JComboBox();
        hitLabel = new javax.swing.JLabel();
        hitCountLabel = new javax.swing.JLabel();
        hitOfLabel = new javax.swing.JLabel();
        hitTotalLabel = new javax.swing.JLabel();
        hitButtonsLabel = new javax.swing.JLabel();
        hitPreviousButton = new javax.swing.JButton();
        hitNextButton = new javax.swing.JButton();
        pageButtonsLabel = new javax.swing.JLabel();
        pagePreviousButton = new javax.swing.JButton();
        pageNextButton = new javax.swing.JButton();
        pagesLabel = new javax.swing.JLabel();
        pageCurLabel = new javax.swing.JLabel();
        pageOfLabel = new javax.swing.JLabel();
        pageTotalLabel = new javax.swing.JLabel();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        extractedTextPane.setEditable(false);
        extractedTextPane.setAutoscrolls(false);
        jScrollPane1.setViewportView(extractedTextPane);

        sourceComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        hitLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitLabel.text")); // NOI18N
        hitLabel.setToolTipText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitLabel.toolTipText")); // NOI18N

        hitCountLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hitCountLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitCountLabel.text")); // NOI18N
        hitCountLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        hitCountLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        hitCountLabel.setPreferredSize(new java.awt.Dimension(18, 14));

        hitOfLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitOfLabel.text")); // NOI18N

        hitTotalLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        hitTotalLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitTotalLabel.text")); // NOI18N
        hitTotalLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        hitTotalLabel.setMinimumSize(new java.awt.Dimension(18, 14));
        hitTotalLabel.setPreferredSize(new java.awt.Dimension(18, 14));

        hitButtonsLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitButtonsLabel.text")); // NOI18N

        hitPreviousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back.png"))); // NOI18N
        hitPreviousButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitPreviousButton.text")); // NOI18N
        hitPreviousButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hitPreviousButton.setBorderPainted(false);
        hitPreviousButton.setContentAreaFilled(false);
        hitPreviousButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_disabled.png"))); // NOI18N
        hitPreviousButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        hitPreviousButton.setPreferredSize(new java.awt.Dimension(23, 23));
        hitPreviousButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_hover.png"))); // NOI18N

        hitNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward.png"))); // NOI18N
        hitNextButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.hitNextButton.text")); // NOI18N
        hitNextButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hitNextButton.setBorderPainted(false);
        hitNextButton.setContentAreaFilled(false);
        hitNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_disabled.png"))); // NOI18N
        hitNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        hitNextButton.setPreferredSize(new java.awt.Dimension(23, 23));
        hitNextButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_hover.png"))); // NOI18N

        pageButtonsLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageButtonsLabel.text")); // NOI18N

        pagePreviousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back.png"))); // NOI18N
        pagePreviousButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagePreviousButton.text")); // NOI18N
        pagePreviousButton.setActionCommand(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagePreviousButton.actionCommand")); // NOI18N
        pagePreviousButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pagePreviousButton.setBorderPainted(false);
        pagePreviousButton.setContentAreaFilled(false);
        pagePreviousButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_back_disabled.png"))); // NOI18N
        pagePreviousButton.setMargin(new java.awt.Insets(2, 0, 2, 0));

        pageNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward.png"))); // NOI18N
        pageNextButton.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageNextButton.text")); // NOI18N
        pageNextButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pageNextButton.setBorderPainted(false);
        pageNextButton.setContentAreaFilled(false);
        pageNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/btn_step_forward_disabled.png"))); // NOI18N
        pageNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        pageNextButton.setPreferredSize(new java.awt.Dimension(23, 23));

        pagesLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pagesLabel.text")); // NOI18N

        pageCurLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pageCurLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageCurLabel.text")); // NOI18N

        pageOfLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageOfLabel.text")); // NOI18N

        pageTotalLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pageTotalLabel.setText(org.openide.util.NbBundle.getMessage(ExtractedContentPanel.class, "ExtractedContentPanel.pageTotalLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(hitLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hitCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hitOfLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hitTotalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hitButtonsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(hitPreviousButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(hitNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pagesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pageCurLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pageOfLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pageTotalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pageButtonsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pagePreviousButton)
                .addGap(0, 0, 0)
                .addComponent(pageNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 71, Short.MAX_VALUE)
                .addComponent(sourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addComponent(jScrollPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sourceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(hitCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(hitOfLabel)
                        .addComponent(hitTotalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(hitLabel)
                        .addComponent(hitButtonsLabel))
                    .addComponent(hitPreviousButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(hitNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(pageButtonsLabel)
                        .addComponent(pageTotalLabel)
                        .addComponent(pagesLabel)
                        .addComponent(pageCurLabel)
                        .addComponent(pageOfLabel))
                    .addComponent(pageNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pagePreviousButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 293, Short.MAX_VALUE))
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
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel pageButtonsLabel;
    private javax.swing.JLabel pageCurLabel;
    private javax.swing.JButton pageNextButton;
    private javax.swing.JLabel pageOfLabel;
    private javax.swing.JButton pagePreviousButton;
    private javax.swing.JLabel pageTotalLabel;
    private javax.swing.JLabel pagesLabel;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JComboBox sourceComboBox;
    // End of variables declaration//GEN-END:variables

    void refreshCurrentMarkup() {
        MarkupSource ms = (MarkupSource) sourceComboBox.getSelectedItem();
        setMarkup(ms);
    }

    /**
     * Set the available sources (selects the first source in the list by
     * default)
     *
     * @param sources
     */
    void setSources(List<MarkupSource> sources) {
        sourceComboBox.removeAllItems();
        setPanelText(null, false);

        for (MarkupSource ms : sources) {
            sourceComboBox.<String>addItem(ms);
        }

        if (!sources.isEmpty()) {
            sourceComboBox.setSelectedIndex(0);
        }
    }

    /**
     *
     * @return currently available sources on the panel
     */
    public List<MarkupSource> getSources() {
        ArrayList<MarkupSource> sources = new ArrayList<MarkupSource>();
        for (int i = 0; i < sourceComboBox.getItemCount(); ++i) {
            sources.add((MarkupSource) sourceComboBox.getItemAt(i));
        }
        return sources;
    }

    /**
     *
     * @return currently selected Source
     */
    public MarkupSource getSelectedSource() {
        return (MarkupSource) sourceComboBox.getSelectedItem();
    }

    private void setPanelText(String text, boolean detectDirection) {
        if (text == null) {
            text = "";
        }

        if (detectDirection) {
            //detect text direction using first 1024 chars and set it
            //get first up to 1024 chars, strip <pre> tag and unescape html to get the string on which to detect
            final int len = text.length();
            final int prefixLen = "<pre>".length();
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

    private void initControls() {
        hitPreviousButton.setEnabled(false);
        hitNextButton.setEnabled(false);
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

    void resetDisplay() {
        resetHitDisplay();
        resetPagesDisplay();
    }

    /**
     * reset the current/total hits display
     */
    void resetHitDisplay() {
        hitTotalLabel.setText("-");
        hitCountLabel.setText("-");
    }

    /**
     * reset the current/total pages display
     */
    void resetPagesDisplay() {
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
    void updateControls(MarkupSource source) {
        updatePageControls(source);
        updateSearchControls(source);
    }

    /**
     * update page controls given the selected source
     *
     * @param source selected source
     */
    void updatePageControls(MarkupSource source) {
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
            if (source.hasNextPage()) {
                enableNextPageControl(true);
            } else {
                enableNextPageControl(false);
            }

            if (source.hasPreviousPage()) {
                enablePrevPageControl(true);
            } else {
                enablePrevPageControl(false);
            }
        }


    }

    /**
     * update search controls given the selected source
     *
     * @param source selected source
     */
    void updateSearchControls(MarkupSource source) {
        //setup search controls
        if (source != null && source.isSearchable()) {

            updateCurrentMatchDisplay(source.currentItem());
            updateTotaMatcheslDisplay(source.getNumberHits());

            if (source.hasNextItem() || source.hasNextPage()) {
                enableNextMatchControl(true);
            } else {
                enableNextMatchControl(false);
            }

            if (source.hasPreviousItem() || source.hasPreviousPage()) {
                enablePrevMatchControl(true);
            } else {
                enablePrevMatchControl(false);
            }

        } else {
            enableNextMatchControl(false);
            enablePrevMatchControl(false);
            updateCurrentMatchDisplay(0);
            updateTotaMatcheslDisplay(0);
        }
    }

    /**
     * Scroll to current (first) hit after SetMarkup worker completed
     *
     * @param source
     */
    private void scrollToCurrentHit(final MarkupSource source) {
        if (source == null || !source.isSearchable()) {
            return;
        }

        //scrolling required invokeLater to enqueue in EDT
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                scrollToAnchor(source.getAnchorPrefix() + Integer.toString(source.currentItem()));
            }
        });

    }

    /**
     * Gets and sets new markup. Updates GUI in GUI thread and gets markup in
     * background thread. To be invoked from GUI thread only.
     */
    private void setMarkup(MarkupSource source) {
        setPanelText("<span style='font-style:italic'>Loading text... Please wait</span>", false);
        new SetMarkup(source).execute();
    }

    /**
     * Swingworker to get makrup source content String from Solr in background
     * thread and then set the panel text in the EDT Helps not to block the UI
     * while content from Solr is retrieved.
     */
    private final class SetMarkup extends SwingWorker<Object, Void> {

        private MarkupSource source;
        private String markup;
        private ProgressHandle progress;

        SetMarkup(MarkupSource source) {
            this.source = source;
        }

        @Override
        protected Object doInBackground() throws Exception {
            progress = ProgressHandleFactory.createHandle("Loading text");
            progress.setDisplayName("Loading text");
            progress.start();
            progress.switchToIndeterminate();

            markup = source.getMarkup();
            return null;
        }

        @Override
        protected void done() {
            //super.done();
            progress.finish();
            if (markup != null) {
                setPanelText(markup, true);
            } else {
                setPanelText("", false);
            }
            updateControls(source);

            scrollToCurrentHit(source);


        }
    }
}
