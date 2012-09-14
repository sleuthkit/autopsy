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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.commons.lang.StringEscapeUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.*;
import java.io.File;
import java.util.List;

/**
 * Generates an HTML report for all the Blackboard Artifacts found in the current case.
 */
public class ReportHTML implements ReportModule {
    //Declare our publically accessible formatted Report, this will change everytime they run a Report

    public static StringBuilder formatted_Report = new StringBuilder();
    private static StringBuilder unformatted_header = new StringBuilder();
    private static StringBuilder formatted_header = new StringBuilder();
    private static String htmlPath = "";
    private ReportConfiguration config;
    private static ReportHTML instance = null;
    private Case currentCase = Case.getCurrentCase(); // get the most updated case
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();

    ReportHTML() {
    }

    public static synchronized ReportHTML getDefault() {
        if (instance == null) {
            instance = new ReportHTML();
        }
        return instance;
    }

    @Override
    public String generateReport(ReportConfiguration reportconfig) throws ReportModuleException {
        config = reportconfig;
        ReportGen reportobj = new ReportGen();
        reportobj.populateReport(reportconfig);
        HashMap<BlackboardArtifact, List<BlackboardAttribute>> report = reportobj.getResults();
        //This is literally a terrible way to count up all the types of artifacts, and doesn't include any added ones. 
        //Unlike the XML Report, which is dynamic, this is formatted and needs to be redone later instead of being hardcoded.
        //Also, clearing variables to generate new Report.
        formatted_Report.setLength(0);
        unformatted_header.setLength(0);
        formatted_header.setLength(0);

        int countGen = 0;
        int countWebBookmark = 0;
        int countWebCookie = 0;
        int countWebHistory = 0;
        int countWebDownload = 0;
        int countRecentObjects = 0;
        int countTrackPoint = 0;
        int countInstalled = 0;
        int countKeyword = 0;
        int countHash = 0;
        int countDevice = 0;
        int countEmail = 0;
        int countWebSearch = 0;
        for (Entry<BlackboardArtifact, List<BlackboardAttribute>> entry : report.entrySet()) {
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()) {
                countGen++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
                countWebBookmark++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {

                countWebCookie++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {

                countWebHistory++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
                countWebDownload++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
                countRecentObjects++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getTypeID()) {
                countTrackPoint++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                countInstalled++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                countKeyword++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                countHash++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                countDevice++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
                countEmail++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
                countWebSearch++;
            }
        }


        String ingestwarning = "<h2 style=\"color: red;\">Warning, this report was run before ingest services completed!</h2>";

        String caseName = currentCase.getName();
        Integer imagecount = currentCase.getImageIDs().length;
        Integer totalfiles = 0;
        Integer totaldirs = 0;
        try {
            totaldirs = skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR);
            totalfiles = skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG);
        } catch (TskException ex) {
            Logger.getLogger(ReportHTML.class.getName()).log(Level.WARNING, "Could not get FsContentType counts from TSK ", ex);
        }



        int reportsize = report.size();
        Integer filesystemcount = currentCase.getRootObjectsCount();
        DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datetime = datetimeFormat.format(date);
        String datenotime = dateFormat.format(date);
        String CSS = "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><style>"
                + "body {padding: 30px; margin: 0; background: #FFFFFF; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353;} "
                + "h1 {font-size: 26px; color: #005577; margin: 0 0 20px 0;} "
                + "h2 {font-size: 20px; font-weight: normal; color: #0077aa; margin: 40px 0 10px 0; padding: 0 0 10px 0; border-bottom: 1px solid #dddddd;} "
                + "h3 {font-size: 16px;color: #0077aa; margin: 40px 0 10px 0;} "
                + "p {margin: 0 0 20px 0;} table {width: 100%; padding: 0; margin: 0; border-collapse: collapse; border-bottom: 1px solid #e5e5e5;} "
                + "table thead th {display: table-cell; text-align: left; padding: 8px 16px; background: #e5e5e5; color: #777;font-size: 11px;text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede;} "
                + "table tr th:nth-child(1) {text-align: center; width: 60px;} "
                + "table td {display: table-cell; padding: 8px 16px; font: 13px/20px Arial, Helvetica, sans-serif;} "
                + "table tr:nth-child(even) td {background: #f3f3f3;} "
                + "table tr td:nth-child(1) {text-align: left; width: 60px; background: #f3f3f3;} "
                + "table tr:nth-child(even) td:nth-child(1) {background: #eaeaea;}"
                + "</style>";
        //Add additional header information
        String header = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\"><head><title>Autopsy Report for Case: " + caseName + "</title>";
        formatted_header.append(header);
        formatted_header.append(CSS);

        //do for unformatted
        String simpleCSS = "<style>"
                + "body {padding: 30px; margin: 0; background: #FFFFFF; color: #535353;} "
                + "h1 {font-size: 26px; color: #005577; margin: 0 0 20px 0;} "
                + "h2 {font-size: 20px; font-weight: normal; color: #0077aa; margin: 40px 0 10px 0; padding: 0 0 10px 0; border-bottom: 1px solid #dddddd;} "
                + "h3 {font-size: 16px;color: #0077aa; margin: 40px 0 10px 0;} "
                + "p {margin: 0 0 20px 0;} table {width: 100%; padding: 0; margin: 0; border-collapse: collapse; border-bottom: 1px solid #e5e5e5;} "
                + "table thead th {display: table-cell; text-align: left; padding: 4px 8px; background: #e5e5e5; color: #777;font-size: 11px; width: 80px; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede;} "
                + "table tr th {text-align: left; width: 80px;} "
                + "table td {width: 100px; font-size: 8px; display: table-cell; padding: 4px 8px;} "
                + "table tr {text-align: left; width: 60px; background: #f3f3f3;} "
                + "tr.alt td{ background-color: #FFFFFF;}"
                + "</style>";
        unformatted_header.append(header);
        unformatted_header.append(simpleCSS);
        //formatted_Report.append("<link rel=\"stylesheet\" href=\"" + rrpath + "Report.css\" type=\"text/css\" />");
        formatted_Report.append("</head><body><div id=\"main\"><div id=\"content\">");
        // Add summary information now

        formatted_Report.append("<h1>Report for Case: ").append(caseName).append("</h1>");
        if (IngestManager.getDefault().isIngestRunning()) {
            formatted_Report.append(ingestwarning);
        }
        else if (IngestManager.getDefault().areModulesRunning()) {
            formatted_Report.append(ingestwarning);
        }
        formatted_Report.append("<h2>Case Summary</h2><p>HTML Report Generated by <strong>Autopsy 3</strong> on ").append(datetime).append("<ul>");
        formatted_Report.append("<li># of Images: ").append(imagecount).append("</li>");
        formatted_Report.append("<li>FileSystems: ").append(filesystemcount).append("</li>");
        formatted_Report.append("<li># of Files: ").append(totalfiles.toString()).append("</li>");
        formatted_Report.append("<li># of Dirs: ").append(totaldirs.toString()).append("</li>");
        formatted_Report.append("<li># of Artifacts: ").append(reportsize).append("</li></ul>");

        formatted_Report.append("<br /><table><thead><tr><th>Section</th><th>Count</th></tr></thead><tbody>");
        if (countWebBookmark > 0) {
            formatted_Report.append("<tr><td><a href=\"#bookmark\">Web Bookmarks</a></td><td>").append(countWebBookmark).append("</td></tr>");
        }
        if (countWebCookie > 0) {
            formatted_Report.append("<tr><td><a href=\"#cookie\">Web Cookies</a></td><td>").append(countWebCookie).append("</td></tr>");
        }
        if (countWebHistory > 0) {
            formatted_Report.append("<tr><td><a href=\"#history\">Web History</a></td><td>").append(countWebHistory).append("</td></tr>");
        }
        if (countWebDownload > 0) {
            formatted_Report.append("<tr><td><a href=\"#download\">Web Downloads</a></td><td>").append(countWebDownload).append("</td></tr>");
        }
        if (countRecentObjects > 0) {
            formatted_Report.append("<tr><td><a href=\"#recent\">Recent Documents</a></td><td>").append(countRecentObjects).append("</td></tr>");
        }
        if (countInstalled > 0) {
            formatted_Report.append("<tr><td><a href=\"#installed\">Installed Programs</a></td><td>").append(countInstalled).append("</td></tr>");
        }
        if (countKeyword > 0) {
            formatted_Report.append("<tr><td><a href=\"#keyword\">Keyword Hits</a></td><td>").append(countKeyword).append("</td></tr>");
        }
        if (countHash > 0) {
            formatted_Report.append("<tr><td><a href=\"#hash\">Hash Hits</a></td><td>").append(countHash).append("</td></tr>");
        }
        if (countDevice > 0) {
            formatted_Report.append("<tr><td><a href=\"#device\">Attached Devices</a></td><td>").append(countDevice).append("</td></tr>");
        }
        if (countEmail > 0) {
            formatted_Report.append("<tr><td><a href=\"#email\">Email Messages</a></td><td>").append(countEmail).append("</td></tr>");
        }
        if (countWebSearch > 0) {
            formatted_Report.append("<tr><td><a href=\"#search\">Web Search Queries</a></td><td>").append(countWebSearch).append("</td></tr>");
        }
        formatted_Report.append("</tbody></table><br />");
        String tableHeader = "<table><thead><tr>";
        StringBuilder nodeGen = new StringBuilder("<h3>General Information (").append(countGen).append(")</h3>").append(tableHeader).append("<th>Attribute</th><th>Value</th></tr></thead><tbody>");
        StringBuilder nodeWebBookmark = new StringBuilder("<h3><a name=\"bookmark\">Web Bookmarks (").append(countWebBookmark).append(")</h3>").append(tableHeader).append("<th>URL</th><th>Title</th><th>Program</th></tr></thead><tbody>");
        StringBuilder nodeWebCookie = new StringBuilder("<h3><a name=\"cookie\">Web Cookies (").append(countWebCookie).append(")</h3>").append(tableHeader).append("<th>URL</th><th>Date</th><th>Name</th><th>Value</th><th>Program</th></tr></thead><tbody>");
        StringBuilder nodeWebHistory = new StringBuilder("<h3><a name=\"history\">Web History (").append(countWebHistory).append(")</h3>").append(tableHeader).append("<th>URL</th><th>Date</th><th>Referrer</th><th>Title</th><th>Program</th></tr></thead><tbody>");
        StringBuilder nodeWebDownload = new StringBuilder("<h3><a name=\"download\">Web Downloads (").append(countWebDownload).append(")</h3>").append(tableHeader).append("<th>File</th><th>Source</th><th>Time</th><th>Program</th></tr></thead><tbody>");
        StringBuilder nodeRecentObjects = new StringBuilder("<h3><a name=\"recent\">Recent Documents (").append(countRecentObjects).append(")</h3>").append(tableHeader).append("<th>Name</th><th>Path</th><th>Related Shortcut</th></tr></thead><tbody>");
        StringBuilder nodeTrackPoint = new StringBuilder("<h3><a name=\"track\">Track Points (").append(countTrackPoint).append(")</h3>").append(tableHeader).append("<th>Artifact ID</th><th>Name</th><th>Size</th><th>Attribute</th><th>Value</th></tr></thead><tbody>");
        StringBuilder nodeInstalled = new StringBuilder("<h3><a name=\"installed\">Installed Programs (").append(countInstalled).append(")</h3>").append(tableHeader).append("<th>Program Name</th><th>Install Date/Time</th></tr></thead><tbody>");
        StringBuilder nodeKeyword = new StringBuilder("<h3><a name=\"keyword\">Keyword Search Hits (").append(countKeyword).append(")</h3>");
        StringBuilder nodeHash = new StringBuilder("<h3><a name=\"hash\">Hashset Hit (").append(countHash).append(")</h3>");
        StringBuilder nodeDevice = new StringBuilder("<h3><a name=\"device\">Attached Devices (").append(countDevice).append(")</h3>").append(tableHeader).append("<th>Name</th><th>Serial #</th><th>Time</th></tr></thead><tbody>");
        StringBuilder nodeEmail = new StringBuilder("<h3><a name=\"email\">Email Messages (").append(countEmail).append(")</h3>");
        StringBuilder nodeWebSearch = new StringBuilder("<h3><a name=\"search\">Web Search Queries (").append(countWebSearch).append(")</h3>").append(tableHeader).append("<th>Program Name</th><th>Domain</th><th>Text</th><th>Last Modified</th></tr></thead><tbody>");

        int alt = 0;
        String altRow = "";
        for (Entry<BlackboardArtifact, List<BlackboardAttribute>> entry : report.entrySet()) {
            if (ReportFilter.cancel == true) {
                break;
            }

            if (alt > 0) {
                altRow = " class=\"alt\"";
                alt = 0;
            } else {
                altRow = "";
                alt++;
            }
            StringBuilder artifact = new StringBuilder("");
            Long objId = entry.getKey().getObjectID();
            //Content file = skCase.getContentById(objId);
            AbstractFile file = null;
            try {
                file = skCase.getAbstractFileById(objId);
            } catch (TskException ex) {
                Logger.getLogger(ReportHTML.class.getName()).log(Level.WARNING, "Could not get AbstractFile from TSK ", ex);
            }

            Long filesize = file.getSize();


            TreeMap<Integer, String> attributes = new TreeMap<Integer, String>();
            // Get all the attributes, line them up to be added. Place empty string placeholders for each attribute type
            int n;
            for (n = 1; n <= 35; n++) {
                attributes.put(n, "");

            }
            for (BlackboardAttribute tempatt : entry.getValue()) {
                if (ReportFilter.cancel == true) {
                    break;
                }
                String value = "";
                Integer type = tempatt.getAttributeTypeID();
                if (type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()) || type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID())) {

                    SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    value = sdf.format(new java.util.Date((tempatt.getValueLong() * 1000)));

                } else {
                    value = tempatt.getValueString();
                }
                if (value == null || value.isEmpty()) {
                    value = "";
                }
                value = ReportUtils.insertPeriodically(value, "<br>", 30);
                attributes.put(type, StringEscapeUtils.escapeHtml(value));

            }


            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()) {

                artifact.append("</tr>");
                nodeGen.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeWebBookmark.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeWebCookie.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeWebHistory.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeWebDownload.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
                //artifact.append("<tr><td>").append(objId.toString());
                artifact.append("<tr").append(altRow).append("><td><strong>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</strong></td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID())).append("</td>");
                artifact.append("<td>").append(file.getName()).append("</td>");
                artifact.append("</tr>");
                nodeRecentObjects.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td>").append(objId.toString());
                artifact.append("</td><td><strong>").append(file.getName().toString()).append("</strong></td>");
                artifact.append("<td>").append(filesize.toString()).append("</td>");
                artifact.append("</tr>");
                nodeTrackPoint.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td><strong>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</strong></td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeInstalled.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                //  artifact.append("<table><thead><tr><th>Artifact ID</th><th>Name</th><th>Size</th>");
                //    artifact.append("</tr></table>");
                //    nodeKeyword.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                // artifact.append("<tr><td>").append(objId.toString());
               // artifact.append("<tr").append(altRow).append("><td><strong>").append(file.getName().toString()).append("</strong></td>");
               // artifact.append("<td>").append(filesize.toString()).append("</td>");
                //artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_INTERESTING_FILE.getTypeID())).append("</td>");
               // artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID())).append("</td>");
              //  artifact.append("</tr>");
               // nodeHash.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td><strong>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID())).append("</strong></td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeDevice.append(artifact);
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
                artifact.append("<tr").append(altRow).append("><td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID())).append("</td>");
                artifact.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID())).append("</td>");
                artifact.append("</tr>");
                nodeWebSearch.append(artifact);
            }
        }
        //Add them back in order
        //formatted_Report.append(nodeGen);
        // formatted_Report.append("</tbody></table>");

        if (countWebBookmark > 0) {
            formatted_Report.append(nodeWebBookmark);
            formatted_Report.append("</tbody></table>");
        }
        if (countWebCookie > 0) {
            formatted_Report.append(nodeWebCookie);
            formatted_Report.append("</tbody></table>");
        }
        if (countWebHistory > 0) {
            formatted_Report.append(nodeWebHistory);
            formatted_Report.append("</tbody></table>");
        }
        if (countWebDownload > 0) {
            formatted_Report.append(nodeWebDownload);
            formatted_Report.append("</tbody></table>");
        }
        if (countRecentObjects > 0) {
            formatted_Report.append(nodeRecentObjects);
            formatted_Report.append("</tbody></table>");
        }
        // formatted_Report.append(nodeTrackPoint);
        //formatted_Report.append("</tbody></table>");
        if (countInstalled > 0) {
            formatted_Report.append(nodeInstalled);
            formatted_Report.append("</tbody></table>");
        }
        if (countKeyword > 0) {
            formatted_Report.append(nodeKeyword);
            Report keywords = new Report();
            formatted_Report.append(keywords.getGroupedKeywordHit());
            // "<table><thead><tr><th>Artifact ID</th><th>Name</th><th>Size</th>
            // formatted_Report.append("</tbody></table>");
        }
        if (countHash > 0) {
            formatted_Report.append(nodeHash);
            Report hashset = new Report();
            formatted_Report.append(hashset.getGroupedHashsetHit());
        }

        if (countDevice > 0) {
            formatted_Report.append(nodeDevice);
            formatted_Report.append("</tbody></table>");
        }
       
        if (countEmail > 0) {
            formatted_Report.append(nodeEmail);
            Report email = new Report();
            formatted_Report.append(email.getGroupedEmailHit());
        }
       
        if (countWebSearch > 0) {
            formatted_Report.append(nodeWebSearch);
            formatted_Report.append("</tbody></table>");
        }
        //end of master loop
        formatted_Report.append("</div></div></body></html>");
        formatted_header.append(formatted_Report);
        // unformatted_header.append(formatted_Report); 
        try {
            htmlPath = currentCase.getCaseDirectory() + File.separator + "Reports" + File.separator + caseName + "-" + datenotime + ".html";
            this.save(htmlPath);

        } catch (Exception e) {

            Logger.getLogger(ReportHTML.class.getName()).log(Level.WARNING, "Could not write out HTML report! ", e);
        }
        return htmlPath;
    }

    @Override
    public String getName() {
        String name = "HTML";
        return name;
    }

    @Override
    public void save(String path) {
        try {
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "UTF-8"));
            out.write(formatted_header.toString());
            out.flush();
            out.close();
        } catch (IOException e) {
            Logger.getLogger(ReportHTML.class.getName()).log(Level.WARNING, "Could not write out HTML report!", e);
        }

    }

    @Override
    public String getReportType() {
        String type = "HTML";
        return type;
    }

    @Override
    public String getExtension() {
        String ext = ".html";
        return ext;
    }

    @Override
    public ReportConfiguration GetReportConfiguration() {
        return config;
    }

    @Override
    public String getReportTypeDescription() {
        String desc = "This is an html formatted report that is meant to be viewed in a modern browser.";
        return desc;
    }

    @Override
    public void getPreview(String path) {
        BrowserControl.openUrl(path);
    }
}