/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.ctoptions.ctcloud;

import com.basistech.df.cybertriage.autopsy.ctapi.CTApiDAO;
import com.basistech.df.cybertriage.autopsy.ctapi.CTCloudException;
import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthTokenResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseInfo;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Installer to check for a functional license at startup.
 */
public class Installer extends ModuleInstall {
    private final static Logger LOGGER = Logger.getLogger(Installer.class.getName());
    
    private final static Installer INSTANCE = new Installer();
    
    public static Installer getDefault() {
        return INSTANCE;
    }
    
    private Installer() {}
    
    @Override
    public void restored() {
        new Thread(new LicenseCheck()).start();
    }
    
    @Messages({
        "Installer_LicenseCheck_cloudExceptionTitle=Cyber Triage Error"
    })
    private static class LicenseCheck implements Runnable {

        @Override
        public void run() {
            try {
                Optional<LicenseInfo> licenseInfoOpt = CTLicensePersistence.getInstance().loadLicenseInfo();
                if (licenseInfoOpt.isEmpty()) {
                    return;
                }

                LicenseInfo licenseInfo = licenseInfoOpt.get();
                AuthTokenResponse authTokenResp = CTApiDAO.getInstance().getAuthToken(licenseInfo.getDecryptedLicense());
                // if we got this far, then it was a successful request
            } catch (CTCloudException cloudEx) {
                LOGGER.log(Level.WARNING, "A cloud exception occurred while fetching an auth token", cloudEx);
                MessageNotifyUtil.Notify.warn(Bundle.Installer_LicenseCheck_cloudExceptionTitle(), cloudEx.getErrorDetails());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "An error occurred while fetching license info", t);
            }
        }
    }
}
