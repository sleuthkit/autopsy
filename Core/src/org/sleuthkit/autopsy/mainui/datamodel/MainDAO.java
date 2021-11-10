/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.collect.ImmutableList;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.PreferenceChangeListener;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * Main entry point for DAO for providing data to populate the data results
 * viewer.
 */
public class MainDAO extends AbstractDAO {

    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS = EnumSet.of(IngestManager.IngestModuleEvent.CONTENT_CHANGED, IngestManager.IngestModuleEvent.DATA_ADDED);
    private static final Set<Case.Events> CASE_EVENTS = EnumSet.of(Case.Events.CURRENT_CASE);
    private static final long MILLIS_BATCH = 5000;

    private static MainDAO instance = null;

    public synchronized static MainDAO getInstance() {
        if (instance == null) {
            instance = new MainDAO();
            instance.register();
        }

        return instance;
    }

    /**
     * The case event listener.
     */
    private final PropertyChangeListener caseEventListener = (evt) -> {
        if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
            this.clearCaches();
        } else {
            handleAutopsyEvent(evt);
        }
    };

    /**
     * The user preference listener.
     */
    private final PreferenceChangeListener userPreferenceListener = (evt) -> {
        this.clearCaches();
    };

    /**
     * The ingest module event listener.
     */
    private final PropertyChangeListener ingestModuleEventListener = (evt) -> {
        handleAutopsyEvent(evt);
    };

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private final DAOEventBatcher<PropertyChangeEvent> eventBatcher = new DAOEventBatcher<>((evts) -> this.getDAOEventsAndFire(evts), MILLIS_BATCH);

    private final DataArtifactDAO dataArtifactDAO = DataArtifactDAO.getInstance();
    private final AnalysisResultDAO analysisResultDAO = AnalysisResultDAO.getInstance();
    private final ViewsDAO viewsDAO = ViewsDAO.getInstance();
    private final FileSystemDAO fileSystemDAO = FileSystemDAO.getInstance();
    private final TagsDAO tagsDAO = TagsDAO.getInstance();
    private final OsAccountsDAO accountsDAO = OsAccountsDAO.getInstance();

    // NOTE: whenever adding a new sub-dao, it should be added to this list for event updates.
    private final List<AbstractDAO> allDAOs = ImmutableList.of(
            dataArtifactDAO,
            analysisResultDAO,
            viewsDAO,
            fileSystemDAO,
            tagsDAO,
            accountsDAO);

    public DataArtifactDAO getDataArtifactsDAO() {
        return dataArtifactDAO;
    }

    public AnalysisResultDAO getAnalysisResultDAO() {
        return analysisResultDAO;
    }

    public ViewsDAO getViewsDAO() {
        return viewsDAO;
    }

    public FileSystemDAO getFileSystemDAO() {
        return fileSystemDAO;
    }

    public TagsDAO getTagsDAO() {
        return tagsDAO;
    }

    public OsAccountsDAO getOsAccountsDAO() {
        return accountsDAO;
    }

    @Override
    void clearCaches() {
        allDAOs.forEach((subDAO) -> subDAO.clearCaches());
    }

    @Override
    List<DAOEvent> handleAutopsyEvent(Collection<PropertyChangeEvent> evt) {
        return allDAOs.stream()
                .map(subDAO -> subDAO.handleAutopsyEvent(evt))
                .flatMap(evts -> evts == null ? Stream.empty() : evts.stream())
                .collect(Collectors.toList());
    }

    /**
     * Determines DAO events from autopsy events and fires DAO aggregate event
     * if there are any created DAO events.
     *
     * @param evts The autopsy events.
     */
    private void getDAOEventsAndFire(Collection<PropertyChangeEvent> evts) {
        List<DAOEvent> daoEvents = handleAutopsyEvent(evts);
        if (!CollectionUtils.isEmpty(daoEvents)) {
            support.firePropertyChange(new PropertyChangeEvent(this, "DATA_CHANGE", null, new DAOAggregateEvent(daoEvents)));
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    /**
     * Registers listeners with autopsy event publishers.
     */
    void register() {
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS, ingestModuleEventListener);
        Case.addEventTypeSubscriber(CASE_EVENTS, caseEventListener);
        UserPreferences.addChangeListener(userPreferenceListener);
    }

    @Override
    protected void finalize() throws Throwable {
        unregister();
    }

    /**
     * Unregisters listeners from autopsy event publishers.
     */
    void unregister() {
        IngestManager.getInstance().removeIngestModuleEventListener(INGEST_MODULE_EVENTS, ingestModuleEventListener);
        Case.removeEventTypeSubscriber(CASE_EVENTS, caseEventListener);
        UserPreferences.removeChangeListener(userPreferenceListener);
    }

    /**
     * Handle incoming autopsy event by queueing in batch and firing events.
     *
     * @param autopsyEvent The autopsy event.
     */
    private void handleAutopsyEvent(PropertyChangeEvent autopsyEvent) {
        this.eventBatcher.queueEvent(autopsyEvent);
    }
}
