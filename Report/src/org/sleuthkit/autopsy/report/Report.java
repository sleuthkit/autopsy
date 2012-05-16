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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import java.sql.*;
import org.sleuthkit.autopsy.recentactivity.dbconnect;

/**
 *
 * @author Alex
 */
public class Report {

    private void Report() {
    }

    public String getGroupedKeywordHit() {
        StringBuilder table = new StringBuilder();
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>>();
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
        try {
            tempDb.copyCaseDB(currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", "jdbc:sqlite:" + currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_keyword;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_preview;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_exp;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_list;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_name;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report;");
            String temp1 = "CREATE TABLE report_keyword AS SELECT value_text as keyword,blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = 10;";
            String temp2 = "CREATE TABLE report_preview AS SELECT value_text as preview, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = 12;";
            String temp3 = "CREATE TABLE report_exp AS SELECT value_text as exp, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = 11;";
            String temp4 = "CREATE TABLE report_list AS SELECT value_text as list, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = 13;";
            String temp5 = "CREATE TABLE report_name AS SELECT name, report_keyword.artifact_id from tsk_files,blackboard_artifacts, report_keyword WHERE blackboard_artifacts.artifact_id = report_keyword.artifact_id AND blackboard_artifacts.obj_id = tsk_files.obj_id;";
            String temp6 = "CREATE TABLE report AS SELECT keyword,preview,exp,list,name from report_keyword INNER JOIN report_preview ON report_keyword.artifact_id=report_preview.artifact_id INNER JOIN report_exp ON report_preview.artifact_id=report_exp.artifact_id INNER JOIN report_list ON report_exp.artifact_id=report_list.artifact_id INNER JOIN report_name ON report_list.artifact_id=report_name.artifact_id;";
            tempdbconnect.executeStmt(temp1);
            tempdbconnect.executeStmt(temp2);
            tempdbconnect.executeStmt(temp3);
            tempdbconnect.executeStmt(temp4);
            tempdbconnect.executeStmt(temp5);
            tempdbconnect.executeStmt(temp6);
            ResultSet uniqueresults = tempdbconnect.executeQry("SELECT keyword, exp, preview, list, name FROM report ORDER BY keyword ASC");
            String keyword = "";
            while (uniqueresults.next()) {
                if (uniqueresults.getString("keyword") == null ? keyword == null : uniqueresults.getString("keyword").equals(keyword)) {
                } else {
                    table.append("</tbody></table><br /><br />");
                    keyword = uniqueresults.getString("keyword");
                    table.append("<strong>").append(keyword).append("</strong>");
                    table.append("<table><thead><tr><th>").append("File Name").append("</th><th>Preview</th><th>Keyword List</th></tr><tbody>");
                }
                table.append("<tr><td>").append(uniqueresults.getString("name")).append("</td>");
                table.append("<td>").append(uniqueresults.getString("preview")).append("</td>").append("<td>").append(uniqueresults.getString("list")).append("<br />(").append(uniqueresults.getString("exp")).append(")").append("</td>").append("</tr>");

            }
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_keyword;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_preview;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_exp;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_name;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_list;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report;");
            tempdbconnect.closeConnection();

            File f1 = new File(currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            boolean success = f1.delete();

        } catch (Exception e) {
            Logger.getLogger(Report.class.getName()).log(Level.WARNING, "Exception occurred", e);
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
            Logger.getLogger(Report.class.getName()).log(Level.INFO, "Exception occurred", e);
        }

        return reportMap;
    }
}