/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.discovery.search.AbstractFilter;
import com.google.common.eventbus.Subscribe;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.Group;
import org.sleuthkit.autopsy.discovery.search.ResultsSorter;
import org.sleuthkit.autopsy.discovery.search.SearchData.Type;
import static org.sleuthkit.autopsy.discovery.search.SearchData.Type.DOMAIN;

/**
 * Panel to display the list of groups which are provided by a search.
 */
final class GroupListPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private Type type = null;
    private Map<GroupKey, Integer> groupMap = null;
    private List<AbstractFilter> searchfilters;
    private DiscoveryAttributes.AttributeType groupingAttribute;
    private Group.GroupSortingAlgorithm groupSort;
    private ResultsSorter.SortingMethod resultSortMethod;
    private GroupKey selectedGroupKey;

    /**
     * Creates new form GroupListPanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    GroupListPanel() {
        initComponents();
    }

    /**
     * Subscribe to and reset the panel in response to SearchStartedEvents.
     *
     * @param searchStartedEvent The SearchStartedEvent which was received.
     */
    @Subscribe
    void handleSearchStartedEvent(DiscoveryEventUtils.SearchStartedEvent searchStartedEvent) {
        type = searchStartedEvent.getType();
        groupKeyList.setListData(new GroupKey[0]);
    }

    @Messages({"GroupsListPanel.noFileResults.message.text=No files were found for the selected filters.\n\n"
        + "Reminder:\n"
        + "  -The File Type Identification module must be run on each data source you want to find results in.\n"
        + "  -The Hash Lookup module must be run on each data source if you want to filter by past occurrence.\n"
        + "  -The Picture Analyzer module must be run on each data source if you are filtering by User Created content.",
        "GroupsListPanel.noDomainResults.message.text=No domains were found for the selected filters.\n\n"
        + "Reminder:\n"
        + "  -The Recent Activity module must be run on each data source you want to find results in.\n"
        + "  -The Central Repository module must be run on each data source if you want to filter or sort by past occurrences.\n"
        + "  -The iOS Analyzer (iLEAPP) module must be run on each data source which contains data from an iOS device.\n",
        "GroupsListPanel.noResults.title.text=No results found"})
    /**
     * Subscribe to and update list of groups in response to
     * SearchCompleteEvents.
     *
     * @param searchCompleteEvent The SearchCompleteEvent which was received.
     */
    @Subscribe
    void handleSearchCompleteEvent(DiscoveryEventUtils.SearchCompleteEvent searchCompleteEvent) {
        groupMap = searchCompleteEvent.getGroupMap();
        searchfilters = searchCompleteEvent.getFilters();
        groupingAttribute = searchCompleteEvent.getGroupingAttr();
        groupSort = searchCompleteEvent.getGroupSort();
        resultSortMethod = searchCompleteEvent.getResultSort();
        groupKeyList.setListData(groupMap.keySet().toArray(new GroupKey[groupMap.keySet().size()]));
        SwingUtilities.invokeLater(() -> {
            if (groupKeyList.getModel().getSize() > 0) {
                groupKeyList.setSelectedIndex(0);
            } else if (type == DOMAIN) {
                JOptionPane.showMessageDialog(DiscoveryTopComponent.getTopComponent(),
                        Bundle.GroupsListPanel_noDomainResults_message_text(),
                        Bundle.GroupsListPanel_noResults_title_text(),
                        JOptionPane.PLAIN_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(DiscoveryTopComponent.getTopComponent(),
                        Bundle.GroupsListPanel_noFileResults_message_text(),
                        Bundle.GroupsListPanel_noResults_title_text(),
                        JOptionPane.PLAIN_MESSAGE);
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        });
    }

    /**
     * Subscribe to SearchCancelledEvent and reset the panel in response to it.
     *
     * @param searchCancelledEvent The SearchCancelledEvent which was received.
     */
    @Subscribe
    void handleSearchCancelledEvent(DiscoveryEventUtils.SearchCancelledEvent searchCancelledEvent) {
        SwingUtilities.invokeLater(() -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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

        javax.swing.JScrollPane groupListScrollPane = new javax.swing.JScrollPane();
        groupKeyList = new javax.swing.JList<>();

        groupKeyList.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GroupListPanel.class, "GroupListPanel.groupKeyList.border.title"))); // NOI18N
        groupKeyList.setModel(new DefaultListModel<GroupKey>());
        groupKeyList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        groupKeyList.setCellRenderer(new GroupListRenderer());
        groupKeyList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                groupSelected(evt);
            }
        });
        groupListScrollPane.setViewportView(groupKeyList);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 144, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(groupListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 144, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(groupListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Reset the group list to be empty.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void resetGroupList() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        groupKeyList.setListData(new GroupKey[0]);
    }

    /**
     * Respond to a group being selected by sending a PageRetrievedEvent
     *
     * @param evt the event which indicates a selection occurs in the list
     */
    private void groupSelected(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_groupSelected
        if (!evt.getValueIsAdjusting()) {
            if (groupKeyList.getSelectedValue() != null) {
                GroupKey selectedGroup = groupKeyList.getSelectedValue();
                for (GroupKey groupKey : groupMap.keySet()) {
                    if (selectedGroup.equals(groupKey)) {
                        selectedGroupKey = groupKey;
                        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.GroupSelectedEvent(
                                searchfilters, groupingAttribute, groupSort, resultSortMethod, selectedGroupKey, groupMap.get(selectedGroupKey), type));
                        break;
                    }
                }
            } else {
                DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.NoResultsEvent());

            }
        }
    }//GEN-LAST:event_groupSelected

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<GroupKey> groupKeyList;
    // End of variables declaration//GEN-END:variables

    /**
     * GroupListCellRenderer displays GroupKeys as their String value followed
     * by the number of items in the group.
     */
    private class GroupListRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            Object newValue = value;
            if (newValue instanceof GroupKey) {
                String valueString = newValue.toString();
                setToolTipText(valueString);
                valueString += " (" + groupMap.get(newValue) + ")";

                if (groupingAttribute instanceof DiscoveryAttributes.ParentPathAttribute) {
                    // Using the list FontRenderContext instead of this because
                    // the label RenderContext was sometimes null, but this should work.
                    FontRenderContext context = ((Graphics2D) list.getGraphics()).getFontRenderContext();

                    //Determine the width of the string with the given font.
                    double stringWidth = getFont().getStringBounds(valueString, context).getWidth();
                    // subtracting 10 from the width as a littl inset.
                    int listWidth = list.getWidth() - 10;

                    if (stringWidth > listWidth) {
                        double avgCharWidth = Math.floor(stringWidth / valueString.length());

                        // The extra 5 is to account for the " ... " that is being added back. 
                        int charToRemove = (int) Math.ceil((stringWidth - listWidth) / avgCharWidth) + 5;
                        int charactersToShow = (int) Math.ceil((valueString.length() - charToRemove) / 2);
                        valueString = valueString.substring(0, charactersToShow) + " ... " + valueString.substring(valueString.length() - charactersToShow);
                    }
                }
                newValue = valueString;
            }
            super.getListCellRendererComponent(list, newValue, index, isSelected, cellHasFocus);
            return this;
        }
    }

}
