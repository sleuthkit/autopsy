/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2014 Basis Technology Corp.
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Provides data source and file ingest pipeline configurations as ordered lists
 * of ingest module factory class names.
 */
final class IngestPipelinesConfiguration {

    private static final Logger logger = Logger.getLogger(IngestPipelinesConfiguration.class.getName());
    private static final String PIPELINES_CONFIG_FILE = "PipelineConfig.xml"; //NON-NLS
    private static final String PIPELINE_ELEM = "PIPELINE"; //NON-NLS
    private static final int NUMBER_OF_PIPELINE_DEFINITIONS = 3;
    private static final String PIPELINE_TYPE_ATTR = "type"; //NON-NLS
    private static final String STAGE_ONE_DATA_SOURCE_INGEST_PIPELINE_ELEM = "ImageAnalysisStageOne"; //NON-NLS
    private static final String STAGE_TWO_DATA_SOURCE_INGEST_PIPELINE_ELEM = "ImageAnalysisStageTwo"; //NON-NLS
    private static final String FILE_INGEST_PIPELINE_ELEM = "FileAnalysis"; //NON-NLS
    private static final String INGEST_MODULE_ELEM = "MODULE"; //NON-NLS

    private static IngestPipelinesConfiguration instance;

    private final List<String> stageOneDataSourceIngestPipelineConfig = new ArrayList<>();
    private final List<String> fileIngestPipelineConfig = new ArrayList<>();
    private final List<String> stageTwoDataSourceIngestPipelineConfig = new ArrayList<>();

    /**
     * Gets the ingest pipelines configuration singleton.
     *
     * @return The singleton.
     */
    synchronized static IngestPipelinesConfiguration getInstance() {
        if (instance == null) {
            Logger.getLogger(IngestPipelinesConfiguration.class.getName()).log(Level.INFO, "Creating ingest module loader instance"); //NON-NLS
            instance = new IngestPipelinesConfiguration();
        }
        return instance;
    }

    /**
     * Constructs an object that provides data source and file ingest pipeline
     * configurations as ordered lists of ingest module factory class names.
     */
    private IngestPipelinesConfiguration() {
        this.readPipelinesConfigurationFile();
    }

    /**
     * Gets the ordered list of ingest module factory class names for the file
     * ingest pipeline.
     *
     * @return An ordered list of ingest module factory class names.
     */
    List<String> getStageOneDataSourceIngestPipelineConfig() {
        return new ArrayList<>(stageOneDataSourceIngestPipelineConfig);
    }

    /**
     * Gets the ordered list of ingest module factory class names for the first
     * stage data source ingest pipeline.
     *
     * @return An ordered list of ingest module factory class names.
     */
    List<String> getFileIngestPipelineConfig() {
        return new ArrayList<>(fileIngestPipelineConfig);
    }

    /**
     * Gets the ordered list of ingest module factory class names for the second
     * stage data source ingest pipeline.
     *
     * @return An ordered list of ingest module factory class names.
     */
    List<String> getStageTwoDataSourceIngestPipelineConfig() {
        return new ArrayList<>(stageTwoDataSourceIngestPipelineConfig);
    }

    /**
     * Attempts to read the ingest pipeline configuration data from an XML file.
     */
    private void readPipelinesConfigurationFile() {
        try {
            PlatformUtil.extractResourceToUserConfigDir(IngestPipelinesConfiguration.class, PIPELINES_CONFIG_FILE, false);

            Path configFilePath = Paths.get(PlatformUtil.getUserConfigDirectory(), PIPELINES_CONFIG_FILE);
            Document doc = XMLUtil.loadDoc(IngestPipelinesConfiguration.class, configFilePath.toAbsolutePath().toString());
            if (doc == null) {
                return;
            }

            // Get the document root element.
            Element rootElement = doc.getDocumentElement();
            if (null == rootElement) {
                logger.log(Level.SEVERE, "Invalid pipelines config file"); //NON-NLS
                return;
            }

            // Get the pipeline elements and confirm that the correct number is
            // present.
            NodeList pipelineElements = rootElement.getElementsByTagName(IngestPipelinesConfiguration.PIPELINE_ELEM);
            int numPipelines = pipelineElements.getLength();
            if (numPipelines != IngestPipelinesConfiguration.NUMBER_OF_PIPELINE_DEFINITIONS) {
                logger.log(Level.SEVERE, "Invalid pipelines config file"); //NON-NLS
                return;
            }

            // Parse the pipeline elements to populate the pipeline 
            // configuration lists.
            List<String> pipelineConfig = null;
            for (int pipelineNum = 0; pipelineNum < numPipelines; ++pipelineNum) {
                Element pipelineElement = (Element) pipelineElements.item(pipelineNum);
                String pipelineTypeAttr = pipelineElement.getAttribute(PIPELINE_TYPE_ATTR);
                if (null != pipelineTypeAttr) {
                    switch (pipelineTypeAttr) {
                        case STAGE_ONE_DATA_SOURCE_INGEST_PIPELINE_ELEM:
                            pipelineConfig = this.stageOneDataSourceIngestPipelineConfig;
                            break;
                        case FILE_INGEST_PIPELINE_ELEM:
                            pipelineConfig = this.fileIngestPipelineConfig;
                            break;
                        case STAGE_TWO_DATA_SOURCE_INGEST_PIPELINE_ELEM:
                            pipelineConfig = this.stageTwoDataSourceIngestPipelineConfig;
                            break;
                        default:
                            logger.log(Level.SEVERE, "Invalid pipelines config file"); //NON-NLS
                            return;
                    }
                }

                // Create an ordered list of class names. The sequence of class 
                // names defines the sequence of modules in the pipeline.
                if (pipelineConfig != null) {
                    NodeList modulesElems = pipelineElement.getElementsByTagName(INGEST_MODULE_ELEM);
                    int numModules = modulesElems.getLength();
                    for (int moduleNum = 0; moduleNum < numModules; ++moduleNum) {
                        Element moduleElement = (Element) modulesElems.item(moduleNum);
                        String className = moduleElement.getTextContent();
                        if (null != className && !className.isEmpty()) {
                            pipelineConfig.add(className);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error copying default pipeline configuration to user dir", ex); //NON-NLS
        }
    }
}
