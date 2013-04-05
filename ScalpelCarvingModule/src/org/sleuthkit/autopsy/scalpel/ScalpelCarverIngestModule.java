/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.scalpel;

import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Scalpel carving ingest module
 */
public class ScalpelCarverIngestModule implements IngestModuleAbstractFile {
    
    private static final Logger logger = Logger.getLogger(ScalpelCarverIngestModule.class.getName());
    private final String MODULE_NAME = "Scalpel Carver";
    private final String MODULE_DESCRIPTION = "Carves a variety of file types from unallocated space.";
    final public static String MODULE_VERSION = "1.0";

    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {
        return null;
    }

    @Override
    public void init(IngestModuleInit initContext) { }

    @Override
    public void complete() { }

    @Override
    public void stop() { }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
    }

    @Override
    public String getArguments() {
        return "";
    }

    @Override
    public void setArguments(String args) { }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public void saveSimpleConfiguration() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void saveAdvancedConfiguration() { }

    @Override
    public JPanel getSimpleConfiguration() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JPanel getAdvancedConfiguration() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
