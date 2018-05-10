/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.testutils;

import java.nio.file.Path;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.datamodel.Content;

/**
 * Common image utility methods.
 */
public final class IngestUtils {

    /**
     * IngestUtils constructor. Since this class is not meant to allow for
     * instantiation, this constructor is 'private'.
     */
    private IngestUtils() {
    }

    /**
     * Add a data source for the data source processor.
     *
     * @param dataSourceProcessor The data source processor.
     * @param dataSourcePath      The path to the data source to be added.
     */
    public static void addDataSource(AutoIngestDataSourceProcessor dataSourceProcessor, Path dataSourcePath) {
        try {
            DataSourceProcessorRunner.ProcessorCallback callBack = DataSourceProcessorRunner.runDataSourceProcessor(dataSourceProcessor, dataSourcePath);
            List<String> callbackErrorMessageList = callBack.getErrorMessages();
            String errorMessage = String.format("The data source processor callback produced %d error messages.", callbackErrorMessageList.size());
            assertEquals(errorMessage, 0, callbackErrorMessageList.size());
        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Run an ingest job.
     *
     * @param dataSourceList    The list of data sources to process.
     * @param ingestJobSettings The ingest job settings to use for ingest.
     */
    public static void runIngestJob(List<Content> dataSourceList, IngestJobSettings ingestJobSettings) {
        try {
            List<IngestModuleError> ingestModuleErrorsList = IngestJobRunner.runIngestJob(dataSourceList, ingestJobSettings);
            for (IngestModuleError err : ingestModuleErrorsList) {
                System.out.println(String.format("Error: %s: %s.", err.getModuleDisplayName(), err.toString()));
            }
            String errorMessage = String.format("The ingest job runner produced %d error messages.", ingestModuleErrorsList.size());
            assertEquals(errorMessage, 0, ingestModuleErrorsList.size());
        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Build a new ingest module template based on the given factory.
     *
     * @param factory The ingest module factory.
     *
     * @return The ingest module template.
     */
    public static IngestModuleTemplate getIngestModuleTemplate(IngestModuleFactoryAdapter factory) {
        IngestModuleIngestJobSettings settings = factory.getDefaultIngestJobSettings();
        IngestModuleTemplate template = new IngestModuleTemplate(factory, settings);
        template.setEnabled(true);
        return template;
    }

}
