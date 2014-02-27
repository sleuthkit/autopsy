/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.awt.Frame;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.SystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.*;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;

/**
 * Stores all information for a given case. Only a single case can currently be
 * open at a time. Use getCurrentCase() to retrieve the object for the current
 * case.
 */
public class Case implements SleuthkitCase.ErrorObserver {

    private static final String autopsyVer = Version.getVersion(); // current version of autopsy. Change it when the version is changed
    private static String appName = null;
    
    /**
     * Name for the property that determines whether to show the dialog at
     * startup
     */
    public static final String propStartup = "LBL_StartupDialog";
    // pcs is initialized in CaseListener constructor
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(Case.class);

    /**
     * Events that the case module will fire. Event listeners can get the event
     * name by using String returned by toString() method on a specific event.
     */
    public enum Events {
        /**
         * Property name that indicates the name of the current case has
         * changed. When a case is opened, "old name" is empty string and "new
         * name" is the name. When a case is closed, "old name" is the case name
         * and "new name" is empty string. When a case is renamed, "old name"
         * has the original name and "new name" has the new name.
         */
        // @@@ BC: I propose that this is no longer called for case open/close.
        NAME,
        /**
         * Property name that indicates the number of the current case has
         * changed. Fired with the case number is changed. The value is an int:
         * the number of the case. -1 is used for no case number set.
         */
        NUMBER,
        /**
         * Property name that indicates the examiner of the current case has
         * changed. Fired with the case examiner is changed. The value is a
         * String: the name of the examiner. The empty string ("") is used for
         * no examiner set.
         */
        EXAMINER,
        /**
         * Property name that indicates a new data source (image, disk or local
         * file) has been added to the current case. The new value is the
         * newly-added instance of the new data source, and the old value is
         * always null.
         */
        DATA_SOURCE_ADDED,
        /**
         * Property name that indicates a data source has been removed from the
         * current case. The "old value" is the (int) content ID of the data
         * source that was removed, the new value is the instance of the data
         * source.
         */
        DATA_SOURCE_DELETED,
        /**
         * Property name that indicates the currently open case has changed.
         * When a case is opened, the "new value" will be an instance of the
         * opened Case object and the "old value" will be null. When a case is
         * closed, the "new value" will be null and the "old value" will be the
         * instance of the Case object being closed.
         */
        CURRENT_CASE;
    };

    private String name;
    private String number;
    private String examiner;
    private String configFilePath;
    private XMLCaseManagement xmlcm;
    private SleuthkitCase db;
    // Track the current case (only set with changeCase() method)
    private static Case currentCase = null;
    private Services services;
    private static final Logger logger = Logger.getLogger(Case.class.getName());
    static final String CASE_EXTENSION = "aut";
    static final String CASE_DOT_EXTENSION = "." + CASE_EXTENSION;

    /**
     * Constructor for the Case class
     */
    private Case(String name, String number, String examiner, String configFilePath, XMLCaseManagement xmlcm, SleuthkitCase db) {
        this.name = name;
        this.number = number;
        this.examiner = examiner;
        this.configFilePath = configFilePath;
        this.xmlcm = xmlcm;
        this.db = db;
        this.services = new Services(db);
        db.addErrorObserver(this);
    }

    /**
     * Gets the currently opened case, if there is one.
     *
     * @return the current open case
     *
     * @throws IllegalStateException if there is no case open.
     */
    public static Case getCurrentCase() {
        if (currentCase != null) {
            return currentCase;
        } else {
            throw new IllegalStateException("Can't get the current case; there is no case open!");
        }
    }

    /**
     * Check if case is currently open
     *
     * @return true if case is open
     */
    public static boolean isCaseOpen() {
        return currentCase != null;
    }

    /**
     * Updates the current case to the given case and fires off the appropriate
     * property-change
     *
     * @param newCase the new current case or null if case is being closed
     * 
     */
    private static void changeCase(Case newCase) {

        // close the existing case
        Case oldCase = Case.currentCase;
        Case.currentCase = null;
        if (oldCase != null) {
            doCaseChange(null); //closes windows, etc

            try {
                pcs.firePropertyChange(Events.CURRENT_CASE.toString(), oldCase, null);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
            doCaseNameChange("");
            
            try {
                pcs.firePropertyChange(Events.NAME.toString(), oldCase.name, "");
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        }

        if (newCase != null) {
            currentCase = newCase;

            
            try {
                pcs.firePropertyChange(Events.CURRENT_CASE.toString(), null, currentCase);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
            doCaseChange(currentCase);

            
            try {
                pcs.firePropertyChange(Events.NAME.toString(), "", currentCase.name);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
            doCaseNameChange(currentCase.name);

            RecentCases.getInstance().addRecentCase(currentCase.name, currentCase.configFilePath); // update the recent cases
        }
    }

    AddImageProcess makeAddImageProcess(String timezone, boolean processUnallocSpace, boolean noFatOrphans) {
        return this.db.makeAddImageProcess(timezone, processUnallocSpace, noFatOrphans);
    }

    /**
     * Creates a new case (create the XML config file and database)
     *
     * @param caseDir    The directory to store case data in. Will be created if
     *                   it doesn't already exist. If it exists, it should have
     *                   all of the needed sub dirs that createCaseDirectory()
     *                   will create.
     * @param caseName   the name of case
     * @param caseNumber the case number
     * @param examiner   the examiner for this case
     */
    public static void create(String caseDir, String caseName, String caseNumber, String examiner) throws CaseActionException {
        logger.log(Level.INFO, "Creating new case.\ncaseDir: {0}\ncaseName: {1}", new Object[]{caseDir, caseName});

        // create case directory if it doesn't already exist.
        if (new File(caseDir).exists() == false) {
            Case.createCaseDirectory(caseDir);
        }

        String configFilePath = caseDir + File.separator + caseName + CASE_DOT_EXTENSION;

        XMLCaseManagement xmlcm = new XMLCaseManagement();
        xmlcm.create(caseDir, caseName, examiner, caseNumber); // create a new XML config file
        xmlcm.writeFile();

        String dbPath = caseDir + File.separator + "autopsy.db";
        SleuthkitCase db = null;
        try {
            db = SleuthkitCase.newCase(dbPath);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error creating a case: " + caseName + " in dir " + caseDir, ex);
            throw new CaseActionException("Error creating a case: " + caseName + " in dir " + caseDir, ex);
        }

        Case newCase = new Case(caseName, caseNumber, examiner, configFilePath, xmlcm, db);

        changeCase(newCase);
    }

    /**
     * Opens the existing case (open the XML config file)
     *
     * @param configFilePath the path of the configuration file that's opened
     *
     * @throws CaseActionException
     */
    public static void open(String configFilePath) throws CaseActionException {
        logger.log(Level.INFO, "Opening case.\nconfigFilePath: {0}", configFilePath);

        try {
            XMLCaseManagement xmlcm = new XMLCaseManagement();

            xmlcm.open(configFilePath); // open and load the config file to the document handler in the XML class
            xmlcm.writeFile(); // write any changes to the config file

            String caseName = xmlcm.getCaseName();
            String caseNumber = xmlcm.getCaseNumber();
            String examiner = xmlcm.getCaseExaminer();
            // if the caseName is "", case / config file can't be opened
            if (caseName.equals("")) {
                throw new CaseActionException("Case name is blank.");
            }

            String caseDir = xmlcm.getCaseDirectory();
            String dbPath = caseDir + File.separator + "autopsy.db";
            SleuthkitCase db = SleuthkitCase.openCase(dbPath);
            if (null != db.getBackupDatabasePath()) {
                JOptionPane.showMessageDialog(null, "Updated case database schema.\nA backup copy of the database with the following path has been made:\n " + db.getBackupDatabasePath(), "Case Database Schema Update", JOptionPane.INFORMATION_MESSAGE);                
            }
            
            checkImagesExist(db);

            Case openedCase = new Case(caseName, caseNumber, examiner, configFilePath, xmlcm, db);

            changeCase(openedCase);

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error opening the case: ", ex);
            // close the previous case if there's any
            CaseCloseAction closeCase = SystemAction.get(CaseCloseAction.class);
            closeCase.actionPerformed(null);
            if (!configFilePath.endsWith(CASE_DOT_EXTENSION)) {
                throw new CaseActionException("Check that you selected the correct case file (usually with "
                        + CASE_DOT_EXTENSION + " extension)", ex);
            } else {
                throw new CaseActionException("Error opening the case", ex);
            }
        }
    }

    static Map<Long, String> getImagePaths(SleuthkitCase db) { //TODO: clean this up
        Map<Long, String> imgPaths = new HashMap<Long, String>();
        try {
            Map<Long, List<String>> imgPathsList = db.getImagePaths();
            for (Map.Entry<Long, List<String>> entry : imgPathsList.entrySet()) {
                if (entry.getValue().size() > 0) {
                    imgPaths.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting image paths", ex);
        }
        return imgPaths;
    }

    /**
     * Ensure that all image paths point to valid image files
     */
    private static void checkImagesExist(SleuthkitCase db) {
        Map<Long, String> imgPaths = getImagePaths(db);
        for (Map.Entry<Long, String> entry : imgPaths.entrySet()) {
            long obj_id = entry.getKey();
            String path = entry.getValue();
            boolean fileExists = (pathExists(path)
                    || driveExists(path));
            if (!fileExists) {
                int ret = JOptionPane.showConfirmDialog(null, appName + " has detected that one of the images associated with \n"
                        + "this case are missing. Would you like to search for them now?\n"
                        + "Previously, the image was located at:\n" + path
                        + "\nPlease note that you will still be able to browse directories and generate reports\n"
                        + "if you choose No, but you will not be able to view file content or run the ingest process.", "Missing Image", JOptionPane.YES_NO_OPTION);
                if (ret == JOptionPane.YES_OPTION) {
                   
                    MissingImageDialog.makeDialog(obj_id, db);
                    
                } else {
                    logger.log(Level.WARNING, "Selected image files don't match old files!");
                }

            }
        }
    }

    /**
     * Adds the image to the current case after it has been added to the DB
     * Sends out event and reopens windows if needed.
     *
     * @param imgPaths the paths of the image that being added
     * @param imgId    the ID of the image that being added
     * @param timeZone the timeZone of the image where it's added
     */
    @Deprecated
    public Image addImage(String imgPath, long imgId, String timeZone) throws CaseActionException {
        logger.log(Level.INFO, "Adding image to Case.  imgPath: {0}  ID: {1} TimeZone: {2}", new Object[]{imgPath, imgId, timeZone});

        try {
            Image newImage = db.getImageById(imgId);
           
                    try {
                        pcs.firePropertyChange(Events.DATA_SOURCE_ADDED.toString(), null, newImage); // the new value is the instance of the image
                    }
                    catch (Exception e) {
                        logger.log(Level.SEVERE, "Case listener threw exception", e);
                        MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
                    }
            CoreComponentControl.openCoreWindows();
            return newImage;
        } catch (Exception ex) {
            throw new CaseActionException("Error adding image to the case", ex);
        }
    }

    /**
     * Finishes adding new local data source to the case Sends out event and
     * reopens windows if needed.
     *
     * @param newDataSource new data source added
     */
   @Deprecated
    void addLocalDataSource(Content newDataSource) {
        
        notifyNewDataSource(newDataSource);
    }

    /**
     * Notifies the UI that a new data source has been added.
     * 
     *
     * @param newDataSource new data source added
     */
    void notifyNewDataSource(Content newDataSource) {
        
        try {
            pcs.firePropertyChange(Events.DATA_SOURCE_ADDED.toString(), null, newDataSource);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Case threw exception", e);
            MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
        }
        CoreComponentControl.openCoreWindows();
    }
    
    /**
     * @return The Services object for this case.
     */
    public Services getServices() {
        return services;
    }

    /**
     * Get the underlying SleuthkitCase instance from the Sleuth Kit bindings
     * library.
     *
     * @return
     */
    public SleuthkitCase getSleuthkitCase() {
        return this.db;
    }

    /**
     * Closes this case. This methods close the xml and clear all the fields.
     */
    public void closeCase() throws CaseActionException {
        changeCase(null);

        try {
            services.close();
            this.xmlcm.close(); // close the xmlcm
            this.db.close();
        } catch (Exception e) {
            throw new CaseActionException("Error while trying to close the current case.", e);
        }
    }

    /**
     * Delete this case. This methods delete all folders and files of this case.
     *
     * @param caseDir case dir to delete
     *
     * @throws CaseActionException exception throw if case could not be deleted
     */
    void deleteCase(File caseDir) throws CaseActionException {
        logger.log(Level.INFO, "Deleting case.\ncaseDir: {0}", caseDir);

        try {

            xmlcm.close(); // close the xmlcm
            boolean result = deleteCaseDirectory(caseDir); // delete the directory

            RecentCases.getInstance().removeRecentCase(this.name, this.configFilePath); // remove it from the recent case
            Case.changeCase(null);
            if (result == false) {
                throw new CaseActionException("Error deleting the case dir: " + caseDir);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error deleting the current case dir: " + caseDir, ex);
            throw new CaseActionException("Error deleting the case dir: " + caseDir, ex);
        }
    }

    /**
     * Updates the case name.
     *
     * @param oldCaseName the old case name that wants to be updated
     * @param oldPath     the old path that wants to be updated
     * @param newCaseName the new case name
     * @param newPath     the new path
     */
    void updateCaseName(String oldCaseName, String oldPath, String newCaseName, String newPath) throws CaseActionException {
        try {
            xmlcm.setCaseName(newCaseName); // set the case
            name = newCaseName; // change the local value
            RecentCases.getInstance().updateRecentCase(oldCaseName, oldPath, newCaseName, newPath); // update the recent case 
            try {
                pcs.firePropertyChange(Events.NAME.toString(), oldCaseName, newCaseName);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
            doCaseNameChange(newCaseName);

        } catch (Exception e) {
            throw new CaseActionException("Error while trying to update the case name.", e);
        }
    }

    /**
     * Updates the case examiner
     *
     * @param oldExaminer the old examiner
     * @param newExaminer the new examiner
     */
    void updateExaminer(String oldExaminer, String newExaminer) throws CaseActionException {
        try {
            xmlcm.setCaseExaminer(newExaminer); // set the examiner
            examiner = newExaminer;      
            try {
                pcs.firePropertyChange(Events.EXAMINER.toString(), oldExaminer, newExaminer);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        } catch (Exception e) {
            throw new CaseActionException("Error while trying to update the examiner.", e);
        }
    }

    /**
     * Updates the case number
     *
     * @param oldCaseNumber the old case number
     * @param newCaseNumber the new case number
     */
    void updateCaseNumber(String oldCaseNumber, String newCaseNumber) throws CaseActionException {
        try {
            xmlcm.setCaseNumber(newCaseNumber); // set the case number
            number = newCaseNumber;
    
            try {
                pcs.firePropertyChange(Events.NUMBER.toString(), oldCaseNumber, newCaseNumber);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Case listener threw exception", e);
                MessageNotifyUtil.Notify.show("Module Error", "A module caused an error listening to Case updates. See log to determine which module. Some data could be incomplete.", MessageNotifyUtil.MessageType.ERROR);
            }
        } catch (Exception e) {
            throw new CaseActionException("Error while trying to update the case number.", e);
        }
    }

    /**
     * Checks whether there is a current case open.
     *
     * @return True if a case is open.
     */
    public static boolean existsCurrentCase() {
        return currentCase != null;
    }

    /**
     * Uses the given path to store it as the configuration file path
     *
     * @param givenPath the given config file path
     */
    private void setConfigFilePath(String givenPath) {
        configFilePath = givenPath;
    }

    /**
     * Get the config file path in the given path
     *
     * @return configFilePath the path of the configuration file
     */
    String getConfigFilePath() {
        return configFilePath;
    }

    /**
     * Returns the current version of Autopsy
     *
     * @return autopsyVer
     */
    public static String getAutopsyVersion() {
        return autopsyVer;
    }

    /**
     * Gets the application name
     *
     * @return appName
     */
    public static String getAppName() {
        if ((appName == null) || appName.equals("")) {
            appName = WindowManager.getDefault().getMainWindow().getTitle();
        }
        return appName;
    }

    /**
     * Gets the case name
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the case number
     *
     * @return number
     */
    public String getNumber() {
        return number;
    }

    /**
     * Gets the Examiner name
     *
     * @return examiner
     */
    public String getExaminer() {
        return examiner;
    }

    /**
     * Gets the case directory path
     *
     * @return caseDirectoryPath
     */
    public String getCaseDirectory() {
        if (xmlcm == null) {
            return "";
        } else {
            return xmlcm.getCaseDirectory();
        }
    }

    /**
     * Gets the full path to the temp directory of this case
     *
     * @return tempDirectoryPath
     */
    public String getTempDirectory() {
        if (xmlcm == null) {
            return "";
        } else {
            return xmlcm.getTempDir();
        }
    }

    /**
     * Gets the full path to the cache directory of this case
     *
     * @return cacheDirectoryPath
     */
    public String getCacheDirectory() {
        if (xmlcm == null) {
            return "";
        } else {
            return xmlcm.getCacheDir();
        }
    }

    /**
     * Gets the full path to the export directory of this case
     *
     * @return export DirectoryPath
     */
    public String getExportDirectory() {
        if (xmlcm == null) {
            return "";
        } else {
            return xmlcm.getExportDir();
        }
    }

    /**
     * get the created date of this case
     *
     * @return case creation date
     */
    public String getCreatedDate() {
        if (xmlcm == null) {
            return "";
        } else {
            return xmlcm.getCreatedDate();
        }
    }

    /**
     * Get absolute module output directory path where modules should save their
     * permanent data The directory is a subdirectory of this case dir.
     *
     * @return absolute path to the module output dir
     */
    public String getModulesOutputDirAbsPath() {
        return this.getCaseDirectory() + File.separator + getModulesOutputDirRelPath();
    }

    /**
     * Get relative (with respect to case dir) module output directory path
     * where modules should save their permanent data The directory is a
     * subdirectory of this case dir.
     *
     * @return relative path to the module output dir
     */
    public static String getModulesOutputDirRelPath() {
        return "ModuleOutput";
    }

    /**
     * get the PropertyChangeSupport of this class
     *
     * @return PropertyChangeSupport
     */
    public static PropertyChangeSupport getPropertyChangeSupport() {
        return pcs;
    }

    String getImagePaths(Long imgID) {
        return getImagePaths(db).get(imgID);
    }

    /**
     * get all the image id in this case
     *
     * @return imageIDs
     */
    public Long[] getImageIDs() {
        Set<Long> ids = getImagePaths(db).keySet();
        return ids.toArray(new Long[ids.size()]);
    }

    public List<Image> getImages() throws TskCoreException {
        return db.getImages();
    }

    /**
     * Count the root objects.
     *
     * @return The number of total root objects in this case.
     */
    public int getRootObjectsCount() {
        return getRootObjects().size();
    }

    /**
     * Get the data model Content objects in the root of this case's hierarchy.
     *
     * @return a list of the root objects
     */
    public List<Content> getRootObjects() {
        try {
            return db.getRootObjects();
        } catch (TskException ex) {
            throw new RuntimeException("Error getting root objects.", ex);
        }
    }

    /**
     * Gets the time zone(s) of the image(s) in this case.
     *
     * @return time zones the set of time zones
     */
    public Set<TimeZone> getTimeZone() {
        Set<TimeZone> timezones = new HashSet<TimeZone>();
        for (Content c : getRootObjects()) {
            try {
                final Image image = c.getImage();
                if (image != null) {
                    timezones.add(TimeZone.getTimeZone(image.getTimeZone()));
                }
            } catch (TskException ex) {
                logger.log(Level.INFO, "Error getting time zones", ex);
            }
        }
        return timezones;
    }

    public static synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public static synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Check if image from the given image path exists.
     *
     * @param imgPath the image path
     *
     * @return isExist whether the path exists
     */
    public static boolean pathExists(String imgPath) {
        return new File(imgPath).isFile();
    }
    /**
     * Does the given string refer to a physical drive?
     */
    private static final String pdisk = "\\\\.\\physicaldrive";
    private static final String dev = "/dev/";

    static boolean isPhysicalDrive(String path) {
        return path.toLowerCase().startsWith(pdisk)
                || path.toLowerCase().startsWith(dev);
    }

    /**
     * Does the given string refer to a local drive / partition?
     */
    static boolean isPartition(String path) {
        return path.toLowerCase().startsWith("\\\\.\\")
                && path.toLowerCase().endsWith(":");
    }

    /**
     * Does the given drive path exist?
     *
     * @param path to drive
     *
     * @return true if the drive exists, false otherwise
     */
    static boolean driveExists(String path) {
        // Test the drive by reading the first byte and checking if it's -1
        BufferedInputStream br = null;
        try {
            File tmp = new File(path);
            br = new BufferedInputStream(new FileInputStream(tmp));
            int b = br.read();
            if (b != -1) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     * Convert the Java timezone ID to the "formatted" string that can be
     * accepted by the C/C++ code. Example: "America/New_York" converted to
     * "EST5EDT", etc
     *
     * @param timezoneID
     *
     * @return
     */
    public static String convertTimeZone(String timezoneID) {
        String result = "";

        TimeZone zone = TimeZone.getTimeZone(timezoneID);
        int offset = zone.getRawOffset() / 1000;
        int hour = offset / 3600;
        int min = (offset % 3600) / 60;

        DateFormat dfm = new SimpleDateFormat("z");
        dfm.setTimeZone(zone);
        boolean hasDaylight = zone.useDaylightTime();
        String first = dfm.format(new GregorianCalendar(2010, 1, 1).getTime()).substring(0, 3); // make it only 3 letters code
        String second = dfm.format(new GregorianCalendar(2011, 6, 6).getTime()).substring(0, 3); // make it only 3 letters code
        int mid = hour * -1;
        result = first + Integer.toString(mid);
        if (min != 0) {
            result = result + ":" + Integer.toString(min);
        }
        if (hasDaylight) {
            result = result + second;
        }

        return result;
    }

    /*
     * The methods below are used to manage the case directories (creating,
     * checking, deleting, etc)
     */
    /**
     * to create the case directory
     *
     * @param caseDir  Path to the case directory (typically base + case name)
     * @param caseName the case name (used only for error messages)
     *
     * @throws CaseActionException throw if could not create the case dir
     * @Deprecated
     */
    static void createCaseDirectory(String caseDir, String caseName) throws CaseActionException {
        createCaseDirectory(caseDir);

    }

    /**
     * Create the case directory and its needed subfolders.
     *
     * @param caseDir Path to the case directory (typically base + case name)
     *
     * @throws CaseActionException throw if could not create the case dir
     */
    static void createCaseDirectory(String caseDir) throws CaseActionException {

        File caseDirF = new File(caseDir);
        if (caseDirF.exists()) {
            if (caseDirF.isFile()) {
                throw new CaseActionException("Cannot create case dir, already exists and is not a directory: " + caseDir);
            } else if (!caseDirF.canRead() || !caseDirF.canWrite()) {
                throw new CaseActionException("Cannot create case dir, already exists and cannot read/write: " + caseDir);
            }
        }

        try {
            boolean result = (caseDirF).mkdirs(); // create root case Directory
            if (result == false) {
                throw new CaseActionException("Cannot create case dir: " + caseDir);
            }

            // create the folders inside the case directory
            result = result && (new File(caseDir + File.separator + XMLCaseManagement.EXPORT_FOLDER_RELPATH)).mkdir()
                    && (new File(caseDir + File.separator + XMLCaseManagement.LOG_FOLDER_RELPATH)).mkdir()
                    && (new File(caseDir + File.separator + XMLCaseManagement.TEMP_FOLDER_RELPATH)).mkdir()
                    && (new File(caseDir + File.separator + XMLCaseManagement.CACHE_FOLDER_RELPATH)).mkdir();

            if (result == false) {
                throw new CaseActionException("Could not create case directory: " + caseDir);
            }

            final String modulesOutDir = caseDir + File.separator + getModulesOutputDirRelPath();
            result = new File(modulesOutDir).mkdir();
            if (result == false) {
                throw new CaseActionException("Could not create modules output directory: " + modulesOutDir);
            }

        } catch (Exception e) {
            throw new CaseActionException("Could not create case directory: " + caseDir, e);
        }
    }

    /**
     * delete the given case directory
     *
     * @param casePath the case path
     *
     * @return boolean whether the case directory is successfully deleted or not
     */
    static boolean deleteCaseDirectory(File casePath) {
        logger.log(Level.INFO, "Deleting case directory: " + casePath.getAbsolutePath());
        return FileUtil.deleteDir(casePath);
    }

    /**
     * Invoke the creation of startup dialog window.
     */
    static public void invokeStartupDialog() {
        StartupWindowProvider.getInstance().open();
    }

    /**
     * Call if there are no images in the case. Displays a dialog offering to
     * add one.
     */
    private static void runAddImageAction() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final AddImageAction action = Lookup.getDefault().lookup(AddImageAction.class);
                action.actionPerformed(null);
            }
        });
    }

    /**
     * Checks if a String is a valid case name
     *
     * @param caseName the candidate String
     *
     * @return true if the candidate String is a valid case name
     */
    static public boolean isValidName(String caseName) {
        return !(caseName.contains("\\") || caseName.contains("/") || caseName.contains(":")
                || caseName.contains("*") || caseName.contains("?") || caseName.contains("\"")
                || caseName.contains("<") || caseName.contains(">") || caseName.contains("|"));
    }

    static private void clearTempFolder() {
        File tempFolder = new File(currentCase.getTempDirectory());
        if (tempFolder.isDirectory()) {
            File[] files = tempFolder.listFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteCaseDirectory(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
        }
    }

    /**
     * Check for existence of certain case sub dirs and create them if needed.
     *
     * @param openedCase
     */
    private static void checkSubFolders(Case openedCase) {
        String modulesOutputDir = openedCase.getModulesOutputDirAbsPath();
        File modulesOutputDirF = new File(modulesOutputDir);
        if (!modulesOutputDirF.exists()) {
            logger.log(Level.INFO, "Creating modules output dir for the case.");

            try {
                if (!modulesOutputDirF.mkdir()) {
                    logger.log(Level.SEVERE, "Error creating modules output dir for the case, dir: " + modulesOutputDir);
                }
            } catch (SecurityException e) {
                logger.log(Level.SEVERE, "Error creating modules output dir for the case, dir: " + modulesOutputDir, e);
            }
        }
    }

    //case change helper
    private static void doCaseChange(Case toChangeTo) {
        logger.log(Level.INFO, "Changing Case to: " + toChangeTo);
        if (toChangeTo != null) { // new case is open

            // clear the temp folder when the case is created / opened
            Case.clearTempFolder();
            checkSubFolders(toChangeTo);

            // enable these menus
            CallableSystemAction.get(AddImageAction.class).setEnabled(true);
            CallableSystemAction.get(CaseCloseAction.class).setEnabled(true);
            CallableSystemAction.get(CasePropertiesAction.class).setEnabled(true);
            CallableSystemAction.get(CaseDeleteAction.class).setEnabled(true); // Delete Case menu

            if (toChangeTo.getRootObjectsCount() > 0) {
                // open all top components
                CoreComponentControl.openCoreWindows();
            } else {
                // close all top components
                CoreComponentControl.closeCoreWindows();
            }
        } else { // case is closed
            // close all top components first
            CoreComponentControl.closeCoreWindows();

            // disable these menus
            CallableSystemAction.get(AddImageAction.class).setEnabled(false); // Add Image menu
            CallableSystemAction.get(CaseCloseAction.class).setEnabled(false); // Case Close menu
            CallableSystemAction.get(CasePropertiesAction.class).setEnabled(false); // Case Properties menu
            CallableSystemAction.get(CaseDeleteAction.class).setEnabled(false); // Delete Case menu

            //clear pending notifications
            MessageNotifyUtil.Notify.clear();


            Frame f = WindowManager.getDefault().getMainWindow();
            f.setTitle(Case.getAppName()); // set the window name to just application name

            //try to force gc to happen
            System.gc();
            System.gc();
        }

        //log memory usage after case changed
        logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());


    }

    //case name change helper
    private static void doCaseNameChange(String newCaseName) {
        // update case name
        if (!newCaseName.equals("")) {
            Frame f = WindowManager.getDefault().getMainWindow();
            f.setTitle(newCaseName + " - " + Case.getAppName()); // set the window name to the new value
        }
    }

    //delete image helper
    private void doDeleteImage() {
        // no more image left in this case
        if (currentCase.getRootObjectsCount() == 0) {
            // close all top components
            CoreComponentControl.closeCoreWindows();
        }
    }

    @Override
    public void receiveError(String context, String errorMessage) {
        MessageNotifyUtil.Notify.error(context, errorMessage);
    }
}
