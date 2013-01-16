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
package org.sleuthkit.autopsy.coreutils;

import java.util.logging.Handler;
import org.openide.modules.ModuleInstall;
import java.util.logging.Level;

/**
 * Manages a this module's lifecycle. Sets up logging to file.
 */
public class Installer extends ModuleInstall {

    private static final Logger autopsyLogger = Logger.getLogger(""); //root logger
    private static Handler logs;

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

    @Override
    public void restored() {
        //Logger.init();
        autopsyLogger.log(Level.INFO, "Default charset: " + PlatformUtil.getDefaultPlatformCharset());
        autopsyLogger.log(Level.INFO, "Default file encoding: " + PlatformUtil.getDefaultPlatformFileEncoding());

        autopsyLogger.log(Level.INFO, "Java runtime version: " + Version.getJavaRuntimeVersion());

        autopsyLogger.log(Level.INFO, "Netbeans Platform build: " + Version.getNetbeansBuild());

        autopsyLogger.log(Level.INFO, "Application name: " + Version.getName()
                + ", version: " + Version.getVersion() + ", build: " + Version.getBuildType());
    }

    @Override
    public void uninstalled() {
    }
}
