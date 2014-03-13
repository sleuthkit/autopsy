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
package org.sleuthkit.autopsy.ingest;

//ingester worker for DataSource queue
import java.awt.EventQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract.IngestModuleException;
import org.sleuthkit.datamodel.Content;

/**
 * Worker thread that runs a data source-level ingest module (image, file set virt dir, etc). 
 * Used to process only a single data-source and single module. 
 */
 class IngestDataSourceThread extends SwingWorker<Void, Void> {

    private final Logger logger = Logger.getLogger(IngestDataSourceThread.class.getName());
    private ProgressHandle progress;
    private final PipelineContext<IngestModuleDataSource>pipelineContext;
    private final Content dataSource;
    private final IngestModuleDataSource module;
    private IngestDataSourceWorkerController controller;
    private final IngestManager manager;
    private final IngestModuleInit init;
    private boolean inited;
    //current method of enqueuing data source ingest modules with locks and internal lock queue
    //ensures that we init, run and complete a single data source ingest module at a time
    //uses fairness policy to run them in order enqueued
    //TODO  use a real queue and manager to allow multiple different modules to run in parallel
    private static final Lock dataSourceIngestModuleLock = new ReentrantReadWriteLock(true).writeLock();

    IngestDataSourceThread(IngestManager manager, PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestModuleDataSource module, IngestModuleInit init) {
        this.manager = manager;
        this.pipelineContext = pipelineContext;
        this.dataSource = dataSource;
        this.module = module;
        this.init = init;
        this.inited = false;
    }

    PipelineContext<IngestModuleDataSource>getContext() {
        return pipelineContext;
    }
    
    Content getContent() {
        return pipelineContext.getDataSourceTask().getContent();
    }

    IngestModuleDataSource getModule() {
        return module;
    }
    
    public void init() throws IngestModuleException{
        
        logger.log(Level.INFO, "Initializing module: " + module.getName());
        try {
            module.init(init);
            inited = true;
        } catch (IngestModuleException e) {
            logger.log(Level.INFO, "Failed initializing module: " + module.getName() + ", will not run.");
            //will not run
            inited = false;
            throw e;
        }
    }

    @Override
    protected Void doInBackground() throws Exception {

        logger.log(Level.INFO, "Pending module: " + module.getName());
        
        final String displayName = NbBundle.getMessage(this.getClass(), "IngestDataSourceThread.displayName.text",
                                                       module.getName(),
                                                       dataSource.getId());
        progress = ProgressHandleFactory.createHandle(
                NbBundle.getMessage(this.getClass(), "IngestDataSourceThread.progress.pending", displayName), new Cancellable() {
            @Override
            public boolean cancel() {
                logger.log(Level.INFO, "DataSource ingest module " + module.getName() + " cancelled by user.");
                if (progress != null) {
                    progress.setDisplayName(
                            NbBundle.getMessage(this.getClass(), "IngestDataSourceThread.progress.cancelling", displayName));
                }
                return IngestDataSourceThread.this.cancel(true);
            }
        });
        progress.start();
        progress.switchToIndeterminate();

        dataSourceIngestModuleLock.lock();
        try {
            if (this.isCancelled()) {
                logger.log(Level.INFO, "Cancelled while pending, module: " + module.getName());
                return Void.TYPE.newInstance();
            }
            logger.log(Level.INFO, "Starting module: " + module.getName());
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
            progress.setDisplayName(displayName);

            if (inited == false) {
                logger.log(Level.INFO, "Module wasn't initialized, will not run: " + module.getName());
                return Void.TYPE.newInstance();
            }
            logger.log(Level.INFO, "Starting processing of module: " + module.getName());

            controller = new IngestDataSourceWorkerController(this, progress);

            if (isCancelled()) {
                logger.log(Level.INFO, "Terminating DataSource ingest module " + module.getName() + " due to cancellation.");
                return Void.TYPE.newInstance();
            }
            final StopWatch timer = new StopWatch();
            timer.start();
            try {
                module.process(pipelineContext, dataSource, controller);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception in module: " + module.getName() + " DataSource: " + dataSource.getName(), e);
            } finally {
                timer.stop();
                logger.log(Level.INFO, "Done processing of module: " + module.getName()
                        + " took " + timer.getElapsedTimeSecs() + " secs. to process()");


                //cleanup queues (worker and DataSource/module)
                manager.removeDataSourceIngestWorker(this);

                if (!this.isCancelled()) {
                    logger.log(Level.INFO, "Module " + module.getName() + " completed");
                    try {
                        module.complete();
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error completing the module " + module.getName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), module.getName());
                } else {
                    logger.log(Level.INFO, "Module " + module.getName() + " stopped");
                    try {
                        module.stop();
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error stopping the module" + module.getName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.STOPPED.toString(), module.getName());
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
            logger.log(Level.INFO, "Done running module: " + module.getName());
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
    }
}
