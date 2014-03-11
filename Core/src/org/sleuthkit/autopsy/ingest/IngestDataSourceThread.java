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
 * Worker thread that runs a single data source ingest module (image, file set virt dir, etc). 
 */
 class IngestDataSourceThread extends SwingWorker<Void, Void> {

    private final Logger logger = Logger.getLogger(IngestDataSourceThread.class.getName());
    private ProgressHandle progress;
    private final Content dataSource;
    private final DataSourceIngestModule module;
    private final IngestModuleProcessingContext context;
    private DataSourceIngestModuleStatusHelper statusHelper;
    private boolean inited;
    //current method of enqueuing data source ingest modules with locks and internal lock queue
    //ensures that we init, run and complete a single data source ingest module at a time
    //uses fairness policy to run them in order enqueued
    private static final Lock dataSourceIngestModuleLock = new ReentrantReadWriteLock(true).writeLock();

    IngestDataSourceThread(Content dataSource, DataSourceIngestModule module, IngestModuleProcessingContext context) {
        this.dataSource = dataSource;
        this.module = module;
        this.context = context;
        this.inited = false;
    }
    
    Content getContent() {
        return dataSource;
    }

    DataSourceIngestModule getModule() {
        return module;
    }
    
    public void init() {
        
        try {
            module.startUp(context);
            inited = true;
        } catch (Exception e) {
            logger.log(Level.INFO, "Failed initializing module: {0}, will not run.", context.getModuleDisplayName());
            //will not run
            inited = false;
            throw e;
        }
    }

    @Override
    protected Void doInBackground() throws Exception {
        
        final String displayName = context.getModuleDisplayName() + " dataSource id:" + dataSource.getId();
        progress = ProgressHandleFactory.createHandle(displayName + " (Pending...)", new Cancellable() {
            @Override
            public boolean cancel() {
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
                return Void.TYPE.newInstance();
            }
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
            progress.setDisplayName(displayName);

            if (inited == false) {
                return Void.TYPE.newInstance();
            }

            statusHelper = new DataSourceIngestModuleStatusHelper(this, progress);

            if (isCancelled()) {
                return Void.TYPE.newInstance();
            }
            final StopWatch timer = new StopWatch();
            timer.start();
            try {
                module.process(dataSource, statusHelper);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception in module: " + context.getModuleDisplayName() + " DataSource: " + dataSource.getName(), e);
            } finally {
                timer.stop();
                logger.log(Level.INFO, "Done processing of module: {0} took {1} secs. to process()", new Object[]{context.getModuleDisplayName(), timer.getElapsedTimeSecs()});

                //cleanup queues (worker and DataSource/module)
                IngestManager.getDefault().removeDataSourceIngestWorker(this);

                if (!this.isCancelled()) {
                    try {
                        module.shutDown(false);
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error completing the module " + context.getModuleDisplayName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), context.getModuleDisplayName());
                } else {
                    try {
                        module.shutDown(true);
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error stopping the module" + context.getModuleDisplayName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.STOPPED.toString(), context.getModuleDisplayName());
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
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
    }
}
