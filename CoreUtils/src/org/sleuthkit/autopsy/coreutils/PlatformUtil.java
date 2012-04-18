/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.InstalledFileLocator;

/**
 *
 * Platform utililities
 */
public class PlatformUtil {
    private static final Logger logger = Logger.getLogger(PlatformUtil.class.getName());
    
    private static String javaPath = null;
    
    /**
     * get file path to the java executable binary
     * use embedded java if available, otherwise use system java in PATH
     * no validation is done if java exists in PATH
     * @return file path to java binary
     */
    public synchronized static String getJavaPath() {
        if (javaPath != null)
            return javaPath;
    
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false);
        File rootPath = coreFolder.getParentFile().getParentFile();
        File jrePath = new File(rootPath.getAbsolutePath() + File.separator + "jre6");
 
        if (jrePath != null && jrePath.exists() && jrePath.isDirectory()) {
            logger.log(Level.INFO, "Embedded jre6 directory not found in: " + jrePath.getAbsolutePath());
            javaPath = jrePath.getAbsolutePath() + File.separator + "bin" + File.separator + "java";
        }
        else {
            //else use system installed java in PATH env variable
            javaPath = "java";
            
        }
        
        logger.log(Level.INFO, "Using java binary path: " + javaPath);
        
        
        return javaPath;
    }
    
    
}
