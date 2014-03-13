/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

/**
 * An adapter that provides a default implementation of the IngestModule
 * interface.
 */
public abstract class IngestModuleAdapter implements IngestModule {

    private IngestModuleContext context;

    @Override
    public void startUp(IngestModuleContext context) {
        this.context = context;
    }

    @Override
    public IngestModuleContext getContext() {
        return context;
    }
        
    @Override
    public void shutDown(boolean ingestJobCancelled) {
    }
    
    protected void setContext(IngestModuleContext context) {
        this.context = context;
    }        
}
