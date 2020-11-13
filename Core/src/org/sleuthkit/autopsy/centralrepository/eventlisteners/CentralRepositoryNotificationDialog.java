/*
 * Central Repository
 *
 * Copyright 2020 Basis Technology Corp.
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
     * This dialog should display iff the mode is RELEASE and the
     * application is running with a GUI.
     */
    static boolean shouldDisplay() {
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
