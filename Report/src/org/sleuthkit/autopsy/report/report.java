/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebHistory() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebCookie() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebBookmark() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getWebDownload() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getRecentObject() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getKeywordHit() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getHashHit() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
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
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}
@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getInstalledProg() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(8);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> getDevices() {
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
        ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(11);
        for (BlackboardArtifact artifact : bbart)
            {
                ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
               reportMap.put(artifact, attributes);    
            }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    return reportMap;
}

@Override
public String getGroupedKeywordHit() {
    StringBuilder table = new StringBuilder();
    HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    try
    {
       ResultSet uniqueresults = tempDb.runQuery("SELECT DISTINCT value_text from blackboard_attributes where attribute_type_id = '10' order by value_text ASC");
        while(uniqueresults.next())
        {  
           table.append("<strong>").append(uniqueresults.getString("value_text")).append("</strong>");
           table.append("<table><thead><tr><th>").append("File Name").append("</th><th>Preview</th><th>Keyword List</th></tr><tbody>");
           ArrayList<BlackboardArtifact> artlist = new ArrayList<BlackboardArtifact>();
           ResultSet tempresults = tempDb.runQuery("select DISTINCT artifact_id from blackboard_attributes where attribute_type_id = '10' and value_text = '" + uniqueresults.getString("value_text") +"'");
            while(tempresults.next())
            {
                artlist.add(tempDb.getBlackboardArtifact(tempresults.getLong("artifact_id")));
            }
            for(BlackboardArtifact art : artlist)
            {
              String filename = tempDb.getFsContentById(art.getObjectID()).getName();
              String preview = "";
              String set = "";
              table.append("<tr><td>").append(filename).append("</td>");
              ArrayList<BlackboardAttribute> tempatts = art.getAttributes();
                for(BlackboardAttribute att : tempatts)
                {                  
                    if(att.getAttributeTypeID() == 12)
                    {
                        preview = "<td>" + att.getValueString() + "</td>";
                    }
                    if(att.getAttributeTypeID() == 13)
                    {
                        set = "<td>" + att.getValueString() + "</td>";
                    }
                }
                table.append(preview).append(set).append("</tr>");
            }
           
           
           table.append("</tbody></table><br /><br />");
        }
    }
    catch (Exception e)
    {
        Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
    }
    
    String result = table.toString();
    return result;
}

}