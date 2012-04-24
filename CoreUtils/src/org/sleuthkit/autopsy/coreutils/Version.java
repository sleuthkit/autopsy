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

import org.openide.util.NbBundle;

/**
 *
 * @author dfickling
 */
public class Version {
    
    private Version() { }
    
    public enum Type{
        RELEASE, DEBUG;
    }
    
    public static String getVersion() {
        return NbBundle.getMessage(Version.class, "app.version");
    }
    
    public static String getName() {
        return NbBundle.getMessage(Version.class, "app.name");
    }
    
    public static Version.Type getBuildType() {
        return Type.valueOf(NbBundle.getMessage(Version.class, "build.type"));
    }
}
