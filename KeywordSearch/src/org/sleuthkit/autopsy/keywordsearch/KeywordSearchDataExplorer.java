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

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;

/**
 * Provides a data explorer to perform Solr searches with
 */
public class KeywordSearchDataExplorer implements DataExplorer {

    private static KeywordSearchDataExplorer theInstance = null;
    private KeywordSearchTabsTopComponent tc;
    private int filesIndexed;

    private KeywordSearchDataExplorer() {
        this.filesIndexed = 0;
        this.tc = KeywordSearchTabsTopComponent.findInstance();

        this.tc.addSearchButtonListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (filesIndexed == 0)
                    return;
                
                tc.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                try {
                    search();
                } finally {
                    tc.setCursor(null);
                }
            }
        });

        KeywordSearch.changeSupport.addPropertyChangeListener(KeywordSearch.NUM_FILES_CHANGE_EVT, new IndexChangeListener());
    }

    public static synchronized KeywordSearchDataExplorer getDefault() {
        if (theInstance == null) {
            theInstance = new KeywordSearchDataExplorer();
        } 
        return theInstance;
    }

    /**
     * Executes a query and populates a DataResult tab with the results
     * @param solrQuery 
     */
    private void search() {
        KeywordSearchQueryManager man = null;
        if (tc.isMultiwordQuery()) {
            final List<Keyword> keywords = tc.getQueryList();
            if (keywords.isEmpty()) {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "Keyword list is empty, please add at least one keyword to the list", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }
            man = new KeywordSearchQueryManager(keywords, Presentation.COLLAPSE);
        } else {
            QueryType queryType = null;
            if (tc.isLuceneQuerySelected()) {
                queryType = QueryType.WORD;
            } else {
                queryType = QueryType.REGEX;
            }
            final String queryText = tc.getQueryText();
            if (queryText == null || queryText.trim().equals("")) {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "Please enter a keyword to search for", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return; 
            }
            man = new KeywordSearchQueryManager(tc.getQueryText(), queryType, Presentation.COLLAPSE);
        }

        if (man.validate()) {
            man.execute();
        } else {
            KeywordSearchUtil.displayDialog("Keyword Search Error", "Invalid query syntax.", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
        }

    }

    @Override
    public org.openide.windows.TopComponent getTopComponent() {
        return this.tc;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
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
                    filesIndexed = newFilesIndexed;
                    tc.setFilesIndexed(newFilesIndexed);

                } else {
                    String msg = "Unsupported change event: " + changed;
                    throw new UnsupportedOperationException(msg);
                }
            }
        }
    }
}
