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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;

/**
 * Runs one or more processes in a highly configurable way.
 */
public class ProcessRunner {

    /**
     * A process runner does a wait with a timeout on its process and queries
     * the terminator each time the timeout expires to determine whether or not
     * to kill the process.
     */
    public interface Terminator {

        /**
         * Returns true if the process runner should terminate the currently
         * running process.
         *
         * @return True or false.
         */
        boolean shouldTerminateProcess();
    }

    private static final Logger logger = Logger.getLogger(ProcessRunner.class.getName());
    private static final long DEFAULT_TIMEOUT = 1000;
    private static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.MILLISECONDS;
    private final ProcessBuilder builder;
    private final Terminator terminator;
    private String command;

    /**
     * Constructs a process runner with an initial command line, the current
     * working directory as the working directory, and a do-nothing process
     * terminator.
     *
     * @param command The command to be executed.
     * @param args The arguments to the command.
     */
    public ProcessRunner(String command, List<String> args) {
        this(command, args, new NullTerminator());
    }

    /**
     * Constructs a process runner for an ingest module with an initial command
     * line, the current working directory as the working directory, and a
     * process terminator that checks for ingest job cancellation.
     *
     * @param ingestModule An ingest module for which the process is to be run.
     * @param context The Ingest job context of the ingest module.
     * @param command The command to be executed.
     * @param args The arguments to the command.
     */
    public ProcessRunner(IngestModule ingestModule, IngestJobContext context, String command, List<String> args) {
        this(command, args, ProcessRunner.getIngestModuleTerminator(ingestModule, context));
    }

    /**
     * Constructs a process runner with an initial command line, the current
     * working directory as the working directory, and a custom process
     * terminator.
     *
     * @param command The command to be executed.
     * @param args The arguments to the command.
     * @param terminator The terminator.
     */
    public ProcessRunner(String command, List<String> args, Terminator terminator) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(args);
        this.builder = new ProcessBuilder(commandLine);
        this.terminator = terminator;
        this.command = command;
    }

    /**
     * Returns a string map view of this process runner's environment.
     *
     * @return The environment.
     */
    public Map<String, String> getEnvironment() {
        return this.builder.environment();
    }

    /**
     * Sets (resets) this process runner's command line.
     *
     * @param command The command to be executed.
     * @param args The arguments to the command.
     */
    public void setCommandLine(String command, List<String> args) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(args);
        this.builder.command(commandLine);
        this.command = command;
    }

    /**
     * Sets (resets) this process runner's working directory.
     *
     * @param directory
     */
    public void setWorkingDirectory(File directory) {
        this.builder.directory(directory);
    }

    /**
     * Redirects standard input for this process runner to a file
     *
     * @param file The file.
     */
    public void redirectInput(File file) {
        this.builder.redirectInput(file);
    }

    /**
     * Redirects standard output for this process runner to a file
     *
     * @param file The file.
     */
    public void redirectOutput(File file) {
        this.builder.redirectOutput(file);
    }

    /**
     * Redirects standard error for this process runner to a file
     *
     * @param file The file.
     */
    public void redirectError(File file) {
        this.builder.redirectError(file);
    }

    /**
     * Runs the configured process with a default timeout for termination
     * checks.
     *
     * @return The exit value of the process.
     * @throws IOException
     */
    public int run() throws IOException {
        return this.run(ProcessRunner.DEFAULT_TIMEOUT, ProcessRunner.DEFAULT_TIMEOUT_UNITS);
    }

    /**
     * Runs the configured process a specified timeout for termination checks.
     *
     * @param timeOut The timeout.
     * @param units The units for the timeout.
     * @return The exit value of the process.
     * @throws IOException
     */
    public int run(long timeOut, TimeUnit units) throws IOException {
        Process process = this.builder.start();
        try {
            do {
                process.waitFor(timeOut, units);
                if (process.isAlive() && this.terminator.shouldTerminateProcess()) {
                    process.destroyForcibly();
                }
            } while (process.isAlive());
        } catch (InterruptedException ex) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            ProcessRunner.logger.log(Level.INFO, "Thread interrupted while running {0}", this.command);
            Thread.currentThread().interrupt();
        }
        return process.exitValue();
    }

    /**
     * Creates a process terminator for an ingest module based on the ingest
     * module type.
     *
     * @param ingestModule The ingest module.
     * @param context The ingest job context for the ingest module.
     * @return The process terminator.
     */
    private static Terminator getIngestModuleTerminator(IngestModule ingestModule, IngestJobContext context) {
        if (ingestModule instanceof DataSourceIngestModule) {
            return new DataSourceIngestModuleTerminator(context);
        } else {
            return new FileIngestModuleTerminator(context);
        }
    }

    /**
     * A do-nothing process terminator.
     */
    private static class NullTerminator implements Terminator {

        /**
         * @inheritDoc
         */
        @Override
        public boolean shouldTerminateProcess() {
            return false;
        }
    }

    /**
     * A process terminator for data source ingest modules that checks for
     * ingest job cancellation.
     */
    private static class DataSourceIngestModuleTerminator implements Terminator {

        private final IngestJobContext context;

        /**
         * Constructs a process terminator for a data source ingest module.
         *
         * @param context The ingest job context for the ingest module.
         */
        private DataSourceIngestModuleTerminator(IngestJobContext context) {
            this.context = context;
        }

        /**
         * @inheritDoc
         */
        @Override
        public boolean shouldTerminateProcess() {
            return this.context.dataSourceIngestIsCancelled();
        }
    }

    /**
     * A process terminator for file ingest modules that checks for ingest job
     * cancellation.
     */
    private static class FileIngestModuleTerminator implements Terminator {

        private final IngestJobContext context;

        /**
         * Constructs a process terminator for a file ingest module.
         *
         * @param context The ingest job context for the ingest module.
         */
        private FileIngestModuleTerminator(IngestJobContext context) {
            this.context = context;
        }

        /**
         * @inheritDoc
         */
        @Override
        public boolean shouldTerminateProcess() {
            return this.context.fileIngestIsCancelled();
        }
    }

}
