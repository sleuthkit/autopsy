/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
}
