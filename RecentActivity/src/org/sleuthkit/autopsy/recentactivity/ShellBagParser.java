/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Parse the ntuser and ursclass regripper output files for shellbags.
 */
class ShellBagParser {
    private static final Logger logger = Logger.getLogger(ShellBagParser.class.getName());
    
    private static final SimpleDateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    // Last Write date\time format from itempos plugin
    private static final SimpleDateFormat DATE_TIME_FORMATTER2 = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyyy", Locale.getDefault());

    private ShellBagParser() {
    }

    /**
     * Parse the given file for shell bags.
     * 
     * @param regFilePath Regripper output file
     * 
     * @return List of the found shellbags
     * 
     * @throws FileNotFoundException
     * @throws IOException 
     */
    static List<ShellBag> parseShellbagOutput(String regFilePath) throws FileNotFoundException, IOException {
        List<ShellBag> shellbags = new ArrayList<>();
        File regfile = new File(regFilePath);

        ShellBagParser sbparser = new ShellBagParser();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(regfile), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();

                if (line.matches("^shellbags_xp v.*")) {
                    shellbags.addAll(sbparser.parseShellBagsXP(reader));
                } else if (line.matches("^shellbags v.*")) {
                    shellbags.addAll(sbparser.parseShellBags(reader));
                } else if (line.matches("^itempos.*")) {
                    shellbags.addAll(sbparser.parseItempos(reader));
                }

                line = reader.readLine();
            }
        }

        return shellbags;
    }

    /**
     * Parse the output from the shellbag_xp plugin.
     * 
     * @param reader File reader
     * 
     * @return List of found shellbags
     * 
     * @throws IOException 
     */
    List<ShellBag> parseShellBagsXP(BufferedReader reader) throws IOException {
        List<ShellBag> shellbags = new ArrayList<>();
        String line = reader.readLine();

        while (line != null && !isSectionSeparator(line)) {

            if (isShellbagXPDataLine(line)) {
                String[] tokens = line.split("\\|");
                if (tokens.length >= 6) {
                    shellbags.add(new ShellBag(tokens[5].trim(), "Software\\Microsoft\\Windows\\ShellNoRoam\\BagMRU", tokens[0].trim(), tokens[1].trim(), tokens[2].trim(), tokens[3].trim()));
                }
            }

            line = reader.readLine();
        }

        return shellbags;
    }

    /**
     * Parse the output of the shellbags regripper plugin.
     * 
     * @param reader
     * @return List of found shellbags
     * 
     * @throws IOException 
     */
    List<ShellBag> parseShellBags(BufferedReader reader) throws IOException {
        List<ShellBag> shellbags = new ArrayList<>();
        String line = reader.readLine();
        String regPath = "Local Settings\\Software\\Microsoft\\Windows\\Shell\\BagMRU";

        while (line != null && !isSectionSeparator(line)) {

            if (isShellbagDataLine(line)) {
                String[] tokens = line.split("\\|");
                String path = tokens[6].replaceAll("\\[.*?\\]", "").trim();
                int index = line.lastIndexOf('[');
                String endstuff = "";
                if (index != -1) {
                    endstuff = line.substring(index, line.length() - 1).replace("[Desktop", "");
                }
                if (tokens.length >= 7) {
                    shellbags.add(new ShellBag(path, regPath + endstuff, tokens[0].trim(), tokens[1].trim(), tokens[2].trim(), tokens[3].trim()));
                }
            }

            line = reader.readLine();
        }

        return shellbags;
    }

    /**
     * Parse the output of the Itempos regripper plugin.
     * 
     * @param reader
     * 
     * @return List of found shell bags.
     * 
     * @throws IOException 
     */
    List<ShellBag> parseItempos(BufferedReader reader) throws IOException {
        List<ShellBag> shellbags = new ArrayList<>();
        String bagpath = "";
        String lastWrite = "";
        String line = reader.readLine();

        while (line != null && !isSectionSeparator(line)) {

            if (isItemposDataLine(line)) {
                String[] tokens = line.split("\\|");
                if (tokens.length >= 5) {
                    shellbags.add(new ShellBag(tokens[4].trim(), bagpath, lastWrite, tokens[1].trim(), tokens[2].trim(), tokens[3].trim()));
                }
            } else if (line.contains("Software\\")) {
                bagpath = line.trim();
                lastWrite = "";
            } else if (line.contains("LastWrite:")) {
                lastWrite = line.replace("LastWrite:", "").trim();
            }

            line = reader.readLine();
        }

        return shellbags;
    }

    /**
     * Return whether or not the given line is a plugin output separator.
     * 
     * \verbatim
     * The format of the plugin output separators is:
     * ----------------------------------------
     * \endverbatim
     * 
     * @param line
     * 
     * @return True if the line is a section separator
     */
    boolean isSectionSeparator(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        return line.trim().matches("^-+");
    }

    /**
     * This data rows from the itempos plugin are in the format:
     * <size> | <Modified time> | <Accessed time> | <Created time> | <Name>
     * The times are in the format YYYY-MM-dd HH:mm:ss
     *
     * @param line
     *
     * @return
     */
    boolean isItemposDataLine(String line) {
        return line.matches("^\\d*?\\s*?\\|.*?\\|.*?\\|.*?\\|.*?");
    }

    /**
     * The data rows from the shellbags_xp plug look like
     * <MRU Time> | <Modified time> | <Accessed time> | <Created time> |
     * <Zip SubFolder> | <Resource>
     *
     * The times are in the format YYYY-MM-dd HH:mm:ss
     *
     * @param line
     *
     * @return
     */
    boolean isShellbagXPDataLine(String line) {
        return line.matches("^(\\d+?.*?\\s*? | \\s*?)\\|.*?\\|.*?\\|.*?\\|.*?\\|.*?");
    }

    /**
     * The data rows from the shellbags plug look like
     * <MRU Time> | <Modified time> | <Accessed time> | <Created time> |
     * <Zip SubFolder> |<MFT File Ref> <Resource>
     *
     * The times are in the format YYYY-MM-dd HH:mm:ss
     *
     * @param line
     *
     * @return
     */
    boolean isShellbagDataLine(String line) {
        return line.matches("^(\\d+?.*?\\s*? | \\s*?)\\|.*?\\|.*?\\|.*?\\|.*?\\|.*?\\|.*?");
    }
    
    /**
     * Class to hold the shell bag data.
     * 
     */
    class ShellBag {

        private final String resource;
        private final String key;
        private final String lastWrite;
        private final String modified;
        private final String accessed;
        private final String created;

        /**
         * Creates a new shell bag object.
         * 
         * Any of the parameters can be "";
         * 
         * @param resource String from the "Resource" or "Name" column, depending on the plug in
         * @param key  String registry key value
         * @param lastWrite Depending on the plug in lastWrite is either Last write value or the MRU Time value
         * @param modified  Modified time string
         * @param accessed  Accessed time string
         * @param created   Created time string
         */
        ShellBag(String resource, String key, String lastWrite, String modified, String accessed, String created) {
            this.resource = resource;
            this.key = key;
            this.lastWrite = lastWrite;
            this.accessed = accessed;
            this.modified = modified;
            this.created = created;
        }

        /**
         * Returns the resource string.
         * 
         * @return The shellbag resource or empty string.
         */
        String getResource() {
            return resource == null ? "" : resource;
        }

         /**
         * Returns the key string.
         * 
         * @return The shellbag key or empty string.
         */
        String getKey() {
            return key == null ? "" : key;
        }

         /**
         * Returns the last time in seconds since java epoch or 
         * 0 if no valid time was found.
         * 
         * @return The time in seconds or 0 if no valid time.
         */
        long getLastWrite() {
            return parseDateTime(lastWrite);
        }

        /**
         * Returns the last time in seconds since java epoch or 
         * 0 if no valid time was found.
         * 
         * @return The time in seconds or 0 if no valid time.
         */
        long getModified() {
            return parseDateTime(modified);
        }
        
        /**
         * Returns the last time in seconds since java epoch or 
         * 0 if no valid time was found.
         * 
         * @return The time in seconds or 0 if no valid time.
         */
        long getAccessed() {
            return parseDateTime(accessed);
        }

        /**
         * Returns the last time in seconds since java epoch or 
         * 0 if no valid time was found.
         * 
         * @return The time in seconds or 0 if no valid time.
         */
        long getCreated() {
            return parseDateTime(created);
        }

        /**
         * Returns the date\time in seconds from epoch for the given string with
         * format yyyy-MM-dd HH:mm:ss;
         *
         * @param dateTimeString String of format yyyy-MM-dd HH:mm:ss
         *
         * @return time in seconds from java epoch
         */
        long parseDateTime(String dateTimeString) {
            if (!dateTimeString.isEmpty()) {
                try {
                    return DATE_TIME_FORMATTER.parse(dateTimeString).getTime() / 1000;
                } catch (ParseException ex) {
                    // The parse of the string may fail because there are two possible formats.
                }

                try {
                    return DATE_TIME_FORMATTER2.parse(dateTimeString).getTime() / 1000;
                } catch (ParseException ex) {
                    logger.log(Level.WARNING, String.format("ShellBag parse failure.  %s is not formated as expected.", dateTimeString), ex);
                }
            }
            return 0;
        }
    }

}
