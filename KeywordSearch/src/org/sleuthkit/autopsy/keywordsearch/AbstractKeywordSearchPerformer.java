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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;

/**
 *
 * @author dfickling
 */
abstract class AbstractKeywordSearchPerformer extends javax.swing.JPanel implements KeywordSearchPerformerInterface{

    int filesIndexed;
    
    AbstractKeywordSearchPerformer() {
        initListeners();
    }

    private void initListeners() {
        KeywordSearch.changeSupport.addPropertyChangeListener(KeywordSearch.NUM_FILES_CHANGE_EVT,
                new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        String changed = evt.getPropertyName();
                        Object oldValue = evt.getOldValue();
                        Object newValue = evt.getNewValue();

                        if (changed.equals(KeywordSearch.NUM_FILES_CHANGE_EVT)) {
                            int newFilesIndexed = ((Integer) newValue).intValue();
                            filesIndexed = newFilesIndexed;
                        }
                    }
                });
    }

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
        KeywordSearchQueryManager man = null;
        if (isMultiwordQuery()) {
            final List<Keyword> keywords = getQueryList();
            if (keywords.isEmpty()) {
                KeywordSearchUtil.displayDialog("Keyword Search Error", "Keyword list is empty, please add at least one keyword to the list", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }
            man = new KeywordSearchQueryManager(keywords, Presentation.COLLAPSE);
        } else {
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
            man = new KeywordSearchQueryManager(getQueryText(), queryType, Presentation.COLLAPSE);
        }

        if (man.validate()) {
            man.execute();
        } else {
            KeywordSearchUtil.displayDialog("Keyword Search Error", "Invalid query syntax.", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
        }
    }
    
}
