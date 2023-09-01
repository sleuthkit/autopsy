/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 * @author gregd
 */
public class CLIProcessor {

    static Option PREV_VERS_PATH_OPT = Option.builder()
            .argName("path")
            .desc("The path to the previous version jar files")
            .hasArg(true)
            .longOpt("prev-path")
            .option("p")
            .required(true)
            .build();

    static Option CUR_VERS_PATH_OPT = Option.builder()
            .argName("path")
            .desc("The path to the current version jar files")
            .hasArg(true)
            .longOpt("curr-path")
            .option("c")
            .required(false)
            .build();

    static Option PREV_VERS_OPT = Option.builder()
            .argName("version")
            .desc("The previous version number")
            .hasArg(true)
            .longOpt("prev-version")
            .option("pv")
            .required(false)
            .build();

    static Option CUR_VERS_OPT = Option.builder()
            .argName("version")
            .desc("The current version number")
            .hasArg(true)
            .longOpt("curr-version")
            .option("cv")
            .required(false)
            .build();

    static Option SRC_LOC_OPT = Option.builder()
            .argName("path")
            .desc("The path to the root of the autopsy repor")
            .hasArg(true)
            .longOpt("src-path")
            .option("s")
            .required(true)
            .build();

    static Option UPDATE_OPT = Option.builder()
            .desc("Update source code versions")
            .hasArg(false)
            .longOpt("update")
            .option("u")
            .required(false)
            .build();

    static List<Option> ALL_OPTIONS = Arrays.asList(
            PREV_VERS_PATH_OPT,
            CUR_VERS_PATH_OPT,
            PREV_VERS_OPT,
            CUR_VERS_OPT,
            SRC_LOC_OPT,
            UPDATE_OPT
    );

    static Options CLI_OPTIONS = getCliOptions(ALL_OPTIONS);
    private static final String DEFAULT_CURR_VERSION = "Current Version";
    private static final String DEFAULT_PREV_VERSION = "Previous Version";
    private static final String BUILD_REL_PATH = "build/cluster/modules";

    private static Options getCliOptions(List<Option> opts) {
        Options toRet = new Options();
        for (Option opt : opts) {
            toRet.addOption(opt);
        }

        return toRet;
    }

    static Option HELP_OPT = Option.builder()
            .desc("Print help message")
            .hasArg(false)
            .longOpt("help")
            .option("h")
            .required(false)
            .build();

    static Options HELP_OPTIONS = getCliOptions(Collections.singletonList(HELP_OPT));

    private static CommandLineParser parser = new DefaultParser();

    private static HelpFormatter helpFormatter = new HelpFormatter();

    static void printHelp(Exception ex) {
        if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            System.out.println(ex.getMessage());
        }

        helpFormatter.printHelp("APIUpdate", CLI_OPTIONS);
    }

    static CLIArgs parseCli(String[] args) throws ParseException {
        CommandLine helpCmd = parser.parse(HELP_OPTIONS, args, true);
        boolean isHelp = helpCmd.hasOption(HELP_OPT);
        if (isHelp) {
            return new CLIArgs(null, null, null, null, null, false, true);
        }

        CommandLine cmd = parser.parse(CLI_OPTIONS, args);
        String curVers = cmd.hasOption(CUR_VERS_OPT) ? cmd.getOptionValue(CUR_VERS_OPT) : DEFAULT_CURR_VERSION;
        String prevVers = cmd.hasOption(PREV_VERS_OPT) ? cmd.getOptionValue(PREV_VERS_OPT) : DEFAULT_PREV_VERSION;
        String curVersPath = cmd.hasOption(CUR_VERS_PATH_OPT) 
                ? cmd.getOptionValue(CUR_VERS_PATH_OPT) 
                : Paths.get(cmd.getOptionValue(SRC_LOC_OPT), BUILD_REL_PATH).toString();
        
        String prevVersPath = cmd.getOptionValue(PREV_VERS_PATH_OPT);
        String srcPath = cmd.getOptionValue(SRC_LOC_OPT);
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

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getPreviousVersion() {
            return previousVersion;
        }

        public File getCurrentVersPath() {
            return currentVersPath;
        }

        public File getPreviousVersPath() {
            return previousVersPath;
        }

        public boolean isIsHelp() {
            return isHelp;
        }

        public boolean isMakeUpdate() {
            return makeUpdate;
        }

        public File getSrcPath() {
            return srcPath;
        }

    }
}
