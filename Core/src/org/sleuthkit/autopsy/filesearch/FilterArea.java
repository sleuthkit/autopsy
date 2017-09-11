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
package org.sleuthkit.autopsy.filesearch;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Holds the filters in one category (and in the future controls toggling the
 * visibility of the category in the search panel)
 *
 * @author pmartel
 */
class FilterArea extends JPanel {

//    private JButton toggleButton;
//    private boolean collapsed;
    private final String title;
    private List<FileSearchFilter> filters;
    private JPanel filtersPanel;

    /**
     * Filter areas with one filter
     *
     * @param title  name of the area
     * @param filter single filter
     */
    FilterArea(String title, FileSearchFilter filter) {
        this(title, Collections.singletonList(filter));
    }

    /**
     * Filter area with multiple filters
     *
     * @param title   name of the area
     * @param filters multiple filters
     */
    FilterArea(String title, List<FileSearchFilter> filters) {
        this.title = title;
        this.filters = filters;
//        this.collapsed = false;
        this.init();
        this.refresh();
    }

    /**
     * Get the filters for this area
     *
     * @return all the filters
     */
    List<FileSearchFilter> getFilters() {
        return filters;
    }

    private void init() {

        // Commmented-out code is for collapable filter areas
//        this.dateFiltersButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/filesearch/arrow_down.gif"))); // NOI18N
//        toggleButton = new JButton();
//        toggleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
//        toggleButton.addActionListener(new ActionListener() {
//
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                FilterArea.this.toggle();
//                FilterArea.this.refresh();
//            }
//        });
//
//        this.add(toggleButton);
        filtersPanel = new JPanel();
        filtersPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        BoxLayout filtersPanelLayout = new BoxLayout(this, BoxLayout.Y_AXIS);
        this.setLayout(filtersPanelLayout);

        for (int i = 0; i < filters.size(); i++) {
            FileSearchFilter f = filters.get(i);
            JComponent filterComponent = f.getComponent();
            filterComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (i != filters.size() - 1) {
                filterComponent.setBorder(new EmptyBorder(0, 0, 15, 0));
            }
            else {
                filterComponent.setBorder(new EmptyBorder(0, 0, 18, 0));
            }
            this.add(filterComponent);
        }
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void refresh() {
//        int height = toggleButton.getHeight();
//        toggleButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
//        
//        filtersPanel.setVisible(!collapsed);
//        toggleButton.setText(title);
//        
//        filtersPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, filtersPanel.getHeight()));
//        
//        this.setPreferredSize(this.getPreferredSize());
//        
//        this.revalidate();
//        this.repaint();
    }
//    private void toggle() {
//        collapsed = !collapsed;
//    }
}
