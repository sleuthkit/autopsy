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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JOptionPane;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;

/**
 * Provides a data explorer to perform Solr searches with
 */
@ServiceProvider(service = DataExplorer.class, position = 300)
public class KeywordSearchDataExplorer implements DataExplorer {

    private static KeywordSearchDataExplorer theInstance;
    private KeywordSearchTabsTopComponent tc;

    public KeywordSearchDataExplorer() {
        this.setTheInstance();
        this.tc = new KeywordSearchTabsTopComponent();

        this.tc.addSearchButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tc.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                QueryType queryType = null;
                if (tc.isLuceneQuerySelected()) {
                    queryType = QueryType.WORD;
                } else {
                    queryType = QueryType.REGEX;
                }
                try {
                    search(tc.getQueryText(), queryType);
                } finally {
                    tc.setCursor(null);
                }
            }
        });

        KeywordSearch.changeSupport.addPropertyChangeListener(KeywordSearch.NUM_FILES_CHANGE_EVT, new IndexChangeListener());
    }
    
    

    private synchronized void setTheInstance() {
        if (theInstance == null) {
            theInstance = this;
        } else {
            throw new RuntimeException("Tried to instantiate mulitple instances of KeywordSearchTopComponent.");
        }
    }

    /**
     * Executes a query and populates a DataResult tab with the results
     * @param solrQuery 
     */
    private void search(String query, QueryType queryType) {
        KeywordSearchQueryManager man = new KeywordSearchQueryManager(query, queryType, Presentation.DETAIL);

        if (man.validate()) {
            man.execute();
        } else {
            displayErrorDialog("Invalid query syntax: " + query);
        }

    }


    @Override
    public org.openide.windows.TopComponent getTopComponent() {
        return this.tc;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    private void displayErrorDialog(final String message) {
        final Component parentComponent = null; // Use default window frame.
        final String title = "Keyword Search Error";
        final int messageType = JOptionPane.ERROR_MESSAGE;
        JOptionPane.showMessageDialog(
                parentComponent,
                message,
                title,
                messageType);
    }

    class IndexChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {

            String changed = evt.getPropertyName();
            //Object oldValue = evt.getOldValue();
            Object newValue = evt.getNewValue();

            if (newValue != null) {
                if (changed.equals(KeywordSearch.NUM_FILES_CHANGE_EVT)) {
                    int newFilesIndexed = ((Integer) newValue).intValue();
                    tc.setFilesIndexed(newFilesIndexed);

                } else {
                    String msg = "Unsupported change event: " + changed;
                    throw new UnsupportedOperationException(msg);
                }
            }
        }
    }
}
