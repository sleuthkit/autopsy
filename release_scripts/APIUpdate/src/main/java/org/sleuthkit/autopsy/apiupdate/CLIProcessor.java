/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Processes CLI options.
 */
public class CLIProcessor {

    private static final Option PREV_VERS_PATH_OPT = Option.builder()
            .argName("path")
            .desc("The path to the previous version jar files")
            .hasArg(true)
            .longOpt("prev-path")
            .option("p")
            .required(true)
            .build();

    private static final Option CUR_VERS_PATH_OPT = Option.builder()
            .argName("path")
            .desc("The path to the current version jar files")
            .hasArg(true)
            .longOpt("curr-path")
            .option("c")
            .required(false)
            .build();

    private static final Option PREV_VERS_OPT = Option.builder()
            .argName("version")
            .desc("The previous version number")
            .hasArg(true)
            .longOpt("prev-version")
            .option("pv")
            .required(false)
            .build();

    private static final Option CUR_VERS_OPT = Option.builder()
            .argName("version")
            .desc("The current version number")
            .hasArg(true)
            .longOpt("curr-version")
            .option("cv")
            .required(false)
            .build();

    private static final Option SRC_LOC_OPT = Option.builder()
            .argName("path")
            .desc("The path to the root of the autopsy report")
            .hasArg(true)
            .longOpt("src-path")
            .option("s")
            .required(false)
            .build();

    private static final Option UPDATE_OPT = Option.builder()
            .desc("Update source code versions")
            .hasArg(false)
            .longOpt("update")
            .option("u")
            .required(false)
            .build();

    private static final List<Option> ALL_OPTIONS = Arrays.asList(
            PREV_VERS_PATH_OPT,
            CUR_VERS_PATH_OPT,
            PREV_VERS_OPT,
            CUR_VERS_OPT,
            SRC_LOC_OPT,
            UPDATE_OPT
    );

    private static final Options CLI_OPTIONS = getCliOptions(ALL_OPTIONS);
    private static final String DEFAULT_CURR_VERSION = "Current Version";
    private static final String DEFAULT_PREV_VERSION = "Previous Version";
    private static final String BUILD_REL_PATH = "build/cluster/modules";
    private static final String JAR_SRC_REL_PATH = "../../../";

    /**
     * Creates an Options object from a list of options.
     *
     * @param opts The list of options.
     * @return The options object.
     */
    private static Options getCliOptions(List<Option> opts) {
        Options toRet = new Options();
        for (Option opt : opts) {
            toRet.addOption(opt);
        }

        return toRet;
    }

    private static final Option HELP_OPT = Option.builder()
            .desc("Print help message")
            .hasArg(false)
            .longOpt("help")
            .option("h")
            .required(false)
            .build();

    private static final Options HELP_OPTIONS = getCliOptions(Collections.singletonList(HELP_OPT));

    private static final CommandLineParser parser = new DefaultParser();

    private static final HelpFormatter helpFormatter = new HelpFormatter();

    /**
     * Prints help message.
     *
     * @param ex The exception or null if no exception.
     */
    static void printHelp(Exception ex) {
        if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            System.out.println(ex.getMessage());
        }

        helpFormatter.printHelp("APIUpdate", CLI_OPTIONS);
    }

    /**
     * Parses the CLI args.
     *
     * @param args The arguments.
     * @return The CLIArgs object.
     * @throws ParseException
     */
    static CLIArgs parseCli(String[] args) throws ParseException {
        CommandLine helpCmd = parser.parse(HELP_OPTIONS, args, true);
        boolean isHelp = helpCmd.hasOption(HELP_OPT);
        if (isHelp) {
            return new CLIArgs(null, null, null, null, null, false, true);
        }

        CommandLine cmd = parser.parse(CLI_OPTIONS, args);
        String curVers = cmd.hasOption(CUR_VERS_OPT) ? cmd.getOptionValue(CUR_VERS_OPT) : DEFAULT_CURR_VERSION;
        String prevVers = cmd.hasOption(PREV_VERS_OPT) ? cmd.getOptionValue(PREV_VERS_OPT) : DEFAULT_PREV_VERSION;

        String srcPath;
        try {
            srcPath = cmd.hasOption(SRC_LOC_OPT)
                    ? cmd.getOptionValue(SRC_LOC_OPT)
                    : new File(CLIProcessor.class.getProtectionDomain().getCodeSource().getLocation()
                            .toURI()).toPath().resolve(JAR_SRC_REL_PATH).toAbsolutePath().toString();
        } catch (URISyntaxException ex) {
            throw new ParseException("Unable to determine source path from current location: " + ex.getMessage());
        }

        String curVersPath = cmd.hasOption(CUR_VERS_PATH_OPT)
                ? cmd.getOptionValue(CUR_VERS_PATH_OPT)
                : Paths.get(srcPath, BUILD_REL_PATH).toString();

        String prevVersPath = cmd.getOptionValue(PREV_VERS_PATH_OPT);

        boolean makeUpdate = cmd.hasOption(UPDATE_OPT);
        File curVersFile = new File(curVersPath);
        File prevVersFile = new File(prevVersPath);
        File srcPathFile = new File(srcPath);

        if (!curVersFile.isDirectory()) {
            throw new ParseException("No directory found at " + curVersFile.getAbsolutePath());
        }

        if (!prevVersFile.isDirectory()) {
            throw new ParseException("No directory found at " + prevVersFile.getAbsolutePath());
        }

        if (!srcPathFile.isDirectory()) {
            throw new ParseException("No directory found at " + srcPathFile.getAbsolutePath());
        }

        return new CLIArgs(curVers, prevVers, curVersFile, prevVersFile, srcPathFile, makeUpdate, false);
    }

    /**
     * The CLI args object.
     */
    public static class CLIArgs {

        private final String currentVersion;
        private final String previousVersion;
        private final File currentVersPath;
        private final File previousVersPath;
        private final boolean isHelp;
        private final File srcPath;
        private final boolean makeUpdate;

        public CLIArgs(String currentVersion, String previousVersion, File currentVersPath, File previousVersPath, File srcPath, boolean makeUpdate, boolean isHelp) {
            this.currentVersion = currentVersion;
            this.previousVersion = previousVersion;
            this.currentVersPath = currentVersPath;
            this.previousVersPath = previousVersPath;
            this.srcPath = srcPath;
            this.isHelp = isHelp;
            this.makeUpdate = makeUpdate;
        }

        /**
         * @return The current version name.
         */
        public String getCurrentVersion() {
            return currentVersion;
        }

        /**
         * @return The previous version name.
         */
        public String getPreviousVersion() {
            return previousVersion;
        }

        /**
         * @return The path to the directory containing the jars for current
         * version.
         */
        public File getCurrentVersPath() {
            return currentVersPath;
        }

        /**
         * @return The path to the directory containing the jars for previous
         * version.
         */
        public File getPreviousVersPath() {
            return previousVersPath;
        }

        /**
         * @return True if only print help message.
         */
        public boolean isHelp() {
            return isHelp;
        }

        /**
         * @return True if module versions should be updated.
         */
        public boolean isMakeUpdate() {
            return makeUpdate;
        }

        /**
         * @return The path to the source directory root for autopsy.
         */
        public File getSrcPath() {
            return srcPath;
        }

    }
}
