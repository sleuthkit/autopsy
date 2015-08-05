/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.OK_CANCEL_OPTION;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import static org.sleuthkit.autopsy.casemodule.Case.MODULE_FOLDER;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;

/**
 * Import case(s) from single-user to multi-user. Recursively scans subfolders.
 */
public class SingleUserCaseImporter implements Runnable {

    private static final String AUTOPSY_DB_FILE = "autopsy.db"; //NON-NLS
    private static final String DOTAUT = ".aut"; //NON-NLS
    public static final String CASE_IMPORT_LOG_FILE = "case_import_log.txt"; //NON-NLS
    private static final String logDateFormat = "yyyy/MM/dd HH:mm:ss"; //NON-NLS
    //If TIMELINE_FOLDER changes, also update TIMELINE in EventsRepository 
    private static final String TIMELINE_FOLDER = "Timeline"; //NON-NLS
    //If TIMELINE_FILE changes, also update TIMELINE_FILE in EventDB 
    private final static String TIMELINE_FILE = "events.db"; //NON-NLS
    private final static String AIM_LOG_FILE_NAME = "auto_ingest_log.txt"; //NON-NLS
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(logDateFormat);
    private static final int MAX_DB_NAME_LENGTH = 63;
    private final String SEP = System.getProperty("line.separator");
    private final Object threadWaitNotifyLock = new Object();

    private final Path caseInputFolder;
    private final String caseOutputFolder;
    private final String imageInputFolder;
    private final String imageOutputFolder;
    private final boolean copySourceImages;
    private final boolean deleteCase;
    private final CaseDbConnectionInfo db;
    private final ImportDoneCallback notifyOnComplete;
    private final UNCPathUtilities uncPathUtilities = new UNCPathUtilities();
    private PrintWriter writer;
    private XMLCaseManagement oldXmlCaseManagement;
    private XMLCaseManagement newXmlCaseManagement;
    private boolean addTimestamp;
    private int userAnswer = 0;

    /**
     * SingleUserCaseImporter constructor
     *
     * @param caseInput the folder to start our case search from. Will find
     * valid cases from this folder down, and process them.
     * @param caseOutput the folder to place processed cases into
     * @param imageInput the folder that holds the images to copy over
     * @param imageOutput the destination folder for the images
     * @param database the connection information to talk to the PostgreSQL db
     * @param copySourceImages true if images should be copied
     * @param deleteCase true if the old version of the case should be deleted
     * after import
     * @param addTimestamp true if the output case name should end in a
     * timestamp, false otherwise
     * @param callback a callback from the calling panel for notification when
     * the import has completed. This is a Runnable on a different thread.
     */
    public SingleUserCaseImporter(String caseInput, String caseOutput, String imageInput, String imageOutput, CaseDbConnectionInfo database,
            boolean copySourceImages, boolean deleteCase, ImportDoneCallback callback, boolean addTimestamp) {
        this.caseInputFolder = Paths.get(caseInput);
        this.caseOutputFolder = caseOutput;
        this.imageInputFolder = imageInput;
        this.imageOutputFolder = imageOutput;
        this.copySourceImages = copySourceImages;
        this.deleteCase = deleteCase;
        this.db = database;
        this.notifyOnComplete = callback;
        this.addTimestamp = addTimestamp;
    }

    /**
     * Tests if the input path has a corresponding image input folder and no
     * repeated case names in the path. If both of these conditions are true, we
     * can process this case, otherwise we can not.
     *
     * @param icd the import case data for the current case
     * @return true if we can process it, false if not
     */
    private boolean canProcess(ImportCaseData icd) {
        try {
            String relativeCaseName = TimeStampUtils.removeTimeStamp(icd.getRelativeCaseName());
            String caseName = TimeStampUtils.removeTimeStamp(icd.getOldCaseName());

            // check for image folder
            Path testImageInputsFromOldCase = Paths.get(imageInputFolder, relativeCaseName);
            if (!testImageInputsFromOldCase.toFile().isDirectory()) {
                log(imageInputFolder + " has no corresponding images folder.  Not able to process.");
                return false;
            } else {
                icd.setSpecificImageInputFolder(testImageInputsFromOldCase);
            }

            Path imagePath = Paths.get(imageInputFolder);
            // see if case name is in the image path. This causes bad things to happen with the parsing.
            for (int x = 0; x < imagePath.getNameCount(); ++x) {
                if (caseName.toLowerCase().equals(imagePath.getName(x).toString().toLowerCase())) {
                    log(imagePath.toString() + " has case name \"" + caseName + "\" within path. Not able to process.");
                    return false;
                }
            }

        } catch (Exception ex) {
            log("Error processing " + icd.specificCaseInputFolder.toString() + ": " + ex.getMessage());
            return false; // anything goes wrong, bail.
        }

        return true;
    }

    /**
     * Handles most of the heavy lifting for importing cases from single-user to
     * multi-user. Creates new .aut file, moves folders to the right place,
     * imports the database, and updates paths within the database.
     *
     * @param icd the Import Case Data for the current case
     * @return true if successful, false if not
     */
    private boolean processCase(ImportCaseData icd) {
        boolean result = true;
        try {
            log("Importing case " + icd.getSpecificCaseInputFolder().toString() + " to " + caseOutputFolder + "\\" + icd.getOldCaseName()); //NON-NLS

            checkInputDatabase(icd.getSpecificCaseInputFolder());

            oldXmlCaseManagement = new XMLCaseManagement();

            // read old xml config
            oldXmlCaseManagement.open(icd.getSpecificCaseInputFolder().resolve(TimeStampUtils.removeTimeStamp(icd.getOldCaseName()) + DOTAUT).toString());
            if (oldXmlCaseManagement.getCaseType() == CaseType.MULTI_USER_CASE) {
                throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.AlreadyMultiUser"));
            }

            prepareOutput(icd);

            // create sanitized names for database and solr 
            String caseName = TimeStampUtils.removeTimeStamp(icd.getNewCaseName());  // caseName holds the deconflicted, timestampless name of the case
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss"); //NON-NLS
            Date date = new Date();
            String santizedDatabaseName = Case.sanitizeCaseName(caseName);
            String dbName = santizedDatabaseName + "_" + dateFormat.format(date); //NON-NLS
            String solrName = dbName;

            icd.setSpecificImageOutputFolder(Paths.get(imageOutputFolder, icd.getNewCaseName()));
            copyResults(icd); // Copy items to new hostname folder structure
            dbName = importDb(dbName, icd.getSpecificCaseInputFolder(), icd.getSpecificCaseOutputFolder().toString()); // Change from SQLite to PostgreSQL

            fixPaths(icd, dbName); // Update paths in DB

            copyImages(icd); // Copy images over

            // create new XML config
            newXmlCaseManagement = new XMLCaseManagement();
            newXmlCaseManagement.create(icd.getSpecificCaseOutputFolder().toString(),
                    caseName,
                    oldXmlCaseManagement.getCaseExaminer(),
                    oldXmlCaseManagement.getCaseNumber(),
                    CaseType.MULTI_USER_CASE, dbName, solrName);

            // Set created date. This calls writefile, no need to call it again
            newXmlCaseManagement.setCreatedDate(oldXmlCaseManagement.getCreatedDate());

            // At this point the import has been finished successfully so we can delete the original case
            // (if requested). This *should* be fairly safe - at this point we know there was an autopsy file
            // and database in the given directory so the user shouldn't be able to accidently blow away
            // their C drive.
            if (deleteCase) {
                log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.DeletingCase") + " " + icd.getSpecificCaseInputFolder().toString());
                FileUtils.deleteDirectory(icd.getSpecificCaseInputFolder().toFile());
            }

            result = reportLostImages(db, dbName);

            log("Finished importing case " + icd.getSpecificCaseInputFolder().toString() + " to " + icd.getSpecificCaseOutputFolder().toString());
        } catch (Exception exp) {
            /// clean up here
            log("Error processing " + icd.specificCaseInputFolder.toString() + ": " + exp.getMessage());
            result = false;
        }
        return result;
    }

    /**
     * Searches for images in the filesystem. It parses the new PostgreSQL
     * database to find images that should exist, and notifies when they do not.
     *
     * @param db database credentials
     * @param dbName the name of the database
     * @return true if successfully found all images, false otherwise.
     */
    private boolean reportLostImages(CaseDbConnectionInfo db, String dbName) {
        boolean result = true;
        if (copySourceImages) {
            try {
                Class.forName("org.postgresql.Driver"); //NON-NLS
                Connection dbConnection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/" + dbName, db.getUserName(), db.getPassword()); //NON-NLS
                Statement inputStatement = dbConnection.createStatement();
                ResultSet inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_image_names"); //NON-NLS

                while (inputResultSet.next()) {

                    File theFile = new File(inputResultSet.getString(2));
                    if (false == theFile.exists()) {
                        log("Unable to find image " + theFile.toString() + " for case " + dbName);
                        result = false;
                    }
                }
            } catch (Exception ex) {
                log("Error. Unable to verify images were copied.");
                result = false;
            }
        }
        return result;
    }

    /**
     * Figure out the input folder for images and return it.
     *
     * @param icd the import case data for the current case
     * @return the name of the proper input folder
     */
    private File findInputFolder(ImportCaseData icd) {

        File thePath = icd.getSpecificImageInputFolder().resolve(icd.getOldCaseName()).toFile();
        if (thePath.isDirectory()) {
            /// we've found it
            return thePath;
        } else {
            return icd.getSpecificImageInputFolder().toFile();
        }
    }

    /**
     * Ensure the input source has an autopsy.db and exists.
     *
     * @param caseInput The folder containing a case to import.
     * @throws Exception
     */
    private void checkInputDatabase(Path caseInput) throws Exception {
        Path path = caseInput.resolve(AUTOPSY_DB_FILE);
        if (false == path.toFile().exists()) {
            throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.BadDatabaseFileName"));
        }
    }

    /**
     * Handles case folder, PosgreSql database, and Solr core name deconfliction
     * Sets the appropriate portions of the ImportCaseData object.
     *
     * @param icd the case data folder name
     * @throws Exception
     */
    private void prepareOutput(ImportCaseData icd) throws Exception {
        // test for uniqueness
        String caseName = icd.getOldCaseName();
        File specificOutputFolder = Paths.get(caseOutputFolder, caseName).toFile();
        String sanitizedCaseName = caseName;
        if (specificOutputFolder.exists()) {
            // not unique. add numbers before timestamp to specific case name
            String timeStamp = TimeStampUtils.getTimeStampOnly(caseName); //NON-NLS
            sanitizedCaseName = TimeStampUtils.removeTimeStamp(caseName);

            int number = 1;
            String temp = ""; //NON-NLS
            while (specificOutputFolder.exists()) {
                if (number == Integer.MAX_VALUE) {
                    // oops. it never became unique. give up.
                    throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.NonUniqueOutputFolder") + sanitizedCaseName);
                }
                temp = sanitizedCaseName + "_" + Integer.toString(number) + timeStamp; //NON-NLS
                specificOutputFolder = Paths.get(caseOutputFolder, temp).toFile();
                ++number;
            }
            sanitizedCaseName = temp;
        }

        if (addTimestamp && !TimeStampUtils.endsWithTimeStamp(sanitizedCaseName)) {
            sanitizedCaseName += "_" + TimeStampUtils.createTimeStamp();
        }

        Path caseOutputPath = Paths.get(caseOutputFolder, sanitizedCaseName);
        icd.setNewCaseName(sanitizedCaseName);
        icd.setSpecificCaseOutputFolder(caseOutputPath);
        caseOutputPath.toFile().mkdirs(); // create output folders just in case
    }

    /**
     * Copy all the folders at the base level to the new scheme involving
     * hostname. Also take care of a few files such as logs, timeline db, etc.
     *
     * @param icd the case data
     * @throws IOException
     */
    private void copyResults(ImportCaseData icd) throws IOException {
        /// get hostname
        String hostName = NetworkUtils.getLocalHostName();
        Path destination;
        Path source;

        source = icd.getSpecificCaseInputFolder();
        if (source.toFile().exists()) {
            destination = icd.getSpecificCaseOutputFolder().resolve(hostName);
            FileUtils.copyDirectory(source.toFile(), destination.toFile());
        }

        source = icd.getSpecificCaseInputFolder().resolve(TIMELINE_FILE);
        if (source.toFile().exists()) {
            destination = Paths.get(icd.getSpecificCaseOutputFolder().toString(), hostName, MODULE_FOLDER, TIMELINE_FOLDER, TIMELINE_FILE);
            FileUtils.copyFile(source.toFile(), destination.toFile());
        }

        source = icd.getSpecificCaseInputFolder().resolve(AIM_LOG_FILE_NAME);
        destination = icd.getSpecificCaseOutputFolder().resolve(AIM_LOG_FILE_NAME);
        if (source.toFile().exists()) {
            FileUtils.copyFile(source.toFile(), destination.toFile());

        }
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(destination.toString(), true)))) {
            out.println(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.ImportedAsMultiUser") + new Date());
        } catch (IOException e) {
            // if unable to log it, no problem
        }

        // Remove the single-user .aut file, database, Timeline database and log
        File oldAutopsyFile = Paths.get(icd.getSpecificCaseOutputFolder().toString(), hostName, TimeStampUtils.removeTimeStamp(icd.getOldCaseName()) + DOTAUT).toFile();
        if (oldAutopsyFile.exists()) {
            oldAutopsyFile.delete();
        }

        File oldDatabaseFile = Paths.get(icd.getSpecificCaseOutputFolder().toString(), hostName, AUTOPSY_DB_FILE).toFile();
        if (oldDatabaseFile.exists()) {
            oldDatabaseFile.delete();
        }

        File oldTimelineFile = Paths.get(icd.getSpecificCaseOutputFolder().toString(), hostName, TIMELINE_FILE).toFile();
        if (oldTimelineFile.exists()) {
            oldTimelineFile.delete();
        }

        File oldIngestLog = Paths.get(icd.getSpecificCaseOutputFolder().toString(), hostName, AIM_LOG_FILE_NAME).toFile();
        if (oldIngestLog.exists()) {
            oldIngestLog.delete();
        }
    }

    /**
     * Import the database from SQLite to PostgreSQL. Do not change any of the
     * data while loading it over. Fixing paths is done once the database is
     * completely imported.
     *
     * @param dbName the name of the database, could have name collision
     * @param inputPath the path to the input case
     * @param outputCaseName the name of the output case, could have extra
     * digits to avoid name collisions
     * @return the deconflicted name of the PostgreSQL database that was created
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private String importDb(String dbName, Path inputPath, String outputCaseName) throws SQLException, ClassNotFoundException, Exception {
        // deconflict the database name
        dbName = deconflictDatabaseName(db, dbName);

        // Create a new database via SleuthkitCase
        SleuthkitCase newCase = SleuthkitCase.newCase(dbName, db, outputCaseName);
        newCase.close();

        /// Migrate from SQLite to PostgreSQL
        Class.forName("org.sqlite.JDBC"); //NON-NLS
        Connection sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + inputPath.resolve(AUTOPSY_DB_FILE).toString(), "", ""); //NON-NLS

        Connection postgresqlConnection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/" + dbName, db.getUserName(), db.getPassword()); //NON-NLS

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
                Statement check = postgresqlConnection.createStatement();
                ResultSet checkResult = check.executeQuery("SELECT * FROM blackboard_artifact_types WHERE artifact_type_id=" + value + " AND type_name LIKE '" + inputResultSet.getString(2) + "' AND display_name LIKE '" + inputResultSet.getString(3) + "'"); //NON-NLS
                if (!checkResult.isBeforeFirst()) { // only insert if it doesn't exist
                    String sql = "INSERT INTO blackboard_artifact_types (artifact_type_id, type_name, display_name) VALUES ("
                            + value + ", '"
                            + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "',"
                            + " ? )"; //NON-NLS
                    PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                    populateNullableString(pst, inputResultSet, 3, 1);
                    pst.executeUpdate();
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
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
                Statement check = postgresqlConnection.createStatement();
                ResultSet checkResult = check.executeQuery("SELECT * FROM blackboard_attribute_types WHERE attribute_type_id=" + value + " AND type_name LIKE '" + inputResultSet.getString(2) + "' AND display_name LIKE '" + inputResultSet.getString(3) + "'"); //NON-NLS
                if (!checkResult.isBeforeFirst()) { // only insert if it doesn't exist
                    String sql = "INSERT INTO blackboard_attribute_types (attribute_type_id, type_name, display_name) VALUES ("
                            + value + ", '"
                            + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "',"
                            + " ? )"; //NON-NLS

                    PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                    populateNullableString(pst, inputResultSet, 3, 1);
                    pst.executeUpdate();
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_attribute_types_attribute_type_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_objects
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_objects"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO tsk_objects (obj_id, par_obj_id, type) VALUES ("
                        + value + ","
                        + getNullableLong(inputResultSet, 2) + ","
                        + inputResultSet.getInt(3) + ")"); //NON-NLS
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_objects_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_image_names, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_image_names"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                outputStatement.executeUpdate("INSERT INTO tsk_image_names (obj_id, name, sequence) VALUES ("
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
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                String sql = "INSERT INTO tsk_image_info (obj_id, type, ssize, tzone, size, md5, display_name) VALUES ("
                        + value + ","
                        + getNullableInt(inputResultSet, 2) + ","
                        + getNullableInt(inputResultSet, 3) + ","
                        + " ? ,"
                        + getNullableLong(inputResultSet, 5) + ","
                        + " ? ,"
                        + " ? )"; //NON-NLS

                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
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
        numberingPK = postgresqlConnection.createStatement();
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
                String sql = "INSERT INTO tsk_fs_info (obj_id, img_offset, fs_type, block_size, block_count, root_inum, first_inum, last_inum, display_name) VALUES ("
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getInt(3) + ","
                        + inputResultSet.getLong(4) + ","
                        + inputResultSet.getLong(5) + ","
                        + inputResultSet.getLong(6) + ","
                        + inputResultSet.getLong(7) + ","
                        + inputResultSet.getLong(8) + ","
                        + " ? )"; //NON-NLS

                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 9, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_fs_info_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_files_path
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_files_path"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO tsk_files_path (obj_id, path) VALUES ("
                        + value + ", '"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(2)) + "')"); //NON-NLS
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
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
                String sql = "INSERT INTO tsk_files (obj_id, fs_obj_id, attr_type, attr_id, name, meta_addr, meta_seq, type, has_layout, has_path, dir_type, meta_type, dir_flags, meta_flags, size, ctime, crtime, atime, mtime, mode, uid, gid, md5, known, parent_path) VALUES ("
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

                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 23, 1);
                populateNullableString(pst, inputResultSet, 25, 2);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_files_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_file_layout, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_file_layout"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                outputStatement.executeUpdate("INSERT INTO tsk_file_layout (obj_id, byte_start, byte_len, sequence) VALUES ("
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
            outputStatement = postgresqlConnection.createStatement();
            try {
                Statement check = postgresqlConnection.createStatement();
                ResultSet checkResult = check.executeQuery("SELECT * FROM tsk_db_info WHERE schema_ver=" + inputResultSet.getInt(1) + " AND tsk_ver=" + inputResultSet.getInt(2)); //NON-NLS
                if (!checkResult.isBeforeFirst()) { // only insert if it doesn't exist
                    outputStatement.executeUpdate("INSERT INTO tsk_db_info (schema_ver, tsk_ver) VALUES ("
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
                String sql = "INSERT INTO tag_names (tag_name_id, display_name, description, color) VALUES ("
                        + value + ","
                        + " ? ,'"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(3)) + "','"
                        + SleuthkitCase.escapeSingleQuotes(inputResultSet.getString(4)) + "')"; //NON-NLS

                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 2, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tag_names_tag_name_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // reports
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM reports"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO reports (report_id, path, crtime, src_module_name, report_name) VALUES ("
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
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE reports_report_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_artifacts
        biggestPK = Long.MIN_VALUE; // This table uses very large negative primary key values, so start at Long.MIN_VALUE
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_artifacts"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO blackboard_artifacts (artifact_id, obj_id, artifact_type_id) VALUES ("
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ")"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_artifacts_artifact_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_attributes, no primary key
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_attributes"); //NON-NLS

        while (inputResultSet.next()) {
            try {
                String sql = "INSERT INTO blackboard_attributes (artifact_id, artifact_type_id, source, context, attribute_type_id, value_type, value_byte, value_text, value_int32, value_int64, value_double) VALUES ("
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
                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
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
                String sql = "INSERT INTO tsk_vs_parts (obj_id, addr, start, length, descr, flags) VALUES ("
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ","
                        + inputResultSet.getLong(4) + ","
                        + " ? ,"
                        + inputResultSet.getInt(6) + ")"; //NON-NLS
                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 5, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_vs_parts_obj_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // tsk_vs_info
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM tsk_vs_info"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO tsk_vs_info (obj_id, vs_type, img_offset, block_size) VALUES ("
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
        numberingPK = postgresqlConnection.createStatement();
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
                String sql = "INSERT INTO tsk_files_derived (obj_id, derived_id, rederive) VALUES ("
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + " ? )"; //NON-NLS
                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 3, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
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
                String sql = "INSERT INTO tsk_files_derived_method (derived_id, tool_name, tool_version, other) VALUES ("
                        + value + ", '"
                        + inputResultSet.getString(2) + "','"
                        + inputResultSet.getString(3) + "',"
                        + " ? )"; //NON-NLS
                PreparedStatement pst = postgresqlConnection.prepareStatement(sql);
                populateNullableString(pst, inputResultSet, 4, 1);
                pst.executeUpdate();

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE tsk_files_derived_method_derived_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // content_tags
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM content_tags"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO content_tags (tag_id, obj_id, tag_name_id, comment, begin_byte_offset, end_byte_offset) VALUES ("
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ",'"
                        + inputResultSet.getString(4) + "',"
                        + inputResultSet.getLong(5) + ","
                        + inputResultSet.getLong(6) + ")"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE content_tags_tag_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        // blackboard_artifact_tags
        biggestPK = 0;
        inputStatement = sqliteConnection.createStatement();
        inputResultSet = inputStatement.executeQuery("SELECT * FROM blackboard_artifact_tags"); //NON-NLS

        while (inputResultSet.next()) {
            outputStatement = postgresqlConnection.createStatement();
            try {
                long value = inputResultSet.getLong(1);
                if (value > biggestPK) {
                    biggestPK = value;
                }
                outputStatement.executeUpdate("INSERT INTO blackboard_artifact_tags (tag_id, artifact_id, tag_name_id, comment) VALUES ("
                        + value + ","
                        + inputResultSet.getLong(2) + ","
                        + inputResultSet.getLong(3) + ",'"
                        + inputResultSet.getString(4) + "')"); //NON-NLS

            } catch (SQLException ex) {
                if (ex.getErrorCode() != 0) { // 0 if the entry already exists
                    throw new SQLException(ex);
                }
            }
        }
        numberingPK = postgresqlConnection.createStatement();
        numberingPK.execute("ALTER SEQUENCE blackboard_artifact_tags_tag_id_seq RESTART WITH " + (biggestPK + 1)); //NON-NLS

        sqliteConnection.close();
        postgresqlConnection.close();

        return dbName;
    }

    /**
     * Checks that our database name is unique. If it is not, attempts to add
     * numbers to it until it is unique. Gives up if it goes through all
     * positive integers without finding a unique name.
     *
     * @param db Database credentials
     * @param baseDbName proposed name of the database to check for collisions
     * @return name to use for the new database. Could be the name passed in.
     * @throws ClassNotFoundException
     * @throws SQLException
     * @throws Exception
     */
    private String deconflictDatabaseName(CaseDbConnectionInfo db, String baseDbName) throws ClassNotFoundException, SQLException, Exception {

        Class.forName("org.postgresql.Driver"); //NON-NLS
        Connection dbNameConnection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS

        int number = 1;
        boolean unique = false;
        String sanitizedDbName = baseDbName;
        if (sanitizedDbName.length() > MAX_DB_NAME_LENGTH) {
            sanitizedDbName = sanitizedDbName.substring(0, MAX_DB_NAME_LENGTH);
        }

        if (dbNameConnection != null) {
            while (unique == false) {
                Statement st = dbNameConnection.createStatement();
                ResultSet answer = st.executeQuery("SELECT datname FROM pg_catalog.pg_database WHERE LOWER(datname) LIKE LOWER('" + sanitizedDbName + "%')"); //NON-NLS

                if (!answer.next()) {
                    unique = true;
                } else {
                    // not unique. add numbers before dbName.
                    if (number == Integer.MAX_VALUE) {
                        // oops. it never became unique. give up.
                        throw new Exception(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.NonUniqueDatabaseName"));
                    }
                    sanitizedDbName = "_" + Integer.toString(number) + "_" + baseDbName; //NON-NLS

                    // Chop full db name to 63 characters (max for PostgreSQL)
                    if (sanitizedDbName.length() > MAX_DB_NAME_LENGTH) {
                        sanitizedDbName = sanitizedDbName.substring(0, MAX_DB_NAME_LENGTH);
                    }
                    ++number;
                }
            }
            dbNameConnection.close();
        } else {
            // Could be caused by database credentials, using user accounts that 
            // can not check if other databases exist, so allow it to continue
            log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.PotentiallyNonUniqueDatabaseName"));
        }

        return sanitizedDbName;
    }

    /**
     * Get the images from the old case and place them in the central
     * repository, if the user chose to.
     *
     * @param icd the Import Case Data
     * @throws IOException
     */
    private void copyImages(ImportCaseData icd) throws IOException {
        if (copySourceImages) {
            File imageSource = findInputFolder(icd); // Find the folder for the input images
            File imageDestination = new File(icd.getSpecificImageOutputFolder().toString());

            // If we can find the input images, copy if needed.
            if (imageSource.exists()) {
                FileUtils.copyDirectory(imageSource, imageDestination);

            } else {
                log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.UnableToCopySourceImages"));
            }
        }
    }

    /**
     * Fix up any paths in the database that refer to items that have moved.
     * Candidates include events.db, input images, reports, file paths, etc.
     *
     * @param icd the import case data for the current case
     * @param dbName the name of the database
     */
    private void fixPaths(ImportCaseData icd, String dbName) throws SQLException {
        /// Fix paths in reports, tsk_files_path, and tsk_image_names tables

        String input = icd.getSpecificImageInputFolder().toString();
        String output = icd.getSpecificImageOutputFolder().toString();

        Connection postgresqlConnection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/" + dbName, db.getUserName(), db.getPassword()); //NON-NLS
        if (postgresqlConnection != null) {
            String hostName = NetworkUtils.getLocalHostName();

            // add hostname to reports
            Statement updateStatement = postgresqlConnection.createStatement();
            updateStatement.executeUpdate("UPDATE reports SET path=CONCAT('" + hostName + "/', path) WHERE path IS NOT NULL AND path != ''"); //NON-NLS

            // add hostname to tsk_files_path
            updateStatement = postgresqlConnection.createStatement();
            updateStatement.executeUpdate("UPDATE tsk_files_path SET path=CONCAT('" + hostName + "\\', path) WHERE path IS NOT NULL AND path != ''"); //NON-NLS

            String caseName = TimeStampUtils.removeTimeStamp(icd.getOldCaseName()).toLowerCase();

            if (copySourceImages) {
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
            log(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.CanNotOpenDatabase"));
        }
    }

    /**
     * Return an integer from the ResultSet converted to String or NULL, by
     * checking ResultSet.wasNull()
     *
     * @param rs the ResultSet to work with
     * @param index the index into the ResultSet to work with
     * @return the proper value, the integer, or NULL
     * @throws SQLException
     */
    private String getNullableInt(ResultSet rs, int index) throws SQLException {
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
     * @param rs the ResultSet to work with
     * @param index the index into the ResultSet to work with
     * @return the proper value, the long, or NULL
     * @throws SQLException
     */
    private String getNullableLong(ResultSet rs, int index) throws SQLException {
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
     * @param pst the prepared statement
     * @param rs the ResultSet to work with
     * @param rsIndex index for the result set
     * @param psIndex index for the prepared statement
     * @throws SQLException
     */
    private void populateNullableString(PreparedStatement pst, ResultSet rs, int rsIndex, int psIndex) throws SQLException {
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
     * @param pst the prepared statement
     * @param rs the ResultSet to work with
     * @param rsIndex index for the result set
     * @param psIndex index for the prepared statement
     * @throws SQLException
     */
    private void populateNullableByteArray(PreparedStatement pst, ResultSet rs, int rsIndex, int psIndex) throws SQLException {
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
     * @param pst the prepared statement
     * @param rs the ResultSet to work with
     * @param rsIndex index for the result set
     * @param psIndex index for the prepared statement
     * @throws SQLException
     */
    private void populateNullableNumeric(PreparedStatement pst, ResultSet rs, int rsIndex, int psIndex) throws SQLException {
        double nullableNumeric = rs.getDouble(rsIndex);
        if (rs.wasNull()) {
            pst.setNull(psIndex, java.sql.Types.NULL);
        } else {
            pst.setDouble(psIndex, nullableNumeric);
        }
    }

    private class ImportCaseData {

        private Path specificCaseInputFolder;
        private Path specificCaseOutputFolder;
        private Path specificImageInputFolder;
        private Path specificImageOutputFolder;
        private String relativeCaseName;
        private String newCaseName;
        private String oldCaseName;

        public Path getSpecificCaseInputFolder() {
            return specificCaseInputFolder;
        }

        public Path getSpecificCaseOutputFolder() {
            return specificCaseOutputFolder;
        }

        public Path getSpecificImageInputFolder() {
            return specificImageInputFolder;
        }

        public Path getSpecificImageOutputFolder() {
            return specificImageOutputFolder;
        }

        public String getRelativeCaseName() {
            return relativeCaseName;
        }

        public String getOldCaseName() {
            return oldCaseName;
        }

        public String getNewCaseName() {
            return newCaseName;
        }

        public void setSpecificCaseInputFolder(Path caseInputFolder) {
            this.specificCaseInputFolder = caseInputFolder;
        }

        public void setSpecificCaseOutputFolder(Path caseOutputFolder) {
            this.specificCaseOutputFolder = caseOutputFolder;
        }

        public void setSpecificImageInputFolder(Path imageInputFolder) {
            this.specificImageInputFolder = imageInputFolder;
        }

        public void setSpecificImageOutputFolder(Path imageOutputFolder) {
            this.specificImageOutputFolder = imageOutputFolder;
        }

        public void setRelativeCaseName(Path input, Path aut) {
            this.relativeCaseName = input.relativize(aut).toString();
        }

        public void setOldCaseName(String oldCaseName) {
            this.oldCaseName = oldCaseName;
        }

        public void setNewCaseName(String newCaseName) {
            this.newCaseName = newCaseName;
        }

        public ImportCaseData(Path p) {
            this.specificCaseInputFolder = p;
            this.oldCaseName = p.getFileName().toString();
            this.specificCaseOutputFolder = null;
            this.specificImageInputFolder = null;
            this.specificImageOutputFolder = null;
            this.relativeCaseName = null;
            this.newCaseName = null;
        }
    }

    /**
     * This is the runnable's run method. It causes the iteration on all .aut
     * files in the path, calling processCase for each one.
     */
    @Override
    public void run() {
        openLog();
        boolean result = true;

        // iterate for .aut files
        FindDotAutFolders dotAutFolders = new FindDotAutFolders();
        try {
            Path walked = Files.walkFileTree(caseInputFolder, dotAutFolders);
        } catch (IOException ex) {
            log(ex.getMessage());
            result = false;
        }

        ArrayList<ImportCaseData> ableToProcess = new ArrayList<>();
        ArrayList<ImportCaseData> unableToProcess = new ArrayList<>();

        // validate we can convert this .aut file, one by one
        for (Path p : dotAutFolders.getTheList()) {
            ImportCaseData icd = new ImportCaseData(p);
            icd.setRelativeCaseName(caseInputFolder, p);
            if (canProcess(icd)) {
                ableToProcess.add(icd);
            } else {
                unableToProcess.add(icd);
            }
        }

        StringBuilder casesThatWillBeProcessed = new StringBuilder();
        StringBuilder casesThatWillNotBeProcessed = new StringBuilder();

        casesThatWillBeProcessed.append(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.WillImport")).append(SEP); // NON-NLS
        if (ableToProcess.isEmpty()) {
            casesThatWillBeProcessed.append(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.None")).append(SEP); // NON-NLS
        } else {
            for (ImportCaseData icd : ableToProcess) {
                casesThatWillBeProcessed.append(icd.getSpecificCaseInputFolder().toString()).append(SEP);
            }
        }

        if (!unableToProcess.isEmpty()) {
            casesThatWillNotBeProcessed.append(NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.WillNotImport")).append(SEP); // NON-NLS
            for (ImportCaseData icd : unableToProcess) {
                casesThatWillNotBeProcessed.append(icd.getSpecificCaseInputFolder().toString()).append(SEP);
            }
        }

        JTextArea jta = new JTextArea(casesThatWillBeProcessed.toString() + SEP + casesThatWillNotBeProcessed.toString());
        jta.setEditable(false);
        JScrollPane jsp = new JScrollPane(jta) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(700, 480);
            }
        };

        SwingUtilities.invokeLater(() -> {
            userAnswer = JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                    jsp,
                    NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.ContinueWithImport"), // NON-NLS
                    OK_CANCEL_OPTION);
            synchronized (threadWaitNotifyLock) {
                threadWaitNotifyLock.notify();
            }
        });

        synchronized (threadWaitNotifyLock) {
            try {
                threadWaitNotifyLock.wait();
            } catch (InterruptedException ex) {
                log("Unable to wait for user input");
            }
        }

        if (userAnswer == JOptionPane.OK_OPTION) {
            // feed .aut files in one by one for processing
            for (ImportCaseData icd : ableToProcess) {
                if (false == processCase(icd)) {
                    result = false;
                }
            }
            closeLog(result);
            if (notifyOnComplete != null) {
                notifyOnComplete.importDoneCallback(result, ""); // NON-NLS
            }
        } else {
            closeLog(result);
            if (notifyOnComplete != null) {
                notifyOnComplete.importDoneCallback(false, NbBundle.getMessage(SingleUserCaseImporter.class, "SingleUserCaseImporter.Cancelled")); // NON-NLS
            }
        }
    }

    /**
     * Open the case import log in the base output folder.
     *
     */
    private void openLog() {
        File temp = new File(caseOutputFolder);
        temp.mkdirs();
        File logFile = Paths.get(caseOutputFolder, CASE_IMPORT_LOG_FILE).toFile();
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, logFile.exists())), true);
        } catch (IOException ex) {
            writer = null;
            Logger.getLogger(SingleUserCaseImporter.class.getName()).log(Level.WARNING, "Error opening log file " + logFile.toString(), ex);
        }
        log("Starting batch processing of " + caseInputFolder.toString() + " to " + caseOutputFolder);
    }

    /**
     * Log a message to the case import log in the base output folder.
     *
     * @param message the message to log.
     */
    private void log(String message) {
        if (writer != null) {
            writer.println(String.format("%s %s", simpleDateFormat.format((Date.from(Instant.now()).getTime())), message)); //NON-NLS
        }
    }

    /**
     *
     * Close the case import log in the base output folder.
     *
     * @param result this informs the log if the end result was successful or
     * not. True if all was successful, false otherwise.
     */
    private void closeLog(boolean result) {
        log("Completed batch processing of " + caseInputFolder.toString() + " to " + caseOutputFolder + ". Batch processing result: " + ((result == true) ? "Success" : "Failure"));
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * This class extends SimpleFileVisitor to find all the cases to process
     * based upon .aut files.
     */
    private class FindDotAutFolders extends SimpleFileVisitor<Path> {

        private final ArrayList<Path> theList;

        public FindDotAutFolders() {
            this.theList = new ArrayList<>();
        }

        /**
         * Handle comparing .aut file and containing folder names without
         * timestamps on either one. It strips them off if they exist.
         *
         * @param directory the directory we are currently visiting.
         * @param attrs file attributes.
         *
         * @return Continue if we want to carry one, SKIP_SUBTREE if we've found
         * a .aut file, precluding searching any deeper into this folder.
         * @throws IOException
         */
        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
            // find all files that end in .aut
            File[] dotAutFiles = directory.toFile().listFiles((File dir, String name) -> name.toLowerCase().endsWith(DOTAUT));

            for (File specificFile : dotAutFiles) {
                // if it ends in a timestamp, strip it off
                String sanitizedCaseName = specificFile.getName();
                if (TimeStampUtils.endsWithTimeStamp(sanitizedCaseName)) {
                    sanitizedCaseName = sanitizedCaseName.substring(0, sanitizedCaseName.length() - TimeStampUtils.getTimeStampLength());
                }

                // if folder ends in a timestamp, strip it off
                String sanitizedFolderName = directory.getFileName().toString();
                if (TimeStampUtils.endsWithTimeStamp(sanitizedFolderName)) {
                    sanitizedFolderName = sanitizedFolderName.substring(0, sanitizedFolderName.length() - TimeStampUtils.getTimeStampLength());
                }

                // If file and folder match, found leaf node case
                if (sanitizedCaseName.toLowerCase().startsWith(sanitizedFolderName.toLowerCase())) {
                    theList.add(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            // If no matching .aut files, traverse subfolders
            return FileVisitResult.CONTINUE;
        }

        /**
         * This returns the list of folders we've found that need to be looked
         * at for possible import as multi-user cases.
         *
         * @return the theList
         */
        public ArrayList<Path> getTheList() {
            return theList;
        }
    }
}
