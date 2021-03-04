/* UNCLASSIFIED
 *
 *  Viking
 *
 *  Copyright (c) 2021 Basis Technology Corporation.
 *  Contact: brianc@basistech.com
 */
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizer;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizerException;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * A DomainCategoryProvider that utilizes a sqlite db for data.
 */
public class CustomDomainCategorizer implements DomainCategorizer {
    
    private static final String ROOT_FOLDER = "DomainCategorization";
    private static final String FILE_REL_PATH = "custom_categorization.db";
    
    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String SUFFIX_COLUMN = "suffix";
    private static final String CATEGORY_COLUMN = "category_id";

    // get the suffix and category from the main table and gets the longest matching suffix.
    private static final String BASE_QUERY_FMT_STR = String.format(
            "SELECT %s, %s FROM domain_suffix WHERE suffix IN (%%s) ORDER BY LENGTH(%s) DESC LIMIT 1",
            SUFFIX_COLUMN, CATEGORY_COLUMN, SUFFIX_COLUMN);

    private static final String CATEGORY_ID_COLUMN = "id";
    private static final String CATEGORY_NAME_COLUMN = "name";
    private static final String CATEGORY_IDS_QUERY = String.format("SELECT %s,%s FROM category", CATEGORY_ID_COLUMN, CATEGORY_NAME_COLUMN);

    private static final Logger logger = Logger.getLogger(CustomDomainCategorizer.class.getName());

    private Connection dbConn = null;
    private Map<Integer, String> categoryIds = null;   
    

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
    public DomainCategory getCategory(String domain, String host) throws DomainCategorizerException {
        String hostToUse = (StringUtils.isBlank(host)) ? domain : host;
        if (StringUtils.isBlank(hostToUse)) {
            return null;
        }

        hostToUse = hostToUse.toLowerCase();

        List<String> segmentations = getSuffixes(host);
        String questionMarks = IntStream.range(0, segmentations.size())
                .mapToObj((num) -> "?")
                .collect(Collectors.joining(","));

        String sql = String.format(BASE_QUERY_FMT_STR, questionMarks);

        try (PreparedStatement stmt = dbConn.prepareStatement(sql)) {
            for (int i = 0; i < segmentations.size(); i++) {
                stmt.setString(i + 1, segmentations.get(i));
            }

            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    String suffix = resultSet.getString(SUFFIX_COLUMN);
                    int categoryId = resultSet.getInt(CATEGORY_COLUMN);
                    String category = (resultSet.wasNull()) ? null : categoryIds.get(categoryId);
                    if (StringUtils.isNotBlank(suffix) && StringUtils.isNotBlank(category)) {
                        return new DomainCategory(suffix, category);
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "There was an error retrieving results for " + hostToUse, ex);
        }

        return null;
    }

    /**
     * Retrieves a mapping of category ids to the name of the category.
     *
     * @param dbConn The database connection to the sqlite database with the
     * category table.
     * @return The mapping of category id to category name.
     * @throws SQLException
     */
    private Map<Integer, String> getCategoryIds(Connection dbConn) throws SQLException {
        if (dbConn == null) {
            return null;
        }

        Map<Integer, String> toRet = new HashMap<>();
        try (PreparedStatement stmt = dbConn.prepareStatement(CATEGORY_IDS_QUERY)) {
            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    toRet.put(resultSet.getInt(CATEGORY_ID_COLUMN), resultSet.getString(CATEGORY_NAME_COLUMN));
                }
            }
        }

        return toRet;
    }

    @Override
    public void initialize() throws DomainCategorizerException {
        File dir = InstalledFileLocator.getDefault().locate(ROOT_FOLDER, CustomDomainCategorizer.class.getPackage().getName(), false);

        boolean needNew = true;
        try {
            if (dbConn != null && !dbConn.isClosed()) {
                needNew = false;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "There was an error while checking to see if previously existing connection.", ex);
        }

        if (needNew) {
            try {
                if (dir == null || !dir.exists()) {
                    throw new DomainCategorizerException("Could not find parent directory of database");
                }

                File dbFile = Paths.get(dir.toString(), FILE_REL_PATH).toFile();
                if (dbFile == null || !dbFile.exists()) {
                    throw new DomainCategorizerException("Could not find database file in directory: " + dir.toString());
                }

                Class.forName("org.sqlite.JDBC");
                dbConn = DriverManager.getConnection(JDBC_SQLITE_PREFIX + dbFile.toString());
                categoryIds = getCategoryIds(dbConn);
            } catch (ClassNotFoundException | SQLException ex) {
                throw new DomainCategorizerException("Unable to connect to class resource db.", ex);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (dbConn != null && !dbConn.isClosed()) {
            dbConn.close();
        }
        dbConn = null;

        categoryIds = null;
    }
}
