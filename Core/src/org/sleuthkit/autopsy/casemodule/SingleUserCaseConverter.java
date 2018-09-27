/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 * Import a case from single-user to multi-user.
 *
 * DO NOT USE, NEEDS TO BE UPDATED
 */
public class SingleUserCaseConverter {

    private static final String MODULE_FOLDER = "ModuleOutput"; //NON-NLS    
    private static final String AUTOPSY_DB_FILE = "autopsy.db"; //NON-NLS
    private static final String DOTAUT = CaseMetadata.getFileExtension(); //NON-NLS
    private static final String TIMELINE_FOLDER = "Timeline"; //NON-NLS
    private static final String TIMELINE_FILE = "events.db"; //NON-NLS
    private static final String POSTGRES_DEFAULT_DB_NAME = "postgres"; //NON-NLS
    private static final int MAX_DB_NAME_LENGTH = 63;

    public class ImportCaseData {

        private final Path imageInputFolder;
        private final Path caseInputFolder;
        private final Path imageOutputFolder;
        private final Path caseOutputFolder;
        private final String oldCaseName;
        private final String newCaseName;
        private final boolean copySourceImages;
        private final boolean deleteCase;
        private String postgreSQLDbName;
        private final String autFileName;
        private final String rawFolderName;
        private final CaseDbConnectionInfo db;

        public ImportCaseData(
                Path imageInput,
                Path caseInput,
                Path imageOutput,
                Path caseOutput,
                String oldCaseName,
                String newCaseName,
                String autFileName,
                String rawFolderName,
                boolean copySourceImages,
                boolean deleteCase) throws UserPreferencesException {

            this.imageInputFolder = imageInput;
            this.caseInputFolder = caseInput;
            this.imageOutputFolder = imageOutput;
            this.caseOutputFolder = caseOutput;
            this.oldCaseName = oldCaseName;
            this.newCaseName = newCaseName;
            this.autFileName = autFileName;
            this.rawFolderName = rawFolderName;
            this.copySourceImages = copySourceImages;
            this.deleteCase = deleteCase;
            this.db = UserPreferences.getDatabaseConnectionInfo();
        }

        public Path getCaseInputFolder() {
            return this.caseInputFolder;
        }

        public Path getCaseOutputFolder() {
            return this.caseOutputFolder;
        }

        Path getImageInputFolder() {
            return this.imageInputFolder;
        }

        Path getImageOutputFolder() {
            return this.imageOutputFolder;
        }

        String getOldCaseName() {
            return this.oldCaseName;
        }

        String getNewCaseName() {
            return this.newCaseName;
        }

        boolean getCopySourceImages() {
            return this.copySourceImages;
        }

        boolean getDeleteCase() {
            return this.deleteCase;
        }

        String getPostgreSQLDbName() {
            return this.postgreSQLDbName;
        }

        String getAutFileName() {
            return this.autFileName;
        }

        String getRawFolderName() {
            return this.rawFolderName;
        }

        CaseDbConnectionInfo getDb() {
            return this.db;
        }

        void setPostgreSQLDbName(String dbName) {
            this.postgreSQLDbName = dbName;
        }
    }

    /**
     * Handles the heavy lifting for importing a case from single-user to
     * multi-user. Creates new .aut file, moves folders to the right place,
     * imports the database, and updates paths within the database.
     *
     * @param icd the Import Case Data for the current case
     *
     * @throws java.lang.Exception
     */
    public static void importCase(ImportCaseData icd) throws Exception {

        Class.forName("org.postgresql.Driver"); //NON-NLS

        // Make sure there is a SQLite databse file
        Path oldDatabasePath = icd.getCaseInputFolder().resolve(AUTOPSY_DB_FILE);
        if (false == oldDatabasePath.toFile().exists()) {
            throw new Exception(NbBundle.getMessage(SingleUserCaseConverter.class, "SingleUserCaseConverter.BadDatabaseFileName")); //NON-NLS
        }

        // Read old xml config
        CaseMetadata oldCaseMetadata = new CaseMetadata(icd.getCaseInputFolder().resolve(icd.getAutFileName()));
        if (oldCaseMetadata.getCaseType() == CaseType.MULTI_USER_CASE) {
            throw new Exception(NbBundle.getMessage(SingleUserCaseConverter.class, "SingleUserCaseConverter.AlreadyMultiUser")); //NON-NLS
        }

        // Create sanitized names for PostgreSQL and Solr 
        /*
         * RJC: Removed package access sanitizeCaseName method, so this is no
         * longer correct, but this whole class is currently out-of-date (out of
         * synch with case database schema) and probably belongs in the TSK
         * layer anyway, see JIRA-1984.
         */
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss"); //NON-NLS
        Date date = new Date();
        String dbName = icd.getNewCaseName() + "_" + dateFormat.format(date); //NON-NLS
        icd.setPostgreSQLDbName(dbName);

        // Copy items to new hostname folder structure
        copyResults(icd);

        // Convert from SQLite to PostgreSQL
        importDb(icd);

        // Update paths inside databse
        fixPaths(icd);

        // Copy images
        copyImages(icd);

        // Create new .aut file
        CaseMetadata newCaseMetadata = new CaseMetadata(CaseType.MULTI_USER_CASE,
                icd.getCaseOutputFolder().toString(),
                icd.getNewCaseName(),
                new CaseDetails(icd.getNewCaseName(),
                        oldCaseMetadata.getCaseNumber(),
                        oldCaseMetadata.getExaminer(),
                        oldCaseMetadata.getExaminerPhone(),
                        oldCaseMetadata.getExaminerEmail(),
                        oldCaseMetadata.getCaseNotes()));
        newCaseMetadata.setCaseDatabaseName(dbName);
        // Set created date. This calls writefile, no need to call it again
        newCaseMetadata.setCreatedDate(oldCaseMetadata.getCreatedDate());
        newCaseMetadata.setCreatedByVersion(oldCaseMetadata.getCreatedByVersion());

        // At this point the import has been finished successfully so we can delete the original case
        // (if requested). This *should* be fairly safe - at this point we know there was an autopsy file
        // and database in the given directory so the user shouldn't be able to accidently blow away
        // their C drive.
        if (icd.getDeleteCase()) {
            FileUtils.deleteDirectory(icd.getCaseInputFolder().toFile());
        }
    }

    /**
     * Figure out the input folder for images and return it.
     *
     * @param icd the Import Case Data for the current case
     *
     * @return the name of the proper Image input folder
     */
    private static File findInputFolder(ImportCaseData icd) {

        File thePath = icd.getImageInputFolder().resolve(icd.getOldCaseName()).toFile();
        if (thePath.isDirectory()) {
            /// we've found it
            return thePath;
        }
        thePath = icd.getImageInputFolder().resolve(icd.getRawFolderName()).toFile();
        if (thePath.isDirectory()) {
            /// we've found it
            return thePath;
        }
        return icd.getImageInputFolder().toFile();
    }

    /**
     * Copy all folders at the base level to the new scheme involving hostname.
     * Also take care of a few files such as logs, timeline database, etc.
     *
     * @param icd the Import Case Data for the current case
     *
     * @throws IOException
     */
    private static void copyResults(ImportCaseData icd) throws IOException {
        /// get hostname
        String hostName = NetworkUtils.getLocalHostName();

        Path destination;
        Path source = icd.getCaseInputFolder();
        if (source.toFile().exists()) {
            destination = icd.getCaseOutputFolder().resolve(hostName);
            FileUtils.copyDirectory(source.toFile(), destination.toFile());
        }

        source = icd.getCaseInputFolder().resolve(TIMELINE_FILE);
        if (source.toFile().exists()) {
            destination = Paths.get(icd.getCaseOutputFolder().toString(), hostName, MODULE_FOLDER, TIMELINE_FOLDER, TIMELINE_FILE);
            FileUtils.copyFile(source.toFile(), destination.toFile());
        }

        // Remove the single-user .aut file from the multi-user folder
        File oldAutopsyFile = Paths.get(icd.getCaseOutputFolder().toString(), hostName, icd.getOldCaseName() + DOTAUT).toFile();
        if (oldAutopsyFile.exists()) {
            oldAutopsyFile.delete();
        }

        // Remove the single-user database file from the multi-user folder
        File oldDatabaseFile = Paths.get(icd.getCaseOutputFolder().toString(), hostName, AUTOPSY_DB_FILE).toFile();
        if (oldDatabaseFile.exists()) {
            oldDatabaseFile.delete();
        }

        // Remove the single-user Timeline file from the multi-user folder
        File oldTimelineFile = Paths.get(icd.getCaseOutputFolder().toString(), hostName, TIMELINE_FILE).toFile();
        if (oldTimelineFile.exists()) {
            oldTimelineFile.delete();
        }
    }

    /**
     * Import the database from SQLite to PostgreSQL. Do not change any of the
     * data while loading it over. Fixing paths is done once the database is
     * completely imported.
     *
     * @param icd the Import Case Data for the current case
     *
     * @throws Exception
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private static void importDb(ImportCaseData icd) throws SQLException, ClassNotFoundException, Exception {
        // deconflict the database name
        deconflictDatabaseName(icd);

        // Create a new database via SleuthkitCase
        SleuthkitCase newCase = SleuthkitCase.newCase(icd.getPostgreSQLDbName(), icd.getDb(), icd.getCaseOutputFolder().toString());
        newCase.close();

        /// Migrate from SQLite to PostgreSQL
        Class.forName("org.sqlite.JDBC"); //NON-NLS
        Connection sqliteConnection = getSQLiteConnection(icd);
        Connection postgreSQLConnection = getPostgreSQLConnection(icd);

        // blackboard_artifact_types        
        Statement inputStatement = sqliteConnection.createStatement();
        ResultSet inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_artifact_types"); //NON-NLS
        Statement outputStatement;
        Statement numberingPK;
        long biggestPK = 0;

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                Statement check = postgreSQLConnection.createStatement();
                ResultSet checkResult = check.executeQuery("SELECT * FROM blackboard_artifact_types WHERE artifact_type_id=" + value + " AND type_name LIKE '" + inputResultSet.getString(2) + "' AND display_name LIKE '" + inputResultSet.getString(3) + "'"); //NON-NLS
                if (!checkResult.isBeforeFirst()) { // only insert if it doesn't exist
                    String sql = "INSERT INTO blackboard_artifact_types (artifact_type_id, type_name, display_name) VALUES (" //NON-NLS
                            + value + ", '"
                            + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "',"
                            + " ? )"; //NON-NLS
                    PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                    populateNullableString(pst, inputResultSet, 3, 1);
                    pst.executeUpdate();
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_artifact_types_artifact_type_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_attribute_types
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_attribute_types"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                Statement check = postgreSQLConnection.createStatement();
                ResultSet checkResult = check.executeQuery("SELECT * FROM blackboard_attribute_types WHERE attribute_type_id=" + value + " AND type_name LIKE '" + inputResultSet.getString(2) + "' AND display_name LIKE '" + inputResultSet.getString(3) + "'"); //NON-NLS
                if (!checkResult.isBeforeFirst()) { // only insert if it doesn't exist
                    String sql = "INSERT INTO blackboard_attribute_types (attribute_type_id, type_name, display_name) VALUES (" //NON-NLS
                            + value + ", '"
                            + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "',"
                            + " ? )"; //NON-NLS

                    PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                    populateNullableString(pst, inputResultSet, 3, 1);
                    pst.executeUpdate();
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_attribute_types_attribute_type_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_objects
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_objects"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO tsk_objects (obj_id, par_obj_id, type) VALUES (" //NON-NLS
                        + value + ","
                        + getNullableLong(inputResultSet, 2) + ","
                        + inputResultSet.getInt(3) + ")"); //NON-NLS
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_objects_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_image_names, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_image_names"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                outputStatement.executeUpdate("INSERT INTO tsk_image_names (obj_id, name, sequence) VALUES (" //NON-NLS
                        + inputResultSet.getLong(1) + ",'"
                        + inputResultSet.getString(2) + "',"
                        + inputResultSet.getInt(3) + ")"); //NON-NLS
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }

        // tsk_image_info
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_image_info"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_image_info (obj_id, type, ssize, tzone, size, md5, display_name) VALUES (" //NON-NLS
                        + value + ","
                        + getNullableInt(inputResultSet, 2) + ","
                        + getNullableInt(inputResultSet, 3) + ","
                        + " ? ,"
                        + getNullableLong(inputResultSet, 5) + ","
                        + " ? ,"
                        + " ? )"; //NON-NLS

                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 4, 1);
                populateNullableString(pst, inputResultSet, 6, 2);
                populateNullableString(pst, inputResultSet, 7, 3);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_image_info_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_fs_info
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_fs_info"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_fs_info (obj_id, img_offset, fs_type, block_size, block_count, root_inum, first_inum, last_inum, display_name) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getInt(3) + ","
                        + inputResultSet.getLong(4) + ","
                        + inputResultSet.getLong(5) + ","
                        + inputResultSet.getLong(6) + ","
                        + inputResultSet.getLong(7) + ","
                        + inputResultSet.getLong(8) + ","
                        + " ? )"; //NON-NLS

                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 9, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_fs_info_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_files_path
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_files_path"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }

                // If the entry contains an encoding type, copy it. Otherwise use NONE.
                // The test on column count can be removed if we upgrade the database before conversion.
                int encoding = TskData.EncodingType.NONE.getType();
                ResultSetMetaData rsMetaData = inputResultSet.getMetaData();
                if (rsMetaData.getColumnCount() == 3) {
                    encoding = inputResultSet.getInt(3);
                }
                outputStatement.executeUpdate("INSERT INTO tsk_files_path (obj_id, path, encoding_type) VALUES (" //NON-NLS
                        + value + ", '"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "', "
                        + encoding + ")"); //NON-NLS
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_files_path_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_files
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_files"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_files (obj_id, fs_obj_id, attr_type, attr_id, name, meta_addr, meta_seq, type, has_layout, has_path, dir_type, meta_type, dir_flags, meta_flags, size, ctime, crtime, atime, mtime, mode, uid, gid, md5, known, parent_path) VALUES (" //NON-NLS
                        + value + ","
                        + getNullableLong(inputResultSet, 2) + ","
                        + getNullableInt(inputResultSet, 3) + ","
                        + getNullableInt(inputResultSet, 4) + ",'"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(5)) + "',"
                        + getNullableLong(inputResultSet, 6) + ","
                        + getNullableLong(inputResultSet, 7) + ","
                        + getNullableInt(inputResultSet, 8) + ","
                        + getNullableInt(inputResultSet, 9) + ","
                        + getNullableInt(inputResultSet, 10) + ","
                        + getNullableInt(inputResultSet, 11) + ","
                        + getNullableInt(inputResultSet, 12) + ","
                        + getNullableInt(inputResultSet, 13) + ","
                        + getNullableInt(inputResultSet, 14) + ","
                        + getNullableLong(inputResultSet, 15) + ","
                        + getNullableLong(inputResultSet, 16) + ","
                        + getNullableLong(inputResultSet, 17) + ","
                        + getNullableLong(inputResultSet, 18) + ","
                        + getNullableLong(inputResultSet, 19) + ","
                        + getNullableInt(inputResultSet, 20) + ","
                        + getNullableInt(inputResultSet, 21) + ","
                        + getNullableInt(inputResultSet, 22) + ","
                        + " ? ,"
                        + getNullableInt(inputResultSet, 24) + ","
                        + " ? )"; //NON-NLS

                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 23, 1);
                populateNullableString(pst, inputResultSet, 25, 2);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_files_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_file_layout, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_file_layout"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                outputStatement.executeUpdate("INSERT INTO tsk_file_layout (obj_id, byte_start, byte_len, sequence) VALUES (" //NON-NLS
                        + inputResultSet.getLong(1) + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ","
                        + inputResultSet.getInt(4) + ")"); //NON-NLS
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }

        // tsk_db_info, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_db_info"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                Statement check = postgreSQLConnection.createStatement();
                ResultSet checkResult = check.executeQuery("SELECT * FROM tsk_db_info WHERE schema_ver=" + inputResultSet.getInt(1) + " AND tsk_ver=" + inputResultSet.getInt(2)); //NON-NLS
                if (!checkResult.isBeforeFirst()) { // only insert if it doesn't exist
                    outputStatement.executeUpdate("INSERT INTO tsk_db_info (schema_ver, tsk_ver) VALUES (" //NON-NLS
                            + getNullableInt(inputResultSet, 1) + ","
                            + getNullableInt(inputResultSet, 2) + ")"); //NON-NLS
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }

        // tag_names
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tag_names"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tag_names (tag_name_id, display_name, description, color) VALUES (" //NON-NLS
                        + value + ","
                        + " ? ,'"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(3)) + "','"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(4)) + "')"; //NON-NLS

                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 2, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tag_names_tag_name_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // reports
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM reports"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO reports (report_id, path, crtime, src_module_name, report_name) VALUES (" //NON-NLS
                        + value + ", '"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "',"
                        + inputResultSet.getInt(3) + ",'"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(4)) + "','"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(5)) + "')"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE reports_report_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_artifacts
        biggestPK = Long.MIN_VALUE; // This table uses very large negative primary key values, so start at Long.MIN_VALUE
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_artifacts"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO blackboard_artifacts (artifact_id, obj_id, artifact_type_id) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ")"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_artifacts_artifact_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_attributes, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_attributes"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                String sql = "INSERT INTO blackboard_attributes (artifact_id, artifact_type_id, source, context, attribute_type_id, value_type, value_byte, value_text, value_int32, value_int64, value_double) VALUES (" //NON-NLS
                        + inputResultSet.getLong(1) + ","
                        + inputResultSet.getLong(2) + ","
                        + " ? ,"
                        + " ? ,"
                        + inputResultSet.getLong(5) + ","
                        + inputResultSet.getInt(6) + ","
                        + " ? ,"
                        + " ? ,"
                        + getNullableInt(inputResultSet, 9) + ","
                        + getNullableLong(inputResultSet, 10) + ","
                        + " ? )"; //NON-NLS
                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 3, 1);
                populateNullableString(pst, inputResultSet, 4, 2);
                populateNullableByteArray(pst, inputResultSet, 7, 3);
                populateNullableString(pst, inputResultSet, 8, 4);
                populateNullableNumeric(pst, inputResultSet, 11, 5);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }

        // tsk_vs_parts
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_vs_parts"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_vs_parts (obj_id, addr, start, length, descr, flags) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ","
                        + inputResultSet.getLong(4) + ","
                        + " ? ,"
                        + inputResultSet.getInt(6) + ")"; //NON-NLS
                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 5, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_vs_parts_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_vs_info
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_vs_info"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO tsk_vs_info (obj_id, vs_type, img_offset, block_size) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getInt(2) + ","
                        + inputResultSet.getLong(3) + ","
                        + inputResultSet.getLong(4) + ")"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_vs_info_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_files_derived 
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_files_derived"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_files_derived (obj_id, derived_id, rederive) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + " ? )"; //NON-NLS
                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 3, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_files_derived_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_files_derived_method
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_files_derived_method"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_files_derived_method (derived_id, tool_name, tool_version, other) VALUES (" //NON-NLS
                        + value + ", '"
                        + inputResultSet.getString(2) + "','"
                        + inputResultSet.getString(3) + "',"
                        + " ? )"; //NON-NLS
                PreparedStatement pst = postgreSQLConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 4, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_files_derived_method_derived_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // content_tags
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM content_tags LEFT OUTER JOIN tsk_examiners ON content_tags.examiner_id = tsk_examiners.examiner_id"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO content_tags (tag_id, obj_id, tag_name_id, comment, begin_byte_offset, end_byte_offset, examiner_id) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ",'"
                        + inputResultSet.getString(4) + "',"
                        + inputResultSet.getLong(5) + ","
                        + inputResultSet.getLong(6) + ","
                        + inputResultSet.getInt(7) + ")"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE content_tags_tag_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_artifact_tags
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_artifact_tags"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgreSQLConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO blackboard_artifact_tags (tag_id, artifact_id, tag_name_id, comment) VALUES (" //NON-NLS
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ",'"
                        + inputResultSet.getString(4) + "','"
                        + inputResultSet.getString(5) + "')"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgreSQLConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_artifact_tags_tag_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        sqliteConnection.close();
        postgreSQLConnection.close();
    }

    /**
     * Checks that the database name is unique. If it is not, attempts to add
     * numbers to it until it is unique. Gives up if it goes through all
     * positive integers without finding a unique name.
     *
     * @param icd the Import Case Data for the current case
     *
     * @throws Exception
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private static void deconflictDatabaseName(ImportCaseData icd) throws ClassNotFoundException, SQLException, Exception {

        Connection postgreSQLConnection = getPostgreSQLConnection(icd, POSTGRES_DEFAULT_DB_NAME);

        int number = 1;
        boolean unique = false;
        String sanitizedDbName = icd.getPostgreSQLDbName();
        if (sanitizedDbName.length() > MAX_DB_NAME_LENGTH) {
            sanitizedDbName = sanitizedDbName.substring(0, MAX_DB_NAME_LENGTH);
        }

        if (postgreSQLConnection != null) {
            while (unique == false) {
                Statement st = postgreSQLConnection.createStatement();
                ResultSet answer = st.executeQuery("SELECT datname FROM pg_catalog.pg_database WHERE LOWER(datname) LIKE LOWER('" + sanitizedDbName + "%')"); //NON-NLS

                if (!answer.next()) {
                    unique = true;
                } else {
                    // not unique. add numbers to db name.
                    if (number == Integer.MAX_VALUE) {
                        // oops. it never became unique. give up.
                        throw new Exception(NbBundle.getMessage(SingleUserCaseConverter.class, "SingleUserCaseConverter.NonUniqueDatabaseName")); //NON-NLS
                    }
                    sanitizedDbName = "db_" + Integer.toString(number) + "_" + icd.getPostgreSQLDbName(); //NON-NLS

                    // Chop full db name to 63 characters (max for PostgreSQL)
                    if (sanitizedDbName.length() > MAX_DB_NAME_LENGTH) {
                        sanitizedDbName = sanitizedDbName.substring(0, MAX_DB_NAME_LENGTH);
                    }
                    ++number;
                }
            }
            postgreSQLConnection.close();
        } else {
            // Could be caused by database credentials, using user accounts that 
            // can not check if other databases exist, so allow it to continue
        }

        icd.setPostgreSQLDbName(sanitizedDbName);
    }

    /**
     * Get the images from the old case and stage them for the new case, if the
     * user chose to copy images over.
     *
     * @param icd the Import Case Data for the current case
     *
     * @throws IOException
     */
    private static void copyImages(ImportCaseData icd) throws Exception {
        if (icd.getCopySourceImages()) {
            File imageSource = findInputFolder(icd); // Find the folder for the input images
            File imageDestination = new File(icd.getImageOutputFolder().toString());

            // If we can find the input images, copy if needed.
            if (imageSource.exists()) {
                FileUtils.copyDirectory(imageSource, imageDestination);

            } else {
                throw new Exception(NbBundle.getMessage(SingleUserCaseConverter.class, "SingleUserCaseConverter.UnableToCopySourceImages")); //NON-NLS
            }
        }
    }

    /**
     * Fix up any paths in the database that refer to items that have moved.
     * Candidates include events.db, input images, reports, file paths, etc.
     *
     * @param icd the Import Case Data for the current case
     *
     * @throws Exception
     * @throws SQLExceptionException
     */
    private static void fixPaths(ImportCaseData icd) throws SQLException, Exception {
        /// Fix paths in reports, tsk_files_path, and tsk_image_names tables

        String input = icd.getImageInputFolder().toString();
        String output = icd.getImageOutputFolder().toString();

        Connection postgresqlConnection = getPostgreSQLConnection(icd);

        if (postgresqlConnection != null) {
            String hostName = NetworkUtils.getLocalHostName();

            // add hostname to reports
            Statement updateStatement = postgresqlConnection.createStatement();
            updateStatement.executeUpdate("UPDATE reports SET path=CONCAT('" + hostName + "/', path) WHERE path IS NOT NULL AND path != ''"); //NON-NLS

            // add hostname to tsk_files_path
            updateStatement = postgresqlConnection.createStatement();
            updateStatement.executeUpdate("UPDATE tsk_files_path SET path=CONCAT('" + hostName + "\\', path) WHERE path IS NOT NULL AND path != ''"); //NON-NLS

            String caseName = icd.getRawFolderName().toLowerCase();

            if (icd.getCopySourceImages()) {
                // update path for images
                Statement inputStatement = postgresqlConnection.createStatement();
                ResultSet inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_image_names"); //NON-NLS

                while (inputResultSet.next()) {
                    Path oldPath = Paths.get(inputResultSet.getString(2));

                    for (int x = 0; x < oldPath.getNameCount(); ++x) {
                        if (oldPath.getName(x).toString().toLowerCase().equals(caseName)) {
                            Path newPath = Paths.get(output, oldPath.subpath(x + 1, oldPath.getNameCount()).toString());
                            updateStatement = postgresqlConnection.createStatement();
                            updateStatement.executeUpdate("UPDATE tsk_image_names SET name='" + newPath.toString() + "' WHERE obj_id = " + inputResultSet.getInt(1)); //NON-NLS
                            break;
                        }
                    }
                }
            }
            postgresqlConnection.close();
        } else {
            throw new Exception(NbBundle.getMessage(SingleUserCaseConverter.class, "SingleUserCaseConverter.CanNotOpenDatabase")); //NON-NLS
        }
    }

    /**
     * Return an integer from the ResultSet converted to String or NULL, by
     * checking ResultSet.wasNull()
     *
     * @param rs    the ResultSet to work with
     * @param index the index into the ResultSet to work with
     *
     * @return the proper value, the integer, or NULL
     *
     * @throws SQLException
     */
    private static String getNullableInt(ResultSet rs, int index) throws SQLException {
        int value = rs.getInt(index);
        if (rs.wasNull()) {
            return "NULL"; //NON-NLS
        } else {
            return Integer.toString(value);
        }
    }

    /**
     * Return a long from the ResultSet converted to String or NULL, by checking
     * ResultSet.wasNull()
     *
     * @param rs    the ResultSet to work with
     * @param index the index into the ResultSet to work with
     *
     * @return the proper value, the long, or NULL
     *
     * @throws SQLException
     */
    private static String getNullableLong(ResultSet rs, int index) throws SQLException {
        long value = rs.getLong(index);
        if (rs.wasNull()) {
            return "NULL"; //NON-NLS
        } else {
            return Long.toString(value);
        }
    }

    /**
     * Place a NULL inside a prepared statement if needed, otherwise, place the
     * String that was in the ResultSet.
     *
     * @param pst     the prepared statement
     * @param rs      the ResultSet to work with
     * @param rsIndex index for the result set
     * @param psIndex index for the prepared statement
     *
     * @throws SQLException
     */
    private static void populateNullableString(PreparedStatement pst, ResultSet rs, int rsIndex, int psIndex) throws SQLException {
        String nullableString = rs.getString(rsIndex);
        if (rs.wasNull()) {
            pst.setNull(psIndex, java.sql.Types.NULL);
        } else {
            pst.setString(psIndex, SleuthkitCase.escapeSingleQuotes(nullableString));
        }
    }

    /**
     * Place a NULL inside a prepared statement if needed, otherwise, place the
     * byte array that was in the ResultSet.
     *
     * @param pst     the prepared statement
     * @param rs      the ResultSet to work with
     * @param rsIndex index for the result set
     * @param psIndex index for the prepared statement
     *
     * @throws SQLException
     */
    private static void populateNullableByteArray(PreparedStatement pst, ResultSet rs, int rsIndex, int psIndex) throws SQLException {
        byte[] nullableBytes = rs.getBytes(rsIndex);
        if (rs.wasNull()) {
            pst.setNull(psIndex, java.sql.Types.NULL);
        } else {
            pst.setBytes(psIndex, nullableBytes);
        }
    }

    /**
     * Place a NULL inside a prepared statement if needed, otherwise, place the
     * double that was in the ResultSet.
     *
     * @param pst     the prepared statement
     * @param rs      the ResultSet to work with
     * @param rsIndex index for the result set
     * @param psIndex index for the prepared statement
     *
     * @throws SQLException
     */
    private static void populateNullableNumeric(PreparedStatement pst, ResultSet rs, int rsIndex, int psIndex) throws SQLException {
        double nullableNumeric = rs.getDouble(rsIndex);
        if (rs.wasNull()) {
            pst.setNull(psIndex, java.sql.Types.NULL);
        } else {
            pst.setDouble(psIndex, nullableNumeric);
        }
    }

    /**
     * Open the PostgreSQL database
     *
     * @param icd Import Case Data holding connection credentials
     *
     * @return returns a Connection
     *
     * @throws SQLException if unable to open
     */
    private static Connection getPostgreSQLConnection(ImportCaseData icd) throws SQLException {
        return getPostgreSQLConnection(icd, icd.getPostgreSQLDbName());
    }

    /**
     * Open the PostgreSQL database
     *
     * @param icd    Import Case Data holding connection credentials
     * @param dbName the name of the database to open
     *
     * @return returns a Connection
     *
     * @throws SQLException if unable to open
     */
    private static Connection getPostgreSQLConnection(ImportCaseData icd, String dbName) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" //NON-NLS
                + icd.getDb().getHost() + ":"
                + icd.getDb().getPort() + "/"
                + dbName,
                icd.getDb().getUserName(),
                icd.getDb().getPassword()); //NON-NLS   
    }

    /**
     * Open the SQLite database
     *
     * @param icd Import Case Data holding database path details
     *
     * @return returns a Connection
     *
     * @throws SQLException if unable to open
     */
    private static Connection getSQLiteConnection(ImportCaseData icd) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + icd.getCaseInputFolder().resolve(AUTOPSY_DB_FILE).toString(), "", ""); //NON-NLS
    }

}
