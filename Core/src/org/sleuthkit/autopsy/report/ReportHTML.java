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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import org.openide.filesystems.FileUtil;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.*;

/**
 * Generates an HTML report for all the Blackboard Artifacts found in the current case.
 */
public class ReportHTML implements ReportModule {
    private static final Logger logger = Logger.getLogger(ReportHTML.class.getName());
    private final String INGEST_WARNING = "<h2 style=\"color: red;\">Warning, this report was run before ingest services completed!</h2>";
    private final String HTML_META = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
                                   + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">\n";
    private final String CSS = "<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n";
    private final String TABLE_FOOT = "</tbody></table>";
    private final String HTML_FOOT = "</body>\n</html>";
    
    private ReportConfiguration config;
    private int reportSize;
    private static ReportHTML instance = null;
    private Case currentCase = Case.getCurrentCase();
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();
    
    //private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> general = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> bookmarks = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> cookies = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> history = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> downloads = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> recent = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> trackpoint = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> installed = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> keywords = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    //private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> hash = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> devices = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    //private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> email = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> search = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> exif = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> fileBookmarks = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
    
    //private int countGeneral;
    private int countBookmarks;
    private int countCookies;
    private int countHistory;
    private int countDownloads;
    private int countRecent;
    private int countTrackpoint;
    private int countInstalled;
    private int countKeywords;
    //private int countHash;
    private int countDevices;
    //private int countEmails;
    private int countSearch;
    private int countExif;
    private int countFileBookmarks;

    ReportHTML() {
    }

    public static synchronized ReportHTML getDefault() {
        if (instance == null) {
            instance = new ReportHTML();
        }
        return instance;
    }

    /**
     * Generate all the data needed for the report.
     * 
     * @return path to the index.html file to be opened
     */
    @Override
    public String generateReport(ReportConfiguration reportConfig) throws ReportModuleException {
        // Reporting variables
        config = reportConfig;
        ReportGen reportGen = new ReportGen();
        reportGen.populateReport(reportConfig);
        HashMap<BlackboardArtifact, List<BlackboardAttribute>> report = reportGen.getResults();
        reportSize = report.size();
        
        // The report output
        DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);
        
        String caseName = currentCase.getName();
        String htmlFolder = currentCase.getCaseDirectory() + File.separator + "Reports" + File.separator + caseName + "-" + datenotime + File.separator;
        try {
            FileUtil.createFolder(new File(htmlFolder));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to make HTML report folder.");
        }
        
        // For every type of artifact, group that type into it's own set
        //general = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        bookmarks = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        cookies = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        history = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        downloads = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        recent = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        trackpoint = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        installed = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        keywords = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        //hash = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        devices = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        //email = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        search = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        exif = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        fileBookmarks = new LinkedHashSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>();
        
        for (Entry<BlackboardArtifact, List<BlackboardAttribute>> entry : report.entrySet()) {
            if (ReportFilter.cancel == true) {
                break;
            }
            //if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()) {
            //    general.add(entry);
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
                bookmarks.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {
                cookies.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {
                history.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
                downloads.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
                recent.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getTypeID()) {
                trackpoint.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                installed.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                keywords.add(entry);
            //} else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
            //    hash.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                devices.add(entry);
            //} else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
            //    email.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
                search.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()){
                exif.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()){
                fileBookmarks.add(entry);
            }
        }
        
        // Get the sizes for each type
        //countGeneral = general.size();
        countBookmarks = bookmarks.size();
        countCookies = cookies.size();
        countHistory = history.size();
        countDownloads = downloads.size();
        countRecent = recent.size();
        countTrackpoint = trackpoint.size();
        countInstalled = installed.size();
        countKeywords = keywords.size();
        //countHash = hash.size();
        countDevices = devices.size();
        //countEmails = email.size();
        countSearch = search.size();
        countExif = exif.size();
        countFileBookmarks = fileBookmarks.size();

        save(htmlFolder);
        return htmlFolder + "index.html";
    }
    
    /**
     * Write the index.css stylesheet.
     * @param folder path to output folder
     */
    private void writeCss(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "index.css"), "UTF-8"));
            String css = "body {padding: 30px; margin: 0; background: #FFFFFF; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353;} \n"
                       + "h1 {font-size: 26px; color: #005577; margin: 0 0 20px 0;} \n"
                       + "h2 {font-size: 20px; font-weight: normal; color: #0077aa; margin: 40px 0 10px 0; padding: 0 0 10px 0; border-bottom: 1px solid #dddddd;} \n"
                       + "h3 {font-size: 16px; color: #0077aa; margin: 40px 0 10px 0;} \n"
                       + "ul.nav {list-style-type: none; line-height: 35px; padding: 0px;} \n"
                       + "ul.nav li a {font-size: 14px; color: #444; text-shadow: #e9f9fd 0 1px 0; text-decoration: none; padding-left: 25px;} \n"
                       + "ul.nav li a:hover {text-decoration: underline;} \n"
                       + "p {margin: 0 0 20px 0;} \n"
                       + "table {max-width: 100%; min-width: 700px; padding: 0; margin: 0; border-collapse: collapse; border-bottom: 1px solid #e5e5e5;} \n"
                       + "table thead th {display: table-cell; text-align: left; padding: 8px 16px; background: #e5e5e5; color: #777; font-size: 11px; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede;} \n"
                       + "/*table tr th:nth-child(1) {text-align: center; width: 60px;}*/ \n"
                       + "table td {display: table-cell; padding: 8px 16px; font: 13px/20px Arial, Helvetica, sans-serif; max-width: 500px; min-width: 125px; word-break: break-all; overflow: auto;} \n"
                       + "table tr:nth-child(even) td {background: #f3f3f3;} \n"
                       + "/*table tr td:nth-child(1) {text-align: left; width: 60px; background: #f3f3f3;}*/ \n"
                       + "/*table tr:nth-child(even) td:nth-child(1) {background: #eaeaea;}*/ \n";
            out.write(css);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find index.css file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing index.css.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.css.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    private String generateHead(String title) {
        return HTML_META + "<head>\n" + CSS +
                "<title>" + title + "</title>\n" +  "</head>\n<body>\n";
    }
    
    private String getTableHead(String... label) {
        String header = "<table><thead><tr>\n";
        for(int i=0; i<label.length; i++) {
            header += "<th>" + label[i] + "</th>";
        }
        header += "</tr></thead>\n<tbody>";
        return header;
    }
    
    /**
     * Write the index.css stylesheet.
     * @param folder path to output folder
     */
    private void writeIndex(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "index.html"), "UTF-8"));
            out.write("<head>\n" + CSS + "<title>Report for " + currentCase.getName() + "</title>\n" +  "</head>\n");
            StringBuilder index = new StringBuilder();
            
            index.append("<frameset cols=\"300px,*\">\n");
            index.append("<frame src=\"nav.html\" name=\"nav\">\n");
            index.append("<frame src=\"summary.html\" name=\"content\">\n");
            index.append("<noframes>Your browser is not compatible with out frame setup.<br />\n");
            index.append("Please see <a href=\"nav.html\">the navigation page</a> for artifact links,<br />\n");
            index.append("and <a href=\"summary.html\">the summary page</a> for a case summary.</noframes>\n");
            index.append("</frameset>\n");
            
            
            out.write(index.toString());
            out.write("</html>");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find index.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing index.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the navigation menu nav.html file.
     * @param folder path to output folder
     */
    private void writeNav(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "nav.html"), "UTF-8"));
            out.write(generateHead("Report Navigation"));
            StringBuilder nav = new StringBuilder();
            nav.append("<h2>Report Navigation</h2>\n");
            nav.append("<ul class=\"nav\">\n");
            nav.append("<li><a href=\"summary.html\" target=\"content\">Case Summary</a></li>\n");
            //if(countGeneral > 0) {
            //    nav.append("<a href=\"general.html\" target=\"content\">General Information (").append(countGeneral).append(")</a><br />\n");
            //}
            if(countBookmarks > 0) {
                nav.append("<li><a href=\"bookmarks.html\" target=\"content\">Web Bookmarks (").append(countBookmarks).append(")</a></li>\n");
            }
            if(countCookies > 0) {
                nav.append("<li><a href=\"cookies.html\" target=\"content\">Web Cookies (").append(countCookies).append(")</a></li>\n");
            }
            if(countHistory > 0) {
                nav.append("<li><a href=\"history.html\" target=\"content\">Web History (").append(countHistory).append(")</a></li>\n");
            }
            if(countDownloads > 0) {
                nav.append("<li><a href=\"downloads.html\" target=\"content\">Web Downloads (").append(countDownloads).append(")</a></li>\n");
            }
            if(countRecent > 0) {
                nav.append("<li><a href=\"recent.html\" target=\"content\">Recent Documents (").append(countRecent).append(")</a></li>\n");
            }
            if(countTrackpoint > 0) {
                nav.append("<li><a href=\"trackpoint.html\" target=\"content\">Trackpoint (").append(countTrackpoint).append(")</a></li>\n");
            }
            if(countInstalled > 0) {
                nav.append("<li><a href=\"installed.html\" target=\"content\">Installed Programs (").append(countInstalled).append(")</a></li>\n");
            }
            if(countKeywords > 0) {
                nav.append("<li><a href=\"keywords.html\" target=\"content\">Keyword Hits (").append(countKeywords).append(")</a></li>\n");
            }
            //if(countHash > 0) {
            //    nav.append("<li><a href=\"hash.html\" target=\"content\">Hashset Hits (").append(countHash).append(")</a></li>\n");
            //}
            if(countDevices > 0) {
                nav.append("<li><a href=\"devices.html\" target=\"content\">Devices Attached (").append(countDevices).append(")</a></li>\n");
            }
            //if(countEmails > 0) {
            //    nav.append("<li><a href=\"emails.html\" target=\"content\">Emails (").append(countEmails).append(")</a></li>\n");
            //}
            if(countSearch > 0) {
                nav.append("<li><a href=\"search.html\" target=\"content\">Web Search Queries (").append(countSearch).append(")</a></li>\n");
            }
            if(countExif > 0) {
                nav.append("<li><a href=\"exif.html\" target=\"content\">Exif Metadata (").append(countExif).append(")</a></li>\n");
            }
            if(countFileBookmarks > 0) {
                nav.append("<li><a href=\"filebookmarks.html\" target=\"content\">File Bookmarks (").append(countFileBookmarks).append(")</a></li>\n");
            }
            nav.append("</ul>\n");
            out.write(nav.toString());
            out.write(HTML_FOOT);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find index.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing index.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the case summary summary.html file.
     * @param folder path to output folder
     */
    private void writeSummary(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "summary.html"), "UTF-8"));
            out.write(generateHead("Case Summary"));
            
            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            String datetime = datetimeFormat.format(date);
            
            String caseName = currentCase.getName();
            String examiner = currentCase.getExaminer();
            String number = currentCase.getNumber();
            Integer imagecount = currentCase.getImageIDs().length;
            Integer filesystemcount = currentCase.getRootObjectsCount();
            Integer totalfiles = 0;
            Integer totaldirs = 0;
            try {
                totaldirs = skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR);
                totalfiles = skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Could not get FsContentType counts from TSK ", ex);
            }
            
            StringBuilder summary = new StringBuilder();
            if (IngestManager.getDefault().isIngestRunning() || IngestManager.getDefault().areModulesRunning()) {
                summary.append(INGEST_WARNING);
            }
            summary.append("<h3>Report for Case: ").append(caseName).append("</h3>\n");
            summary.append("<p>HTML Report Generated by <strong>Autopsy 3</strong> on ").append(datetime).append("\n");
            summary.append("<ul>\n");
            summary.append("<li>Examiner: ").append(examiner).append("</li>\n");
            summary.append("<li>Number: ").append(number).append("</li>\n");
            summary.append("<li># of Images: ").append(imagecount).append("</li>\n");
            summary.append("<li>FileSystems: ").append(filesystemcount).append("</li>\n");
            summary.append("<li># of Files: ").append(totalfiles.toString()).append("</li>\n");
            summary.append("<li># of Dirs: ").append(totaldirs.toString()).append("</li>\n");
            summary.append("<li># of Artifacts: ").append(reportSize).append("</li>\n");
            summary.append("</ul>\n");
            out.write(summary.toString());
            out.write(HTML_FOOT);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find summary.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing summary.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for summary.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Given a list of blackboard attributes, return a map of those
     * attributes to their attribute_type_id.
     */
    private TreeMap<Integer, String> getAttributes(List<BlackboardAttribute> attList) {
        TreeMap<Integer, String> attributes = new TreeMap<Integer, String>();
        int size = BlackboardAttribute.ATTRIBUTE_TYPE.values().length;
        for (int n = 0; n <= size; n++) {
            attributes.put(n, "");
        }
        for (BlackboardAttribute tempatt : attList) {
            if (ReportFilter.cancel == true) {
                break;
            }
            String value = "";
            Integer type = tempatt.getAttributeTypeID();
            if (type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()) || type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID())) {

                SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                value = sdf.format(new java.util.Date((tempatt.getValueLong() * 1000)));

            } else {
                value = tempatt.getValueString();
            }
            if (value == null || value.isEmpty()) {
                value = "";
            }
            value = EscapeUtil.escapeHtml(value);
            // Inserts a zero-width space for breaking purposes:
            //value = ReportUtils.insertPeriodically(value, "&#8203;", 500);
            attributes.put(type, value);
        }
        return attributes;
    }
    
    /**
     * Given an obj_id, return the AbstractFile with this id.
     */
    private AbstractFile getFile(long objId) {
        AbstractFile file = null;
        try {
            file = skCase.getAbstractFileById(objId);
        } catch (TskCoreException ex) {
            Logger.getLogger(ReportHTML.class.getName()).log(Level.WARNING, "Could not get AbstractFile from TSK ", ex);
        }
        return file;
    }
    
    /**
     * Write the bookmarks.html file.
     * @param folder path to output folder
     */
    private void writeBookmark(String folder) {
        Writer out = null;
        try {
            // Get the writer for the HTML file
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "bookmarks.html"), "UTF-8"));
            // Write the HTML title
            out.write(generateHead("Web Bookmark Artifacts (" + countBookmarks + ")"));
            // Write the title for the artifact and the top of the table
            String title = "<h3>Web Bookmarks (" + countBookmarks + ")</h3>\n";
            String tableHeader = getTableHead("URL", "Title", "Program", "Path");
            out.write(title);
            out.write(tableHeader);
            
            // For every artifact we have, add a row
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: bookmarks) {
                if (ReportFilter.cancel == true) { break; }
                // Get the AbstractFile that belongs to this artifact
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                // Use the helper to get all the attributes for this artifact,
                // and map them to their attribute_type_id
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                // Write the row to file, so we don't get too cluttered
                out.write(row.toString());
            }
            // Write the bottom of the table, and the end of the HTML page
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for bookmarks.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find bookmarks.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing bookmarks.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for bookmarks.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the cookies.html file.
     * @param folder path to output folder
     */
    private void writeCookie(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "cookies.html"), "UTF-8"));
            out.write(generateHead("Web Cookie Artifacts (" + countCookies + ")"));
            String title = "<h3>Web Cookies (" + countCookies + ")</h3>\n";
            String tableHeader = getTableHead("URL", "Date", "Name", "Value", "Program", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: cookies) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for cookies.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find cookies.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing cookies.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for cookies.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
        
    }
    
    /**
     * Write the history.html file.
     * @param folder path to output folder
     */
    private void writeHistory(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "history.html"), "UTF-8"));
            out.write(generateHead("Web History Artifacts (" + countHistory + ")"));
            String title = "<h3>Web History (" + countHistory + ")</h3>\n";
            String tableHeader = getTableHead("URL", "Date", "Referrer", "Name", "Program", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: history) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for history.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find history.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing history.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for history.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the downloads.html file.
     * @param folder path to output folder
     */
    private void writeDownload(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "downloads.html"), "UTF-8"));
            out.write(generateHead("Web Download Artifacts (" + countDownloads + ")"));
            String title = "<h3>Web Downloads (" + countDownloads + ")</h3>\n";
            String tableHeader = getTableHead("URL", "Source", "Time", "Program", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: downloads) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for downloads.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find downloads.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing downloads.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for downloads.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the recent.html file.
     * @param folder path to output folder
     */
    private void writeRecent(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "recent.html"), "UTF-8"));
            out.write(generateHead("Recent Document Artifacts (" + countRecent + ")"));
            String title = "<h3>Recent Documents (" + countRecent + ")</h3>\n";
            String tableHeader = getTableHead("Name", "Related Shortcut", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: recent) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td><strong>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID())).append("</strong></td>\n");
                row.append("<td>").append(file !=null ? file.getName() : "").append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for recent.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find recent.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing recent.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for recent.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the trackpoint.html file.
     * @param folder path to output folder
     */
    private void writeTrackpoint(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "trackpoint.html"), "UTF-8"));
            out.write(generateHead("Track Point Artifacts (" + countTrackpoint + ")"));
            String title = "<h3>Track Points (" + countTrackpoint + ")</h3>\n";
            String tableHeader = getTableHead("Object ID", "Name", "Size", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: trackpoint) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                Long fileSize = file.getSize();
                
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(objId.toString()).append("</td>\n");
                row.append("<td><strong>").append(file != null ? file.getName().toString() : "").append("</strong></td>\n");
                row.append("<td>").append(fileSize.toString()).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for trackpoint.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find trackpoint.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing trackpoint.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for trackpoint.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the installed.html file.
     * @param folder path to output folder
     */
    private void writeInstalled(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "installed.html"), "UTF-8"));
            out.write(generateHead("Installed Program Artifacts (" + countInstalled + ")"));
            String title = "<h3>Installed Programs (" + countInstalled + ")</h3>\n";
            String tableHeader = getTableHead("Program Name", "Install Date/Time", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: installed) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td><strong>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</strong></td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for installed.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find installed.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing installed.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for installed.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the keywords.html file.
     * @param folder path to output folder
     */
    private void writeKeyword(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "keywords.html"), "UTF-8"));
            out.write(generateHead("Keyword Hit Artifacts (" + countKeywords + ")"));
            String title = "<h3>Keyword Hits (" + countKeywords + ")</h3>\n";
            out.write(title);
            
            Report key = new Report();
            key.getGroupedKeywordHit(out);
            
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find keywords.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing keywords.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for keywords.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the device.html file.
     * @param folder path to output folder
     */
    private void writeDevice(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "devices.html"), "UTF-8"));
            out.write(generateHead("Attached Device Artifacts (" + countDevices + ")"));
            String title = "<h3>Attached Devices (" + countDevices + ")</h3>\n";
            String tableHeader = getTableHead("Name", "Serial #", "Time", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: devices) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td><strong>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID())).append("</strong></td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for devices.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find devices.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing devices.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for devices.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the search.html file.
     * @param folder path to output folder
     */
    private void writeSearch(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "search.html"), "UTF-8"));
            out.write(generateHead("Web Search Query Artifacts (" + countSearch + ")"));
            String title = "<h3>Web Search Queries (" + countSearch + ")</h3>\n";
            String tableHeader = getTableHead("Program Name", "Domain", "Text", "Last Modified", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: search) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for search.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find search.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing search.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for search.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the exif.html file.
     * @param folder path to output folder
     */
    private void writeExif(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "exif.html"), "UTF-8"));
            out.write(generateHead("Exif Metadata Artifacts (" + countExif + ")"));
            String title = "<h3>Exif Metadata (" + countExif + ")</h3>\n";
            String tableHeader = getTableHead("File Name", "Date Taken", "Device Manufacturer", "Device Model", "Latitude", "Longitude", "Altitude", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: exif) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(file != null ? file.getName() : "").append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID())).append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for exif.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find exif.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing exif.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for exif.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the filebookmarks.html file.
     * @param folder path to output folder
     */
    private void writeFileBookmarks(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "filebookmarks.html"), "UTF-8"));
            out.write(generateHead("File Bookmarks (" + countFileBookmarks + ")"));
            String title = "<h3>File Bookmarks (" + countFileBookmarks + ")</h3>\n";
            String tableHeader = getTableHead("Description", "File Name", "Path");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: fileBookmarks) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID())).append("</td>\n");
                row.append("<td>").append(file != null ? file.getName() : "").append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for filebookmarks.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find filebookmarks.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing filebookmarks.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for filebookmarks.html.");
        } finally {
            try {
                if(out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    @Override
    public String getName() {
        String name = "HTML";
        return name;
    }

    /**
     * Save this generated report in the given folder path.
     * @param path to report folder
     */
    @Override
    public void save(String path) {
        writeCss(path);
        writeIndex(path);
        writeNav(path);
        writeSummary(path);
        writeBookmark(path);
        writeCookie(path);
        writeHistory(path);
        writeDownload(path);
        writeRecent(path);
        writeTrackpoint(path);
        writeInstalled(path);
        writeKeyword(path);
        writeDevice(path);
        writeSearch(path);
        writeExif(path);
        writeFileBookmarks(path);
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