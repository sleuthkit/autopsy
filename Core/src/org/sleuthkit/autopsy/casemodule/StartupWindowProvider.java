/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.apputils.ResetWindowsAction;
import org.sleuthkit.autopsy.commandlineingest.CommandLineIngestManager;
import org.sleuthkit.autopsy.commandlineingest.CommandLineOpenCaseManager;
import org.sleuthkit.autopsy.commandlineingest.CommandLineOptionProcessor;
import org.sleuthkit.autopsy.commandlineingest.CommandLineStartupWindow;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Provides the start up window to rest of the application. It may return the
 * main / default startup window, or a custom one if it has been discovered.
 *
 * All that is required to create a custom startup window in a module and active
 * it, is to implement StartupWindowInterface and register it with lookup as a
 * ServiceProvider. The custom startup window is automatically chosen over the
 * default one, given it is the only external module custom startup window.
 */
public class StartupWindowProvider implements StartupWindowInterface {

    private static volatile StartupWindowProvider instance;
    private static final Logger logger = Logger.getLogger(StartupWindowProvider.class.getName());
    private volatile StartupWindowInterface startupWindowToUse;

    public static StartupWindowProvider getInstance() {
        if (instance == null) {
            synchronized (StartupWindowProvider.class) {
                if (instance == null) {
                    instance = new StartupWindowProvider();
                    instance.init();
                }
            }
        }

        return instance;
    }

    private void init() {
        if (startupWindowToUse == null) {
            // first check whether we are running from command line
            if (isRunningFromCommandLine()) {

                String defaultArg = getDefaultArgument();
                if (defaultArg != null) {
                    new CommandLineOpenCaseManager(defaultArg).start();
                    return;
                } else {
                    // Autopsy is running from command line
                    logger.log(Level.INFO, "Running from command line"); //NON-NLS
                    startupWindowToUse = new CommandLineStartupWindow();
                    // kick off command line processing
                    new CommandLineIngestManager().start();
                    return;
                }
            }

            if (RuntimeProperties.runningWithGUI()) {
                checkSolr();
            }

            File openPreviousCaseFile = new File(ResetWindowsAction.getCaseToReopenFilePath());
            String caseFilePath = "";
            if (openPreviousCaseFile.exists()) {
                try {
                    Charset encoding = null;
                    caseFilePath = FileUtils.readFileToString(openPreviousCaseFile, encoding);
                    if (new File(caseFilePath).exists()) {
                        FileUtils.forceDelete(openPreviousCaseFile);
                        Case.openAsCurrentCase(caseFilePath);
                        //the case is now open we do not want to display the start up windows
                        return;
                    } else {
                        logger.log(Level.WARNING, "Unable to open previously open case because metadata file not found at: {0}", caseFilePath);
                    }
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Unable to open or delete file containing path " + ResetWindowsAction.getCaseToReopenFilePath() + " to previously open case, will not open previous case.", ex);
                } catch (CaseActionException ex) {
                    logger.log(Level.WARNING, "Unable to open previously open case with metadata file: " + caseFilePath, ex);
                }
            }
            //discover the registered windows
            Collection<? extends StartupWindowInterface> startupWindows
                    = Lookup.getDefault().lookupAll(StartupWindowInterface.class);

            int windowsCount = startupWindows.size();
            switch (windowsCount) {
                case 1:
                    startupWindowToUse = startupWindows.iterator().next();
                    logger.log(Level.INFO, "Will use the default startup window: {0}", startupWindowToUse.toString()); //NON-NLS
                    break;
                case 2: {
                    //pick the non default one
                    Iterator<? extends StartupWindowInterface> it = startupWindows.iterator();
                    while (it.hasNext()) {
                        StartupWindowInterface window = it.next();
                        if (!org.sleuthkit.autopsy.casemodule.StartupWindow.class.isInstance(window)) {
                            startupWindowToUse = window;
                            logger.log(Level.INFO, "Will use the custom startup window: {0}", startupWindowToUse.toString()); //NON-NLS
                            break;
                        }
                    }
                    break;
                }
                default: {
                    // select first non-Autopsy start up window
                    Iterator<? extends StartupWindowInterface> it = startupWindows.iterator();
                    while (it.hasNext()) {
                        StartupWindowInterface window = it.next();
                        if (!window.getClass().getCanonicalName().startsWith("org.sleuthkit.autopsy")) {
                            startupWindowToUse = window;
                            logger.log(Level.INFO, "Will use the custom startup window: {0}", startupWindowToUse.toString()); //NON-NLS
                            break;
                        }
                    }
                    break;
                }
            }

            if (startupWindowToUse == null) {
                logger.log(Level.SEVERE, "Unexpected error, no startup window chosen, using the default"); //NON-NLS
                startupWindowToUse = new org.sleuthkit.autopsy.casemodule.StartupWindow();
            }
        }
    }

    private void checkSolr() {

        // if Multi-User settings are enabled and Solr8 server is not configured,
        // display an error message and a dialog
        if (UserPreferences.getIsMultiUserModeEnabled() && UserPreferences.getIndexingServerHost().isEmpty()) {
            // Solr 8 host name is not configured. This could be the first time user 
            // runs Autopsy with Solr 8. Display a message.
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(CueBannerPanel.class, "SolrNotConfiguredDialog.title"),
                    NbBundle.getMessage(SolrNotConfiguredDialog.class, "SolrNotConfiguredDialog.EmptyKeywordSearchHostName"));

            SolrNotConfiguredDialog dialog = new SolrNotConfiguredDialog();
            dialog.setVisible(true);
        }
    }

    /**
     * Checks whether Autopsy is running from command line. There is an
     * OptionProcessor that is responsible for processing command line inputs.
     * If Autopsy is indeed running from command line, then use the command line
     * startup window.
     *
     * @return True if running from command line, false otherwise
     */
    private boolean isRunningFromCommandLine() {

        CommandLineOptionProcessor processor = Lookup.getDefault().lookup(CommandLineOptionProcessor.class);
        if (processor != null) {
            return processor.isRunFromCommandLine();
        }
        return false;
    }

    /**
     * Get the default argument from the CommandLineOptionProcessor.
     *
     * @return If set, the default argument otherwise null.
     */
    private String getDefaultArgument() {
        CommandLineOptionProcessor processor = Lookup.getDefault().lookup(CommandLineOptionProcessor.class);
        if (processor != null) {
            return processor.getDefaultArgument();
        }
        return null;
    }

    @Override
    public void open() {
        if (startupWindowToUse != null) {
            startupWindowToUse.open();
        }
    }

    @Override
    public void close() {
        if (startupWindowToUse != null) {
            startupWindowToUse.close();
        }
    }

    /**
     * Get the chosen startup window.
     *
     * @return The startup window.
     */
    public StartupWindowInterface getStartupWindow() {
        return startupWindowToUse;
    }
}
