/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.Content;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Manages the execution of ingest jobs.
 */
public class IngestManager {

    private static final int MAX_NUMBER_OF_DATA_SOURCE_INGEST_THREADS = 1;
    private static final int MIN_NUMBER_OF_FILE_INGEST_THREADS = 1;
    private static final int MAX_NUMBER_OF_FILE_INGEST_THREADS = 16;
    private static final int DEFAULT_NUMBER_OF_FILE_INGEST_THREADS = 2;
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static final IngestManager instance = new IngestManager();
    private final PropertyChangeSupport ingestJobEventPublisher = new PropertyChangeSupport(IngestManager.class);
    private final PropertyChangeSupport ingestModuleEventPublisher = new PropertyChangeSupport(IngestManager.class);
    private final IngestScheduler scheduler = IngestScheduler.getInstance();
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final ExecutorService startIngestJobsThreadPool = Executors.newSingleThreadExecutor();
    private final ExecutorService dataSourceIngestThreadPool = Executors.newSingleThreadExecutor();
    private final ExecutorService fileIngestThreadPool = Executors.newFixedThreadPool(MAX_NUMBER_OF_FILE_INGEST_THREADS);
    private final ExecutorService fireIngestEventsThreadPool = Executors.newSingleThreadExecutor();
    private final AtomicLong nextThreadId = new AtomicLong(0L);
    private final ConcurrentHashMap<Long, Future<Void>> startIngestJobThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final ConcurrentHashMap<Long, Future<?>> dataSourceIngestThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final ConcurrentHashMap<Long, Future<?>> fileIngestThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private volatile IngestMessageTopComponent ingestMessageBox;

    /**
     * Gets the ingest manager.
     *
     * @returns A singleton IngestManager object.
     */
    public static IngestManager getInstance() {
        return instance;
    }

    /**
     * Starts the ingest monitor and the data source ingest and file ingest
     * threads.
     */
    private IngestManager() {
        startDataSourceIngestThread();
        int numberOfFileIngestThreads = UserPreferences.numberOfFileIngestThreads();
        if ((numberOfFileIngestThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS) || (numberOfFileIngestThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS)) {
            numberOfFileIngestThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
            UserPreferences.setNumberOfFileIngestThreads(numberOfFileIngestThreads);
        }
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            startFileIngestThread();
        }

        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                if (evt.getKey().equals(UserPreferences.NUMBER_OF_FILE_INGEST_THREADS)) {
                    setNumberOfFileIngestThreads();
                }
            }
        });
    }

    /**
     * Signals to the ingest manager that it can go about finding the top
     * component for the ingest messages in box. Called by the custom installer
     * for this package once the window system is initialized.
     */
    void initIngestMessageInbox() {
        if (ingestMessageBox == null) {
            ingestMessageBox = IngestMessageTopComponent.findInstance();
        }
    }

    /**
     * Gets the maximum number of data source ingest threads the ingest manager
     * will use.
     */
    public static int getMaxNumberOfDataSourceIngestThreads() {
        return MAX_NUMBER_OF_DATA_SOURCE_INGEST_THREADS;
    }

    /**
     * Gets the maximum number of file ingest threads the ingest manager will
     * use.
     */
    public static int getMaxNumberOfFileIngestThreads() {
        return MAX_NUMBER_OF_FILE_INGEST_THREADS;
    }

    /**
     * Changes the number of file ingest threads the ingest manager will use to
     * no more than MAX_NUMBER_OF_FILE_INGEST_THREADS and no less than
     * MIN_NUMBER_OF_FILE_INGEST_THREADS. Out of range requests are converted to
     * requests for DEFAULT_NUMBER_OF_FILE_INGEST_THREADS.
     *
     * @param numberOfThreads The desired number of file ingest threads.
     */
    public synchronized static void setNumberOfFileIngestThreads() {
        int numberOfThreads = UserPreferences.numberOfFileIngestThreads();
        if ((numberOfThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS) || (numberOfThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS)) {
            numberOfThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
            UserPreferences.setNumberOfFileIngestThreads(numberOfThreads);
        }
        if (instance.fileIngestThreads.size() != numberOfThreads) {
            if (instance.fileIngestThreads.size() > numberOfThreads) {
                Long[] threadIds = instance.fileIngestThreads.keySet().toArray(new Long[instance.fileIngestThreads.size()]);
                int numberOfThreadsToCancel = instance.fileIngestThreads.size() - numberOfThreads;
                for (int i = 0; i < numberOfThreadsToCancel; ++i) {
                    instance.cancelFileIngestThread(threadIds[i]);
                }
            } else if (instance.fileIngestThreads.size() < numberOfThreads) {
                int numberOfThreadsToAdd = numberOfThreads - instance.fileIngestThreads.size();
                for (int i = 0; i < numberOfThreadsToAdd; ++i) {
                    instance.startFileIngestThread();
                }
            }
        }
    }

    /**
     * Submits a DataSourceIngestThread Runnable to the data source ingest
     * thread pool.
     */
    private void startDataSourceIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        Future<?> handle = dataSourceIngestThreadPool.submit(new ExecuteIngestTasksThread(scheduler.getDataSourceIngestTaskQueue()));
        dataSourceIngestThreads.put(threadId, handle);
    }

    /**
     * Submits a DataSourceIngestThread Runnable to the data source ingest
     * thread pool.
     */
    private void startFileIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        Future<?> handle = fileIngestThreadPool.submit(new ExecuteIngestTasksThread(scheduler.getFileIngestTaskQueue()));
        fileIngestThreads.put(threadId, handle);
    }

    /**
     * Cancels a DataSourceIngestThread Runnable in the file ingest thread pool.
     */
    private void cancelFileIngestThread(long threadId) {
        Future<?> handle = fileIngestThreads.remove(threadId);
        handle.cancel(true);
    }

    synchronized void startIngestJobs(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        if (!isIngestRunning() && ingestMessageBox != null) {
            ingestMessageBox.clearMessages();
        }

        long taskId = nextThreadId.incrementAndGet();
        Future<Void> task = startIngestJobsThreadPool.submit(new StartIngestJobsThread(taskId, dataSources, moduleTemplates, processUnallocatedSpace));
        startIngestJobThreads.put(taskId, task);

        if (ingestMessageBox != null) {
            ingestMessageBox.restoreMessages();
        }
    }

    /**
     * Test if any ingest jobs are in progress.
     *
     * @return True if any ingest jobs are in progress, false otherwise.
     */
    public boolean isIngestRunning() {
        return scheduler.ingestJobsAreRunning();
    }

    public void cancelAllIngestJobs() {
        // Stop creating new ingest jobs.
        for (Future<Void> handle : startIngestJobThreads.values()) {
            handle.cancel(true);
            try {
                // Blocks until the job starting thread responds. The thread
                // removes itself from this collection, which does not disrupt
                // this loop since the collection is a ConcurrentHashMap.
                handle.get();
            } catch (InterruptedException | ExecutionException ex) {
                // This should never happen, something is awry, but everything
                // should be o.k. anyway.
                logger.log(Level.SEVERE, "Unexpected thread interrupt", ex);
            }
        }

        // Cancel all the jobs already created. This will make the the ingest
        // threads flush out any lingering ingest tasks without processing them.
        scheduler.cancelAllIngestJobs();
    }

    /**
     * Ingest events.
     */
    public enum IngestEvent {

        /**
         * Property change event fired when an ingest job is started. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        INGEST_JOB_STARTED,
        /**
         * Property change event fired when an ingest job is completed. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        INGEST_JOB_COMPLETED,
        /**
         * Property change event fired when an ingest job is canceled. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        INGEST_JOB_CANCELLED,
        /**
         * Event sent when an ingest module posts new data to blackboard or
         * somewhere else. Second argument of the property change fired contains
         * ModuleDataEvent object and third argument is null. The object can
         * contain encapsulated new data created by the module. Listener can
         * also query new data as needed.
         */
        DATA,
        /**
         * Event send when content changed, either its attributes changed, or
         * new content children have been added. I.e. from ZIP files or Carved
         * files
         */
        CONTENT_CHANGED,
        /**
         * Event sent when a file has finished going through a pipeline of
         * modules. Second argument is the object ID. Third argument is null
         */
        FILE_DONE,
    };

    /**
     * Add an ingest job event property change listener.
     *
     * @param listener The PropertyChangeListener to register.
     */
    public void addIngestJobEventListener(final PropertyChangeListener listener) {
        ingestJobEventPublisher.addPropertyChangeListener(listener);
    }

    /**
     * Remove an ingest job event property change listener.
     *
     * @param listener The PropertyChangeListener to unregister.
     */
    public void removeIngestJobEventListener(final PropertyChangeListener listener) {
        ingestJobEventPublisher.removePropertyChangeListener(listener);
    }

    /**
     * Add an ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to register.
     */
    public void addIngestModuleEventListener(final PropertyChangeListener listener) {
        ingestModuleEventPublisher.addPropertyChangeListener(listener);
    }

    /**
     * Remove an ingest module event property change listener.
     *
     * @param listener The PropertyChangeListener to unregister.
     */
    public void removeIngestModuleEventListener(final PropertyChangeListener listener) {
        ingestModuleEventPublisher.removePropertyChangeListener(listener);
    }

    /**
     * Add an ingest module event property change listener.
     *
     * @deprecated
     * @param listener The PropertyChangeListener to register.
     */
    public static void addPropertyChangeListener(final PropertyChangeListener listener) {
        instance.ingestJobEventPublisher.addPropertyChangeListener(listener);
        instance.ingestModuleEventPublisher.addPropertyChangeListener(listener);
    }

    /**
     * Remove an ingest module event property change listener.
     *
     * @deprecated
     * @param listener The PropertyChangeListener to unregister.
     */
    public static void removePropertyChangeListener(final PropertyChangeListener listener) {
        instance.ingestJobEventPublisher.removePropertyChangeListener(listener);
        instance.ingestModuleEventPublisher.removePropertyChangeListener(listener);
    }

    /**
     * Fire an ingest event signifying an ingest job started.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobStarted(long ingestJobId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestJobEventPublisher, IngestEvent.INGEST_JOB_STARTED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying an ingest job finished.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCompleted(long ingestJobId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestJobEventPublisher, IngestEvent.INGEST_JOB_COMPLETED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying an ingest job was canceled.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCancelled(long ingestJobId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestJobEventPublisher, IngestEvent.INGEST_JOB_CANCELLED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying the ingest of a file is completed.
     *
     * @param fileId The object id of file.
     */
    void fireFileIngestDone(long fileId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestModuleEventPublisher, IngestEvent.FILE_DONE, fileId, null));
    }

    /**
     * Fire an event signifying a blackboard post by an ingest module.
     *
     * @param moduleDataEvent A ModuleDataEvent with the details of the posting.
     */
    void fireIngestModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestModuleEventPublisher, IngestEvent.DATA, moduleDataEvent, null));
    }

    /**
     * Fire an event signifying discovery of additional content by an ingest
     * module.
     *
     * @param moduleDataEvent A ModuleContentEvent with the details of the new
     * content.
     */
    void fireIngestModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestModuleEventPublisher, IngestEvent.CONTENT_CHANGED, moduleContentEvent, null));
    }

    /**
     * Post a message to the ingest messages in box.
     *
     * @param message The message to be posted.
     */
    void postIngestMessage(IngestMessage message) {
        if (ingestMessageBox != null) {
            ingestMessageBox.displayMessage(message);
        }
    }

    /**
     * Get the free disk space of the drive where to which ingest data is being
     * written, as reported by the ingest monitor.
     *
     * @return Free disk space, -1 if unknown
     */
    long getFreeDiskSpace() {
        if (ingestMonitor != null) {
            return ingestMonitor.getFreeSpace();
        } else {
            return -1;
        }
    }

    /**
     * Creates ingest jobs.
     */
    private class StartIngestJobsThread implements Callable<Void> {

        private final long threadId;
        private final List<Content> dataSources;
        private final List<IngestModuleTemplate> moduleTemplates;
        private final boolean processUnallocatedSpace;
        private ProgressHandle progress;

        StartIngestJobsThread(long threadId, List<Content> dataSources, List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
            this.threadId = threadId;
            this.dataSources = dataSources;
            this.moduleTemplates = moduleTemplates;
            this.processUnallocatedSpace = processUnallocatedSpace;
        }

        @Override
        public Void call() {
            try {
                final String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestManager.StartIngestJobsTask.run.displayName");
                progress = ProgressHandleFactory.createHandle(displayName, new Cancellable() {
                    @Override
                    public boolean cancel() {
                        if (progress != null) {
                            progress.setDisplayName(NbBundle.getMessage(this.getClass(),
                                    "IngestManager.StartIngestJobsTask.run.cancelling",
                                    displayName));
                        }
                        cancelFileIngestThread(threadId);
                        return true;
                    }
                });
                progress.start(dataSources.size());

                if (!ingestMonitor.isRunning()) {
                    ingestMonitor.start();
                }

                int dataSourceProcessed = 0;
                for (Content dataSource : dataSources) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    // Start an ingest job for the data source.
                    List<IngestModuleError> errors = scheduler.startIngestJob(dataSource, moduleTemplates, processUnallocatedSpace);
                    if (!errors.isEmpty()) {
                        // Report the errors to the user. They have already been logged.
                        StringBuilder moduleStartUpErrors = new StringBuilder();
                        for (IngestModuleError error : errors) {
                            String moduleName = error.getModuleDisplayName();
                            moduleStartUpErrors.append(moduleName);
                            moduleStartUpErrors.append(": ");
                            moduleStartUpErrors.append(error.getModuleError().getLocalizedMessage());
                            moduleStartUpErrors.append("\n");
                        }
                        StringBuilder notifyMessage = new StringBuilder();
                        notifyMessage.append(NbBundle.getMessage(this.getClass(),
                                "IngestManager.StartIngestJobsTask.run.startupErr.dlgMsg"));
                        notifyMessage.append("\n");
                        notifyMessage.append(NbBundle.getMessage(this.getClass(),
                                "IngestManager.StartIngestJobsTask.run.startupErr.dlgSolution"));
                        notifyMessage.append("\n");
                        notifyMessage.append(NbBundle.getMessage(this.getClass(),
                                "IngestManager.StartIngestJobsTask.run.startupErr.dlgErrorList",
                                moduleStartUpErrors.toString()));
                        notifyMessage.append("\n\n");
                        JOptionPane.showMessageDialog(null, notifyMessage.toString(),
                                NbBundle.getMessage(this.getClass(),
                                "IngestManager.StartIngestJobsTask.run.startupErr.dlgTitle"), JOptionPane.ERROR_MESSAGE);
                    }
                    progress.progress(++dataSourceProcessed);

                    if (!Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            } finally {
                progress.finish();
                startIngestJobThreads.remove(threadId);
                return null;
            }
        }
    }

    /**
     * A consumer for an ingest task queue.
     */
    private class ExecuteIngestTasksThread implements Runnable {

        private IngestTaskQueue tasks;

        ExecuteIngestTasksThread(IngestTaskQueue tasks) {
            this.tasks = tasks;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    IngestTask task = tasks.getNextTask(); // Blocks.
                    task.execute();
                    scheduler.ingestTaskIsCompleted(task);
                } catch (InterruptedException ex) {
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
    }

    /**
     * Fires ingest events to ingest manager property change listeners.
     */
    private static class FireIngestEventThread implements Runnable {

        private final PropertyChangeSupport publisher;
        private final IngestEvent event;
        private final Object oldValue;
        private final Object newValue;

        FireIngestEventThread(PropertyChangeSupport publisher, IngestEvent event, Object oldValue, Object newValue) {
            this.publisher = publisher;
            this.event = event;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void run() {
            try {
                publisher.firePropertyChange(event.toString(), oldValue, newValue);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Ingest manager listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                        NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
    }
}
