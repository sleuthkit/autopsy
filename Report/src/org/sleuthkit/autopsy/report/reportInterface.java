 /*
 *
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.report;

import java.util.ArrayList;
import java.util.HashMap;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 * @author Alex
 */
public interface reportInterface{
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getGenInfo();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebHistory();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebCookie();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebBookmark();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebDownload();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getRecentObject();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getHashHit();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getKeywordHit();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getInstalledProg();
    public String getGroupedKeywordHit();
    public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getDevices();
}
