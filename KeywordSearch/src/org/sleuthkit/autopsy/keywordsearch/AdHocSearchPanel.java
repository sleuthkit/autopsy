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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.DataSource;
import javax.swing.DefaultListModel;
/**
 * Common functionality among keyword search widgets / panels. This is extended
 * by the various panels and interfaces that perform the keyword searches. This
 * class and extended classes model the user's intentions, not necessarily how
 * the search manager and 3rd party tools actually perform the search.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
abstract class AdHocSearchPanel extends javax.swing.JPanel {

    private final String keywordSearchErrorDialogHeader = org.openide.util.NbBundle.getMessage(this.getClass(), "AbstractKeywordSearchPerformer.search.dialogErrorHeader");
    protected int filesIndexed;
    private final Map<Long, String> dataSourceMap = new HashMap<>();
    private List<DataSource> dataSources = new ArrayList<>();
    private final DefaultListModel<String> dataSourceListModel = new DefaultListModel<>();

    AdHocSearchPanel() {
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
                            int newFilesIndexed = ((Integer) newValue);
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

    /**
     * Returns the list of Keyword objects that the user entered/selected
     *
     * @return
     */
    abstract List<KeywordList> getKeywordLists();

    /**
     * Get a set of data source object ids that are selected.
     *
     * @return A set of selected object ids.
     */
    abstract Set<Long> getDataSourcesSelected();

    /**
     * Set the number of files that have been indexed
     *
     * @param filesIndexed
     */
    public void setFilesIndexed(int filesIndexed) {
        this.filesIndexed = filesIndexed;
    }

    /**
     * Performs the search using the selected keywords. Creates a
     * DataResultTopComponent with the results.
     *
     * @param saveResults Flag whether to save search results as KWS artifacts.
     */
    public void search(boolean saveResults) {
        boolean isIngestRunning = IngestManager.getInstance().isIngestRunning();

        if (filesIndexed == 0) {
            try { // see if another node added any indexed files
                filesIndexed = KeywordSearch.getServer().queryNumIndexedFiles();
            } catch (KeywordSearchModuleException | NoOpenCoreException ignored) {
            }
        }
        if (filesIndexed == 0) {
            if (isIngestRunning) {
                KeywordSearchUtil.displayDialog(keywordSearchErrorDialogHeader, NbBundle.getMessage(this.getClass(),
                        "AbstractKeywordSearchPerformer.search.noFilesInIdxMsg",
                        KeywordSearchSettings.getUpdateFrequency().getTime()), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            } else {
                KeywordSearchUtil.displayDialog(keywordSearchErrorDialogHeader, NbBundle.getMessage(this.getClass(),
                        "AbstractKeywordSearchPerformer.search.noFilesIdxdMsg"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            }
            return;
        }

        //check if keyword search module  ingest is running (indexing, etc)
        if (isIngestRunning) {
            if (KeywordSearchUtil.displayConfirmDialog(org.openide.util.NbBundle.getMessage(this.getClass(), "AbstractKeywordSearchPerformer.search.searchIngestInProgressTitle"),
                    NbBundle.getMessage(this.getClass(), "AbstractKeywordSearchPerformer.search.ingestInProgressBody"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN) == false) {
                return;
            }
        }

        final List<KeywordList> keywordLists = getKeywordLists();
        if (keywordLists.isEmpty()) {
            KeywordSearchUtil.displayDialog(keywordSearchErrorDialogHeader, NbBundle.getMessage(this.getClass(),
                    "AbstractKeywordSearchPerformer.search.emptyKeywordErrorBody"),
                    KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            return;
        }
      
        AdHocSearchDelegator man = new AdHocSearchDelegator(keywordLists, getDataSourcesSelected());
        if (man.validate()) {
            man.execute(saveResults);
        } else {
            KeywordSearchUtil.displayDialog(keywordSearchErrorDialogHeader, NbBundle.getMessage(this.getClass(),
                    "AbstractKeywordSearchPerformer.search.invalidSyntaxHeader"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
        }
    }

    /**
     * Get a list of data source display names.
     *
     * @return The list of data source name
     */
    synchronized List<String> getDataSourceArray() {
        List<String> dsList = new ArrayList<>();
        Collections.sort(this.dataSources, (DataSource ds1, DataSource ds2) -> ds1.getName().compareTo(ds2.getName()));
        for (DataSource ds : dataSources) {
            String dsName = ds.getName();
            File dataSourceFullName = new File(dsName);
            String displayName = dataSourceFullName.getName();
            dataSourceMap.put(ds.getId(), displayName);
            dsList.add(displayName);
        }
        return dsList;
    }

    /**
     * Set dataSources
     * @param dataSources A list of DataSource 
     */
    synchronized void setDataSources(List<DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    /**
     * Get dataSourceMap with object id and data source display name.
     *
     * @return The list of data source name
     */
    Map<Long, String> getDataSourceMap() {
        return dataSourceMap;
    }
    
    /**
     * Get a list of DataSourceListModel
     * @return A list of DataSourceListModel
     */
    final DefaultListModel<String> getDataSourceListModel() {
        return dataSourceListModel;
    }
}
