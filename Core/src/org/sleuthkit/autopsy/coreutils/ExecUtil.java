/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2020 Basis Technology Corp.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.lang3.SystemUtils;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Executes a command line using an operating system process with pluggable
 * logic to terminate the process under certain conditions.
 */
public final class ExecUtil {

    private static final Logger logger = Logger.getLogger(ExecUtil.class.getName());
    private static final long DEFAULT_TERMINATION_CHECK_INTERVAL = 5;
    private static final TimeUnit DEFAULT_TERMINATION_CHECK_INTERVAL_UNITS = TimeUnit.SECONDS;
    private static final long MAX_WAIT_FOR_TERMINATION = 1;
    private static final TimeUnit MAX_WAIT_FOR_TERMINATION_UNITS = TimeUnit.MINUTES;

    /**
     * An interface for defining the conditions under which an operating system
     * process spawned by an ExecUtil method should be terminated.
     *
     * Some existing implementations: TimedProcessTerminator,
     * InterruptedThreadProcessTerminator,
     * DataSourceIngestModuleProcessTerminator and
     * FileIngestModuleProcessTerminator.
     */
    public interface ProcessTerminator {

        /**
         * Decides whether or not to terminate a process being run by an
         * ExecUtil method.
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
        private final Long maxRunTimeInSeconds;

        /**
         * Creates a process terminator that can be used to kill a process after
         * it exceeds a maximum allowable run time.
         *
         * @param maxRunTimeInSeconds The maximum allowable run time in seconds.
         */
        public TimedProcessTerminator(long maxRunTimeInSeconds) {
            this.maxRunTimeInSeconds = maxRunTimeInSeconds;
            this.startTimeInSeconds = (new Date().getTime()) / 1000;
        }

        /**
         * Creates a process terminator that can be used to kill a process after
         * it exceeds a global maximum allowable run time specified as a user
         * preference. If the user preference is not set, this terminator has no
         * effect.
         */
        public TimedProcessTerminator() {
            if (UserPreferences.getIsTimeOutEnabled() && UserPreferences.getProcessTimeOutHrs() > 0) {
                this.maxRunTimeInSeconds = (long) UserPreferences.getProcessTimeOutHrs() * 3600;
            } else {
                this.maxRunTimeInSeconds = null;
            }
            this.startTimeInSeconds = (new Date().getTime()) / 1000;
        }

        @Override
        public boolean shouldTerminateProcess() {
            if (maxRunTimeInSeconds != null) {
                long currentTimeInSeconds = (new Date().getTime()) / 1000;
                return (currentTimeInSeconds - this.startTimeInSeconds) > this.maxRunTimeInSeconds;
            } else {
                return false;
            }
        }
    }
    
    /**
     * This class takes a list of ProcessTerminators checking all of them
     * during shouldTerminateProcess.
     */
    public static class HybridTerminator implements ProcessTerminator {
        private final List<ProcessTerminator> terminatorList;
        
        /**
         * Constructs a new instance of the terminator.
         * 
         * @param terminators A list of terminators.
         */
        public HybridTerminator(List<ProcessTerminator> terminators) {
            this.terminatorList = terminators;
        }
        
        @Override
        public boolean shouldTerminateProcess() {
            for(ProcessTerminator terminator: terminatorList) {
                if(terminator.shouldTerminateProcess()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Runs a process without a process terminator. This method should be used
     * with caution because there is nothing to stop the process from running
     * forever.
     *
     * IMPORTANT: This method blocks while the process is running. For legacy
     * API reasons, if there is an interrupt the InterruptedException is wrapped
     * in an IOException instead of being thrown. Callers that need to know
     * about interrupts to detect backgound task cancellation can call
     * Thread.isInterrupted() or, if the thread's interrupt flag should be
     * cleared, Thread.interrupted().
     *
     * @param processBuilder A process builder used to configure and construct
     *                       the process to be run.
     *
     * @return The exit value of the process.
     *
     * @throws SecurityException If a security manager exists and vetoes any
     *                           aspect of running the process.
     * @throws IOException       If an error occurs while executing or
     *                           terminating the process.
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
     * Runs a process using the default termination check interval and a process
     * terminator.
     *
     * IMPORTANT: This method blocks while the process is running. For legacy
     * API reasons, if there is an interrupt the InterruptedException is wrapped
     * in an IOException instead of being thrown. Callers that need to know
     * about interrupts to detect backgound task cancellation can call
     * Thread.isInterrupted() or, if the thread's interrupt flag should be
     * cleared, Thread.interrupted().
     *
     * @param processBuilder A process builder used to configure and construct
     *                       the process to be run.
     * @param terminator     The terminator.
     *
     * @return The exit value of the process.
     *
     * @throws SecurityException If a security manager exists and vetoes any
     *                           aspect of running the process.
     * @throws IOException       If an error occurs while executing or
     *                           terminating the process.
     */
    public static int execute(ProcessBuilder processBuilder, ProcessTerminator terminator) throws SecurityException, IOException {
        return ExecUtil.execute(processBuilder, ExecUtil.DEFAULT_TERMINATION_CHECK_INTERVAL, ExecUtil.DEFAULT_TERMINATION_CHECK_INTERVAL_UNITS, terminator);
    }

    /**
     * Runs a process using a custom termination check interval and a process
     * terminator.
     *
     * IMPORTANT: This method blocks while the process is running. For legacy
     * API reasons, if there is an interrupt the InterruptedException is wrapped
     * in an IOException instead of being thrown. Callers that need to know
     * about interrupts to detect backgound task cancellation can call
     * Thread.isInterrupted() or, if the thread's interrupt flag should be
     * cleared, Thread.interrupted().
     *
     * @param processBuilder           A process builder used to configure and
     *                                 construct the process to be run.
     * @param terminationCheckInterval The interval at which to query the
     *                                 process terminator to see if the process
     *                                 should be killed.
     * @param units                    The units for the termination check
     *                                 interval.
     * @param terminator               The terminator.
     *
     * @return The exit value of the process.
     *
     * @throws SecurityException If a security manager exists and vetoes any
     *                           aspect of running the process.
     * @throws IOException       If an error occurs while executing or
     *                           terminating the process.
     */
    public static int execute(ProcessBuilder processBuilder, long terminationCheckInterval, TimeUnit units, ProcessTerminator terminator) throws SecurityException, IOException {
        return waitForTermination(processBuilder.command().get(0), processBuilder.start(), terminationCheckInterval, units, terminator);
    }

    /**
     * Waits for an existing process to finish, using a custom termination check
     * interval and a process terminator.
     *
     * IMPORTANT: This method blocks while the process is running. For legacy
     * API reasons, if there is an interrupt the InterruptedException is wrapped
     * in an IOException instead of being thrown. Callers that need to know
     * about interrupts to detect backgound task cancellation can call
     * Thread.isInterrupted() or, if the thread's interrupt flag should be
     * cleared, Thread.interrupted().
     *
     * @param processName              The name of the process, for logging
     *                                 purposes.
     * @param process                  The process.
     * @param terminationCheckInterval The interval at which to query the
     *                                 process terminator to see if the process
     *                                 should be killed.
     * @param units                    The units for the termination check
     *                                 interval.
     * @param terminator               The process terminator.
     *
     * @return The exit value of the process.
     *
     * @throws IOException If an error occurs while executing or terminating the
     *                     process.
     */
    public static int waitForTermination(String processName, Process process, long terminationCheckInterval, TimeUnit units, ProcessTerminator terminator) throws IOException {
        try {
            return waitForProcess(processName, process, terminationCheckInterval, units, terminator);
        } catch (InterruptedException ex) {
            /*
             * Reset the interrupted flag and wrap the exception in an
             * IOException for backwards compatibility.
             */
            Thread.currentThread().interrupt();
            throw new IOException(String.format("Interrupted executing %s", processName), ex); //NON-NLS
        }
    }

    /**
     * Waits for an existing process to finish, using a custom termination check
     * interval and a process terminator.
     *
     * @param processName              The name of the process, for logging
     *                                 purposes.
     * @param process                  The process.
     * @param terminationCheckInterval The interval at which to query the
     *                                 process terminator to see if the process
     *                                 should be killed.
     * @param units                    The units for the termination check
     *                                 interval.
     * @param terminator               The process terminator.
     *
     * @return The exit value of the process.
     *
     * @throws IOException          If an error occurs while executing or
     *                              terminating the process.
     * @throws InterruptedException If the thread running this code is
     *                              interrupted while the process is running.
     */
    private static int waitForProcess(String processName, Process process, long terminationCheckInterval, TimeUnit units, ProcessTerminator terminator) throws IOException, InterruptedException {
        do {
            try {
                process.waitFor(terminationCheckInterval, units);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, String.format("Interrupted executing %s", processName), ex); //NON-NLS
                Thread.currentThread().interrupt();
                terminateProcess(processName, process);
                /*
                 * Note that if the preceding call to terminateProcess() throws
                 * an IOException, the caller will get that exception instead of
                 * this InterruptedException, which is arguably preferable. If
                 * terminateProcess() does not throw an IOException, then its
                 * call to waitFor() will throw a fresh InterruptedException,
                 * which is fine.
                 */
                throw ex;
            }
            if (process.isAlive() && terminator.shouldTerminateProcess()) {
                terminateProcess(processName, process);
            }
        } while (process.isAlive());

        /*
         * Careful: Process.exitValue() throws an IllegalStateException if the
         * process is still alive when the method is called. This code is set up
         * so that the only way Process.exitValue() can be called is when it has
         * not been bypassed by an exception and the preceding loop has
         * terminated with Process.isAlive == false.
         */
        return process.exitValue();
    }

    /**
     * Terminates a process and its children, waiting with a time out to try to
     * ensure the process is no longer alive before returning.
     *
     * IMPORTANT: This method blocks while the process is running. For legacy
     * API reasons, if there is an interrupt (or any other exception) the
     * exception is logged instead of being thrown. Callers that need to know
     * about interrupts to detect backgound task cancellation can call
     * Thread.isInterrupted() or, if the thread's interrupt flag should be
     * cleared, Thread.interrupted().
     *
     * @param process The process.
     */
    public static void killProcess(Process process) {
        String processName = process.toString();
        try {
            terminateProcess(processName, process);
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Error occured executing %s", processName), ex); //NON-NLS
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, String.format("Interrupted executing %s", processName), ex); //NON-NLS
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Terminates a process and its children, waiting with a time out to try to
     * ensure the process is no longer alive before returning.
     *
     * @param processName The name of the process, for logging purposes.
     * @param process     The process.
     *
     * @throws IOException          If an error occurs while trying to terminate
     *                              the process.
     * @throws InterruptedException If the thread running this code is
     *                              interrupted while waiting for the process to
     *                              terminate.
     */
    private static void terminateProcess(String processName, Process process) throws IOException, InterruptedException {
        if (process == null || !process.isAlive()) {
            return;
        }

        if (SystemUtils.IS_OS_WINDOWS) {
            try {
                Win32Process parentProcess = new Win32Process(process);
                List<Win32Process> children = parentProcess.getChildren();
                children.stream().forEach((child) -> {
                    child.terminate();
                });
                parentProcess.terminate();
            } catch (Exception ex) {
                /*
                 * Wrap whatever exception was thrown from Windows in an
                 * exception that is appropriate for this API.
                 */
                throw new IOException(String.format("Error occured terminating %s", processName), ex); //NON-NLS
            }
        } else {
            process.destroyForcibly();
        }

        if (!process.waitFor(MAX_WAIT_FOR_TERMINATION, MAX_WAIT_FOR_TERMINATION_UNITS)) {
            throw new IOException(String.format("Failed to terminate %s after %d %s", processName, MAX_WAIT_FOR_TERMINATION, MAX_WAIT_FOR_TERMINATION_UNITS)); //NON-NLS            
        }
    }

    /*
     * Fields used by deprecated methods that require instantiation of an
     * ExecUtil object.
     */
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
     *
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
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
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
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
