/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Make a system call through a system shell in a platform-independent manner in
 * Java. <br /> This class only demonstrate a 'dir' or 'ls' within current
 * (execution) path, if no parameters are used. If parameters are used, the
 * first one is the system command to execute, the others are its system command
 * parameters. <br /> To be system independent, an <b><a
 * href="http://www.allapplabs.com/java_design_patterns/abstract_factory_pattern.htm">
 * Abstract Factory Pattern</a></b> will be used to build the right underlying
 * system shell in which the system command will be executed.
 *
 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
 * @see <a href="http://stackoverflow.com/questions/236737#236873"> How to make
 * a system call that returns the stdout output as a string in various
 * languages?</a>
 */
public final class JavaSystemCaller {

    /**
     * Execute a system command. <br /> Default is 'ls' in current directory if
     * no parameters, or a system command (if Windows, it is automatically
     * translated to 'dir')
     *
     * @param args first element is the system command, the others are its
     * parameters (NOT NULL)
     * @throws IllegalArgumentException if one parameters is null or empty.
     * 'args' can be empty (default 'ls' performed then)
     */
//    public static void main(final String[] args) {
//        String anOutput = "";
//        if (args.length == 0) {
//            anOutput = JavaSystemCaller.Exec.execute("ls");
//        } else {
//            String[] someParameters = null;
//            anOutput = JavaSystemCaller.Exec.execute(args[0], someParameters);
//        }
//        logger.log(Level.INFO, "Final output: " + anOutput);
//    }
    /**
     * Asynchronously read the output of a given input stream. <br /> Any
     * exception during execution of the command in managed in this thread.
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public static class StreamGobbler extends Thread {

        private static final Logger logger = Logger.getLogger(StreamGobbler.class.getName());
        private InputStream is;
        private String type;
        private StringBuffer output = new StringBuffer();

        StreamGobbler(final InputStream anIs, final String aType) {
            this.is = anIs;
            this.type = aType;
        }

        /**
         * Asynchronous read of the input stream. <br /> Will report output as
         * its its displayed.
         *
         * @see java.lang.Thread#run()
         */
        @Override
        public final void run() {
            try {
                final InputStreamReader isr = new InputStreamReader(this.is);
                final BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    logger.log(Level.INFO, this.type + ">" + line);
                    this.output.append(line + System.getProperty("line.separator"));
                }
            } catch (final IOException ioe) {
                logger.log(Level.WARNING, ioe.getMessage());
            }
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
     * Execute a system command in the appropriate shell. <br /> Read
     * asynchronously stdout and stderr to report any result.
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public static final class Exec {

        private static final Logger logger = Logger.getLogger(Exec.class.getName());
        private static Process proc = null;
        private static String command = null;
        private static JavaSystemCaller.IShell aShell = null;

        /**
         * Execute a system command. <br /> Listen asynchronously to stdout and
         * stderr
         *
         * @param aCommand system command to be executed (must not be null or
         * empty)
         * @param someParameters parameters of the command (must not be null or
         * empty)
         * @return final output (stdout only)
         */
        public static String execute(final String aCommand, final String... someParameters) {
            String output = "";
            try {
                JavaSystemCaller.ExecEnvironmentFactory anExecEnvFactory = getExecEnvironmentFactory(aCommand, someParameters);
                aShell = anExecEnvFactory.createShell();
                command = anExecEnvFactory.createCommandLine();

                final Runtime rt = Runtime.getRuntime();
                logger.log(Level.INFO, "Executing " + aShell.getShellCommand() + " " + command);

                proc = rt.exec(aShell.getShellCommand() + " " + command);
                try {
                    //block, give time to fully stary the process
                    //so if it's restarted solr operations can be resumed seamlessly
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                }

                // any error message?
                final JavaSystemCaller.StreamGobbler errorGobbler = new JavaSystemCaller.StreamGobbler(proc.getErrorStream(), "ERROR");

                // any output?
                final JavaSystemCaller.StreamGobbler outputGobbler = new JavaSystemCaller.StreamGobbler(proc.getInputStream(), "OUTPUT");

                // kick them off
                errorGobbler.start();
                outputGobbler.start();

                // any error???
                final int exitVal = proc.waitFor();
                logger.log(Level.INFO, "ExitValue: " + exitVal);

                output = outputGobbler.getOutput();

            } catch (final Throwable t) {
                logger.log(Level.WARNING, "Error executing command: " + aCommand + " " + someParameters, t);
            }
            return output;
        }

        private static JavaSystemCaller.ExecEnvironmentFactory getExecEnvironmentFactory(final String aCommand, final String... someParameters) {
            final String anOSName = System.getProperty("os.name");
            if (anOSName.toLowerCase().startsWith("windows")) {
                return new JavaSystemCaller.WindowsExecEnvFactory(aCommand, someParameters);
            }
            return new JavaSystemCaller.UnixExecEnvFactory(aCommand, someParameters);
            // TODO be more specific for other OS.
        }

        private Exec() { /*
             *
             */ }

        public static synchronized void stop() {
            try {
                logger.log(Level.INFO, "Stopping Execution of: " + command);
                //try to graceful shutdown
                Process stop = Runtime.getRuntime().exec(aShell.getShellCommand() + " " + command);
                stop.waitFor();
                //if still running, forcefully stop it
                if (proc != null) {
                    proc.destroy();
                    proc = null;
                }

            } catch (InterruptedException intex) {
                logger.log(Level.WARNING, intex.getMessage());
            } catch (IOException ioex) {
                logger.log(Level.WARNING, ioex.getMessage());
            }
        }

        public static Process getProcess() {
            return proc;
        }
    }

    private JavaSystemCaller() { /*
         *
         */ }

    /*
     * ABSTRACT FACTORY PATTERN
     */
    /**
     * Environment needed to be build for the Exec class to be able to execute
     * the system command. <br /> Must have the right shell and the right
     * command line. <br />
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public abstract static class ExecEnvironmentFactory {

        private String command = null;
        private ArrayList<String> parameters = new ArrayList<String>();

        final String getCommand() {
            return this.command;
        }

        final ArrayList<String> getParameters() {
            return this.parameters;
        }

        /**
         * Builds an execution environment for a system command to be played.
         * <br /> Independent from the OS.
         *
         * @param aCommand system command to be executed (must not be null or
         * empty)
         * @param someParameters parameters of the command (must not be null or
         * empty)
         */
        public ExecEnvironmentFactory(final String aCommand, final String... someParameters) {
            if (aCommand == null || aCommand.length() == 0) {
                throw new IllegalArgumentException("Command must not be empty");
            }
            this.command = aCommand;
            for (int i = 0; i < someParameters.length; i++) {
                final String aParameter = someParameters[i];
                if (aParameter == null || aParameter.length() == 0) {
                    throw new IllegalArgumentException("Parameter n° '" + i + "' must not be empty");
                }
                this.parameters.add(aParameter);
            }
        }

        /**
         * Builds the right Shell for the current OS. <br /> Allow for
         * independent platform execution.
         *
         * @return right shell, NEVER NULL
         */
        public abstract JavaSystemCaller.IShell createShell();

        /**
         * Builds the right command line for the current OS. <br /> Means that a
         * command might be translated, if it does not fit the right OS ('dir'
         * => 'ls' on unix)
         *
         * @return right complete command line, with parameters added (NEVER
         * NULL)
         */
        public abstract String createCommandLine();

        protected final String buildCommandLine(final String aCommand, final ArrayList<String> someParameters) {
            final StringBuilder aCommandLine = new StringBuilder();
            aCommandLine.append(aCommand);
            for (String aParameter : someParameters) {
                aCommandLine.append(" ");
                aCommandLine.append(aParameter);
            }
            return aCommandLine.toString();
        }
    }

    /**
     * Builds a Execution Environment for Windows. <br /> Cmd with windows
     * commands
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public static final class WindowsExecEnvFactory extends JavaSystemCaller.ExecEnvironmentFactory {

        /**
         * Builds an execution environment for a Windows system command to be
         * played. <br /> Any command not from windows will be translated in its
         * windows equivalent if possible.
         *
         * @param aCommand system command to be executed (must not be null or
         * empty)
         * @param someParameters parameters of the command (must not be null or
         * empty)
         */
        public WindowsExecEnvFactory(final String aCommand, final String... someParameters) {
            super(aCommand, someParameters);
        }

        /**
         * @see test.JavaSystemCaller.ExecEnvironmentFactory#createShell()
         */
        @Override
        public JavaSystemCaller.IShell createShell() {
            return new JavaSystemCaller.WindowsShell();
        }

        /**
         * @see test.JavaSystemCaller.ExecEnvironmentFactory#createCommandLine()
         */
        @Override
        public String createCommandLine() {
            String aCommand = getCommand();
            if (aCommand.toLowerCase().trim().equals("ls")) {
                aCommand = "dir";
            }
            // TODO translates other Unix commands
            return buildCommandLine(aCommand, getParameters());
        }
    }

    /**
     * Builds a Execution Environment for Unix. <br /> Sh with Unix commands
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public static final class UnixExecEnvFactory extends JavaSystemCaller.ExecEnvironmentFactory {

        /**
         * Builds an execution environment for a Unix system command to be
         * played. <br /> Any command not from Unix will be translated in its
         * Unix equivalent if possible.
         *
         * @param aCommand system command to be executed (must not be null or
         * empty)
         * @param someParameters parameters of the command (must not be null or
         * empty)
         */
        public UnixExecEnvFactory(final String aCommand, final String... someParameters) {
            super(aCommand, someParameters);
        }

        /**
         * @see test.JavaSystemCaller.ExecEnvironmentFactory#createShell()
         */
        @Override
        public JavaSystemCaller.IShell createShell() {
            return new JavaSystemCaller.UnixShell();
        }

        /**
         * @see test.JavaSystemCaller.ExecEnvironmentFactory#createCommandLine()
         */
        @Override
        public String createCommandLine() {
            String aCommand = getCommand();
            if (aCommand.toLowerCase().trim().equals("dir")) {
                aCommand = "ls";
            }
            // TODO translates other Windows commands
            return buildCommandLine(aCommand, getParameters());
        }
    }

    /**
     * System Shell with its right OS command. <br /> 'cmd' for Windows or 'sh'
     * for Unix, ...
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public interface IShell {

        /**
         * Get the right shell command. <br /> Used to launch a new shell
         *
         * @return command used to launch a Shell (NEVEL NULL)
         */
        String getShellCommand();
    }

    /**
     * Windows shell (cmd). <br /> More accurately 'cmd /C'
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public static class WindowsShell implements JavaSystemCaller.IShell {

        /**
         * @see test.JavaSystemCaller.IShell#getShellCommand()
         */
        @Override
        public final String getShellCommand() {
            final String osName = System.getProperty("os.name");
            if (osName.equals("Windows 95")) {
                return "command.com /C";
            }
            return "cmd.exe /C";
        }
    }

    /**
     * Unix shell (sh). <br /> More accurately 'sh -C'
     *
     * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
     */
    public static class UnixShell implements JavaSystemCaller.IShell {

        /**
         * @see test.JavaSystemCaller.IShell#getShellCommand()
         */
        @Override
        public final String getShellCommand() {
            return "/bin/sh -c";
        }
    }
}