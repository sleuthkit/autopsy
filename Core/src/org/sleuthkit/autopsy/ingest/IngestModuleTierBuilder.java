/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A utility that builds the ingest module tiers needed to execute an ingest
 * job.
 */
class IngestModuleTierBuilder {

    private static final String AUTOPSY_MODULE_PREFIX = "org.sleuthkit.autopsy";
    private static final Pattern JYTHON_MODULE_REGEX = Pattern.compile("org\\.python\\.proxies\\.(.+?)\\$(.+?)(\\$[0-9]*)?$");

    /**
     * Builds the ingest module tiers needed to execute an ingest job.
     *
     * @param settings The ingest job settings.
     * @param executor The ingest job executor.
     *
     * @return The ingest module tiers.
     *
     * @throws InterruptedException The exception is thrown if the current
     *                              thread is interrupted while blocked during
     *                              the building process.
     */
    static List<IngestModuleTier> buildIngestModuleTiers(IngestJobSettings settings, IngestJobExecutor executor) throws InterruptedException {
        /*
         * Get the enabled ingest module templates from the ingest job settings.
         */
        List<IngestModuleTemplate> enabledTemplates = settings.getEnabledIngestModuleTemplates();

        /**
         * Sort the ingest module templates into buckets based on the module
         * types the template can be used to create. A template may go into more
         * than one bucket. Each bucket actually consists of two collections:
         * one for Java modules and one for Jython modules.
         */
        Map<String, IngestModuleTemplate> javaDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonDataSourceModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonFileModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaArtifactModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonArtifactModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> javaResultModuleTemplates = new LinkedHashMap<>();
        Map<String, IngestModuleTemplate> jythonResultModuleTemplates = new LinkedHashMap<>();
        for (IngestModuleTemplate template : enabledTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, template);
            }
            if (template.isFileIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaFileModuleTemplates, jythonFileModuleTemplates, template);
            }
            if (template.isDataArtifactIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaArtifactModuleTemplates, jythonArtifactModuleTemplates, template);
            }
            if (template.isAnalysisResultIngestModuleTemplate()) {
                addModuleTemplateToSortingMap(javaResultModuleTemplates, jythonResultModuleTemplates, template);
            }
        }

        /**
         * Take the module templates that have pipeline configuration entries
         * out of the buckets, and add them to ingest module pipeline templates
         * in the order prescribed by the pipeline configuration. There is
         * currently no pipeline configuration file support for data artifact or
         * analysis result ingest module pipelines.
         */
        IngestPipelinesConfiguration pipelineConfig = IngestPipelinesConfiguration.getInstance();
        List<IngestModuleTemplate> firstStageDataSourcePipelineTemplate = createIngestPipelineTemplate(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfig.getStageOneDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> secondStageDataSourcePipelineTemplate = createIngestPipelineTemplate(javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates, pipelineConfig.getStageTwoDataSourceIngestPipelineConfig());
        List<IngestModuleTemplate> filePipelineTemplate = createIngestPipelineTemplate(javaFileModuleTemplates, jythonFileModuleTemplates, pipelineConfig.getFileIngestPipelineConfig());
        List<IngestModuleTemplate> artifactPipelineTemplate = new ArrayList<>();
        List<IngestModuleTemplate> resultsPipelineTemplate = new ArrayList<>();

        /**
         * Add any ingest module templates remaining in the buckets to the
         * appropriate ingest module pipeline templates. Data source level
         * ingest modules templates that were not listed in the pipeline
         * configuration are added to the first stage data source pipeline
         * template, Java modules are added before Jython modules, and Core
         * Autopsy modules are added before third party modules.
         */
        addToIngestPipelineTemplate(firstStageDataSourcePipelineTemplate, javaDataSourceModuleTemplates, jythonDataSourceModuleTemplates);
        addToIngestPipelineTemplate(filePipelineTemplate, javaFileModuleTemplates, jythonFileModuleTemplates);
        addToIngestPipelineTemplate(artifactPipelineTemplate, javaArtifactModuleTemplates, jythonArtifactModuleTemplates);
        addToIngestPipelineTemplate(resultsPipelineTemplate, javaResultModuleTemplates, jythonResultModuleTemplates);

        /**
         * Construct the ingest module pipelines from the ingest module pipeline
         * templates and populate the ingest module tiers.
         */
        List<IngestModuleTier> moduleTiers = new ArrayList<>();
        IngestModuleTier firstTier = new IngestModuleTier();
        int numberOfFileIngestThreads = IngestManager.getInstance().getNumberOfFileIngestThreads();
        List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();
        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
            fileIngestPipelines.add(new FileIngestPipeline(executor, filePipelineTemplate));
        }
        firstTier.setsFileIngestPipelines(fileIngestPipelines);
        firstTier.setDataSourceIngestPipeline(new DataSourceIngestPipeline(executor, firstStageDataSourcePipelineTemplate));
        firstTier.setDataArtifactIngestPipeline(new DataArtifactIngestPipeline(executor, artifactPipelineTemplate));
        firstTier.setAnalysisResultIngestPipeline(new AnalysisResultIngestPipeline(executor, resultsPipelineTemplate));
        moduleTiers.add(firstTier);
        IngestModuleTier secondTier = new IngestModuleTier();
        secondTier.setDataSourceIngestPipeline(new DataSourceIngestPipeline(executor, secondStageDataSourcePipelineTemplate));
        
        // RJCTODO: Remove test
//        List<FileIngestPipeline> fileIngestPipelines2 = new ArrayList<>();
//        for (int i = 0; i < numberOfFileIngestThreads; ++i) {
//            fileIngestPipelines2.add(new FileIngestPipeline(executor, filePipelineTemplate));
//        }
//        secondTier.setsFileIngestPipelines(fileIngestPipelines2); 
        
        moduleTiers.add(secondTier);
        return moduleTiers;
    }

    /**
     * Adds an ingest module template to one of two mappings of ingest module
     * factory class names to module templates. One mapping is for ingest
     * modules imnplemented using Java, and the other is for ingest modules
     * implemented using Jython.
     *
     * @param mapping       Mapping for Java ingest module templates.
     * @param jythonMapping Mapping for Jython ingest module templates.
     * @param template      The ingest module template.
     */
    private static void addModuleTemplateToSortingMap(Map<String, IngestModuleTemplate> mapping, Map<String, IngestModuleTemplate> jythonMapping, IngestModuleTemplate template) {
        String className = template.getModuleFactory().getClass().getCanonicalName();
        String jythonName = getModuleNameFromJythonClassName(className);
        if (jythonName != null) {
            jythonMapping.put(jythonName, template);
        } else {
            mapping.put(className, template);
        }
    }

    /**
     * Extracts a module class name from a Jython module proxy class name. For
     * example, a Jython class name such
     * "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory$14"
     * will be parsed to return
     * "GPX_Parser_Module.GPXParserFileIngestModuleFactory."
     *
     * @param className The canonical class name.
     *
     * @return The Jython proxu class name or null if the extraction fails.
     */
    private static String getModuleNameFromJythonClassName(String className) {
        Matcher m = JYTHON_MODULE_REGEX.matcher(className);
        if (m.find()) {
            return String.format("%s.%s", m.group(1), m.group(2)); //NON-NLS
        } else {
            return null;
        }
    }

    /**
     * Creates an ingest module pipeline template that can be used to construct
     * an ingest module pipeline.
     *
     * @param javaIngestModuleTemplates   Ingest module templates for ingest
     *                                    modules implemented using Java.
     * @param jythonIngestModuleTemplates Ingest module templates for ingest
     *                                    modules implemented using Jython.
     * @param pipelineConfig              An ordered list of the ingest modules
     *                                    that belong in the ingest pipeline for
     *                                    which the template is being created.
     *
     * @return An ordered list of ingest module templates, i.e., a template for
     *         creating ingest module pipelines.
     */
    private static List<IngestModuleTemplate> createIngestPipelineTemplate(Map<String, IngestModuleTemplate> javaIngestModuleTemplates, Map<String, IngestModuleTemplate> jythonIngestModuleTemplates, List<String> pipelineConfig) {
        List<IngestModuleTemplate> pipelineTemplate = new ArrayList<>();
        for (String moduleClassName : pipelineConfig) {
            if (javaIngestModuleTemplates.containsKey(moduleClassName)) {
                pipelineTemplate.add(javaIngestModuleTemplates.remove(moduleClassName));
            } else if (jythonIngestModuleTemplates.containsKey(moduleClassName)) {
                pipelineTemplate.add(jythonIngestModuleTemplates.remove(moduleClassName));
            }
        }
        return pipelineTemplate;
    }

    /**
     * Sorts ingest module templates so that core Autopsy ingest modules come
     * before third party ingest modules, and ingest modules implemented using
     * Java come before ingest modules implemented using Jython.
     *
     * @param sortedModules The output list to hold the sorted modules.
     * @param javaModules   The input ingest module templates for modules
     *                      implemented using Java.
     * @param jythonModules The ingest module templates for modules implemented
     *                      using Jython.
     */
    private static void addToIngestPipelineTemplate(final List<IngestModuleTemplate> sortedModules, final Map<String, IngestModuleTemplate> javaModules, final Map<String, IngestModuleTemplate> jythonModules) {
        final List<IngestModuleTemplate> autopsyModules = new ArrayList<>();
        final List<IngestModuleTemplate> thirdPartyModules = new ArrayList<>();
        Stream.concat(javaModules.entrySet().stream(), jythonModules.entrySet().stream()).forEach((templateEntry) -> {
            if (templateEntry.getKey().startsWith(AUTOPSY_MODULE_PREFIX)) {
                autopsyModules.add(templateEntry.getValue());
            } else {
                thirdPartyModules.add(templateEntry.getValue());
            }
        });
        sortedModules.addAll(autopsyModules);
        sortedModules.addAll(thirdPartyModules);
    }

    /**
     * Private constructor to prevent instatiation of this utility class.
     */
    IngestModuleTierBuilder() {
    }

}
