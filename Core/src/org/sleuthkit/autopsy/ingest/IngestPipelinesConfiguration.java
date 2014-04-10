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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Provides data source and file ingest pipeline configurations as ordered lists
 * of ingest module class names. The order of the module class names indicates
 * the desired sequence of ingest module instances in an ingest modules
 * pipeline.
 */
final class IngestPipelinesConfiguration {

    private static final Logger logger = Logger.getLogger(IngestPipelinesConfiguration.class.getName());
    private static final String PIPELINE_CONFIG_FILE_VERSION_KEY = "PipelineConfigFileVersion";
    private static final String PIPELINE_CONFIG_FILE_VERSION_NO_STRING = "1";
    private static final int PIPELINE_CONFIG_FILE_VERSION_NO = 1;
    private static final String PIPELINES_CONFIG_FILE = "pipeline_config.xml";
    private static final String PIPELINES_CONFIG_FILE_XSD = "PipelineConfigSchema.xsd";
    private static final String XML_PIPELINE_ELEM = "PIPELINE";
    private static final String XML_PIPELINE_TYPE_ATTR = "type";
    private static final String DATA_SOURCE_INGEST_PIPELINE_TYPE = "ImageAnalysis";
    private static final String FILE_INGEST_PIPELINE_TYPE = "FileAnalysis";
    private static final String XML_MODULE_ELEM = "MODULE";
    private static final String XML_MODULE_CLASS_NAME_ATTR = "location";
    private static IngestPipelinesConfiguration instance;
    private final List<String> dataSourceIngestPipelineConfig = new ArrayList<>();
    private final List<String> fileIngestPipelineConfig = new ArrayList<>();

    private IngestPipelinesConfiguration() {
        readPipelinesConfigurationFile();
    }

    synchronized static IngestPipelinesConfiguration getInstance() {
        if (instance == null) {
            Logger.getLogger(IngestPipelinesConfiguration.class.getName()).log(Level.INFO, "Creating ingest module loader instance");
            instance = new IngestPipelinesConfiguration();
        }
        return instance;
    }

    List<String> getDataSourceIngestPipelineConfig() {
        return new ArrayList<>(dataSourceIngestPipelineConfig);
    }

    List<String> getFileIngestPipelineConfig() {
        return new ArrayList<>(fileIngestPipelineConfig);
    }

    private void readPipelinesConfigurationFile() {
        try {
            boolean overWrite;
            if (!ModuleSettings.settingExists(this.getClass().getSimpleName(), PIPELINE_CONFIG_FILE_VERSION_KEY)) {
                ModuleSettings.setConfigSetting(this.getClass().getSimpleName(), PIPELINE_CONFIG_FILE_VERSION_KEY, PIPELINE_CONFIG_FILE_VERSION_NO_STRING);
                overWrite = true;
            } else {
                int versionNumber = Integer.parseInt(ModuleSettings.getConfigSetting(this.getClass().getSimpleName(), PIPELINE_CONFIG_FILE_VERSION_KEY));
                overWrite = versionNumber < PIPELINE_CONFIG_FILE_VERSION_NO;
                // TODO: Migrate user edits
            }

            boolean fileCopied = PlatformUtil.extractResourceToUserConfigDir(IngestPipelinesConfiguration.class, PIPELINES_CONFIG_FILE, overWrite);
            if (!fileCopied) {
                logger.log(Level.SEVERE, "Failure copying default pipeline configuration to user dir");
            }

            String configFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + PIPELINES_CONFIG_FILE;
            Document doc = XMLUtil.loadDoc(IngestPipelinesConfiguration.class, configFilePath, PIPELINES_CONFIG_FILE_XSD);
            if (doc == null) {
                return;
            }

            Element rootElement = doc.getDocumentElement();
            if (rootElement == null) {
                logger.log(Level.SEVERE, "Invalid pipelines config file");
                return;
            }

            NodeList pipelineElements = rootElement.getElementsByTagName(XML_PIPELINE_ELEM);
            int numPipelines = pipelineElements.getLength();
            if (numPipelines < 1 || numPipelines > 2) {
                logger.log(Level.SEVERE, "Invalid pipelines config file");
                return;
            }

            List<String> pipelineConfig = null;
            for (int pipelineNum = 0; pipelineNum < numPipelines; ++pipelineNum) {
                Element pipelineElement = (Element) pipelineElements.item(pipelineNum);
                String pipelineTypeAttr = pipelineElement.getAttribute(XML_PIPELINE_TYPE_ATTR);
                if (pipelineTypeAttr != null) {
                    switch (pipelineTypeAttr) {
                        case DATA_SOURCE_INGEST_PIPELINE_TYPE:
                            pipelineConfig = dataSourceIngestPipelineConfig;
                            break;
                        case FILE_INGEST_PIPELINE_TYPE:
                            pipelineConfig = fileIngestPipelineConfig;
                            break;
                        default:
                            logger.log(Level.SEVERE, "Invalid pipelines config file");
                            return;
                    }
                }

                // Create an ordered list of class names. The sequence of class 
                // names defines the sequence of modules in the pipeline.
                if (pipelineConfig != null) {
                    NodeList modulesElems = pipelineElement.getElementsByTagName(XML_MODULE_ELEM);
                    int numModules = modulesElems.getLength();
                    if (numModules == 0) {
                        break;
                    }
                    for (int moduleNum = 0; moduleNum < numModules; ++moduleNum) {
                        Element moduleElement = (Element) modulesElems.item(moduleNum);
                        final String moduleClassName = moduleElement.getAttribute(XML_MODULE_CLASS_NAME_ATTR);
                        if (moduleClassName != null) {
                            pipelineConfig.add(moduleClassName);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error copying default pipeline configuration to user dir", ex);
        }
    }
}
