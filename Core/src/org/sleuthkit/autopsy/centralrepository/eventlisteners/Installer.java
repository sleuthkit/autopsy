/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import javax.swing.JOptionPane;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Install event listeners during module initialization
 */
public class Installer extends ModuleInstall {

    private static final Logger LOGGER = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;
    private final CaseEventListener pcl = new CaseEventListener();
    private final IngestEventsListener ieListener = new IngestEventsListener();

    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
        super();
    }

    @NbBundle.Messages({"Installer.centralRepoUpgradeFailed.title=Central repository disabled"})
    @Override
    public void restored() {
        Case.addPropertyChangeListener(pcl);
        ieListener.installListeners();

        // Perform the database upgrade and inform the user if it fails
        try {
            EamDbUtil.upgradeDatabase();
        } catch (EamDbException ex) {
            if (RuntimeProperties.runningWithGUI()) {
                WindowManager.getDefault().invokeWhenUIReady(() -> {
                    JOptionPane.showMessageDialog(null,
                            ex.getUserMessage(),
                            NbBundle.getMessage(this.getClass(),
                                    "Installer.centralRepoUpgradeFailed.title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    @Override
    public boolean closing() {
        //platform about to close

        return true;
    }

    @Override
    public void uninstalled() {
        //module is being unloaded

        Case.removePropertyChangeListener(pcl);
        pcl.shutdown();
        ieListener.shutdown();
        ieListener.uninstallListeners();

        // TODO: remove thread pool
    }
}
