/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author Alex
 */
public class report implements reportInterface {
    
private void report(){

}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getGenInfo() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(1);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebHistory() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(4);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebCookie() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(3);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebBookmark() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(2);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebDownload() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(5);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getRecentObject() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(6);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getKeywordHit() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(9);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getHashHit() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(10);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
    }
    
    return reportMap;
}

}