/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.url.analytics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.modules.InstalledFileLocator;

/**
 *
 * @author gregd
 */
public class WebCategoriesManager {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomCategorizationJsonDto {

        private final String category;
        private final List<String> domains;

        @JsonCreator
        public CustomCategorizationJsonDto(
                @JsonProperty("category") String category,
                @JsonProperty("domains") List<String> domains) {
            this.category = category;
            this.domains = domains == null
                    ? Collections.emptyList()
                    : new ArrayList<>(domains);
        }

        public String getCategory() {
            return category;
        }

        public List<String> getDomains() {
            return domains;
        }
    }


    private static final int MAX_CAT_SIZE = 100;
    private static final int MAX_DOMAIN_SIZE = 255;

    private static final String ROOT_FOLDER = "DomainCategorization";
    private static final String FILE_REL_PATH = "custom_list.db";
    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";
    private static final Logger logger = Logger.getLogger(WebCategoriesManager.class.getName());
    
    
    private final File sqlitePath;
    
    
    static int getMaxDomainSuffixLength() {
        return MAX_DOMAIN_SIZE;
    }
    
    static int getMaxCategoryLength() {
        return MAX_DOMAIN_SIZE;
    }
    
    
    private static File getDefaultPath() {
        File dir = InstalledFileLocator.getDefault().locate(ROOT_FOLDER, WebCategoriesManager.class.getPackage().getName(), false);
        return Paths.get(dir.getAbsolutePath(), FILE_REL_PATH).toFile();
    }

    public WebCategoriesManager(File sqlitePath) {
        this.sqlitePath = sqlitePath;
    }
    
    public WebCategoriesManager() {
        this(getDefaultPath());
    }
    
    
    
    private Connection getConnection() throws SQLException {
        String url = JDBC_SQLITE_PREFIX + sqlitePath.getAbsolutePath();
        return DriverManager.getConnection(url);
    }

    public void convertJsonToSqlite(File jsonInput) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<CustomCategorizationJsonDto> customCategorizations = mapper.readValue(jsonInput, new TypeReference<List<CustomCategorizationJsonDto>>() {
        });

        customCategorizations = customCategorizations == null ? Collections.emptyList() : customCategorizations;

        
        try (Connection conn = getConnection()) {
            if (conn != null) {
                try (Statement turnOffWal = conn.createStatement()) {
                    turnOffWal.execute("PRAGMA journal_mode=OFF");
                }

                try (Statement createDomainsTable = conn.createStatement()) {
                    createDomainsTable.execute(
                            "    CREATE TABLE domain_suffix (\n"
                            + "        suffix VARCHAR(" + MAX_DOMAIN_SIZE + ") PRIMARY KEY,\n"
                            + "        category VARCHAR(" + MAX_CAT_SIZE + ")\n"
                            + "    ) WITHOUT ROWID");
                }

                try (PreparedStatement domainInsert = conn.prepareStatement(
                        "INSERT INTO domain_suffix(suffix, category) VALUES (?, ?)", Statement.NO_GENERATED_KEYS)) {

                    for (int i = 0; i < customCategorizations.size(); i++) {
                        CustomCategorizationJsonDto category = customCategorizations.get(i);
                        if (category == null || category.getDomains() == null || category.getCategory() == null) {
                            logger.log(Level.WARNING, String.format("Could not process item in file: %s at index: %d", jsonInput.getAbsolutePath(), i));
                            continue;
                        }

                        String categoryStr = category.getCategory().substring(0, Math.max(category.getCategory().length(), MAX_CAT_SIZE));

                        for (int listIdx = 0; listIdx < category.getDomains().size(); i++) {
                            String domain = category.getDomains().get(listIdx);
                            if (domain == null) {
                                logger.log(Level.WARNING, String.format("Could not process domain at idx: %d in category %s for file %s",
                                        listIdx, categoryStr, jsonInput.getAbsolutePath()));
                            }

                            domainInsert.setString(1, domain);
                            domainInsert.setString(2, categoryStr);
                            domainInsert.addBatch();
                        }
                    }

                    domainInsert.executeBatch();
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "There was an error while writing json to sqlite", ex);
                }
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void convertSqliteToJson(File jsonOutput) throws SQLException, IOException {
        List<Pair<String, String>> categoryDomains = new ArrayList<>();
        try (Connection conn = getConnection();
                Statement domainSelect = conn.createStatement();
                ResultSet resultSet = domainSelect.executeQuery(
                        "SELECT suffix, category FROM domain_suffix")) {

            while (resultSet.next()) {
                categoryDomains.add(Pair.of(resultSet.getString("category"), resultSet.getString("domain")));
            }
        }

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

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(jsonOutput, categories);
    }

    public boolean deleteRecord(String domainSuffix) throws SQLException {
        try (Connection conn = getConnection()) {
            if (domainSuffix == null || domainSuffix.length() > MAX_DOMAIN_SIZE) {
                throw new IllegalArgumentException("Expected non-empty category <= " + MAX_CAT_SIZE + " characters and non-empty domain suffix <= " + MAX_DOMAIN_SIZE + " characters.");
            }

            try (PreparedStatement suffixDelete = conn.prepareStatement("DELETE FROM domain_suffix WHERE LOWER(suffix) = LOWER(?)", Statement.RETURN_GENERATED_KEYS);) {

                suffixDelete.setString(1, domainSuffix.trim());
                return suffixDelete.executeUpdate() > 0;
            }
        }
    }

    public boolean insertUpdateSuffix(DomainCategory entry) throws SQLException, IllegalStateException, IllegalArgumentException {
        try (Connection conn = getConnection()) {
            if (entry == null
                    || StringUtils.isBlank(entry.getCategory()) || (entry.getCategory().length() > MAX_CAT_SIZE)
                    || StringUtils.isBlank(entry.getHostSuffix()) || (entry.getHostSuffix().length() > MAX_DOMAIN_SIZE)) {
                throw new IllegalArgumentException("Expected non-empty category <= 100 characters and non-empty domain suffix <= 255 characters.");
            }

            try (PreparedStatement insertUpdate = conn.prepareStatement("INSERT OR REPLACE INTO domain_suffix(suffix, category) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);) {
                insertUpdate.setString(1, entry.getHostSuffix().trim());
                insertUpdate.setString(1, entry.getCategory().trim());
                return insertUpdate.executeUpdate() > 0;
            }
        }
    }

    public List<DomainCategory> getRecords() throws SQLException {
        try (Connection conn = getConnection()) {
            List<DomainCategory> entries = new ArrayList<>();
            try (Statement domainSelect = conn.createStatement();
                    ResultSet resultSet = domainSelect.executeQuery("SELECT suffix, category FROM domain_suffix")) {
                while (resultSet.next()) {
                    entries.add(new DomainCategory(
                            resultSet.getString("suffix"),
                            resultSet.getString("category")));
                }
            }
            return entries;
        }
    }
}
