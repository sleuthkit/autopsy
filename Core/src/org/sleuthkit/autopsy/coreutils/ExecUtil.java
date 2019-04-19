/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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

import com.sun.javafx.PlatformUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Executes a command line using an operating system process with a configurable
 * timeout and pluggable logic to kill or continue the process on timeout.
 */
public final class ExecUtil {

    private static final long DEFAULT_TIMEOUT = 5;
    private static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;

    /**
     * The execute() methods do a wait() with a timeout on the executing process
     * and query a process terminator each time the timeout expires to determine
     * whether or not to kill the process. See
     * DataSourceIngestModuleProcessTerminator and
     * FileIngestModuleProcessTerminator as examples of ProcessTerminator
     * implementations.
     */
    public interface ProcessTerminator {

        /**
         * Decides whether or not to terminate a process being run by a
         * ExcUtil.execute() methods.
         *
         * @return True or false.
         */
        boolean shouldTerminateProcess();
    }

    /**
     * A process terminator that can be used to kill a process spawned by a
     * thread that has been interrupted.
     */
    public static class InterruptedThreadProcessTerminator implements ProcessTerminator {

        @Override
        public boolean shouldTerminateProcess() {
            return Thread.currentThread().isInterrupted();
        }
    }

    /**
     * A process terminator that can be used to kill a process after it exceeds
     * a maximum allowable run time.
     */
    public static class TimedProcessTerminator implements ProcessTerminator {

        private final long startTimeInSeconds;
        private final long maxRunTimeInSeconds;

        /**
         * Creates a process terminator that can be used to kill a process after
         * it has run for a given period of time.
         *
         * @param maxRunTimeInSeconds The maximum allowable run time in seconds.
         */
        public TimedProcessTerminator(long maxRunTimeInSeconds) {
            this.maxRunTimeInSeconds = maxRunTimeInSeconds;
            this.startTimeInSeconds = (new Date().getTime()) / 1000;
        }

        /**
         * Creates a process terminator that can be used to kill a process after
         * it has run for a given period of time. Maximum allowable run time is
         * set via Autopsy Options panel. If the process termination
         * functionality is disabled then the maximum allowable time is set to
         * MAX_INT seconds.
         */
        public TimedProcessTerminator() {
            if (UserPreferences.getIsTimeOutEnabled() && UserPreferences.getProcessTimeOutHrs() > 0) {
                // user specified time out
                this.maxRunTimeInSeconds = UserPreferences.getProcessTimeOutHrs() * 3600;
            } else {
                // never time out
                this.maxRunTimeInSeconds = Long.MAX_VALUE;
            }
            this.startTimeInSeconds = (new Date().getTime()) / 1000;
        }

        @Override
        public boolean shouldTerminateProcess() {
            long currentTimeInSeconds = (new Date().getTime()) / 1000;
            return (currentTimeInSeconds - this.startTimeInSeconds) > this.maxRunTimeInSeconds;
        }
    }

    /**
     * Runs a process without a timeout and terminator.
     *
     * @param processBuilder A process builder used to configure and construct
     *                       the process to be run.
     *
     * @return the exit value of the process
     *
     * @throws SecurityException if a security manager exists and vetoes any
     *                           aspect of running the process.
     * @throws IOException       if an I/O error occurs.
     */
    public static int execute(ProcessBuilder processBuilder) throws SecurityException, IOException {
        return ExecUtil.execute(processBuilder, 30, TimeUnit.DAYS, new ProcessTerminator() {
            @Override
            public boolean shouldTerminateProcess() {
                return false;
            }
        });
    }

    /**
     * Runs a process using the default timeout and a custom terminator.
     *
     * @param processBuilder A process builder used to configure and construct
     *                       the process to be run.
     * @param terminator     The terminator.
     *
     * @return the exit value of the process
     *
     * @throws SecurityException if a security manager exists and vetoes any
     *                           aspect of running the process.
     * @throws IOException       if an I/O error occurs.
     */
    public static int execute(ProcessBuilder processBuilder, ProcessTerminator terminator) throws SecurityException, IOException {
        return ExecUtil.execute(processBuilder, ExecUtil.DEFAULT_TIMEOUT, ExecUtil.DEFAULT_TIMEOUT_UNITS, terminator);
    }

    /**
     * Runs a process using a custom terminator.
     *
     * @param processBuilder A process builder used to configure and construct
     *                       the process to be run.
     * @param timeOut        The duration of the timeout.
     * @param units          The units for the timeout.
     * @param terminator     The terminator.
     *
     * @return the exit value of the process
     *
     * @throws SecurityException if a security manager exists and vetoes any
     *                           aspect of running the process.
     * @throws IOException       if an I/o error occurs.
     */
    public static int execute(ProcessBuilder processBuilder, long timeOut, TimeUnit units, ProcessTerminator terminator) throws SecurityException, IOException {
        Process process = processBuilder.start();
        try {
            do {
                process.waitFor(timeOut, units);
                if (process.isAlive() && terminator.shouldTerminateProcess()) {
                    killProcess(process);
                    try {
                        process.waitFor(); //waiting to help ensure process is shutdown before calling interrupt() or returning 
                    } catch (InterruptedException exx) {
                        Logger.getLogger(ExecUtil.class.getName()).log(Level.INFO, String.format("Wait for process termination following killProcess was interrupted for command %s", processBuilder.command().get(0)));
                    }
                }
            } while (process.isAlive());
        } catch (InterruptedException ex) {
            if (process.isAlive()) {
                killProcess(process);
            }
            try {
                process.waitFor(); //waiting to help ensure process is shutdown before calling interrupt() or returning 
            } catch (InterruptedException exx) {
                Logger.getLogger(ExecUtil.class.getName()).log(Level.INFO, String.format("Wait for process termination following killProcess was interrupted for command %s", processBuilder.command().get(0)));
            }
            Logger.getLogger(ExecUtil.class.getName()).log(Level.INFO, "Thread interrupted while running {0}", processBuilder.command().get(0)); // NON-NLS
            Thread.currentThread().interrupt();
        }
        return process.exitValue();
    }

    /**
     * Kills a process and its children
     *
     * @param process The parent process to kill
     */
    public static void killProcess(Process process) {
        if (process == null) {
            return;
        }

        try {
            if (PlatformUtil.isWindows()) {
                Win32Process parentProcess = new Win32Process(process);
                List<Win32Process> children = parentProcess.getChildren();

                children.stream().forEach((child) -> {
                    child.terminate();
                });
                parentProcess.terminate();
            } else {
                process.destroyForcibly();
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error occurred when attempting to kill process: {0}", ex.getMessage()); // NON-NLS
        }
    }

    private static final Logger logger = Logger.getLogger(ExecUtil.class.getName());
    private Process proc = null;
    private ExecUtil.StreamToStringRedirect errorStringRedirect = null;
    private ExecUtil.StreamToStringRedirect outputStringRedirect = null;
    private ExecUtil.StreamToWriterRedirect outputWriterRedirect = null;
    private int exitValue = -100;

    /**
     * Execute a process. Redirect asynchronously stdout to a string and stderr
     * to nowhere. Use only for small outputs, otherwise use the execute()
     * variant with Writer.
     *
     * @param aCommand command to be executed
     * @param params   parameters of the command
     *
     * @return string buffer with captured stdout
     */
    @Deprecated
    public synchronized String execute(final String aCommand, final String... params) throws IOException, InterruptedException {
        // build command array
        String[] arrayCommand = new String[params.length + 1];
        arrayCommand[0] = aCommand;

        StringBuilder arrayCommandToLog = new StringBuilder();
        arrayCommandToLog.append(aCommand).append(" ");

        for (int i = 1; i < arrayCommand.length; i++) {
            arrayCommand[i] = params[i - 1];
            arrayCommandToLog.append(arrayCommand[i]).append(" ");
        }

        final Runtime rt = Runtime.getRuntime();
        logger.log(Level.INFO, "Executing {0}", arrayCommandToLog.toString()); //NON-NLS

        proc = rt.exec(arrayCommand);

        //stderr redirect
        errorStringRedirect = new ExecUtil.StreamToStringRedirect(proc.getErrorStream(), "ERROR"); //NON-NLS
        errorStringRedirect.start();

        //stdout redirect
        outputStringRedirect = new ExecUtil.StreamToStringRedirect(proc.getInputStream(), "OUTPUT"); //NON-NLS
        outputStringRedirect.start();

        //wait for process to complete and capture error core
        this.exitValue = proc.waitFor();

        // wait for output redirectors to finish writing / reading
        outputStringRedirect.join();
        errorStringRedirect.join();

        return outputStringRedirect.getOutput();
    }

    /**
     * Execute a process. Redirect asynchronously stdout to a passed in writer
     * and stderr to nowhere.
     *
     * @param stdoutWriter file writer to write stdout to
     * @param aCommand     command to be executed
     * @param params       parameters of the command
     *
     * @return string buffer with captured stdout
     */
    @Deprecated
    public synchronized void execute(final Writer stdoutWriter, final String aCommand, final String... params) throws IOException, InterruptedException {

        // build command array
        String[] arrayCommand = new String[params.length + 1];
        arrayCommand[0] = aCommand;

        StringBuilder arrayCommandToLog = new StringBuilder();
        arrayCommandToLog.append(aCommand).append(" ");

        for (int i = 1; i < arrayCommand.length; i++) {
            arrayCommand[i] = params[i - 1];
            arrayCommandToLog.append(arrayCommand[i]).append(" ");
        }

        final Runtime rt = Runtime.getRuntime();
        logger.log(Level.INFO, "Executing {0}", arrayCommandToLog.toString()); //NON-NLS

        proc = rt.exec(arrayCommand);

        //stderr redirect
        errorStringRedirect = new ExecUtil.StreamToStringRedirect(proc.getErrorStream(), "ERROR"); //NON-NLS
        errorStringRedirect.start();

        //stdout redirect
        outputWriterRedirect = new ExecUtil.StreamToWriterRedirect(proc.getInputStream(), stdoutWriter);
        outputWriterRedirect.start();

        //wait for process to complete and capture error core
        this.exitValue = proc.waitFor();
        logger.log(Level.INFO, "{0} exit value: {1}", new Object[]{aCommand, exitValue}); //NON-NLS

        // wait for them to finish writing / reading
        outputWriterRedirect.join();
        errorStringRedirect.join();

        //gc process with its streams
        //proc = null;
    }

    /**
     * Interrupt the running process and stop its stream redirect threads
     */
    @Deprecated
    public synchronized void stop() {

        if (errorStringRedirect != null) {
            errorStringRedirect.stopRun();
            errorStringRedirect = null;
        }

        if (outputStringRedirect != null) {
            outputStringRedirect.stopRun();
            outputStringRedirect = null;
        }

        if (outputWriterRedirect != null) {
            outputWriterRedirect.stopRun();
            outputWriterRedirect = null;
        }

        if (proc != null) {
            proc.destroy();
            proc = null;
        }
    }

    /**
     * Gets the exit value returned by the subprocess used to execute a command.
     *
     * @return The exit value or the distinguished value -100 if this method is
     *         called before the exit value is set.
     */
    @Deprecated
    synchronized public int getExitValue() {
        return this.exitValue;
    }

    /**
     * Asynchronously read the output of a given input stream and write to a
     * string to be returned. Any exception during execution of the command is
     * managed in this thread.
     *
     */
    private static class StreamToStringRedirect extends Thread {

        private static final Logger logger = Logger.getLogger(StreamToStringRedirect.class.getName());
        private final InputStream is;
        private final StringBuffer output = new StringBuffer();
        private volatile boolean doRun = false;

        StreamToStringRedirect(final InputStream anIs, final String aType) {
            this.is = anIs;
            this.doRun = true;
        }

        /**
         * Asynchronous read of the input stream. <br /> Will report output as
         * its its displayed.
         *
         * @see java.lang.Thread#run()
         */
        @Override
        public final void run() {
            final String SEP = System.getProperty("line.separator");
            InputStreamReader isr;
            BufferedReader br = null;
            try {
                isr = new InputStreamReader(this.is);
                br = new BufferedReader(isr);
                String line = null;
                while (doRun && (line = br.readLine()) != null) {
                    this.output.append(line).append(SEP);
                }
            } catch (final IOException ex) {
                logger.log(Level.WARNING, "Error redirecting stream to string buffer", ex); //NON-NLS
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error closing stream reader", ex); //NON-NLS
                    }
                }
            }
        }

        /**
         * Stop running the stream redirect. The thread will exit out gracefully
         * after the current readLine() on stream unblocks
         */
        public void stopRun() {
            doRun = false;
        }

        /**
         * Get output filled asynchronously. <br /> Should be called after
         * execution
         *
         * @return final output
         */
        public final String getOutput() {
            return this.output.toString();
        }
    }

    /**
     * Asynchronously read the output of a given input stream and write to a
     * file writer passed in by the client. Client is responsible for closing
     * the writer.
     *
     * Any exception during execution of the command is managed in this thread.
     *
     */
    private static class StreamToWriterRedirect extends Thread {

        private static final Logger logger = Logger.getLogger(StreamToStringRedirect.class.getName());
        private final InputStream is;
        private volatile boolean doRun = false;
        private Writer writer = null;

        StreamToWriterRedirect(final InputStream anIs, final Writer writer) {
            this.is = anIs;
            this.writer = writer;
            this.doRun = true;
        }

        /**
         * Asynchronous read of the input stream. <br /> Will report output as
         * its its displayed.
         *
         * @see java.lang.Thread#run()
         */
        @Override
        public final void run() {
            final String SEP = System.getProperty("line.separator");
            InputStreamReader isr;
            BufferedReader br = null;
            try {
                isr = new InputStreamReader(this.is);
                br = new BufferedReader(isr);
                String line = null;
                while (doRun && (line = br.readLine()) != null) {
                    writer.append(line).append(SEP);
                }
            } catch (final IOException ex) {
                logger.log(Level.SEVERE, "Error reading output and writing to file writer", ex); //NON-NLS
            } finally {
                try {
                    if (doRun) {
                        writer.flush();
                    }
                    if (br != null) {
                        br.close();
                    }

                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error flushing file writer", ex); //NON-NLS
                }
            }
        }

        /**
         * Stop running the stream redirect. The thread will exit out gracefully
         * after the current readLine() on stream unblocks
         */
        public void stopRun() {
            doRun = false;
        }
    }
}
