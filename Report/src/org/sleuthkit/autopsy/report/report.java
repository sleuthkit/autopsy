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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
public class report {

    private void report() {
    }

    public String getGroupedKeywordHit() {
        StringBuilder table = new StringBuilder();
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>>();
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
        try {
            
            ResultSet uniqueresults = tempDb.runQuery("SELECT DISTINCT value_text from blackboard_attributes where attribute_type_id = '10' order by value_text ASC");
           
            while (uniqueresults.next()) { 
                table.append("<strong>").append(uniqueresults.getString("value_text")).append("</strong>");
                table.append("<table><thead><tr><th>").append("File Name").append("</th><th>Preview</th><th>Keyword List</th></tr><tbody>");
                ArrayList<BlackboardArtifact> artlist = new ArrayList<BlackboardArtifact>();
                ResultSet tempresults = tempDb.runQuery("select DISTINCT artifact_id from blackboard_attributes where attribute_type_id = '10' and value_text = '" + uniqueresults.getString("value_text") + "'");
                while (tempresults.next()) {
                    artlist.add(tempDb.getBlackboardArtifact(tempresults.getLong("artifact_id")));
                }
                
                for (BlackboardArtifact art : artlist) {
                    String filename = tempDb.getFsContentById(art.getObjectID()).getName();
                    String preview = "";
                    String set = "";
                    table.append("<tr><td>").append(filename).append("</td>");
                    ArrayList<BlackboardAttribute> tempatts = art.getAttributes();
                    for (BlackboardAttribute att : tempatts) {
                        if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()) {
                            preview = "<td>" + att.getValueString() + "</td>";
                        }
                        if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID()) {
                            set = "<td>" + att.getValueString() + "</td>";
                        }
                    }
                    table.append(preview).append(set).append("</tr>");
                }
          
                table.append("</tbody></table><br /><br />");
            }
        } catch (Exception e) {
            Logger.getLogger(report.class.getName()).log(Level.WARNING, "Exception occurred", e);
        }
        
        return table.toString();
    }

    public HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> getAllTypes(ReportConfiguration config) {
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>>();
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
        try {
            for (Map.Entry<BlackboardArtifact.ARTIFACT_TYPE, Boolean> entry : config.config.entrySet()) {
                if (entry.getValue()) {
                    ArrayList<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(entry.getKey());
                    for (BlackboardArtifact artifact : bbart) {
                        ArrayList<BlackboardAttribute> attributes = artifact.getAttributes();
                        reportMap.put(artifact, attributes);
                    }
                }
            }
        } catch (Exception e) {
            Logger.getLogger(report.class.getName()).log(Level.INFO, "Exception occurred", e);
        }

        return reportMap;
    }
}