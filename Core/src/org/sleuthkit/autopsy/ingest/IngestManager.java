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
import java.util.concurrent.ConcurrentHashMap;
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
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.Content;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;

/**
 * Manages the execution of ingest jobs.
 */
public class IngestManager {

    private static final int MAX_NUMBER_OF_DATA_SOURCE_INGEST_THREADS = 1;
    private static final String NUMBER_OF_FILE_INGEST_THREADS_KEY = "NumberOfFileingestThreads"; //NON-NLS
    private static final int MIN_NUMBER_OF_FILE_INGEST_THREADS = 1;
    private static final int MAX_NUMBER_OF_FILE_INGEST_THREADS = 4;
    private static final int DEFAULT_NUMBER_OF_FILE_INGEST_THREADS = 2;
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private static final Preferences userPreferences = NbPreferences.forModule(IngestManager.class);
    private static final IngestManager instance = new IngestManager();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(IngestManager.class);
    private final IngestMonitor ingestMonitor = new IngestMonitor();
    private final ExecutorService startIngestJobsThreadPool = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Long, Future<?>> startIngestJobThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final ExecutorService dataSourceIngestThreadPool = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Long, Future<?>> dataSourceIngestThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final ExecutorService fileIngestThreadPool = Executors.newFixedThreadPool(MAX_NUMBER_OF_FILE_INGEST_THREADS);
    private final ExecutorService fireIngestJobEventsThreadPool = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Long, Future<?>> fileIngestThreads = new ConcurrentHashMap<>(); // Maps thread ids to cancellation handles.
    private final AtomicLong nextThreadId = new AtomicLong(0L);
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
        int numberOfFileIngestThreads = getNumberOfFileIngestThreads();
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            startFileIngestThread();
        }
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
     * Gets the number of file ingest threads the ingest manager will use.
     */
    public synchronized static int getNumberOfFileIngestThreads() {
        return userPreferences.getInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, DEFAULT_NUMBER_OF_FILE_INGEST_THREADS);
    }

    /**
     * Changes the number of file ingest threads the ingest manager will use to
     * no more than MAX_NUMBER_OF_FILE_INGEST_THREADS and no less than
     * MIN_NUMBER_OF_FILE_INGEST_THREADS. Out of range requests are converted to
     * requests for DEFAULT_NUMBER_OF_FILE_INGEST_THREADS.
     *
     * @param numberOfThreads The desired number of file ingest threads.
     */
    public synchronized static void setNumberOfFileIngestThreads(int numberOfThreads) {
        if ((numberOfThreads < MIN_NUMBER_OF_FILE_INGEST_THREADS) || (numberOfThreads > MAX_NUMBER_OF_FILE_INGEST_THREADS)) {
            numberOfThreads = DEFAULT_NUMBER_OF_FILE_INGEST_THREADS;
        }
        userPreferences.putInt(NUMBER_OF_FILE_INGEST_THREADS_KEY, numberOfThreads);

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
        Future<?> handle = dataSourceIngestThreadPool.submit(new DataSourceIngestThread(threadId));
        dataSourceIngestThreads.put(threadId, handle);
    }

    /**
     * Submits a DataSourceIngestThread Runnable to the data source ingest
     * thread pool.
     */
    private void startFileIngestThread() {
        long threadId = nextThreadId.incrementAndGet();
        Future<?> handle = fileIngestThreadPool.submit(new FileIngestThread(threadId));
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
        Future<?> task = startIngestJobsThreadPool.submit(new StartIngestJobsThread(taskId, dataSources, moduleTemplates, processUnallocatedSpace));
        fileIngestThreads.put(taskId, task);

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
        return IngestJob.jobsAreRunning();
    }

    public void cancelAllIngestJobs() {
        cancelStartIngestJobsTasks();
        IngestJob.cancelAllIngestJobs();
    }

    private void cancelStartIngestJobsTasks() {
        for (Future<?> future : startIngestJobThreads.values()) {
            future.cancel(true);
        }
        startIngestJobThreads.clear();
    }

    /**
     * Ingest events.
     */
    public enum IngestEvent { // RJCTODO: Update comments if time permits

        /**
         * Property change event fired when an ingest job is started. The old
         * and new values of the PropertyChangeEvent object are set to null.
         */
        INGEST_JOB_STARTED,
        /**
         * Property change event fired when an ingest job is completed. The old
         * and new values of the PropertyChangeEvent object are set to null.
         */
        INGEST_JOB_COMPLETED,
        /**
         * Property change event fired when an ingest job is canceled. The old
         * and new values of the PropertyChangeEvent object are set to null.
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
     * Add an ingest event property change listener.
     *
     * @param listener The PropertyChangeListener to register.
     */
    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Remove an ingest event property change listener.
     *
     * @param listener The PropertyChangeListener to unregister.
     */
    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Fire an ingest event signifying an ingest job started.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobStarted(long ingestJobId) {
        fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.INGEST_JOB_STARTED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying an ingest job finished.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCompleted(long ingestJobId) {
        fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.INGEST_JOB_COMPLETED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying an ingest job was canceled.
     *
     * @param ingestJobId The ingest job id.
     */
    void fireIngestJobCancelled(long ingestJobId) {
        fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.INGEST_JOB_CANCELLED, ingestJobId, null));
    }

    /**
     * Fire an ingest event signifying the ingest of a file is completed.
     *
     * @param fileId The object id of file.
     */
    void fireFileIngestDone(long fileId) {
        fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.FILE_DONE, fileId, null));
    }

    /**
     * Fire an event signifying a blackboard post by an ingest module.
     *
     * @param moduleDataEvent A ModuleDataEvent with the details of the posting.
     */
    void fireIngestModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.DATA, moduleDataEvent, null));
    }

    /**
     * Fire an event signifying discovery of additional content by an ingest
     * module.
     *
     * @param moduleDataEvent A ModuleContentEvent with the details of the new
     * content.
     */
    void fireIngestModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.CONTENT_CHANGED, moduleContentEvent, null));
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
     * @return Free disk space, -1 if unknown // RJCTODO: What units?
     */
    long getFreeDiskSpace() {
        if (ingestMonitor != null) {
            return ingestMonitor.getFreeSpace();
        } else {
            return -1;
        }
    }

    /**
     * A Runnable that creates ingest jobs and submits the initial data source
     * and file ingest tasks to the task schedulers.
     */
    private class StartIngestJobsThread implements Runnable {

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
        public void run() {
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
                progress.start(dataSources.size() * 2);

                if (!ingestMonitor.isRunning()) {
                    ingestMonitor.start();
                }

                int workUnitsCompleted = 0;
                for (Content dataSource : dataSources) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    // Create an ingest job.
                    List<IngestModuleError> errors = IngestJob.startIngestJob(dataSource, moduleTemplates, processUnallocatedSpace);
                    if (!errors.isEmpty()) {
                        // Report the error to the user.
                        StringBuilder moduleStartUpErrors = new StringBuilder();
                        for (IngestModuleError error : errors) {
                            String moduleName = error.getModuleDisplayName();
                            logger.log(Level.SEVERE, "The " + moduleName + " module failed to start up", error.getModuleError()); //NON-NLS
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

                    fireIngestJobEventsThreadPool.submit(new FireIngestEventThread(IngestEvent.INGEST_JOB_STARTED));

                    // Queue a data source ingest task for the ingest job.
                    final String inputName = dataSource.getName();
                    progress.progress(
                            NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsThread.run.progress.msg1",
                            inputName), workUnitsCompleted);
                    DataSourceIngestTaskScheduler.getInstance().addTask(new DataSourceIngestTask(ingestJob, ingestJob.getDataSource()));
                    progress.progress(
                            NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsThread.run.progress.msg2",
                            inputName), ++workUnitsCompleted);

                    // Queue the file ingest tasks for the ingest job.
                    progress.progress(
                            NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsThread.run.progress.msg3",
                            inputName), workUnitsCompleted);
                    FileIngestTaskScheduler.getInstance().addTasks(ingestJob, ingestJob.getDataSource());
                    progress.progress(
                            NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsThread.run.progress.msg4",
                            inputName), ++workUnitsCompleted);

                    if (!Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            } catch (Exception ex) {
                String message = String.format("StartIngestJobsTask (id=%d) caught exception", threadId); //NON-NLS
                logger.log(Level.SEVERE, message, ex);
                MessageNotifyUtil.Message.error(
                        NbBundle.getMessage(this.getClass(), "IngestManager.StartIngestJobsTask.run.catchException.msg"));
            } finally {
                progress.finish();
                startIngestJobThreads.remove(threadId);
            }
        }
    }

    /**
     * A Runnable that acts as a consumer for the data ingest task scheduler's
     * task queue.
     */
    private class DataSourceIngestThread implements Runnable {

        @Override
        public void run() {
            DataSourceIngestTaskScheduler scheduler = DataSourceIngestTaskScheduler.getInstance();
            while (true) {
                try {
                    DataSourceIngestTask task = scheduler.getNextTask(); // Blocks.
                    task.execute();
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
     * A Runnable that acts as a consumer for the file task scheduler's task
     * queue.
     */
    private static class FileIngestThread implements Runnable {

        @Override
        public void run() {
            FileIngestTaskScheduler scheduler = FileIngestTaskScheduler.getInstance();
            while (true) {
                try {
                    FileIngestTask task = scheduler.getNextTask(); // Blocks.
                    task.execute();
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
     * A Runnable that fire ingest events to ingest manager property change
     * listeners.
     */
    private class FireIngestEventThread implements Runnable {

        private final IngestEvent event;
        private final Object oldValue;
        private final Object newValue;

        FireIngestEventThread(IngestEvent event, Object oldValue, Object newValue) {
            this.event = event;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void run() {
            try {
                pcs.firePropertyChange(event.toString(), oldValue, newValue);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Ingest manager listener threw exception", e); //NON-NLS
                MessageNotifyUtil.Notify.show(NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr"), // RJCTODO: Oddly named strings
                        NbBundle.getMessage(IngestManager.class, "IngestManager.moduleErr.errListenToUpdates.msg"),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
    }
}
