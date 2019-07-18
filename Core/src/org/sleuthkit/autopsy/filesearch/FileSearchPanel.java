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
package org.sleuthkit.autopsy.filesearch;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * FileSearchPanel that present search options
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class FileSearchPanel extends javax.swing.JPanel {

    private final List<FileSearchFilter> filters = new ArrayList<>();
    private static int resultWindowCount = 0; //keep track of result windows so they get unique names
    private static final String EMPTY_WHERE_CLAUSE = NbBundle.getMessage(DateSearchFilter.class, "FileSearchPanel.emptyWhereClause.text");

    enum EVENT {
        CHECKED
    }

    /**
     * Creates new form FileSearchPanel
     */
    FileSearchPanel() {
        initComponents();
        customizeComponents();

    }

    /**
     * This method is called from within the constructor to initialize the form.
     */
    private void customizeComponents() {
        
        JLabel label = new JLabel(NbBundle.getMessage(this.getClass(), "FileSearchPanel.custComp.label.text"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayout(1,2));
        panel1.add(new JLabel(""));
        JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayout(1,2, 20, 0));
        JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayout(1,2, 20, 0));
        JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayout(1,2, 20, 0));
        JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayout(1,2, 20, 0));

        // Create and add filter areas
        NameSearchFilter nameFilter =  new NameSearchFilter();
        SizeSearchFilter sizeFilter = new SizeSearchFilter();
        DateSearchFilter dateFilter = new DateSearchFilter();
        KnownStatusSearchFilter knowStatusFilter = new KnownStatusSearchFilter();
        HashSearchFilter hashFilter = new HashSearchFilter();
        MimeTypeFilter mimeTypeFilter = new MimeTypeFilter();
        DataSourceFilter dataSourceFilter = new DataSourceFilter();
        
        panel2.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.name"),nameFilter));
        
        panel3.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.metadata"),sizeFilter));
        
        panel2.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.metadata"), dateFilter)); 
        panel3.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.knownStatus"), knowStatusFilter));
        
        panel5.add(new FilterArea(NbBundle.getMessage(this.getClass(), "HashSearchPanel.md5CheckBox.text"), hashFilter));
        panel5.add(new JLabel(""));
        panel4.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.metadata"), mimeTypeFilter));
        panel4.add(new FilterArea(NbBundle.getMessage(this.getClass(), "DataSourcePanel.dataSourceCheckBox.text"), dataSourceFilter));
        filterPanel.add(panel1);
        filterPanel.add(panel2);
        filterPanel.add(panel3);
        filterPanel.add(panel4);
        filterPanel.add(panel5);
        
        filters.add(nameFilter);
        filters.add(sizeFilter);
        filters.add(dateFilter);
        filters.add(knowStatusFilter);
        filters.add(hashFilter);
        filters.add(mimeTypeFilter);
        filters.add(dataSourceFilter);
        
        for (FileSearchFilter filter : this.getFilters()) {
            filter.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    searchButton.setEnabled(isValidSearch());
                }
            });
        }
        addListenerToAll(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });        
        searchButton.setEnabled(isValidSearch());
    }

    /**
     * @return true if any of the filters in the panel are enabled (checked)
     */
    private boolean isValidSearch() {
        boolean enabled = false;
        for (FileSearchFilter filter : this.getFilters()) {
            if (filter.isEnabled()) {
                enabled = true;
                if (!filter.isValid()) {
                    errorLabel.setText(filter.getLastError());
                    return false;
                }
            }
        }

        errorLabel.setText("");
        return enabled;
    }

    /**
     * Action when the "Search" button is pressed.
     *
     */
    @NbBundle.Messages("FileSearchPanel.emptyNode.display.text=No results found.")
    private void search() {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (this.isValidSearch()) {
                String title = NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.results.title", ++resultWindowCount);
                String pathText = NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.results.pathText");

                // try to get the number of matches first
                Case currentCase = Case.getCurrentCaseThrows(); // get the most updated case
                long totalMatches = 0;
                List<AbstractFile> contentList = null;
                try {
                    SleuthkitCase tskDb = currentCase.getSleuthkitCase();
                    contentList = tskDb.findAllFilesWhere(this.getQuery());

                } catch (TskCoreException ex) {
                    Logger logger = Logger.getLogger(this.getClass().getName());
                    logger.log(Level.WARNING, "Error while trying to get the number of matches.", ex); //NON-NLS
                }

                if (contentList == null) {
                    contentList = Collections.<AbstractFile>emptyList();
                }

                SearchNode sn = new SearchNode(contentList);
                TableFilterNode tableFilterNode = new TableFilterNode(sn, true, sn.getName());
                final TopComponent searchResultWin;
                if (contentList.isEmpty()) {
                    Node emptyNode = new TableFilterNode(new EmptyNode(Bundle.FileSearchPanel_emptyNode_display_text()), true);
                    searchResultWin = DataResultTopComponent.createInstance(title, pathText,
                        emptyNode, 0);
                } else {
                    searchResultWin = DataResultTopComponent.createInstance(title, pathText,
                        tableFilterNode, contentList.size());
                }
                searchResultWin.requestActive(); // make it the active top component

                /**
                 * If total matches more than 1000, pop up a dialog box that say
                 * the performance maybe be slow and to increase the
                 * performance, tell the users to refine their search.
                 */
                if (totalMatches > 10000) {
                    // show info
                    String msg = NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.results.msg", totalMatches);
                    String details = NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.results.details");
                    MessageNotifyUtil.Notify.info(msg, details);
                }
            } else {
                throw new FilterValidationException(
                        NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.exception.noFilterSelected.msg"));
            }
        } catch (FilterValidationException | NoCurrentCaseException ex) {
            NotifyDescriptor d = new NotifyDescriptor.Message(
                    NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.validationErr.msg", ex.getMessage()));
            DialogDisplayer.getDefault().notify(d);
        } finally {
            this.setCursor(null);
        }
    }

    /**
     * Gets the SQL query to get the data from the database based on the
     * criteria that user chooses on the FileSearch.
     *
     * @return query the SQL query
     *
     * @throws
     * org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException
     *                                                                           if
     *                                                                           an
     *                                                                           enabled
     *                                                                           filter
     *                                                                           is
     *                                                                           in
     *                                                                           an
     *                                                                           invalid
     *                                                                           state
     */
    private String getQuery() throws FilterValidationException {

        //String query = "SELECT " + tempQuery + " FROM tsk_files WHERE ";
        String query = "";
        int i = 0;
        for (FileSearchFilter f : this.getEnabledFilters()) {
            String result = f.getPredicate();
            if (!result.isEmpty()) {
                if (i > 0) {
                    query += " AND (" + result + ")"; //NON-NLS
                } else {
                    query += " (" + result + ")"; //NON-NLS
                }
                ++i;
            }
        }

        if (query.isEmpty()) {
            throw new FilterValidationException(EMPTY_WHERE_CLAUSE);
        }
        return query;
    }

    private Collection<FileSearchFilter> getFilters() {
        return filters;
    }

    private Collection<FileSearchFilter> getEnabledFilters() {
        Collection<FileSearchFilter> enabledFilters = new ArrayList<>();

        for (FileSearchFilter f : this.getFilters()) {
            if (f.isEnabled()) {
                enabledFilters.add(f);
            }
        }

        return enabledFilters;
    }

    void addListenerToAll(ActionListener l) {
        searchButton.addActionListener(l);
        for (FileSearchFilter fsf : getFilters()) {
            fsf.addActionListener(l);
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

        filterPanel = new javax.swing.JPanel();
        searchButton = new javax.swing.JButton();
        errorLabel = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(300, 300));

        filterPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        filterPanel.setPreferredSize(new java.awt.Dimension(300, 400));
        filterPanel.setLayout(new javax.swing.BoxLayout(filterPanel, javax.swing.BoxLayout.Y_AXIS));

        searchButton.setText(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.searchButton.text")); // NOI18N

        errorLabel.setText(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.errorLabel.text")); // NOI18N
        errorLabel.setForeground(new java.awt.Color(255, 51, 51));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(filterPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(searchButton)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(filterPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 266, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(searchButton)
                    .addComponent(errorLabel))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel errorLabel;
    private javax.swing.JPanel filterPanel;
    private javax.swing.JButton searchButton;
    // End of variables declaration//GEN-END:variables
}
