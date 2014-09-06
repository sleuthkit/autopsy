/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2014 Basis Technology Corp.
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
import java.util.logging.Level;

/**
 * Takes care of forking a process and reading output / error streams to either a
 * string buffer or directly to a file writer
 * BC: @@@ This code scares me in a multi-threaded env. I think the arguments should be passed into the constructor
 * and different run methods that either return the string or use the redirected writer. 
 */
 public final class ExecUtil {

    private static final Logger logger = Logger.getLogger(ExecUtil.class.getName());
    private Process proc = null;
    private final String command = null;
    private ExecUtil.StreamToStringRedirect errorStringRedirect = null;
    private ExecUtil.StreamToStringRedirect outputStringRedirect = null;
    private ExecUtil.StreamToWriterRedirect outputWriterRedirect = null;

    /**
     * Execute a process. Redirect asynchronously stdout to a string and stderr
     * to nowhere. Use only for small outputs, otherwise use the execute()
     * variant with Writer.
     *
     * @param aCommand command to be executed
     * @param params parameters of the command
     * @return string buffer with captured stdout
     */
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
        final int exitVal = proc.waitFor();
        logger.log(Level.INFO, aCommand + " exit value: " + exitVal); //NON-NLS

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
     * @param aCommand command to be executed
     * @param params parameters of the command
     * @return string buffer with captured stdout
     */
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
        final int exitVal = proc.waitFor();
        logger.log(Level.INFO, "{0} exit value: {1}", new Object[]{aCommand, exitVal}); //NON-NLS

        // wait for them to finish writing / reading
        outputWriterRedirect.join();
        errorStringRedirect.join();
        
        //gc process with its streams
        //proc = null;
    }

    /**
     * Interrupt the running process and stop its stream redirect threads
     */
    public synchronized void stop() {
        logger.log(Level.INFO, "Stopping Execution of: {0}", command); //NON-NLS

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