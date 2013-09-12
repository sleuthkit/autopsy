/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2013 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;

/**
 * Common functionality among keyword search performers / widgets.
 * This is extended by the various panels and interfaces that perform the keyword searches.
 */
abstract class AbstractKeywordSearchPerformer extends javax.swing.JPanel implements KeywordSearchPerformerInterface {

    protected int filesIndexed;

    AbstractKeywordSearchPerformer() {
        initListeners();
    }

    private void initListeners() {
        KeywordSearch.addNumIndexedFilesChangeListener(
                new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        String changed = evt.getPropertyName();
                        Object newValue = evt.getNewValue();

                        if (changed.equals(KeywordSearch.NUM_FILES_CHANGE_EVT)) {
                            int newFilesIndexed = ((Integer) newValue).intValue();
                            filesIndexed = newFilesIndexed;
                            postFilesIndexedChange();
                        }
                    }
                });
    }

    /**
     * Hook to run after indexed files number changed
     */
    protected abstract void postFilesIndexedChange();
    
    @Override
    public abstract boolean isMultiwordQuery();

    @Override
    public abstract boolean isLuceneQuerySelected();

    @Override
    public abstract String getQueryText();

    @Override
    public abstract List<Keyword> getQueryList();

    @Override
    public void setFilesIndexed(int filesIndexed) {
        this.filesIndexed = filesIndexed;
    }

    @Override
    public void search() {
        boolean isRunning = IngestManager.getDefault().isModuleRunning(KeywordSearchIngestModule.getDefault());
        
        if (filesIndexed == 0) {
            if (isRunning) {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "<html>No files are in index yet. <br />"
                        + "Try again later.  Index is updated every " + KeywordSearchSettings.getUpdateFrequency().getTime() + " minutes.</html>", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            }
            else {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "<html>No files were indexed.<br />"
                        + "Re-ingest the image with the Keyword Search Module enabled. </html>", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            }
            return;
        }

        //check if keyword search module  ingest is running (indexing, etc)
        if (isRunning) {
            if (KeywordSearchUtil.displayConfirmDialog("Keyword Search Ingest in Progress",
                    "<html>Keyword Search Ingest is currently running.<br />"
                    + "Not all files have been indexed and this search might yield incomplete results.<br />"
                    + "Do you want to proceed with this search anyway?</html>", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN) == false) {
                return;
            }
        }

        KeywordSearchQueryManager man = null;
        if (isMultiwordQuery()) {
            final List<Keyword> keywords = getQueryList();
            if (keywords.isEmpty()) {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "Keyword list is empty, please add at least one keyword to the list", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }
            man = new KeywordSearchQueryManager(keywords, Presentation.FLAT);
        } 
        else {
            QueryType queryType = null;
            if (isLuceneQuerySelected()) {
                queryType = QueryType.WORD;
            } else {
                queryType = QueryType.REGEX;
            }
            final String queryText = getQueryText();
            if (queryText == null || queryText.trim().equals("")) {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "Please enter a keyword to search for", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }
            man = new KeywordSearchQueryManager(getQueryText(), queryType, Presentation.FLAT);
        }

        if (man.validate()) {
            man.execute();
        } else {
            KeywordSearchUtil.displayDialog("Keyword Search Error", "Invalid query syntax.", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
        }
    }
}
