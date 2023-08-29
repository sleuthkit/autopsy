/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
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
            .required(true)
            .build();

    static Option PREV_VERS_OPT = Option.builder()
            .argName("version")
            .desc("The previous version number")
            .hasArg(true)
            .longOpt("prev-version")
            .option("pv")
            .required(true)
            .build();

    static Option CUR_VERS_OPT = Option.builder()
            .argName("version")
            .desc("The current version number")
            .hasArg(true)
            .longOpt("curr-version")
            .option("cv")
            .required(true)
            .build();

    static Option SRC_LOC_OPT = Option.builder()
            .argName("path")
            .desc("The path to the root of the autopsy repor")
            .hasArg(true)
            .longOpt("src-path")
            .option("s")
            .required(true)
            .build();

    static List<Option> ALL_OPTIONS = Arrays.asList(
            PREV_VERS_PATH_OPT,
            CUR_VERS_PATH_OPT,
            PREV_VERS_OPT,
            CUR_VERS_OPT,
            SRC_LOC_OPT
    );

    static Options CLI_OPTIONS = getCliOptions(ALL_OPTIONS);

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
            return new CLIArgs(null, null, null, null, null, true);
        }

        CommandLine cmd = parser.parse(CLI_OPTIONS, args);
        String curVers = cmd.getOptionValue(CUR_VERS_OPT);
        String prevVers = cmd.getOptionValue(PREV_VERS_OPT);
        String curVersPath = cmd.getOptionValue(CUR_VERS_PATH_OPT);
        String prevVersPath = cmd.getOptionValue(PREV_VERS_PATH_OPT);
        String srcPath = cmd.getOptionValue(SRC_LOC_OPT);
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

        return new CLIArgs(curVers, prevVers, curVersFile, prevVersFile, srcPathFile, false);
    }

    public static class CLIArgs {

        private final String currentVersion;
        private final String previousVersion;
        private final File currentVersPath;
        private final File previousVersPath;
        private final boolean isHelp;
        private final File srcPath;

        public CLIArgs(String currentVersion, String previousVersion, File currentVersPath, File previousVersPath, File srcPath, boolean isHelp) {
            this.currentVersion = currentVersion;
            this.previousVersion = previousVersion;
            this.currentVersPath = currentVersPath;
            this.previousVersPath = previousVersPath;
            this.srcPath = srcPath;
            this.isHelp = isHelp;
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

        public File getSrcPath() {
            return srcPath;
        }

    }
}
