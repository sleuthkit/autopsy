/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * A thin wrapper around the EDB Explorer command line tool
 * (https://github.com/keyuraghao/EDB_FILE_EXPLORER), a cross-platform,
 * read-only parser for Microsoft Extensible Storage Engine (ESE / JET Blue)
 * databases such as ntds.dit, SRUDB.dat, WebCacheV01.dat, Windows Search and
 * Exchange mailbox stores.
 *
 * The tool is an optional external dependency: it is not bundled with
 * Autopsy. Each method runs one short-lived process and parses its JSON
 * output, so nothing is held open on the database between calls.
 */
final class EdbExplorerTool {

    private static final Logger logger = Logger.getLogger(EdbExplorerTool.class.getName());

    /**
     * ModuleSettings file and key under which a user-selected path to the
     * executable is stored (see ESEDatabaseViewer's "Locate..." button).
     */
    static final String SETTINGS_MODULE_NAME = "ESEDatabaseViewer"; //NON-NLS
    static final String SETTINGS_EXECUTABLE_KEY = "EdbExplorerExecutable"; //NON-NLS

    private static final String EXECUTABLE_NAME = "edb-explorer"; //NON-NLS
    private static final String WINDOWS_EXECUTABLE_NAME = "edb-explorer.exe"; //NON-NLS

    /**
     * How often the process terminator is polled while a command runs.
     */
    private static final long TERMINATION_CHECK_INTERVAL_MS = 250;

    private final Path executable;
    private final Path workingDirectory;

    /**
     * Creates a wrapper for a given executable.
     *
     * @param executable       Absolute path of the edb-explorer executable.
     * @param workingDirectory Directory in which the output of each command is
     *                         captured (normally the case temp directory).
     */
    private EdbExplorerTool(Path executable, Path workingDirectory) {
        this.executable = executable;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Locates the edb-explorer executable. The lookup order is: the path the
     * user selected (ModuleSettings), the EDB_EXPLORER_EXECUTABLE environment
     * variable, the directories on PATH, and finally the default install
     * locations of the EDB Explorer installers for the current platform.
     *
     * @param workingDirectory Directory in which the output of each command
     *                         will be captured.
     *
     * @return The tool, or empty if no executable could be found.
     */
    static Optional<EdbExplorerTool> find(Path workingDirectory) {
        return findExecutable().map(exe -> new EdbExplorerTool(exe, workingDirectory));
    }

    /**
     * Locates the edb-explorer executable (see find()).
     *
     * @return Absolute path of the executable, or empty if none was found.
     */
    static Optional<Path> findExecutable() {
        List<Path> candidates = new ArrayList<>();

        String configured = ModuleSettings.getConfigSetting(SETTINGS_MODULE_NAME, SETTINGS_EXECUTABLE_KEY);
        if (StringUtils.isNotBlank(configured)) {
            candidates.add(Paths.get(configured));
        }

        String fromEnvironment = System.getenv("EDB_EXPLORER_EXECUTABLE"); //NON-NLS
        if (StringUtils.isNotBlank(fromEnvironment)) {
            candidates.add(Paths.get(fromEnvironment));
        }

        String executableName = PlatformUtil.isWindowsOS() ? WINDOWS_EXECUTABLE_NAME : EXECUTABLE_NAME;
        String pathVariable = System.getenv("PATH"); //NON-NLS
        if (StringUtils.isNotBlank(pathVariable)) {
            for (String dir : pathVariable.split(java.io.File.pathSeparator)) {
                if (StringUtils.isNotBlank(dir)) {
                    candidates.add(Paths.get(dir, executableName));
                }
            }
        }

        candidates.addAll(defaultInstallLocations(executableName));

        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath());
                }
            } catch (SecurityException ex) {
                logger.log(Level.FINE, String.format("Not allowed to inspect %s", candidate), ex); //NON-NLS
            }
        }
        return Optional.empty();
    }

    /**
     * Default install locations of the EDB Explorer installers.
     */
    private static List<Path> defaultInstallLocations(String executableName) {
        List<Path> locations = new ArrayList<>();
        String home = System.getProperty("user.home"); //NON-NLS
        if (PlatformUtil.isWindowsOS()) {
            for (String envVar : new String[]{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) { //NON-NLS
                String base = System.getenv(envVar);
                if (StringUtils.isNotBlank(base)) {
                    locations.add(Paths.get(base, "EDB Explorer", executableName)); //NON-NLS
                    locations.add(Paths.get(base, "Programs", "EDB Explorer", executableName)); //NON-NLS
                }
            }
        } else {
            if (PlatformUtil.isMacOS()) {
                locations.add(Paths.get("/Applications", "EDB Explorer.app", "Contents", "MacOS", executableName)); //NON-NLS
                locations.add(Paths.get("/opt/homebrew/bin", executableName)); //NON-NLS
            }
            if (StringUtils.isNotBlank(home)) {
                locations.add(Paths.get(home, ".local", "bin", executableName)); //NON-NLS
            }
            locations.add(Paths.get("/usr/local/bin", executableName)); //NON-NLS
            locations.add(Paths.get("/usr/bin", executableName)); //NON-NLS
            locations.add(Paths.get("/opt/edb-explorer", executableName)); //NON-NLS
        }
        return locations;
    }

    /**
     * Remembers a user-selected executable so that subsequent lookups find it.
     *
     * @param executable Path of the executable.
     */
    static void setConfiguredExecutable(Path executable) {
        ModuleSettings.setConfigSetting(SETTINGS_MODULE_NAME, SETTINGS_EXECUTABLE_KEY, executable.toAbsolutePath().toString());
    }

    /**
     * @return The executable this wrapper runs.
     */
    Path getExecutable() {
        return executable;
    }

    /**
     * Runs "edb-explorer --version".
     *
     * @return The version string printed by the tool, e.g. "EDB Explorer 1.0.0".
     */
    String getVersion(ProcessTerminator terminator) throws EdbExplorerException {
        return run(terminator, "--version").trim(); //NON-NLS
    }

    /**
     * Reads the metadata of a database ("edb-explorer info --json").
     *
     * @param database   Path of the database file on the local file system.
     * @param terminator Polled to find out whether the command should be
     *                   abandoned.
     */
    DatabaseInfo getDatabaseInfo(Path database, ProcessTerminator terminator) throws EdbExplorerException {
        JsonObject info = parseObject(run(terminator, "info", "--json", database.toString())); //NON-NLS
        return new DatabaseInfo(
                getString(info, "kind_name"), //NON-NLS
                getString(info, "profile_name"), //NON-NLS
                getString(info, "state"), //NON-NLS
                getString(info, "page_size")); //NON-NLS
    }

    /**
     * Lists the user tables of a database ("edb-explorer tables --json").
     * System tables (MSys*) are not included.
     *
     * @param database   Path of the database file on the local file system.
     * @param terminator Polled to find out whether the command should be
     *                   abandoned.
     */
    List<TableInfo> listTables(Path database, ProcessTerminator terminator) throws EdbExplorerException {
        JsonArray tables = parseArray(run(terminator, "tables", "--json", database.toString())); //NON-NLS
        List<TableInfo> result = new ArrayList<>();
        for (JsonElement element : tables) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject table = element.getAsJsonObject();
            String name = getString(table, "name"); //NON-NLS
            if (StringUtils.isBlank(name)) {
                continue;
            }
            result.add(new TableInfo(name, getString(table, "display_name"), getString(table, "description"))); //NON-NLS
        }
        return result;
    }

    /**
     * Lists the columns of a table, in schema order ("edb-explorer schema
     * --json").
     *
     * @param database   Path of the database file on the local file system.
     * @param table      Name of the table.
     * @param terminator Polled to find out whether the command should be
     *                   abandoned.
     */
    List<String> listColumns(Path database, String table, ProcessTerminator terminator) throws EdbExplorerException {
        JsonObject schema = parseObject(run(terminator, "schema", "--json", database.toString(), table)); //NON-NLS
        List<String> columns = new ArrayList<>();
        JsonElement columnsElement = schema.get("columns"); //NON-NLS
        if (columnsElement != null && columnsElement.isJsonArray()) {
            for (JsonElement element : columnsElement.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    String name = getString(element.getAsJsonObject(), "name"); //NON-NLS
                    if (StringUtils.isNotBlank(name)) {
                        columns.add(name);
                    }
                }
            }
        }
        return columns;
    }

    /**
     * Reads a page of records from a table ("edb-explorer dump --format jsonl
     * --nulls"). Values are returned as display strings: null columns become
     * empty strings, binary columns are hex encoded by the tool, and
     * timestamps are decoded by the tool according to the detected
     * application profile.
     *
     * @param database   Path of the database file on the local file system.
     * @param table      Name of the table.
     * @param columns    Column names, in the order the rows should be
     *                   populated. Columns the tool does not report are left
     *                   empty.
     * @param offset     Number of records to skip.
     * @param limit      Maximum number of records to return.
     * @param terminator Polled to find out whether the command should be
     *                   abandoned.
     *
     * @return One map per record, keyed by column name in the given order.
     */
    List<Map<String, Object>> readRows(Path database, String table, List<String> columns, int offset, int limit, ProcessTerminator terminator) throws EdbExplorerException {
        String output = run(terminator, "dump", "--format", "jsonl", "--nulls", //NON-NLS
                "--limit", Integer.toString(limit), "--offset", Integer.toString(offset), //NON-NLS
                database.toString(), table);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            if (StringUtils.isBlank(line)) {
                continue;
            }
            JsonObject record = parseObject(line);
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : columns) {
                row.put(column, toDisplayString(record.get(column)));
            }
            // Columns that are present in the record but missing from the
            // schema (should not happen) are appended so no data is hidden.
            for (Map.Entry<String, JsonElement> entry : record.entrySet()) {
                String key = entry.getKey();
                if (!row.containsKey(key) && !key.startsWith("_")) {
                    row.put(key, toDisplayString(entry.getValue()));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Exports one table to a CSV file ("edb-explorer export --format csv").
     *
     * @param database   Path of the database file on the local file system.
     * @param table      Name of the table.
     * @param outputFile Path of the CSV file to write.
     * @param terminator Polled to find out whether the command should be
     *                   abandoned.
     */
    void exportTableToCsv(Path database, String table, Path outputFile, ProcessTerminator terminator) throws EdbExplorerException {
        run(terminator, "export", "--table", table, "--format", "csv", "--out", outputFile.toString(), database.toString()); //NON-NLS
    }

    /**
     * Runs the tool with the given arguments and returns what it printed to
     * standard output.
     */
    private String run(ProcessTerminator terminator, String... args) throws EdbExplorerException {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        Collections.addAll(command, args);

        Path stdoutFile = null;
        Path stderrFile = null;
        try {
            Files.createDirectories(workingDirectory);
            stdoutFile = Files.createTempFile(workingDirectory, "edb-explorer-", ".out"); //NON-NLS
            stderrFile = Files.createTempFile(workingDirectory, "edb-explorer-", ".err"); //NON-NLS

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectOutput(stdoutFile.toFile());
            processBuilder.redirectError(stderrFile.toFile());
            // Force UTF-8, plain output whatever the console encoding is.
            processBuilder.environment().put("PYTHONUTF8", "1"); //NON-NLS
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8"); //NON-NLS
            processBuilder.environment().put("NO_COLOR", "1"); //NON-NLS
            processBuilder.environment().put("TERM", "dumb"); //NON-NLS

            int exitCode = ExecUtil.execute(processBuilder, TERMINATION_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS, terminator);
            if (terminator.shouldTerminateProcess()) {
                throw new EdbExplorerException(String.format("%s was cancelled", describe(args))); //NON-NLS
            }
            if (exitCode != 0) {
                String error = readTextQuietly(stderrFile);
                throw new EdbExplorerException(String.format("%s exited with code %d%s", describe(args), exitCode, //NON-NLS
                        StringUtils.isBlank(error) ? "" : ": " + lastLine(error)));
            }
            return new String(Files.readAllBytes(stdoutFile), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ex) {
            throw new EdbExplorerException(String.format("Failed to run %s", describe(args)), ex); //NON-NLS
        } finally {
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
        }
    }

    private String describe(String... args) {
        return EXECUTABLE_NAME + (args.length > 0 ? " " + args[0] : ""); //NON-NLS
    }

    private static String readTextQuietly(Path file) {
        if (file == null) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Failed to read %s", file), ex); //NON-NLS
            return "";
        }
    }

    private static String lastLine(String text) {
        String[] lines = text.trim().split("\\r?\\n");
        return lines[lines.length - 1].trim();
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Failed to delete %s", file), ex); //NON-NLS
        }
    }

    private static JsonObject parseObject(String json) throws EdbExplorerException {
        JsonElement element = parse(json);
        if (!element.isJsonObject()) {
            throw new EdbExplorerException("Expected a JSON object from " + EXECUTABLE_NAME); //NON-NLS
        }
        return element.getAsJsonObject();
    }

    private static JsonArray parseArray(String json) throws EdbExplorerException {
        JsonElement element = parse(json);
        if (!element.isJsonArray()) {
            throw new EdbExplorerException("Expected a JSON array from " + EXECUTABLE_NAME); //NON-NLS
        }
        return element.getAsJsonArray();
    }

    private static JsonElement parse(String json) throws EdbExplorerException {
        try {
            return JsonParser.parseString(json);
        } catch (JsonParseException | IllegalStateException ex) {
            throw new EdbExplorerException("Could not parse the output of " + EXECUTABLE_NAME, ex); //NON-NLS
        }
    }

    private static String getString(JsonObject object, String key) {
        return toDisplayString(object.get(key));
    }

    /**
     * Converts a JSON value to the string shown in the table: nulls become
     * empty strings, primitives are shown as is, and nested values are shown
     * as JSON text.
     */
    private static String toDisplayString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }

    /**
     * Metadata of a database, as reported by "edb-explorer info".
     */
    static final class DatabaseInfo {

        private final String kindName;
        private final String profileName;
        private final String state;
        private final String pageSize;

        DatabaseInfo(String kindName, String profileName, String state, String pageSize) {
            this.kindName = kindName;
            this.profileName = profileName;
            this.state = state;
            this.pageSize = pageSize;
        }

        /**
         * @return Human readable format name, e.g. "Microsoft ESE / JET Blue".
         */
        String getKindName() {
            return kindName;
        }

        /**
         * @return Name of the detected application profile, e.g. "Active
         *         Directory (NTDS.dit)", or an empty string if none matched.
         */
        String getProfileName() {
            return profileName;
        }

        /**
         * @return Shutdown state of the database, e.g. "clean shutdown" or
         *         "dirty shutdown".
         */
        String getState() {
            return state;
        }

        /**
         * @return Page size in bytes, as a string.
         */
        String getPageSize() {
            return pageSize;
        }
    }

    /**
     * A table of a database, as reported by "edb-explorer tables".
     */
    static final class TableInfo {

        private final String name;
        private final String displayName;
        private final String description;

        TableInfo(String name, String displayName, String description) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
        }

        /**
         * @return Real name of the table, as used by the tool's commands.
         */
        String getName() {
            return name;
        }

        /**
         * @return Friendly name from the application profile, e.g. "datatable
         *         (AD objects)", or the real name if there is none.
         */
        String getDisplayName() {
            return StringUtils.isBlank(displayName) ? name : displayName;
        }

        /**
         * @return Description from the application profile, possibly empty.
         */
        String getDescription() {
            return Objects.toString(description, "");
        }
    }

    /**
     * Thrown when the tool cannot be run or its output cannot be understood.
     */
    static final class EdbExplorerException extends Exception {

        private static final long serialVersionUID = 1L;

        EdbExplorerException(String message) {
            super(message);
        }

        EdbExplorerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
