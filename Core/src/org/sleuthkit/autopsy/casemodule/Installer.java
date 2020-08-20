/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Installer for casemodule that cleans out user specified temp directory.
 */
public final class Installer extends ModuleInstall {

    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;

    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
    }

    @Override
    public void restored() {
        String tempDirStr = null;
        try {
            tempDirStr = UserPreferences.getAppTempDirectory();
            if (StringUtils.isNotBlank(tempDirStr)) {
                File tempDir = new File(tempDirStr);
                if (tempDir.exists()) {
                    FileUtil.deleteDir(tempDir);
                }
            }
        } catch (Exception ex) {
            // This is a firewall exception should any issues occur 
            // during temp directory deletion
            logger.log(Level.WARNING, "There was an error while cleaning up temp directory: " + (tempDirStr == null ? "<null>" : tempDirStr), ex);
        }
    }
}
