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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.List;
import org.apache.commons.lang.StringEscapeUtils;
import org.sleuthkit.autopsy.recentactivity.dbconnect;

public class Report {

    private void Report() {
    }

    /**
     * Returns all the keywords related artifact/attributes and groups them
     * based on keyword
     *
     * @return String table is a string of an html table
     *
     */
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
            String temp1 = "CREATE TABLE report_keyword AS SELECT value_text as keyword,blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID() + ";";
            String temp2 = "CREATE TABLE report_preview AS SELECT value_text as preview, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID() + ";";
            String temp3 = "CREATE TABLE report_exp AS SELECT value_text as exp, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID() + ";";
            String temp4 = "CREATE TABLE report_list AS SELECT value_text as list, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ";";
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
                String previewreplace = StringEscapeUtils.escapeHtml(uniqueresults.getString("preview"));
                table.append("<td>").append(previewreplace.replaceAll("<!", "")).append("</td>").append("<td>").append(uniqueresults.getString("list")).append("<br />(").append(uniqueresults.getString("exp")).append(")").append("</td>").append("</tr>");

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
             table.append("</tbody></table><br /><br />");
        } catch (Exception e) {
            Logger.getLogger(Report.class.getName()).log(Level.WARNING, "Exception occurred", e);
        }

        return table.toString();
    }

    /**
     * Returns all the hash lookups related artifact/attributes and groups them
     * based on hashset name
     *
     * @return String table is a string of an html table
     *
     */
    public String getGroupedHashsetHit() {
        StringBuilder table = new StringBuilder();
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>>();
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
        try {
            tempDb.copyCaseDB(currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", "jdbc:sqlite:" + currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_hashset;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_hashname;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_hash;");
            String temp1 = "CREATE TABLE report_hashset AS SELECT value_text as hashset,blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = '" + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + "';";
            String temp5 = "CREATE TABLE report_hashname AS SELECT name, size, report_hashset.artifact_id from tsk_files,blackboard_artifacts, report_hashset WHERE blackboard_artifacts.artifact_id = report_hashset.artifact_id AND blackboard_artifacts.obj_id = tsk_files.obj_id and blackboard_artifacts.artifact_type_id='" + BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + "';";
            String temp6 = "CREATE TABLE report_hash AS SELECT hashset,size,name from report_hashset INNER JOIN report_hashname ON report_hashset.artifact_id=report_hashname.artifact_id;";
            tempdbconnect.executeStmt(temp1);
            tempdbconnect.executeStmt(temp5);
            tempdbconnect.executeStmt(temp6);
            ResultSet uniqueresults = tempdbconnect.executeQry("SELECT name, size, hashset FROM report_hash ORDER BY hashset ASC");
            String keyword = "";
            while (uniqueresults.next()) {
                if (uniqueresults.getString("hashset") == null ? keyword == null : uniqueresults.getString("hashset").equals(keyword)) {
                } else {
                    table.append("</tbody></table><br /><br />");
                    keyword = uniqueresults.getString("hashset");
                    table.append("<strong>").append(keyword).append("</strong>");
                    table.append("<table><thead><tr><th>").append("File Name").append("</th><th>Size</th></tr><tbody>");
                }
                table.append("<tr><td>").append(uniqueresults.getString("name")).append("</td>");
                table.append("<td>").append(uniqueresults.getString("size")).append("</td>").append("</tr>");

            }
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_hashset;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_hashname;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_hash;");
            tempdbconnect.closeConnection();
            
            File f1 = new File(currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            boolean success = f1.delete();
             table.append("</tbody></table><br /><br />");
        } catch (Exception e) {
            Logger.getLogger(Report.class.getName()).log(Level.WARNING, "Exception occurred", e);
        }

        return table.toString();
    }

    /**
     * Returns all the hash lookups related artifact/attributes and groups them
     * based on hashset name
     *
     * @return String table is a string of an html table
     *
     */
    public String getGroupedEmailHit() {
        StringBuilder table = new StringBuilder();
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>>();
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
        try {
            tempDb.copyCaseDB(currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", "jdbc:sqlite:" + currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_path;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_from;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_bcc;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_subject;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_to;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_content;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_cc;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report_name;");
            tempdbconnect.executeStmt("DROP TABLE IF EXISTS report;");
            String temp1 = "CREATE TABLE report_path AS SELECT value_text as path,blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID() + ";";
            String temp0 = "CREATE TABLE report_date AS SELECT value_int64 as date, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID() + ";";

            String temp2 = "CREATE TABLE report_to AS SELECT value_text as receiver, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID() + ";";
            String temp3 = "CREATE TABLE report_content AS SELECT value_text as content, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID() + ";";
            String temp4 = "CREATE TABLE report_cc AS SELECT value_text as cc, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID() + ";";
            String temp7 = "CREATE TABLE report_bcc AS SELECT value_text as bcc, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID() + ";";
            String temp8 = "CREATE TABLE report_author AS SELECT value_text as author, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID() + ";";
            String temp6 = "CREATE TABLE report_subject AS SELECT value_text as subject, blackboard_attributes.attribute_type_id, blackboard_attributes.artifact_id FROM blackboard_attributes WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID() + ";";
            String temp5 = "CREATE TABLE report_name AS SELECT name, report_path.artifact_id from tsk_files,blackboard_artifacts, report_path WHERE blackboard_artifacts.artifact_id = report_path.artifact_id AND blackboard_artifacts.obj_id = tsk_files.obj_id;";
            String temp9 = "CREATE TABLE report AS SELECT path,receiver,content,cc,bcc,subject,name,author,date from report_path INNER JOIN report_to ON report_path.artifact_id=report_to.artifact_id INNER JOIN report_content ON report_to.artifact_id=report_content.artifact_id INNER JOIN report_cc ON report_content.artifact_id=report_cc.artifact_id INNER JOIN report_name ON report_cc.artifact_id=report_name.artifact_id INNER JOIN report_bcc ON report_name.artifact_id=report_bcc.artifact_id INNER JOIN report_subject ON report_bcc.artifact_id=report_subject.artifact_id INNER JOIN report_author ON report_subject.artifact_id=report_author.artifact_id INNER JOIN report_date ON report_author.artifact_id=report_date.artifact_id";
            tempdbconnect.executeStmt(temp1);
            tempdbconnect.executeStmt(temp0);
            tempdbconnect.executeStmt(temp2);
            tempdbconnect.executeStmt(temp3);
            tempdbconnect.executeStmt(temp4);
            tempdbconnect.executeStmt(temp5);
            tempdbconnect.executeStmt(temp6);
            tempdbconnect.executeStmt(temp7);
            tempdbconnect.executeStmt(temp8);
            tempdbconnect.executeStmt(temp9);
            ResultSet uniqueresults = tempdbconnect.executeQry("SELECT path,receiver,content,cc,bcc,subject,name,author,date FROM report ORDER BY path ASC");
            String keyword = "";
            while (uniqueresults.next()) {
                if (uniqueresults.getString("path") == null ? keyword == null : uniqueresults.getString("path").equals(keyword)) {
                } else {
                    table.append("</tbody></table><br /><br />");
                    keyword = uniqueresults.getString("path");
                    table.append("<strong>").append(keyword).append("</strong>");
                    table.append("<table><thead><tr><th>").append("Folder").append("</th><th>From</th><th>To</th><th>Subject</th><th>Date/Time</th><th>Content</th><th>CC</th><th>BCC</th><th>Path</th></tr><tbody>");
                }
                table.append("<tr><td>").append(uniqueresults.getString("name")).append("</td>");
                table.append("<td>").append(uniqueresults.getString("receiver")).append("</td>").append("<td>").append(uniqueresults.getString("author")).append("</td><td>").append(uniqueresults.getString("subject")).append("</td>");
                SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                String value = sdf.format(new java.util.Date(uniqueresults.getLong("date") * 1000));
                table.append("<td>").append(value).append("</td>");
                table.append("<td>").append(uniqueresults.getString("content")).append("</td>");
                table.append("<td>").append(uniqueresults.getString("cc")).append("</td>");
                table.append("<td>").append(uniqueresults.getString("bcc")).append("</td>");
                table.append("<td>").append(uniqueresults.getString("path")).append("</td>");
                table.append("</tr>");
            }
            tempdbconnect.closeConnection();

            File f1 = new File(currentCase.getTempDirectory() + File.separator + "autopsy-copy.db");
            boolean success = f1.delete();
             table.append("</tbody></table><br /><br />");

        } catch (Exception e) {
            Logger.getLogger(Report.class.getName()).log(Level.WARNING, "Exception occurred", e);
        }

        return table.toString();
    }

    /**
     * Returns a hashmap of associated blackboard artifacts/attributes that were
     * requested by the config param
     *
     * @param config is a ReportConfiguration object that has all the types of
     * artifacts desired from the blackboard
     * @return reportMap a hashmap of all the artifacts for artifact types were
     * input
     */
    public HashMap<BlackboardArtifact, List<BlackboardAttribute>> getAllTypes(ReportConfiguration config) {
        HashMap<BlackboardArtifact, List<BlackboardAttribute>> reportMap = new HashMap<BlackboardArtifact, List<BlackboardAttribute>>();
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
        try {
            for (Map.Entry<BlackboardArtifact.ARTIFACT_TYPE, Boolean> entry : config.config.entrySet()) {
                if (entry.getValue()) {
                    List<BlackboardArtifact> bbart = tempDb.getBlackboardArtifacts(entry.getKey());
                    for (BlackboardArtifact artifact : bbart) {
                        List<BlackboardAttribute> attributes = artifact.getAttributes();
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