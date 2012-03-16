/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.SystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.datamodel.*;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;

/**
 * Class to store the case information
 */
public class Case {
    // change the CTL_MainWindow_Title in Bundle.properties as well if you change this value

    private static final String autopsyVer = "3.0.0b2"; // current version of autopsy. Change it when the version is changed
    private static final String appName = "Autopsy " + autopsyVer;
    /**
     * Property name that indicates the name of the current case has changed.
     * Fired with the case is renamed, and when the current case is
     * opened/closed/changed. The value is a String: the name of the case.
     * The empty string ("") is used for no open case.
     */
    public static final String CASE_NAME = "caseName";
    /**
     * Property name that indicates the number of the current case has changed.
     * Fired with the case number is changed.
     * The value is an int: the number of the case.
     * -1 is used for no case number set.
     */
    public static final String CASE_NUMBER = "caseNumber";
    /**
     * Property name that indicates the examiner of the current case has changed.
     * Fired with the case examiner is changed.
     * The value is a String: the name of the examiner.
     * The empty string ("") is used for no examiner set.
     */
    public static final String CASE_EXAMINER = "caseExaminer";
    /**
     * Property name that indicates a new image has been added to the current
     * case. The new value is the newly-added instance of Image, and the old
     * value is always null.
     */
    public static final String CASE_ADD_IMAGE = "addImages";
    /**
     * Property name that indicates an image has been removed from the current
     * case. The "old value" is the (int) image ID of the image that was
     * removed, the new value is the instance of the Image.
     */
    public static final String CASE_DEL_IMAGE = "removeImages";
    /**
     * Property name that indicates the currently open case has changed.
     * The new value is the instance of the opened Case, or null if there is no
     * open case.
     * The old value is the instance of the closed Case, or null if there was no
     * open case.
     */
    public static final String CASE_CURRENT_CASE = "currentCase";
    /**
     * Name for the property that determines whether to show the dialog at
     * startup
     */
    public static final String propStartup = "LBL_StartupDialog";
    // pcs is initialized in CaseListener constructor
    private static final PropertyChangeSupport pcs = new PropertyChangeSupport(Case.class);
    private String name;
    private int number;
    private String examiner;
    private String configFilePath;
    private XMLCaseManagement xmlcm;
    private SleuthkitCase db;
    // Track the current case (only set with changeCase() method)
    private static Case currentCase = null;

    /**
     * Constructor for the Case class
     */
    private Case(String name, int number, String examiner, String configFilePath, XMLCaseManagement xmlcm, SleuthkitCase db) {
        this.name = name;
        this.number = number;
        this.examiner = examiner;
        this.configFilePath = configFilePath;
        this.xmlcm = xmlcm;
        this.db = db;
    }

    /**
     * Gets the currently opened case, if there is one.
     *
     * @return the current open case
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
     * Updates the current case to the given case and fires off
     * the appropriate property-change
     * @param newCase the new current case
     */
    private static void changeCase(Case newCase) {

        Case oldCase = Case.currentCase;
        Case.currentCase = null;

        String oldCaseName = oldCase != null ? oldCase.name : "";

        pcs.firePropertyChange(CASE_CURRENT_CASE, oldCase, null);
        doCaseChange(null);

        pcs.firePropertyChange(CASE_NAME, oldCaseName, "");
        doCaseNameChange("");



        if (newCase != null) {
            currentCase = newCase;

            pcs.firePropertyChange(CASE_CURRENT_CASE, null, currentCase);
            doCaseChange(currentCase);

            pcs.firePropertyChange(CASE_NAME, "", currentCase.name);
            doCaseNameChange(currentCase.name);

            RecentCases.getInstance().addRecentCase(currentCase.name, currentCase.configFilePath); // update the recent cases
        }
    }

    AddImageProcess makeAddImageProcess(String timezone) {
        return this.db.makeAddImageProcess(timezone);
    }

    /**
     * Creates a new case (create the XML config file and the directory)
     * 
     * @param caseDir  the base directory where the configuration file is saved
     * @param caseName  the name of case
     * @param caseNumber the case number
     * @param examiner the examiner for this case
     */
    static void create(String caseDir, String caseName, int caseNumber, String examiner) throws Exception {
        Log.get(Case.class).log(Level.INFO, "Creating new case.\ncaseDir: {0}\ncaseName: {1}", new Object[]{caseDir, caseName});

        String configFilePath = caseDir + File.separator + caseName + ".aut";

        XMLCaseManagement xmlcm = new XMLCaseManagement();
        xmlcm.create(caseDir, caseName, examiner, caseNumber); // create a new XML config file
        xmlcm.writeFile();

        String dbPath = caseDir + File.separator + "autopsy.db";
        SleuthkitCase db = SleuthkitCase.newCase(dbPath);

        Case newCase = new Case(caseName, caseNumber, examiner, configFilePath, xmlcm, db);

        changeCase(newCase);
    }

    /**
     * Opens the existing case (open the XML config file)
     *
     * @param configFilePath  the path of the configuration file that's opened
     * @throws Exception
     */
    static void open(String configFilePath) throws Exception {
        Log.get(Case.class).log(Level.INFO, "Opening case.\nconfigFilePath: {0}", configFilePath);

        try {
            XMLCaseManagement xmlcm = new XMLCaseManagement();

            xmlcm.open(configFilePath); // open and load the config file to the document handler in the XML class
            xmlcm.writeFile(); // write any changes to the config file

            String caseName = xmlcm.getCaseName();
            int caseNumber = xmlcm.getCaseNumber();
            String examiner = xmlcm.getCaseExaminer();
            // if the caseName is "", case / config file can't be opened
            if (caseName.equals("")) {
                throw new Exception("Case name is blank.");
            }

            String caseDir = xmlcm.getCaseDirectory();
            String dbPath = caseDir + File.separator + "autopsy.db";
            SleuthkitCase db = SleuthkitCase.openCase(dbPath);

            Case openedCase = new Case(caseName, caseNumber, examiner, configFilePath, xmlcm, db);

            changeCase(openedCase);

        } catch (Exception ex) {
            // close the previous case if there's any
            CaseCloseAction closeCase = SystemAction.get(CaseCloseAction.class);
            closeCase.actionPerformed(null);
            throw ex;
        }
    }

    /**
     * Adds the image to the current case after it has been added to the DB
     *
     * @param imgPaths  the paths of the image that being added
     * @param imgId  the ID of the image that being added
     * @param timeZone  the timeZone of the image where it's added
     */
    Image addImage(String[] imgPaths, long imgId, String timeZone) throws Exception {
        Log.get(this.getClass()).log(Level.INFO, "Adding image to Case.  imgPaths: {0}  ID: {1} TimeZone: {2}", new Object[]{Arrays.toString(imgPaths), imgId, timeZone});

        try {
            xmlcm.addImage(imgPaths, imgId, timeZone); // add the image to the document handler in the XML class and to the config file
            xmlcm.writeFile(); // write any changes to the config file
            Image newImage = db.getImageById(imgId);
            pcs.firePropertyChange(CASE_ADD_IMAGE, null, newImage); // the new value is the instance of the image
            doAddImage();
            return newImage;
        } catch (Exception ex) {
            // throw an error here
            throw ex;
        }
    }

    /**
     * Get the underlying SleuthkitCase instance from the Sleuth Kit bindings
     * library.
     * @return
     */
    public SleuthkitCase getSleuthkitCase() {
        return this.db;
    }

    /**
     * Closes this case. This methods close the xml and clear all the fields.
     */
    void closeCase() throws Exception {
        changeCase(null);

        try {
            this.xmlcm.close(); // close the xmlcm
            this.db.close();
        } catch (Exception e) {
            throw new Exception("Error while trying to close the current case.", e);
        }
    }

    /**
     * Delete this case. This methods delete all folders and files of this case.
     */
    boolean deleteCase(File caseDir) {
        Log.get(this.getClass()).log(Level.FINE, "Deleting case.\ncaseDir: {0}", caseDir);

        try {

            xmlcm.close(); // close the xmlcm
            boolean result = deleteCaseDirectory(caseDir); // delete the directory

            RecentCases.getInstance().removeRecentCase(this.name, this.configFilePath); // remove it from the recent case
            Case.changeCase(null);
            return result;
        } catch (Exception ex) {
            // TODO: change to using exceptions instead of return value.
            // throw an error here
            Logger logger = Logger.getLogger(Case.class.getName());
            logger.log(Level.SEVERE, "Error deleting the current case.", ex);
            return false;
        }
    }

    /**
     * Updates the case name.
     *
     * @param oldCaseName  the old case name that wants to be updated
     * @param oldPath  the old path that wants to be updated
     * @param newCaseName  the new case name
     * @param newPath  the new path
     */
    void updateCaseName(String oldCaseName, String oldPath, String newCaseName, String newPath) throws Exception {
        try {
            xmlcm.setCaseName(newCaseName); // set the case
            name = newCaseName; // change the local value
            RecentCases.getInstance().updateRecentCase(oldCaseName, oldPath, newCaseName, newPath); // update the recent case

            pcs.firePropertyChange(CASE_NAME, oldCaseName, newCaseName);
            doCaseNameChange(newCaseName);

        } catch (Exception e) {
            throw new Exception("Error while trying to update the case name.", e);
        }
    }

    /**
     * Updates the case examiner
     * 
     * @param oldExaminer   the old examiner
     * @param newExaminer   the new examiner
     */
    void updateExaminer(String oldExaminer, String newExaminer) throws Exception {
        try {
            xmlcm.setCaseExaminer(newExaminer); // set the examiner
            examiner = newExaminer;

            pcs.firePropertyChange(CASE_EXAMINER, oldExaminer, newExaminer);
        } catch (Exception e) {
            throw new Exception("Error while trying to update the examiner.", e);
        }
    }

    /**
     * Updates the case number
     * 
     * @param oldCaseNumber the old case number
     * @param newCaseNumber the new case number
     */
    void updateCaseNumber(int oldCaseNumber, int newCaseNumber) throws Exception {
        try {
            xmlcm.setCaseNumber(newCaseNumber); // set the case number
            number = newCaseNumber;

            pcs.firePropertyChange(CASE_NUMBER, oldCaseNumber, newCaseNumber);
        } catch (Exception e) {
            throw new Exception("Error while trying to update the case number.", e);
        }
    }

// Not dealing with removing images for now.
//    /**
//     * Removes the image from this case
//     *
//     * @param givenID    the ID of the image to be removed
//     * @param givenPath  the path of the image to be removed
//     */
//    void removeImage(int givenID, String givenPath) throws Exception {
//        Log.get(this.getClass()).log(Level.FINE, "Removing image.\ngivenID: {0}\ngivenPath: {1}", new Object[] {givenID, givenPath});
//        
//        String[] tempPaths = xmlcm.getImageSet(givenID);
//        String tempDb = xmlcm.getImageSetDbPath(givenID);
//
//        if (tempPaths[0].equals(givenPath)) {
//            xmlcm.removeImageSet(givenID); // make the changes in the config file
//            try {
//                xmlcm.writeFile();
//            } catch (Exception ex) {
//                throw new Exception("Error while trying to remove the image from this case.", ex);
//            }
//
//            Image img = imageIdToData.get(givenID).image; // save the image that we want to delete temporarily
//
//            imageIdToData.remove(givenID);
//
//            pcs.firePropertyChange(CASE_DEL_IMAGE, givenID, img); // the old value is the image ID that removed, the new value is the instance of the image
//
//            img.getSleuthkit().closeConnection(); // to make sure that we close the connection
//
//            // need to remove the database as well??
//            File database = new File(tempDb);
//            boolean test = database.delete(); // delete the database of the image that we remove
//            if (!test) {
//                database.deleteOnExit(); // delete on exit if the delete is not successful
//            }
//        } else {
//            // throw an error here
//            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Couldn't remove image.", new Exception("Couldn't find the image in this case."));
//        }
//    }
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
     * @param givenPath  the given config file path
     */
    private void setConfigFilePath(String givenPath) {
        configFilePath = givenPath;
    }

    /**
     *  Get the config file path in the given path
     *
     * @return configFilePath  the path of the configuration file
     */
    String getConfigFilePath() {
        return configFilePath;
    }

    /**
     * Returns the current version of Autopsy
     * @return autopsyVer
     */
    public static String getAutopsyVersion() {
        return autopsyVer;
    }

    /**
     * Gets the application name
     * @return appName
     */
    public static String getAppName() {
        return appName;
    }

    /**
     * Gets the case name
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the case number
     * @return number
     */
    public int getNumber() {
        return number;
    }

    /**
     * Gets the Examiner name
     * @return examiner
     */
    public String getExaminer() {
        return examiner;
    }

    /**
     * Gets the case directory path
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
     * get the created date of this case
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
     * get the PropertyChangeSupport of this class
     * @return PropertyChangeSupport
     */
    public static PropertyChangeSupport getPropertyChangeSupport() {
        return pcs;
    }

    String[] getImagePaths(int imgID) {
        return xmlcm.getImageSet(imgID);
    }

    /**
     * get all the image id in this case
     * @return imageIDs
     */
    public int[] getImageIDs() {
        if (xmlcm == null) {
            return new int[0];
        } else {
            return xmlcm.getImageIDs();
        }
    }

    /**
     * Count the root objects.
     * @return The number of total root objects in this case.
     */
    public int getRootObjectsCount() {
        return getRootObjects().size();
    }

    /**
     * Get the data model Content objects in the root of this case's hierarchy.
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
     * @return time zones  the set of time zones
     */
    public Set<TimeZone> getTimeZone() {
        if (xmlcm == null) {
            return new HashSet<TimeZone>();
        } else {
            return xmlcm.getTimeZone();
        }
    }

    public static synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public static synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * convert the image Path to array string
     * @param imgPath   the image path
     * @return imgPath  the converted image path
     */
    public static String[] convertImgPath(String imgPath) {
        String[] result;
        String unsplitPaths = imgPath;
        int count = 0;

        for (int i = 0; i < unsplitPaths.length(); i++) {
            if (unsplitPaths.charAt(i) == '\"') {
                count++;
            }
        }

        if (count != 0) {
            result = new String[count / 2];
            int start = 0;
            int current = 0;
            for (int i = 0; i < count / 2; i++) {
                while (unsplitPaths.charAt(current) != '\"') {
                    current++;
                }
                start = current;
                current++;
                while (unsplitPaths.charAt(current) != '\"') {
                    current++;
                }
                result[i] = unsplitPaths.substring(start + 1, current);
                current++;
            }
        } else {
            result = new String[1];
            result[0] = unsplitPaths;
        }

        return result;
    }

    /**
     * Check if all the images from the given image path exist.
     * @param imgPaths  the image paths
     * @return isExist  whether the multiple paths exist
     */
    public static boolean checkMultiplePathExist(String[] imgPaths) {
        boolean result = false;
        int totalLength = imgPaths.length;
        if (totalLength > 0) {
            result = true;
            for (int i = 0; i < totalLength; i++) {
                if (new File(imgPaths[i]).exists()) {
                    result = result && true;
                } else {
                    result = result && false;
                }
            }
        }
        return result;
    }

    /**
     * Convert the Java timezone ID to the "formatted" string that can be
     * accepted by the C/C++ code.
     * Example: "America/New_York" converted to "EST5EDT", etc
     *
     * @param timezoneID
     * @return
     */
    public static String convertTimeZone(String timezoneID) {
        String result = "";

        TimeZone zone = TimeZone.getTimeZone(timezoneID);
        int offset = zone.getRawOffset() / 1000;
        int hour = offset / 3600;

        DateFormat dfm = new SimpleDateFormat("z");
        dfm.setTimeZone(zone);
        boolean hasDaylight = zone.useDaylightTime();
        String first = dfm.format(new Date(2010, 1, 1)).substring(0, 3); // make it only 3 letters code
        String second = dfm.format(new Date(2011, 6, 6)).substring(0, 3); // make it only 3 letters code
        int mid = hour * -1;
        result = first + Integer.toString(mid);
        if (hasDaylight) {
            result = result + second;
        }

        return result;
    }

    /* The methods below are used to manage the case directories (creating, checking, deleting, etc) */
    /**
     * to create the case directory
     * @param caseDir   the case directory path
     * @param caseName  the case name
     * @return boolean  whether the case directory is successfully created or not
     */
    static boolean createCaseDirectory(String caseDir, String caseName) {
        boolean result = false;

        try {
            result = (new File(caseDir)).mkdirs(); // create root case Directory

            // create the folders inside the case directory
            result = result && (new File(caseDir + File.separator + XMLCaseManagement.EXPORT_FOLDER_RELPATH)).mkdir()
                    && (new File(caseDir + File.separator + XMLCaseManagement.LOG_FOLDER_RELPATH)).mkdir()
                    && (new File(caseDir + File.separator + XMLCaseManagement.TEMP_FOLDER_RELPATH)).mkdir();

            return result;
        } catch (Exception e) {
            // TODO: change to use execptions instead of return values for error handling
            return false;
        }
    }

    /**
     * delete the given case directory
     * @param casePath  the case path
     * @return boolean  whether the case directory is successfully deleted or not
     */
    static boolean deleteCaseDirectory(File casePath) {
        if (casePath.exists()) {
            File[] files = casePath.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteCaseDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (casePath.delete());
    }

    /**
     * Invoke the creation of startup dialog window.
     */
    static public void invokeStartupDialog() {
        StartupWindow.getInstance().open();
    }

    /**
     * Call if there are no images in the case. Displays
     * a dialog offering to add one.
     */
    private static void noRootObjectsNotification() {
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
     * @param caseName the candidate String
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

    //case change helper
    private static void doCaseChange(Case toChangeTo) {
        if (toChangeTo != null) { // new case is open

            // clear the temp folder when the case is created / opened
            Case.clearTempFolder();

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
                // prompt user to add an image
                Case.noRootObjectsNotification();
            }
        } else { // case is closed
            // disable these menus
            CallableSystemAction.get(AddImageAction.class).setEnabled(false); // Add Image menu
            CallableSystemAction.get(CaseCloseAction.class).setEnabled(false); // Case Close menu
            CallableSystemAction.get(CasePropertiesAction.class).setEnabled(false); // Case Properties menu
            CallableSystemAction.get(CaseDeleteAction.class).setEnabled(false); // Delete Case menu

            // close all top components
            CoreComponentControl.closeCoreWindows();

            Frame f = WindowManager.getDefault().getMainWindow();
            f.setTitle(Case.getAppName()); // set the window name to just application name
        }


    }

    //case name change helper
    private static void doCaseNameChange(String newCaseName) {
        // update case name
        if (!newCaseName.equals("")) {
            Frame f = WindowManager.getDefault().getMainWindow();
            f.setTitle(newCaseName + " - " + Case.getAppName()); // set the window name to the new value
        }
    }

    //add image helper
    private void doAddImage() {
        // open all top components
        CoreComponentControl.openCoreWindows();
    }

    //delete image helper
    private void doDeleteImage() {
        // no more image left in this case
        if (currentCase.getRootObjectsCount() == 0) {
            // close all top components
            CoreComponentControl.closeCoreWindows();
        }
    }
}
