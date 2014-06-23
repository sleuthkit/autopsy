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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.Content;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Manages the execution of ingest jobs.
 */
public class IngestManager {

    private static final int DEFAULT_NUMBER_OF_DATA_SOURCE_INGEST_THREADS = 1;
    private static final int MIN_NUMBER_OF_FILE_INGEST_THREADS = 1;
    private static final int MAX_NUMBER_OF_FILE_INGEST_THREADS = 16;
    private static final int DEFAULT_NUMBER_OF_FILE_INGEST_THREADS = 2;
    private static final int MAX_ERROR_MESSAGE_POSTS = 200;
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static IngestManager instance = null;
    private final PropertyChangeSupport ingestJobEventPublisher = new PropertyChangeSupport(IngestManager.class);
    private final PropertyChangeSupport ingestModuleEventPublisher = new PropertyChangeSupport(IngestManager.class);
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final ExecutorService startIngestJobsThreadPool = Executors.newSingleThreadExecutor();
    private final ExecutorService dataSourceIngestThreadPool = Executors.newSingleThreadExecutor();
    private final ExecutorService fileIngestThreadPool;
    private final ExecutorService fireIngestEventsThreadPool = Executors.newSingleThreadExecutor();
    private final AtomicLong nextThreadId = new AtomicLong(0L);
    private final ConcurrentHashMap<Long, Future<Void>> startIngestJobThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final ConcurrentHashMap<Long, Future<?>> dataSourceIngestThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final ConcurrentHashMap<Long, Future<?>> fileIngestThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final AtomicLong ingestErrorMessagePosts = new AtomicLong(0L);
    private final ConcurrentHashMap<Long, IngestThreadActivitySnapshot> ingestThreadActivitySnapshots = new ConcurrentHashMap<>(); // Maps ingest thread ids to progress ingestThreadActivitySnapshots.    
    private final Object processedFilesSnapshotLock = new Object();
    private ProcessedFilesSnapshot processedFilesSnapshot = new ProcessedFilesSnapshot();
    private volatile IngestMessageTopComponent ingestMessageBox;
    private int numberOfFileIngestThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;

    /**
     * Gets the ingest manager.
     *
     * @return A singleton IngestManager object.
     */
    public synchronized static IngestManager getInstance() {
        if (instance == null) {
            // Two stage construction to avoid allowing "this" reference to 
            // escape from the constructor via the property change listener.
            // This is to ensure that a partially constructed ingest manager is
            // not published to other threads.
            instance = new IngestManager();
            instance.subscribeToCaseEvents();
        }
        return instance;
    }

    /**
     * Starts the ingest monitor and the data source ingest and file ingest
     * threads.
     */
    private IngestManager() {
        startDataSourceIngestThread();

        numberOfFileIngestThreads = UserPreferences.numberOfFileIngestThreads();
        if ((numberOfFileIngestThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS) || (numberOfFileIngestThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS)) {
            numberOfFileIngestThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
            UserPreferences.setNumberOfFileIngestThreads(numberOfFileIngestThreads);
        }
        fileIngestThreadPool = Executors.newFixedThreadPool(numberOfFileIngestThreads);
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            startFileIngestThread();
        }
    }

    /**
     * Called by the custom installer for this package once the window system is
     * initialized, allowing the ingest manager to get the top component used to
     * display ingest messages.
     */
    void initIngestMessageInbox() {
        ingestMessageBox = IngestMessageTopComponent.findInstance();
    }

    /**
     * Gets the number of data source ingest threads the ingest manager will
     * use.
     *
     * @return The number of data source ingest threads.
     */
    public int getNumberOfDataSourceIngestThreads() {
        return DEFAULT_NUMBER_OF_DATA_SOURCE_INGEST_THREADS;
    }

    /**
     * Gets the number of file ingest threads the ingest manager will use.
     *
     * @return The number of file ingest threads.
     */
    public int getNumberOfFileIngestThreads() {
        return numberOfFileIngestThreads;
    }

    /**
     * Submits a DataSourceIngestThread Runnable to the data source ingest
     * thread pool.
     */
    private void startDataSourceIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        Future<?> handle = dataSourceIngestThreadPool.submit(new ExecuteIngestTasksThread(threadId, IngestScheduler.getInstance().getDataSourceIngestTaskQueue()));
        dataSourceIngestThreads.put(threadId, handle);
        ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));
    }

    /**
     * Submits a DataSourceIngestThread Runnable to the data source ingest
     * thread pool.
     */
    private void startFileIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        Future<?> handle = fileIngestThreadPool.submit(new ExecuteIngestTasksThread(threadId, IngestScheduler.getInstance().getFileIngestTaskQueue()));
        fileIngestThreads.put(threadId, handle);
        ingestThreadActivitySnapshots.put(threadId, new IngestThreadActivitySnapshot(threadId));
    }

    /**
     * Cancels a DataSourceIngestThread Runnable in the file ingest thread pool.
     */
    private void cancelFileIngestThread(long threadId) {
        Future<?> handle = fileIngestThreads.remove(threadId);
        handle.cancel(true);
    }

    synchronized void startIngestJobs(final List<Content> dataSources, final List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        if (!isIngestRunning()) {
            clearIngestMessageBox();
        }
    
        long taskId = nextThreadId.incrementAndGet();
        Future<Void> task = startIngestJobsThreadPool.submit(new StartIngestJobsThread(taskId, dataSources, moduleTemplates, processUnallocatedSpace));
        startIngestJobThreads.put(taskId, task);
    }

    private void subscribeToCaseEvents() {
        Case.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                    if (event.getNewValue() != null) {
                        handleCaseOpened();
                    } else {
                        handleCaseClosed();
                    }
                }
            }
        });
    }

    void handleCaseOpened() {
        IngestScheduler.getInstance().setEnabled(true);
        clearIngestMessageBox();
    }

    void handleCaseClosed() {
        IngestScheduler.getInstance().setEnabled(false);
        cancelAllIngestJobs();
        clearIngestMessageBox();
    }

    private void clearIngestMessageBox() {
        if (ingestMessageBox != null) {
            ingestMessageBox.clearMessages();
        }
        ingestErrorMessagePosts.set(0);        
    }
    
    /**
     * Test if any ingest jobs are in progress.
     *
     * @return True if any ingest jobs are in progress, false otherwise.
     */
    public boolean isIngestRunning() {
        return IngestScheduler.getInstance().ingestJobsAreRunning();
    }

    void setIngestTaskProgress(DataSourceIngestTask task, String ingestModuleDisplayName) {
        ingestThreadActivitySnapshots.put(task.getThreadId(), new IngestThreadActivitySnapshot(task.getThreadId(), ingestModuleDisplayName, task.getDataSource()));
    }

    void setIngestTaskProgress(FileIngestTask task, String ingestModuleDisplayName) {
        ingestThreadActivitySnapshots.put(task.getThreadId(), new IngestThreadActivitySnapshot(task.getThreadId(), ingestModuleDisplayName, task.getDataSource(), task.getFile()));
    }
        
    void setIngestTaskProgressCompleted(DataSourceIngestTask task) {
        ingestThreadActivitySnapshots.put(task.getThreadId(), new IngestThreadActivitySnapshot(task.getThreadId()));
    }

    void setIngestTaskProgressCompleted(FileIngestTask task) {
        ingestThreadActivitySnapshots.put(task.getThreadId(), new IngestThreadActivitySnapshot(task.getThreadId()));
        synchronized (processedFilesSnapshotLock) {
            processedFilesSnapshot.incrementProcessedFilesCount();
        }
    }

    List<IngestThreadActivitySnapshot> getIngestThreadActivitySnapshots() {
        return new ArrayList(ingestThreadActivitySnapshots.values());
    }

    ProcessedFilesSnapshot getProcessedFilesSnapshot() {
        ProcessedFilesSnapshot snapshot;
        synchronized (processedFilesSnapshotLock) {
            snapshot = processedFilesSnapshot;
            processedFilesSnapshot = new ProcessedFilesSnapshot();
        }
        return snapshot;
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
                logger.log(Level.SEVERE, "Unexpected thread interrupt", ex); //NON-NLS
            }
        }

        // Cancel all the jobs already created.
        IngestScheduler.getInstance().cancelAllIngestJobs();
    }

    /**
     * Ingest job events.
     */
    public enum IngestJobEvent {

        /**
         * Property change event fired when an ingest job is started. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        STARTED,
        /**
         * Property change event fired when an ingest job is completed. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        COMPLETED,
        /**
         * Property change event fired when an ingest job is canceled. The old
         * value of the PropertyChangeEvent object is set to the ingest job id,
         * and the new value is set to null.
         */
        CANCELLED,
    };

    /**
     * Ingest module events.
     */
    public enum IngestModuleEvent {

        /**
         * Property change event fired when an ingest module adds new data to a
         * case, usually by posting to the blackboard. The old value of the
         * PropertyChangeEvent is a ModuleDataEvent object, and the new value is
         * set to null.
         */
        DATA_ADDED,
        /**
         * Property change event fired when an ingest module adds new content to
         * a case or changes a recorded attribute of existing content. For
         * example, if a module adds an extracted or carved file to a case, the
         * module should fire this event. The old value of the
         * PropertyChangeEvent is a ModuleContentEvent object, and the new value
         * is set to null.
         */
        CONTENT_CHANGED,
        /**
         * Property change event fired when the ingest of a file is completed.
         * The old value of the PropertyChangeEvent is the Autopsy object ID of
         * the file, and the new value is set to null.
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
     * Add an ingest job and ingest module event property change listener.
     *
     * @deprecated Use addIngestJobEventListener() and/or
     * addIngestModuleEventListener().
     * @param listener The PropertyChangeListener to register.
     */
    public static void addPropertyChangeListener(final PropertyChangeListener listener) {
        instance.ingestJobEventPublisher.addPropertyChangeListener(listener);
        instance.ingestModuleEventPublisher.addPropertyChangeListener(listener);
    }

    /**
     * Remove an ingest job and ingest module event property change listener.
     *
     * @deprecated Use removeIngestJobEventListener() and/or
     * removeIngestModuleEventListener().
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
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestJobEventPublisher, IngestJobEvent.STARTED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying an ingest job finished.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCompleted(long ingestJobId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestJobEventPublisher, IngestJobEvent.COMPLETED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying an ingest job was canceled.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCancelled(long ingestJobId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestJobEventPublisher, IngestJobEvent.CANCELLED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying the ingest of a file is completed.
     *
     * @param fileId The object id of file.
     */
    void fireFileIngestDone(long fileId) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestModuleEventPublisher, IngestModuleEvent.FILE_DONE, fileId, null));
    }

    /**
     * Fire an event signifying a blackboard post by an ingest module.
     *
     * @param moduleDataEvent A ModuleDataEvent with the details of the posting.
     */
    void fireIngestModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestModuleEventPublisher, IngestModuleEvent.DATA_ADDED, moduleDataEvent, null));
    }

    /**
     * Fire an event signifying discovery of additional content by an ingest
     * module.
     *
     * @param moduleDataEvent A ModuleContentEvent with the details of the new
     * content.
     */
    void fireIngestModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        fireIngestEventsThreadPool.submit(new FireIngestEventThread(ingestModuleEventPublisher, IngestModuleEvent.CONTENT_CHANGED, moduleContentEvent, null));
    }

    /**
     * Post a message to the ingest messages in box.
     *
     * @param message The message to be posted.
     */
    void postIngestMessage(IngestMessage message) {
        if (ingestMessageBox != null) {
            if (message.getMessageType() != IngestMessage.MessageType.ERROR && message.getMessageType() != IngestMessage.MessageType.WARNING) {
                ingestMessageBox.displayMessage(message);
            } else {
                long errorPosts = ingestErrorMessagePosts.incrementAndGet();
                if (errorPosts <= MAX_ERROR_MESSAGE_POSTS) {
                    ingestMessageBox.displayMessage(message);
                } else if (errorPosts == MAX_ERROR_MESSAGE_POSTS + 1) {
                    IngestMessage errorMessageLimitReachedMessage = IngestMessage.createErrorMessage(
                            NbBundle.getMessage(this.getClass(), "IngestManager.IngestMessage.ErrorMessageLimitReached.title"),
                            NbBundle.getMessage(this.getClass(), "IngestManager.IngestMessage.ErrorMessageLimitReached.subject"),
                            NbBundle.getMessage(this.getClass(), "IngestManager.IngestMessage.ErrorMessageLimitReached.msg", MAX_ERROR_MESSAGE_POSTS));
                    ingestMessageBox.displayMessage(errorMessageLimitReachedMessage);
                }
            }
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
                    List<IngestModuleError> errors = IngestScheduler.getInstance().startIngestJob(dataSource, moduleTemplates, processUnallocatedSpace);
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
            } catch (InterruptedException ex) {
                // Reset interrupted status.
                Thread.currentThread().interrupt();
            } finally {
                progress.finish();
                startIngestJobThreads.remove(threadId);
            }
            return null;
        }
    }

    /**
     * A consumer for an ingest task queue.
     */
    private class ExecuteIngestTasksThread implements Runnable {

        private final long threadId;
        private final IngestTaskQueue tasks;

        ExecuteIngestTasksThread(long threadId, IngestTaskQueue tasks) {
            this.threadId = threadId;
            this.tasks = tasks;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    IngestTask task = tasks.getNextTask(); // Blocks.
                    task.execute(threadId);
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
        private final IngestJobEvent jobEvent;
        private final IngestModuleEvent moduleEvent;
        private final Object oldValue;
        private final Object newValue;

        FireIngestEventThread(PropertyChangeSupport publisher, IngestJobEvent event, Object oldValue, Object newValue) {
            this.publisher = publisher;
            this.jobEvent = event;
            this.moduleEvent = null;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        FireIngestEventThread(PropertyChangeSupport publisher, IngestModuleEvent event, Object oldValue, Object newValue) {
            this.publisher = publisher;
            this.jobEvent = null;
            this.moduleEvent = event;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void run() {
            try {
                publisher.firePropertyChange((jobEvent != null ? jobEvent.toString() : moduleEvent.toString()), oldValue, newValue);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Ingest manager listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"),
                        NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
    }

    static final class IngestThreadActivitySnapshot {

        private final long threadId;
        private final Date startTime;
        private final String activity;
        private final String dataSourceName;
        private final String fileName;

        IngestThreadActivitySnapshot(long threadId) {
            this.threadId = threadId;
            startTime = new Date();
            this.activity = NbBundle.getMessage(this.getClass(), "IngestManager.IngestThreadActivitySnapshot.idleThread");
            this.dataSourceName = "";
            this.fileName = "";
        }

        IngestThreadActivitySnapshot(long threadId, String activity, Content dataSource) {
            this.threadId = threadId;
            startTime = new Date();
            this.activity = activity;
            this.dataSourceName = dataSource.getName();
            this.fileName = "";
        }

        IngestThreadActivitySnapshot(long threadId, String activity, Content dataSource, AbstractFile file) {
            this.threadId = threadId;
            startTime = new Date();
            this.activity = activity;
            this.dataSourceName = dataSource.getName();
            this.fileName = file.getName();
        }

        long getThreadId() {
            return threadId;
        }

        Date getStartTime() {
            return startTime;
        }

        String getActivity() {
            return activity;
        }

        String getDataSourceName() {
            return dataSourceName;
        }

        String getFileName() {
            return fileName;
        }
    }

    static final class ProcessedFilesSnapshot {

        private final Date startTime;
        private long processedFilesCount;

        ProcessedFilesSnapshot() {
            this.startTime = new Date();
            this.processedFilesCount = 0;
        }

        void incrementProcessedFilesCount() {
            ++processedFilesCount;
        }

        Date getStartTime() {
            return startTime;
        }

        long getProcessedFilesCount() {
            return processedFilesCount;
        }
    }
}
