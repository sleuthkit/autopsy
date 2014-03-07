/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.awt.EventQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.datamodel.Content;

/**
 * Worker thread that runs a data source-level ingest module (image, file set virt dir, etc). 
 * Used to process only a single data-source and single module. 
 */
 class IngestDataSourceThread extends SwingWorker<Void, Void> {

    private final Logger logger = Logger.getLogger(IngestDataSourceThread.class.getName());
    private ProgressHandle progress;
    private final Content dataSource;
    private final DataSourceIngestModule module;
    private DataSourceIngestModuleStatusHelper controller;
    private final IngestManager manager;
    private boolean inited;
    //current method of enqueuing data source ingest modules with locks and internal lock queue
    //ensures that we init, run and complete a single data source ingest module at a time
    //uses fairness policy to run them in order enqueued
    private static final Lock dataSourceIngestModuleLock = new ReentrantReadWriteLock(true).writeLock();

    IngestDataSourceThread(IngestManager manager, Content dataSource, DataSourceIngestModule module) {
        this.manager = manager;
        this.dataSource = dataSource;
        this.module = module;
        this.inited = false;
    }
    
    Content getContent() {
        return dataSource;
    }

    DataSourceIngestModule getModule() {
        return module;
    }
    
    public void init() {
        
        logger.log(Level.INFO, "Initializing module: {0}", module.getDisplayName());
        try {
            module.init(dataSource.getId());
            inited = true;
        } catch (Exception e) {
            logger.log(Level.INFO, "Failed initializing module: {0}, will not run.", module.getDisplayName());
            //will not run
            inited = false;
            throw e;
        }
    }

    @Override
    protected Void doInBackground() throws Exception {

        logger.log(Level.INFO, "Pending module: {0}", module.getDisplayName());
        
        final String displayName = module.getDisplayName() + " dataSource id:" + dataSource.getId();
        progress = ProgressHandleFactory.createHandle(displayName + " (Pending...)", new Cancellable() {
            @Override
            public boolean cancel() {
                logger.log(Level.INFO, "DataSource ingest module {0} cancelled by user.", module.getDisplayName());
                if (progress != null) {
                    progress.setDisplayName(displayName + " (Cancelling...)");
                }
                return IngestDataSourceThread.this.cancel(true);
            }
        });
        progress.start();
        progress.switchToIndeterminate();

        dataSourceIngestModuleLock.lock();
        try {
            if (this.isCancelled()) {
                logger.log(Level.INFO, "Cancelled while pending, module: {0}", module.getDisplayName());
                return Void.TYPE.newInstance();
            }
            logger.log(Level.INFO, "Starting module: {0}", module.getDisplayName());
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
            progress.setDisplayName(displayName);

            if (inited == false) {
                logger.log(Level.INFO, "Module wasn''t initialized, will not run: {0}", module.getDisplayName());
                return Void.TYPE.newInstance();
            }
            logger.log(Level.INFO, "Starting processing of module: {0}", module.getDisplayName());

            controller = new DataSourceIngestModuleStatusHelper(this, progress);

            if (isCancelled()) {
                logger.log(Level.INFO, "Terminating DataSource ingest module {0} due to cancellation.", module.getDisplayName());
                return Void.TYPE.newInstance();
            }
            final StopWatch timer = new StopWatch();
            timer.start();
            try {
                // RJCTODO
//                module.process(pipelineContext, dataSource, controller);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception in module: " + module.getDisplayName() + " DataSource: " + dataSource.getName(), e);
            } finally {
                timer.stop();
                logger.log(Level.INFO, "Done processing of module: {0} took {1} secs. to process()", new Object[]{module.getDisplayName(), timer.getElapsedTimeSecs()});


                //cleanup queues (worker and DataSource/module)
                manager.removeDataSourceIngestWorker(this);

                if (!this.isCancelled()) {
                    logger.log(Level.INFO, "Module {0} completed", module.getDisplayName());
                    try {
                        module.complete();
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error completing the module " + module.getDisplayName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), module.getDisplayName());
                } else {
                    logger.log(Level.INFO, "Module {0} stopped", module.getDisplayName());
                    try {
                        module.stop();
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error stopping the module" + module.getDisplayName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.STOPPED.toString(), module.getDisplayName());
                }

            }
            return Void.TYPE.newInstance();
        } finally {
            //release the lock so next module can run
            dataSourceIngestModuleLock.unlock();
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progress.finish();
                }
            });
            logger.log(Level.INFO, "Done running module: {0}", module.getDisplayName());
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
    }
}
