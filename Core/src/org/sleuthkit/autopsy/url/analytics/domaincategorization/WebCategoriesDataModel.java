/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.url.analytics.domaincategorization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * Provides the data model for exporting, importing and CRUD operations on
 * custom web categories.
 */
class WebCategoriesDataModel implements AutoCloseable {

    /**
     * DTO to be used with jackson when converting to and from exported content.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CustomCategorizationJsonDto {

        private final String category;
        private final List<String> domains;

        /**
         * Main constructor.
         *
         * @param category The category.
         * @param domains The list of host suffixes in this category.
         */
        @JsonCreator
        CustomCategorizationJsonDto(
                @JsonProperty("category") String category,
                @JsonProperty("domains") List<String> domains) {
            this.category = category;
            this.domains = domains == null
                    ? Collections.emptyList()
                    : new ArrayList<>(domains);
        }

        /**
         * Returns the category.
         *
         * @return The category.
         */
        @JsonGetter("category")
        String getCategory() {
            return category;
        }

        /**
         * Returns the list of domain suffixes in this category.
         *
         * @return The list of domain suffixes in this category.
         */
        @JsonGetter("domains")
        List<String> getDomains() {
            return domains;
        }
    }

    private static final int MAX_CAT_SIZE = 300;
    private static final int MAX_DOMAIN_SIZE = 255;

    private static final String ROOT_FOLDER = "DomainCategorization";
    private static final String FILE_REL_PATH = "custom_list.db";
    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String TABLE_NAME = "domain_suffix";
    private static final String SUFFIX_COLUMN = "suffix";
    private static final String CATEGORY_COLUMN = "category";

    private static final Logger logger = Logger.getLogger(WebCategoriesDataModel.class.getName());
    private static WebCategoriesDataModel instance;

    /**
     * Returns the maximum string length of a domain suffix.
     *
     * @return The maximum string length of a domain suffix.
     */
    static int getMaxDomainSuffixLength() {
        return MAX_DOMAIN_SIZE;
    }

    /**
     * Returns the maximum string length of a category.
     *
     * @return The maximum string length of a category.
     */
    static int getMaxCategoryLength() {
        return MAX_DOMAIN_SIZE;
    }

    /**
     * Retrieves the default path for where custom domain categorization exists.
     *
     * @return The path or null if the path cannot be reconciled.
     */
    private static File getDefaultPath() {
        String configDir = PlatformUtil.getUserConfigDirectory();
        if (configDir == null || !new File(configDir).exists()) {
            logger.log(Level.WARNING, "Unable to find UserConfigDirectory");
            return null;
        }

        Path subDirPath = Paths.get(configDir, ROOT_FOLDER);
        File subDir = subDirPath.toFile();
        if (!subDir.exists() && !subDir.mkdirs()) {
                logger.log(Level.WARNING, "There was an issue creating custom domain config at: {0}", subDirPath.toString());
        }

        return Paths.get(configDir, ROOT_FOLDER, FILE_REL_PATH).toFile();
    }

    /**
     * Generates the normalized category string to be inserted into the
     * database.
     *
     * @param category The category.
     * @return The normalized string.
     */
    static String getNormalizedCategory(String category) {
        if (category == null) {
            return "";
        }

        String trimmedCategory = category.trim();

        return trimmedCategory.substring(0, Math.min(trimmedCategory.length(), MAX_CAT_SIZE));
    }

    /**
     * Generates the normalized domain suffix string to be inserted into the
     * database.
     *
     * @param domainSuffix The domain suffix.
     * @return The normalized string.
     */
    static String getNormalizedSuffix(String domainSuffix) {
        if (domainSuffix == null) {
            return "";
        }

        String sanitized = Stream.of(domainSuffix.split("\\."))
                .map(s -> {
                    return s
                            // alphanumeric and hyphen
                            .replaceAll("[^0-9a-zA-Z\\-]", "")
                            // no leading or trailing hyphen
                            .replaceAll("^\\-*(.+?)?\\-*$", "$1");
                })
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.joining("."));

        return sanitized.substring(0, Math.min(sanitized.length(), MAX_DOMAIN_SIZE)).toLowerCase();
    }

    /**
     * Retrieves a singleton instance of this class.
     *
     * @return The singleton instance of this class.
     */
    static WebCategoriesDataModel getInstance() {
        if (instance == null) {
            instance = new WebCategoriesDataModel();
        }

        return instance;
    }

    private final File sqlitePath;
    private Connection dbConn = null;

    /**
     * Constructor used to create singleton instance.
     */
    private WebCategoriesDataModel() {
        this(getDefaultPath());
    }

    /**
     * Constructor that accepts a variable path for the custom sqlite database
     * for custom domain categories.
     *
     * @param sqlitePath The path.
     */
    WebCategoriesDataModel(File sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    /**
     * Creates a sqlite jdbc connection.
     *
     * @throws SQLException
     */
    synchronized void initialize() throws SQLException {
        String url = JDBC_SQLITE_PREFIX + sqlitePath.getAbsolutePath();
        if (this.dbConn != null) {
            this.dbConn.close();
            this.dbConn = null;
        }

        this.dbConn = DriverManager.getConnection(url);

        // speed up operations by turning off WAL
        try (Statement turnOffWal = dbConn.createStatement()) {
            turnOffWal.execute("PRAGMA journal_mode=OFF");
        }

        // create table if it doesn't exist
        try (Statement createDomainsTable = dbConn.createStatement()) {
            createDomainsTable.execute(
                    "    CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (\n"
                    + "        " + SUFFIX_COLUMN + " VARCHAR(" + MAX_DOMAIN_SIZE + ") PRIMARY KEY,\n"
                    + "        " + CATEGORY_COLUMN + " VARCHAR(" + MAX_CAT_SIZE + ")\n"
                    + "    ) WITHOUT ROWID");
        }
    }

    /**
     * Returns true if initialized.
     *
     * @return True if initialized.
     */
    synchronized boolean isInitialized() {
        return this.dbConn != null;
    }

    /**
     * Retrieves all domain categories present in json file.
     *
     * @param jsonInput The json file.
     * @return The domain categories.
     * @throws IOException
     */
    List<DomainCategory> getJsonEntries(File jsonInput) throws IOException {
        if (jsonInput == null) {
            logger.log(Level.WARNING, "No valid file provided.");
            return Collections.emptyList();
        }

        ObjectMapper mapper = new ObjectMapper();
        List<CustomCategorizationJsonDto> customCategorizations = mapper.readValue(jsonInput, new TypeReference<List<CustomCategorizationJsonDto>>() {
        });

        Stream<CustomCategorizationJsonDto> categoryStream = (customCategorizations != null) ? customCategorizations.stream() : Stream.empty();

        return categoryStream
                .filter(c -> c != null && c.getCategory() != null && c.getDomains() != null)
                .flatMap(c -> c.getDomains().stream()
                .map(WebCategoriesDataModel::getNormalizedSuffix)
                .filter(StringUtils::isNotBlank)
                .map(d -> new DomainCategory(d, getNormalizedCategory(c.getCategory()))))
                .collect(Collectors.toList());

    }

    /**
     * Exports current database to a json file.
     *
     * @param jsonOutput The output file.
     * @throws SQLException
     * @throws IOException
     */
    synchronized void exportToJson(File jsonOutput) throws SQLException, IOException {
        if (jsonOutput == null) {
            logger.log(Level.WARNING, "Null file provided.");
            return;
        }

        if (!isInitialized()) {
            initialize();
        }

        // retrieve items from the database
        List<Pair<String, String>> categoryDomains = new ArrayList<>();
        try (Statement domainSelect = dbConn.createStatement();
                ResultSet resultSet = domainSelect.executeQuery(
                        "SELECT " + SUFFIX_COLUMN + ", " + CATEGORY_COLUMN + " FROM " + TABLE_NAME + " ORDER BY " + SUFFIX_COLUMN)) {

            while (resultSet.next()) {
                categoryDomains.add(Pair.of(resultSet.getString(CATEGORY_COLUMN), resultSet.getString(SUFFIX_COLUMN)));
            }
        }

        // aggregate data appropriately into CustomCategorizationJsonDto
        List<CustomCategorizationJsonDto> categories
                = categoryDomains.stream()
                        .collect(Collectors.toMap(
                                p -> p.getKey(),
                                p -> new ArrayList<>(Arrays.asList(p.getValue())),
                                (p1, p2) -> {
                                    p1.addAll(p2);
                                    return p1;
                                }
                        ))
                        .entrySet().stream()
                        .map(entry -> new CustomCategorizationJsonDto(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());

        // write to disk
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonOutput, categories);
    }

    /**
     * Delete a record from the database.
     *
     * @param domainSuffix The domain suffix of the item to delete.
     * @return Whether or not the operation actually deleted something.
     * @throws SQLException
     * @throws IllegalArgumentException
     */
    synchronized boolean deleteRecord(String domainSuffix) throws SQLException, IllegalArgumentException {
        if (StringUtils.isBlank(domainSuffix)) {
            throw new IllegalArgumentException("Expected non-empty domain suffix");
        }

        if (!isInitialized()) {
            initialize();
        }

        try (PreparedStatement suffixDelete = dbConn.prepareStatement(
                "DELETE FROM " + TABLE_NAME + " WHERE LOWER(" + SUFFIX_COLUMN + ") = LOWER(?)", Statement.RETURN_GENERATED_KEYS);) {

            suffixDelete.setString(1, getNormalizedSuffix(domainSuffix));
            return suffixDelete.executeUpdate() > 0;
        }
    }

    /**
     * Inserts or updates the entry for the given domain suffix.
     *
     * @param entry The domain suffix and category.
     * @return True if successfully inserted/updated.
     * @throws SQLException
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     */
    synchronized boolean insertUpdateSuffix(DomainCategory entry) throws SQLException, IllegalStateException, IllegalArgumentException {
        if (entry == null || StringUtils.isBlank(getNormalizedCategory(entry.getCategory())) || StringUtils.isBlank(getNormalizedSuffix(entry.getHostSuffix()))) {
            throw new IllegalArgumentException("Expected non-empty, valid category and domain suffix.");
        }

        if (!isInitialized()) {
            initialize();
        }

        try (PreparedStatement insertUpdate = dbConn.prepareStatement(
                "INSERT OR REPLACE INTO " + TABLE_NAME + "(" + SUFFIX_COLUMN + ", " + CATEGORY_COLUMN + ") VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {

            insertUpdate.setString(1, getNormalizedSuffix(entry.getHostSuffix()));
            insertUpdate.setString(2, getNormalizedCategory(entry.getCategory()));
            return insertUpdate.executeUpdate() > 0;
        }
    }

    /**
     * Return all records in the database.
     *
     * @return The list of domain suffixes and their categories.
     * @throws SQLException
     */
    synchronized List<DomainCategory> getRecords() throws SQLException {
        if (!isInitialized()) {
            initialize();
        }

        List<DomainCategory> entries = new ArrayList<>();

        try (Statement domainSelect = dbConn.createStatement();
                ResultSet resultSet = domainSelect.executeQuery(
                        "SELECT " + SUFFIX_COLUMN + ", " + CATEGORY_COLUMN + " FROM " + TABLE_NAME + " ORDER BY " + SUFFIX_COLUMN)) {

            while (resultSet.next()) {
                entries.add(new DomainCategory(
                        resultSet.getString(SUFFIX_COLUMN),
                        resultSet.getString(CATEGORY_COLUMN)));
            }
        }
        return entries;

    }

    private static final String GET_DOMAIN_SUFFIX_QUERY
            = "SELECT " + SUFFIX_COLUMN + ", " + CATEGORY_COLUMN
            + " FROM " + TABLE_NAME + " WHERE " + SUFFIX_COLUMN + " = ?";

    /**
     * Return the matching domain suffix or null if none found.
     *
     * @param domainSuffix The domain suffix.
     * @return The found entry or null.
     * @throws SQLException
     */
    synchronized DomainCategory getRecordBySuffix(String domainSuffix) throws SQLException {
        if (!isInitialized()) {
            initialize();
        }

        try (PreparedStatement domainSelect = dbConn.prepareStatement(GET_DOMAIN_SUFFIX_QUERY)) {
            domainSelect.setString(1, domainSuffix);

            try (ResultSet resultSet = domainSelect.executeQuery()) {
                if (resultSet.next()) {
                    return new DomainCategory(
                            resultSet.getString(SUFFIX_COLUMN),
                            resultSet.getString(CATEGORY_COLUMN));
                } else {
                    return null;
                }
            }
        }
    }

    // get the suffix and category from the main table and gets the longest matching suffix.
    private static final String BASE_QUERY_FMT_STR
            = "SELECT " + SUFFIX_COLUMN + ", " + CATEGORY_COLUMN + " FROM " + TABLE_NAME
            + " WHERE suffix IN (%s) ORDER BY LENGTH(" + SUFFIX_COLUMN + ") DESC LIMIT 1";

    /**
     * Retrieves the longest matching domain suffix and category matching the
     * list of suffixes or null if no item can be found.
     *
     * @param suffixes The list of suffixes.
     * @return The longest matching entry or null if no entry found.
     * @throws SQLException
     */
    synchronized DomainCategory getLongestSuffixRecord(List<String> suffixes) throws SQLException {
        if (suffixes == null) {
            return null;
        }

        if (!isInitialized()) {
            initialize();
        }

        String questionMarks = IntStream.range(0, suffixes.size())
                .mapToObj((num) -> "?")
                .collect(Collectors.joining(","));

        try (PreparedStatement stmt = dbConn.prepareStatement(String.format(BASE_QUERY_FMT_STR, questionMarks))) {
            for (int i = 0; i < suffixes.size(); i++) {
                stmt.setString(i + 1, suffixes.get(i));
            }

            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    String suffix = resultSet.getString(SUFFIX_COLUMN);
                    String category = resultSet.getString(CATEGORY_COLUMN);
                    return new DomainCategory(suffix, category);
                }
            }
        }

        return null;
    }

    /**
     * Retrieves the longest matching domain suffix and category matching the
     * list of suffixes or null if no item can be found.
     *
     * @param host The host name.
     * @return The longest matching entry or null if no entry found.
     * @throws SQLException
     */
    DomainCategory getMatchingRecord(String host) throws SQLException {
        return getLongestSuffixRecord(getSuffixes(host));
    }

    /**
     * Retrieves all the possible suffixes that could be tracked. For instance,
     * if the host was "chatenabled.mail.google.com", the list should be
     * ["chatenabled.mail.google.com", "mail.google.com", "google.com", "com"].
     *
     * @param host The host.
     * @return The possible suffixes.
     */
    private List<String> getSuffixes(String host) {
        if (host == null) {
            return null;
        }

        List<String> hostTokens = Arrays.asList(host.split("\\."));
        List<String> hostSegmentations = new ArrayList<>();

        for (int i = 0; i < hostTokens.size(); i++) {
            String searchString = String.join(".", hostTokens.subList(i, hostTokens.size()));
            hostSegmentations.add(searchString);
        }

        return hostSegmentations;
    }

    @Override
    public synchronized void close() throws SQLException {
        if (dbConn != null) {
            dbConn.close();
            dbConn = null;
        }
    }
}
