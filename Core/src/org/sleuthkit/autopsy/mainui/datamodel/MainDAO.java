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

import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventBatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeListener;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.python.google.common.collect.ImmutableSet;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;

/**
 * Main entry point for DAO for providing data to populate the data results
 * viewer.
 */
public class MainDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(MainDAO.class.getName());

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS = EnumSet.of(
            IngestManager.IngestJobEvent.COMPLETED,
            IngestManager.IngestJobEvent.CANCELLED
    );

    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS = EnumSet.of(
            IngestManager.IngestModuleEvent.CONTENT_CHANGED,
            IngestManager.IngestModuleEvent.DATA_ADDED,
            IngestManager.IngestModuleEvent.FILE_DONE
    );

    private static final Set<String> QUEUED_CASE_EVENTS = ImmutableSet.of(
            Case.Events.OS_ACCOUNTS_ADDED.toString(),
            Case.Events.OS_ACCOUNTS_UPDATED.toString(),
            Case.Events.OS_ACCOUNTS_DELETED.toString(),
            Case.Events.OS_ACCT_INSTANCES_ADDED.toString()
    );

    private static final long WATCH_RESOLUTION_MILLIS = 30 * 1000;

    private static final long RESULT_BATCH_MILLIS = 5 * 1000;

    private static MainDAO instance = null;

    public synchronized static MainDAO getInstance() {
        if (instance == null) {
            instance = new MainDAO();
            instance.init();
        }

        return instance;
    }

    /**
     * The case event listener.
     */
    private final PropertyChangeListener caseEventListener = (evt) -> {
        try {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                this.clearCaches();
            } else if (QUEUED_CASE_EVENTS.contains(evt.getPropertyName())) {
                handleEvent(evt, false);
            } else {
                // handle case events immediately
                handleEvent(evt, true);
            }
        } catch (Throwable ex) {
            // firewall exception
            logger.log(Level.WARNING, "An exception occurred while handling case events", ex);
        }
    };

    /**
     * The user preference listener.
     */
    private final PreferenceChangeListener userPreferenceListener = (evt) -> {
        try {
            this.clearCaches();
        } catch (Throwable ex) {
            // firewall exception
            logger.log(Level.WARNING, "An exception occurred while handling user preference change", ex);
        }

    };

    /**
     * The ingest module event listener.
     */
    private final PropertyChangeListener ingestModuleEventListener = (evt) -> {
        try {
            handleEvent(evt, false);
        } catch (Throwable ex) {
            // firewall exception
            logger.log(Level.WARNING, "An exception occurred while handling ingest module event", ex);
        }
    };

    /**
     * The ingest job event listener.
     */
    private final PropertyChangeListener ingestJobEventListener = (evt) -> {
        try {
            handleEventFlush();
        } catch (Throwable ex) {
            // firewall exception
            logger.log(Level.WARNING, "An exception occurred while handling ingest job event", ex);
        }

    };

    private final ScheduledThreadPoolExecutor timeoutExecutor
            = new ScheduledThreadPoolExecutor(1,
                    new ThreadFactoryBuilder().setNameFormat(MainDAO.class.getName()).build());

    private final PropertyChangeManager resultEventsManager = new PropertyChangeManager();
    private final PropertyChangeManager treeEventsManager = new PropertyChangeManager();

    private final DAOEventBatcher<DAOEvent> eventBatcher = new DAOEventBatcher<>(
            (evts) -> {
                try {
                    fireResultEvts(evts);
                } catch (Throwable ex) {
                    // firewall exception
                    logger.log(Level.WARNING, "An exception occurred while handling batched dao events", ex);
                }
            },
            RESULT_BATCH_MILLIS);

    private final DataArtifactDAO dataArtifactDAO = DataArtifactDAO.getInstance();
    private final AnalysisResultDAO analysisResultDAO = AnalysisResultDAO.getInstance();
    private final ViewsDAO viewsDAO = ViewsDAO.getInstance();
    private final FileSystemDAO fileSystemDAO = FileSystemDAO.getInstance();
    private final TagsDAO tagsDAO = TagsDAO.getInstance();
    private final OsAccountsDAO osAccountsDAO = OsAccountsDAO.getInstance();
    private final CommAccountsDAO commAccountsDAO = CommAccountsDAO.getInstance();

    // NOTE: whenever adding a new sub-dao, it should be added to this list for event updates.
    private final List<AbstractDAO> allDAOs = ImmutableList.of(
            dataArtifactDAO,
            analysisResultDAO,
            viewsDAO,
            fileSystemDAO,
            tagsDAO,
            osAccountsDAO,
            commAccountsDAO);

    /**
     * Registers listeners with autopsy event publishers and starts internal
     * threads.
     */
    void init() {
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS, ingestModuleEventListener);
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS, ingestJobEventListener);
        Case.addPropertyChangeListener(caseEventListener);
        UserPreferences.addChangeListener(userPreferenceListener);

        this.timeoutExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        handleTreeEventTimeouts();
                    } catch (Throwable ex) {
                        // firewall exception
                        logger.log(Level.WARNING, "An exception occurred while handling tree event timeouts", ex);
                    }
                },
                WATCH_RESOLUTION_MILLIS,
                WATCH_RESOLUTION_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Unregisters listeners from autopsy event publishers.
     */
    void unregister() {
        IngestManager.getInstance().removeIngestModuleEventListener(INGEST_MODULE_EVENTS, ingestModuleEventListener);
        IngestManager.getInstance().removeIngestJobEventListener(INGEST_JOB_EVENTS, ingestJobEventListener);
        Case.removePropertyChangeListener(caseEventListener);
        UserPreferences.removeChangeListener(userPreferenceListener);
    }

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
        return osAccountsDAO;
    }

    public CommAccountsDAO getCommAccountsDAO() {
        return commAccountsDAO;
    }

    public PropertyChangeManager getResultEventsManager() {
        return this.resultEventsManager;
    }

    public PropertyChangeManager getTreeEventsManager() {
        return treeEventsManager;
    }

    @Override
    void clearCaches() {
        allDAOs.forEach((subDAO) -> subDAO.clearCaches());
    }

    @Override
    List<DAOEvent> processEvent(PropertyChangeEvent evt) {
        return allDAOs.stream()
                .map(subDAO -> subDAO.processEvent(evt))
                .flatMap(evts -> evts == null ? Stream.empty() : evts.stream())
                .collect(Collectors.toList());
    }

    @Override
    List<TreeEvent> shouldRefreshTree() {
        return allDAOs.stream()
                .map((subDAO) -> subDAO.shouldRefreshTree())
                .flatMap(evts -> evts == null ? Stream.empty() : evts.stream())
                .collect(Collectors.toList());
    }

    @Override
    Collection<? extends DAOEvent> flushEvents() {
        Stream<Collection<? extends DAOEvent>> daoStreamEvts = allDAOs.stream()
                .map((subDAO) -> subDAO.flushEvents());

        Collection<DAOEvent> batchFlushedEvts = eventBatcher.flushEvents();

        return Stream.concat(daoStreamEvts, Stream.of(batchFlushedEvts))
                .flatMap(evts -> evts == null ? Stream.empty() : evts.stream())
                .collect(Collectors.toList());
    }

    private void handleEvent(PropertyChangeEvent evt, boolean immediateAction) {
        Collection<DAOEvent> daoEvts = processEvent(evt);

        Map<DAOEvent.Type, List<DAOEvent>> daoEvtsByType = daoEvts.stream()
                .collect(Collectors.groupingBy(e -> e.getType()));

        fireTreeEvts(daoEvtsByType.get(DAOEvent.Type.TREE));

        List<DAOEvent> resultEvts = daoEvtsByType.get(DAOEvent.Type.RESULT);
        if (immediateAction) {
            fireResultEvts(resultEvts);
        } else {
            eventBatcher.enqueueAllEvents(resultEvts);
        }
    }

    private void handleEventFlush() {
        Collection<? extends DAOEvent> daoEvts = flushEvents();

        Map<DAOEvent.Type, List<DAOEvent>> daoEvtsByType = daoEvts.stream()
                .collect(Collectors.groupingBy(e -> e.getType()));

        fireTreeEvts(daoEvtsByType.get(DAOEvent.Type.TREE));

        List<DAOEvent> resultEvts = daoEvtsByType.get(DAOEvent.Type.RESULT);
        fireResultEvts(resultEvts);
    }

    private void fireResultEvts(Collection<DAOEvent> resultEvts) {
        if (CollectionUtils.isNotEmpty(resultEvts)) {
            resultEventsManager.firePropertyChange("DATA_CHANGE", null, new DAOAggregateEvent(resultEvts));
        }
    }

    private void fireTreeEvts(Collection<? extends DAOEvent> treeEvts) {
        if (CollectionUtils.isNotEmpty(treeEvts)) {
            treeEventsManager.firePropertyChange("TREE_CHANGE", null, new DAOAggregateEvent(treeEvts));
        }
    }

    private void handleTreeEventTimeouts() {
        fireTreeEvts(this.shouldRefreshTree());
    }

    @Override
    protected void finalize() throws Throwable {
        unregister();
    }

    /**
     * A wrapper around property change support that exposes
     * addPropertyChangeListener and removePropertyChangeListener so that
     * netbeans weak listeners can automatically unregister.
     */
    public static class PropertyChangeManager {

        private final PropertyChangeSupport support = new PropertyChangeSupport(this);

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            support.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            support.removePropertyChangeListener(listener);
        }

        PropertyChangeListener[] getPropertyChangeListeners() {
            return support.getPropertyChangeListeners();
        }

        void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
            support.firePropertyChange(propertyName, oldValue, newValue);
        }
    }
}
