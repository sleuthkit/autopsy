/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import org.python.google.common.collect.ImmutableList;

/**
 * A set of ingest module pipelines grouped into a tier for concurrent analysis
 * during an ingest job.
 */
class IngestModuleTier {

    private DataSourceIngestPipeline dataSourceIngestPipeline;
    private final LinkedBlockingQueue<FileIngestPipeline> fileIngestPipelinesQueue = new LinkedBlockingQueue<>();
    private final List<FileIngestPipeline> fileIngestPipelines = new ArrayList<>();
    private DataArtifactIngestPipeline dataArtifactIngestPipeline;
    private AnalysisResultIngestPipeline analysisResultIngestPipeline;

    /**
     * Sets the data source ingest pipeline for this tier, if there is one.
     *
     * @param pipeline The pipeline.
     */
    void setDataSourceIngestPipeline(DataSourceIngestPipeline pipeline) {
        dataSourceIngestPipeline = pipeline;
    }    
    
    /**
     * Checks to see if there is at least one data source level ingest module in
     * this tier.
     *
     * @return True or false.
     */
    boolean hasDataSourceIngestModules() {
        return (dataSourceIngestPipeline != null && dataSourceIngestPipeline.isEmpty() == false);
    }

    /**
     * Gets the data source ingest pipeline for this tier, if there is one.
     *
     * @return The pipeline, in Optional form.
     */
    Optional<DataSourceIngestPipeline> getDataSourceIngestPipeline() {
        return Optional.ofNullable(dataSourceIngestPipeline);
    }    

    /**
     * Sets the file ingest pipelines for this tier, if there are any. All of
     * the pipelines should be identical copies, and the number of pipeline
     * copies should match the number of file ingest threads in the ingest
     * manager.
     *
     * @param pipelines The pipelines.
     *
     * @throws InterruptedException The exception is thrown if the current
     *                              thread is interrupted while blocked waiting
     *                              for the pipelines to be added to an internal
     *                              data structure.
     */
    void setsFileIngestPipelines(List<FileIngestPipeline> pipelines) throws InterruptedException {
        fileIngestPipelines.addAll(pipelines);
        for (FileIngestPipeline pipeline : pipelines) {
            fileIngestPipelinesQueue.put(pipeline);
        }
    }

    /**
     * Checks to see if there is at least one file ingest module in this tier.
     *
     * @return True or false.
     */
    boolean hasFileIngestModules() {
        return (!fileIngestPipelines.isEmpty() && !fileIngestPipelines.get(0).isEmpty());
    }

    /**
     * Gets all of the file ingest pipeline copies.
     *
     * @return The pipeline copies, may be an empty list.
     */
    List<FileIngestPipeline> getFileIngestPipelines() {
        return ImmutableList.copyOf(fileIngestPipelines);
    }

    /**
     * Gets the next available file ingest pipeline copy for this tier, blocking
     * until one becomes available.
     *
     * @return The pipeline.
     *
     * @throws InterruptedException The exception is thrown if the current
     *                              thread is interrupted while blocked waiting
     *                              for the next available file ingest pipeline.
     */
    FileIngestPipeline takeFileIngestPipeline() throws InterruptedException {
        return fileIngestPipelinesQueue.take();
    }

    /**
     * Returns a file ingest pipeline.
     *
     * @param pipeline The pipeline.
     *
     * @throws InterruptedException The exception is thrown if the current
     *                              thread is interrupted while blocked waiting
     *                              for pipeline to be stored in an internal
     *                              data structure.
     */
    void returnFileIngestPipeleine(FileIngestPipeline pipeline) throws InterruptedException {
        fileIngestPipelinesQueue.put(pipeline);
    }

    /**
     * Sets the data artifact ingest pipeline for this tier, if there is one.
     *
     * @param pipeline The pipeline.
     */
    void setDataArtifactIngestPipeline(DataArtifactIngestPipeline pipeline) {
        dataArtifactIngestPipeline = pipeline;
    }

    /**
     * Checks to see if there is at least one data artifact ingest module in
     * this tier.
     *
     * @return True or false.
     */
    boolean hasDataArtifactIngestModules() {
        return (dataArtifactIngestPipeline != null && dataArtifactIngestPipeline.isEmpty() == false);
    }

    /**
     * Gets the data artifact ingest pipeline for this tier, if there is one.
     *
     * @return The pipeline, in Optional form.
     */
    Optional<DataArtifactIngestPipeline> getDataArtifactIngestPipeline() {
        return Optional.ofNullable(dataArtifactIngestPipeline);
    }

    /**
     * Sets the analysis result ingest pipeline for this tier, if there is one.
     *
     * @param pipeline The pipeline.
     */
    void setAnalysisResultIngestPipeline(AnalysisResultIngestPipeline pipeline) {
        analysisResultIngestPipeline = pipeline;
    }

    /**
     * Checks to see if there is at least one analysis result ingest module in
     * this tier.
     *
     * @return True or false.
     */
    boolean hasAnalysisResultIngestModules() {
        return (analysisResultIngestPipeline != null && analysisResultIngestPipeline.isEmpty() == false);
    }

    /**
     * Gets the analysis result ingest pipeline for this tier, if there is one.
     *
     * @return The pipeline, in Optional form.
     */
    Optional<AnalysisResultIngestPipeline> getAnalysisResultIngestPipeline() {
        return Optional.ofNullable(analysisResultIngestPipeline);
    }

}
