/*
 * Central Repository
 *
 * Copyright 2020-2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * Notifies new installations or old upgrades that the central repository will
 * be enabled by default.
 */
public class CentralRepositoryNotificationDialog {

    /**
     * This dialog should display if the mode is RELEASE and the application is
     * running with a GUI. In addition to the Autopsy flag setting, it also
     * checks whether the AUTOPSY_HEADLESS environment variable is set. The
     * environment variable is set by some of the projects built on top of
     * Autopsy platform. This is necessary because sometimes this method is
     * called from Installer classes, i.e. before we have been able to determine
     * whether we are running headless or not. See JIRA-8422.
     */
    static boolean shouldDisplay() {
        if (System.getenv("AUTOPSY_HEADLESS") != null) {
            // Some projects built on top of Autopsy platform set this environment 
            // variable to make sure there are no UI popups
            return false;
        }

        return Version.getBuildType() == Version.Type.RELEASE
                && RuntimeProperties.runningWithGUI();
    }

    /**
     * Displays an informational modal dialog to the user, which is dismissed by
     * pressing 'OK'.
     */
    @NbBundle.Messages({
        "CentralRepositoryNotificationDialog.header=Autopsy stores data about each case in its Central Repository.",
        "CentralRepositoryNotificationDialog.bulletHeader=This data is used to:",
        "CentralRepositoryNotificationDialog.bulletOne=Ignore common items (files, domains, and accounts)",
        "CentralRepositoryNotificationDialog.bulletTwo=Identify where an item was previously seen",
        "CentralRepositoryNotificationDialog.bulletThree=Create personas that group accounts",
        "CentralRepositoryNotificationDialog.finalRemarks=To limit what is stored, use the Central Repository options panel."
    })
    static void display() {
        assert shouldDisplay();

        MessageNotifyUtil.Message.info(
                "<html>"
                + "<body>"
                    + "<div>"
                        + "<p>" + Bundle.CentralRepositoryNotificationDialog_header() + "</p>"
                        + "<p>" + Bundle.CentralRepositoryNotificationDialog_bulletHeader() + "</p>"
                        + "<ul>"
                            + "<li>" + Bundle.CentralRepositoryNotificationDialog_bulletOne() + "</li>"
                            + "<li>" + Bundle.CentralRepositoryNotificationDialog_bulletTwo() + "</li>"
                            + "<li>" + Bundle.CentralRepositoryNotificationDialog_bulletThree() + "</li>"
                        + "</ul>"
                        + "<p>" + Bundle.CentralRepositoryNotificationDialog_finalRemarks() + "</p>"
                    + "</div>"
                + "</body>"
                + "</html>"
        );
    }
}
