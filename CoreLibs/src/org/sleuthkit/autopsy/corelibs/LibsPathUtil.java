 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-15 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.corelibs;


import java.io.File;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;

class LibsPathUtil {
    
    public static final String OS_ARCH_UNKNOWN = NbBundle.getMessage(LibsPathUtil.class, "LibsPathUtil.archUnknown");
    
    private LibsPathUtil() {}
    
    /**
     * load library from library path (hardcoded .dll for now).
     * 
     * @param libName name of library to be loaded
     */
    public static void loadLibrary(String libName) {
        String path = getLibsPath() + File.separator;
        String ext = ".dll";
        System.load(path + libName + ext);
    }
    
    /**
     * Gets the absolute path to the lib folder
     * 
     * @return Lib path string
     */
    public static String getLibsPath() {
        // locate uses "/" regardless of format
        File libFolder = InstalledFileLocator.getDefault().locate("modules/lib/" + getOSArch(), LibsPathUtil.class.getPackage().getName(), false); //NON-NLS
        return libFolder.getAbsolutePath();
    }
    
    /**
     * Get OS arch details, or OS_ARCH_UNKNOWN
     *
     * @return OS arch string
     */
    public static String getOSArch() {
        String arch = System.getProperty("os.arch"); //NON-NLS
        if(arch == null)
            return OS_ARCH_UNKNOWN;
        else
            return arch.endsWith("64") ? "x86_64" : "x86"; //NON-NLS
    }
}
