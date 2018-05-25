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
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleError;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.datamodel.Content;

/**
 * Class with common methods for testing related to adding and ingesting
 * datasources.
 */
public final class IngestUtils {

    /**
     * IngestUtils constructor. Since this class is not meant to allow for
     * instantiation, this constructor is 'private'.
     */
    private IngestUtils() {
    }

    /**
     * Add the specified datasource to the case current case and processes it.
     * Causes failure if it was unable to add and process the datasource.
     *
     * @param dataSourceProcessor the datasource processer to use to process the
     *                            datasource
     * @param dataSourcePath      the path to the datasource which is being
     *                            added
     */
    public static void addDataSource(AutoIngestDataSourceProcessor dataSourceProcessor, Path dataSourcePath) {
        DataSourceProcessorCallback.DataSourceProcessorResult result = null;
        try {
            if (!dataSourcePath.toFile().exists()) {
                Assert.fail("Data source not found: " + dataSourcePath.toString());
            }
            
            DataSourceProcessorRunner.ProcessorCallback callBack = DataSourceProcessorRunner.runDataSourceProcessor(dataSourceProcessor, dataSourcePath);
            result = callBack.getResult();
            if (result.equals(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS)) {
                String joinedErrors = String.join(System.lineSeparator(), callBack.getErrorMessages());
                Assert.fail(String.format("Error(s) occurred while running the data source processor: %s", joinedErrors));
            }
        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Run ingest on the specified datasources with the specified ingest job
     * settings. Causes failure if there are any errors or other problems while
     * running ingest.
     *
     * @param datasources       - the datasources to run ingest on
     * @param ingestJobSettings - the ingest job settings to use for ingest
     */
    public static void runIngestJob(List<Content> datasources, IngestJobSettings ingestJobSettings) {
        try {
            List<IngestModuleError> errs = IngestJobRunner.runIngestJob(datasources, ingestJobSettings);
            StringBuilder joinedErrors = new StringBuilder("");
            errs.forEach((err) -> {
                joinedErrors.append(String.format("Error: %s: %s.", err.getModuleDisplayName(), err.toString())).append(System.lineSeparator());
            });
            assertEquals(joinedErrors.toString(), 0, errs.size());
        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Get the ingest module template for the the specified factories default
     * ingest job settings.
     *
     * @param factory the factory to get the ingest job settings from
     *
     * @return template - the IngestModuleTemplate created with the factory and
     *         it's default settings.
     */
    public static IngestModuleTemplate getIngestModuleTemplate(IngestModuleFactory factory) {
        IngestModuleIngestJobSettings settings = factory.getDefaultIngestJobSettings();
        IngestModuleTemplate template = new IngestModuleTemplate(factory, settings);
        template.setEnabled(true);
        return template;
    }
}
