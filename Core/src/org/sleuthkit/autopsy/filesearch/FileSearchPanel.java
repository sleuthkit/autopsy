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

/*
 * FileSearchPanel.java
 *
 * Created on Mar 5, 2012, 1:51:50 PM
 */
package org.sleuthkit.autopsy.filesearch;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * FileSearchPanel that present search options
 */
 class FileSearchPanel extends javax.swing.JPanel {

    private List<FilterArea> filterAreas = new ArrayList<FilterArea>();
    private JButton searchButton;
    private static int resultWindowCount = 0; //keep track of result windows so they get unique names

    /**
     * Creates new form FileSearchPanel
     */
    public FileSearchPanel() {
        initComponents();
        customizeComponents();
      
    }

    /**
     * This method is called from within the constructor to initialize the form.
     */
    private void customizeComponents() {

        this.setLayout(new BorderLayout());

        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        this.add(filterPanel, BorderLayout.CENTER);

        JLabel label = new JLabel(NbBundle.getMessage(this.getClass(), "FileSearchPanel.custComp.label.text"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 10, 0));
        filterPanel.add(label);

        // Create and add filter areas
        this.filterAreas.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.name"), new NameSearchFilter()));

        List<FileSearchFilter> metadataFilters = new ArrayList<FileSearchFilter>();
        metadataFilters.add(new SizeSearchFilter());
        metadataFilters.add(new DateSearchFilter());
        this.filterAreas.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.metadata"), metadataFilters));

        this.filterAreas.add(new FilterArea(NbBundle.getMessage(this.getClass(), "FileSearchPanel.filterTitle.knownStatus"), new KnownStatusSearchFilter()));

        for (FilterArea fa : this.filterAreas) {
            fa.setMaximumSize(new Dimension(Integer.MAX_VALUE, fa.getMinimumSize().height));
            fa.setAlignmentX(Component.LEFT_ALIGNMENT);
            filterPanel.add(fa);
        }

        // Create and add search button
        this.searchButton = new JButton(NbBundle.getMessage(this.getClass(), "FileSearchPanel.searchButton.text"));
        this.searchButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterPanel.add(searchButton);

        addListenerToAll(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });
    }

    /**
     * @return true if any of the filters in the panel are enabled (checked)
     */
    private boolean anyFiltersEnabled() {
        for (FileSearchFilter filter : this.getFilters()) {
            if (filter.isEnabled()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Action when the "Search" button is pressed.
     *
     */
    private void search() {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (this.anyFiltersEnabled()) {
                String title = NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.results.title", ++resultWindowCount);
                String pathText = NbBundle.getMessage(this.getClass(), "FileSearchPanel.search.results.pathText");

                // try to get the number of matches first
                Case currentCase = Case.getCurrentCase(); // get the most updated case
                long totalMatches = 0;
                List<AbstractFile> contentList = null;
                try {
                    SleuthkitCase tskDb = currentCase.getSleuthkitCase();
                    //ResultSet rs = tempDb.runQuery(this.getQuery("count(*) as TotalMatches"));
                    contentList = tskDb.findAllFilesWhere(this.getQuery());

                } catch (TskCoreException ex) {
                    Logger logger = Logger.getLogger(this.getClass().getName());
                    logger.log(Level.WARNING, "Error while trying to get the number of matches.", ex);
                }
                
                if (contentList == null) {
                    contentList = Collections.<AbstractFile>emptyList();
                }

                final TopComponent searchResultWin = DataResultTopComponent.createInstance(title, pathText,
                        new TableFilterNode(new SearchNode(contentList), true), contentList.size());

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
        } catch (FilterValidationException ex) {
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
     * @throws
     * org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException
     * if an enabled filter is in an invalid state
     */
    private String getQuery() throws FilterValidationException {

        //String query = "select " + tempQuery + " from tsk_files where 1";
        String query = " 1";

        for (FileSearchFilter f : this.getEnabledFilters()) {
            query += " and (" + f.getPredicate() + ")";
        }

        return query;
    }

    private Collection<FileSearchFilter> getFilters() {
        Collection<FileSearchFilter> filters = new ArrayList<FileSearchFilter>();

        for (FilterArea fa : this.filterAreas) {
            filters.addAll(fa.getFilters());
        }

        return filters;
    }

    private Collection<FileSearchFilter> getEnabledFilters() {
        Collection<FileSearchFilter> enabledFilters = new ArrayList<FileSearchFilter>();

        for (FileSearchFilter f : this.getFilters()) {
            if (f.isEnabled()) {
                enabledFilters.add(f);
            }
        }

        return enabledFilters;
    }

    void addListenerToAll(ActionListener l) {
        searchButton.addActionListener(l);
        for (FilterArea fa : this.filterAreas) {
            for (FileSearchFilter fsf : fa.getFilters()) {
                fsf.addActionListener(l);
            }
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 376, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
