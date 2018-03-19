/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JMenuItem;
import org.apache.commons.lang.ArrayUtils;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * The action in this class is to clear the list of "Recent Cases". The
 * constructor is called when the autopsy is running. All the method to create
 * and modify the properties file are within this class
 */
final class RecentCases extends CallableSystemAction implements Presenter.Menu {

    private static final long serialVersionUID = 1L;
    private static final int LENGTH = 6;
    private static final String NAME_PROP_KEY = "LBL_RecentCase_Name"; //NON-NLS
    private static final String PATH_PROP_KEY = "LBL_RecentCase_Path"; //NON-NLS
    private static final RecentCase BLANK_RECENTCASE = new RecentCase("", "");
    private final static RecentCases instance = new RecentCases();
    private final Deque<RecentCase> recentCases; // newest case is last case

    /**
     * Gets the instance of the RecentCases singleton.
     *
     *
     * @return INSTANCE the RecentCases singleton
     */
    static public RecentCases getInstance() {
        instance.refreshRecentCases();
        return instance;
    }

    /**
     * the constructor
     */
    private RecentCases() {

        for (int i = 0; i < LENGTH; i++) {
            try {
                if (ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, nameKey(i)) == null) {
                    ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, nameKey(i), "");
                }
                if (ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, pathKey(i)) == null) {
                    ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, pathKey(i), "");
                }
            } catch (Exception e) {

            }
        }

        // Load recentCases from properties
        recentCases = new LinkedList<>();

        for (int i = 0; i < LENGTH; i++) {
            final RecentCase rc = new RecentCase(getName(i), getPath(i));
            if (!rc.equals(BLANK_RECENTCASE)) {
                recentCases.add(rc);
            }
        }

        refreshRecentCases();
    }

    private static void validateCaseIndex(int i) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(RecentCases.class, "RecentCases.exception.caseIdxOutOfRange.msg", i));
        }
    }

    private static String nameKey(int i) {
        validateCaseIndex(i);
        return NAME_PROP_KEY + Integer.toString(i + 1);
    }

    private static String pathKey(int i) {
        validateCaseIndex(i);
        return PATH_PROP_KEY + Integer.toString(i + 1);
    }

    private String getName(int i) {
        try {
            return ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, nameKey(i));
        } catch (Exception e) {
            return null;
        }
    }

    private String getPath(int i) {
        try {
            return ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, pathKey(i));
        } catch (Exception e) {
            return null;
        }
    }

    private void setName(int i, String name) {
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, nameKey(i), name);
    }

    private void setPath(int i, String path) {
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, pathKey(i), path);
    }

    private void setRecentCase(int i, RecentCase rc) {
        setName(i, rc.name);
        setPath(i, rc.path);
    }

    private static final class RecentCase {

        String name, path;

        /**
         * @param name The case name or "" if a blank placeholder case
         * @param path A normalized path (via FileUtil.normalizePath(path)) or
         *             "" if a blank placeholder case
         */
        private RecentCase(String name, String path) {
            this.name = name;
            this.path = path;
        }

        /**
         * Used when creating RecentCases with external data. The path must be
         * normalized so that duplicate cases always have the same path.
         *
         * @param name       The case name.
         * @param unsafePath The (potentially un-normalized) case path.
         *
         * @return The created RecentCase.s
         */
        static RecentCase createSafe(String name, String unsafePath) {
            return new RecentCase(name, FileUtil.normalizePath(unsafePath));
        }

        /**
         * Does this case exist or was it manually deleted or moved?
         *
         * @return true if the case exists, false otherwise
         */
        boolean exists() {
            return !(name.isEmpty() || path.isEmpty() || !new File(path).exists());
        }

        // netbeans autogenerated hashCode
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 13 * hash + (this.name != null ? this.name.hashCode() : 0);
            hash = 13 * hash + (this.path != null ? this.path.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RecentCase other = (RecentCase) obj;
            if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
                return false;
            }
            if ((this.path == null) ? (other.path != null) : !this.path.equals(other.path)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Refresh the current list of cases, removing any cases that no longer
     * exist.
     */
    private void refreshRecentCases() {
        List<RecentCase> toDelete = new ArrayList<>();
        for (RecentCase rc : recentCases) {
            if (!rc.exists()) {
                toDelete.add(rc);
            }
        }
        for (RecentCase deleteMe : toDelete) {
            removeRecentCase(deleteMe.name, deleteMe.path);
        }
    }

    private void storeRecentCases() throws IOException {
        int i = 0;

        // store however many recent cases exist
        for (RecentCase rc : recentCases) {
            setRecentCase(i, rc);
            i++;
        }

        // set the rest to blanks
        while (i < LENGTH) {
            setRecentCase(i, BLANK_RECENTCASE);
            i++;
        }

    }

    /**
     * Gets a menu item that can present this action in a JMenu.
     *
     * @return menuItem the representation menu item for this action
     */
    @Override
    public JMenuItem getMenuPresenter() {
        return new UpdateRecentCases();
    }

    /**
     * This action is used to clear all the recent cases menu options.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        UpdateRecentCases.setHasRecentCase(false);

        recentCases.clear();

        try {
            // clear the properties file
            storeRecentCases();
        } catch (IOException ex) {
            Logger.getLogger(RecentCases.class.getName()).log(Level.WARNING, "Error: Could not clear the properties file.", ex); //NON-NLS
        }
    }

    private void addRecentCase(RecentCase rc) {
        // remove the case if it's already in the list
        recentCases.remove(rc);

        // make space if it's needed
        if (recentCases.size() == LENGTH) {
            recentCases.remove();
        }

        recentCases.add(rc);
    }

    /**
     * Adds a recent case to the top of the list. If the case is already in the
     * list, it will be removed before the new entry is added.
     *
     * @param name       the name of the recent case to be added
     * @param unsafePath the (potentially un-normalized) path of the case config
     *                   file
     */
    public void addRecentCase(String name, String unsafePath) {
        RecentCase rc = RecentCase.createSafe(name, unsafePath);

        addRecentCase(rc);

        this.getMenuPresenter().setVisible(true); // invoke the contructor again

        try {
            storeRecentCases();
        } catch (IOException ex) {
            Logger.getLogger(RecentCases.class.getName()).log(Level.WARNING, "Error: Could not update the properties file.", ex); //NON-NLS
        }
    }

    /**
     * This method is used to update the name and path of a RecentCase.
     *
     * @param oldName the old recent case name
     * @param oldPath the old recent case config file path
     * @param newName the new recent case name
     * @param newPath the new recent case config file path
     *
     * @throws Exception
     */
    public void updateRecentCase(String oldName, String oldPath, String newName, String newPath) throws Exception {
        RecentCase oldRc = RecentCase.createSafe(oldName, oldPath);
        RecentCase newRc = RecentCase.createSafe(newName, newPath);

        // remove all instances of the old recent case
        recentCases.removeAll(Arrays.asList(oldRc));

        addRecentCase(newRc);

        this.getMenuPresenter().setVisible(true); // invoke the contructor again

        try {
            storeRecentCases();
        } catch (IOException ex) {
            Logger.getLogger(RecentCases.class.getName()).log(Level.WARNING, "Error: Could not update the properties file.", ex); //NON-NLS
        }
    }

    /**
     * Gets the total number of recent cases
     *
     * @return total total number of recent cases
     */
    public int getTotalRecentCases() {
        return recentCases.size();
    }

    /**
     * This method is used to remove the selected name and path of the
     * RecentCase
     *
     * @param name the case name to be removed from the recent case
     * @param path the config file path to be removed from the recent case
     */
    public void removeRecentCase(String name, String path) {
        RecentCase rc = RecentCase.createSafe(name, path);

        // remove all instances of the old recent case
        recentCases.removeAll(Arrays.asList(rc));

        this.getMenuPresenter().setVisible(true); // invoke the contructor again

        // write the properties file
        try {
            storeRecentCases();
        } catch (IOException ex) {
            Logger.getLogger(RecentCases.class.getName()).log(Level.WARNING, "Error: Could not update the properties file.", ex); //NON-NLS
        }
    }

    /**
     * Gets the recent case names.
     *
     * @return caseNames An array String[LENGTH - 1], newest case first, with any
     * extra spots filled with ""
     */
    public String[] getRecentCaseNames() {
        String[] caseNames = new String[LENGTH];

        Iterator<RecentCase> mostRecentFirst = recentCases.descendingIterator();
        int i = 0;
        String currentCaseName = null;
        try {
            currentCaseName = Case.getOpenCase().getDisplayName();
        } catch (NoCurrentCaseException ex) {
            // in case there is no current case.
        }

        while (mostRecentFirst.hasNext()) {
            String name = mostRecentFirst.next().name;
            if ((currentCaseName != null && !name.equals(currentCaseName)) || currentCaseName == null) {
                // exclude currentCaseName from the caseNames[]
                caseNames[i] = name;
                i++;
            }
        }

        while (i < caseNames.length) {
            caseNames[i] = "";
            i++;
        }

        // return last 5 case names
        return (String[]) ArrayUtils.subarray(caseNames, 0, LENGTH - 1);
    }

    /**
     * Gets the recent case paths.
     *
     * @return casePaths An array String[LENGTH - 1], newest case first, with any
     * extra spots filled with ""
     */
    public String[] getRecentCasePaths() {
        String[] casePaths = new String[LENGTH];
        String currentCasePath = null;
        try {
            currentCasePath = Case.getOpenCase().getMetadata().getFilePath().toString();
        } catch (NoCurrentCaseException ex) {
            /*
             * There may be no current case.
             */
        }

        Iterator<RecentCase> mostRecentFirst = recentCases.descendingIterator();
        int i = 0;
        while (mostRecentFirst.hasNext()) {
            String path = mostRecentFirst.next().path;
            if ((currentCasePath != null && !path.equals(currentCasePath)) || currentCasePath == null) {
                // exclude currentCasePath from the casePaths[]
                casePaths[i] = path;
                i++;
            }
        }

        while (i < casePaths.length) {
            casePaths[i] = "";
            i++;
        }

        // return last 5 case paths
        return (String[]) ArrayUtils.subarray(casePaths, 0, LENGTH - 1);
    }

    /**
     * This method does nothing. Use the actionPerformed instead of this method.
     */
    @Override
    public void performAction() {
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     *
     * @return actionName
     */
    @Override
    public String getName() {
        //return NbBundle.getMessage(RecentCases.class, "CTL_RecentCases");
        return NbBundle.getMessage(RecentCases.class, "RecentCases.getName.text");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     *
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
