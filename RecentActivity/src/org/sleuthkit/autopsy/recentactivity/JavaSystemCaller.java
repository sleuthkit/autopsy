/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Takes of forking a process and reading output / error streams to either a
 * string buffer or directly to a file writer
 */
public final class JavaSystemCaller {

    /**
     * Asynchronously read the output of a given input stream and write to a
     * string to be returned. Any exception during execution of the command is
     * managed in this thread.
     *
     */
    public static class StreamToStringRedirect extends Thread {

        private static final Logger logger = Logger.getLogger(StreamToStringRedirect.class.getName());
        private InputStream is;
        private StringBuffer output = new StringBuffer();
        private boolean doRun = false;

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
            try {
                final InputStreamReader isr = new InputStreamReader(this.is);
                final BufferedReader br = new BufferedReader(isr);
                String line = null;
                while (doRun && (line = br.readLine()) != null) {
                    this.output.append(line).append(SEP);
                }
            } catch (final IOException ex) {
                logger.log(Level.WARNING, "Error redirecting stream to string buffer", ex);
            }
        }

        /**
         * Stop running the stream gobbler The thread will exit out gracefully
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
    public static class StreamToWriterRedirect extends Thread {

        private static final Logger logger = Logger.getLogger(StreamToStringRedirect.class.getName());
        private InputStream is;
        private boolean doRun = false;
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
            try {
                final InputStreamReader isr = new InputStreamReader(this.is);
                final BufferedReader br = new BufferedReader(isr);
                String line = null;
                while (doRun && (line = br.readLine()) != null) {
                    writer.append(line).append(SEP);
                }
            } catch (final IOException ex) {
                logger.log(Level.SEVERE, "Error reading output and writing to file writer", ex);
            } finally {
                try {
                    writer.flush();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error flushing file writer", ex);
                }
            }
        }

        /**
         * Stop running the stream gobbler The thread will exit out gracefully
         * after the current readLine() on stream unblocks
         */
        public void stopRun() {
            doRun = false;
        }
    }



    public static final class Exec {

        private static final Logger logger = Logger.getLogger(Exec.class.getName());
        private static Process proc = null;
        private static String command = null;

        /**
         * Execute a process. Redirect asynchronously stdout to a string and
         * stderr to nowhere.
         *
         * @param aCommand command to be executed
         * @param params parameters of the command
         * @return string buffer with captured stdout
         */
        public static String execute(final String aCommand, final String... params) throws IOException, InterruptedException {
            String output = "";

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
            logger.log(Level.INFO, "Executing " + arrayCommandToLog.toString());

            proc = rt.exec(arrayCommand);
            try {
                //give time to fully start the process
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Pause interrupted", ex);
            }

            //stderr redirect
            final JavaSystemCaller.StreamToStringRedirect errorRedirect = new JavaSystemCaller.StreamToStringRedirect(proc.getErrorStream(), "ERROR");
            //stdout redirect
            final JavaSystemCaller.StreamToStringRedirect outputRedirect = new JavaSystemCaller.StreamToStringRedirect(proc.getInputStream(), "OUTPUT");

            //start redurectors
            errorRedirect.start();
            outputRedirect.start();

            //wait for process to complete and capture error core
            final int exitVal = proc.waitFor();
            logger.log(Level.INFO, aCommand + " exit value: " + exitVal);

            errorRedirect.stopRun();
            outputRedirect.stopRun();

            output = outputRedirect.getOutput();

            //gc process with its streams
            proc = null;

            return output;
        }

        public static synchronized void stop() {
            logger.log(Level.INFO, "Stopping Execution of: " + command);
            
            if (proc != null) {
                proc.destroy();
                proc = null;
            }
        }

        public static Process getProcess() {
            return proc;
        }
    }

    private JavaSystemCaller() { }
}