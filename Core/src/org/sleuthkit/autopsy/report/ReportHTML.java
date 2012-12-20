 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.*;

/**
 * Generates an HTML report for all the Blackboard Artifacts found in the current case.
 */
public class ReportHTML implements ReportModule {
    private static final Logger logger = Logger.getLogger(ReportHTML.class.getName());
    private final String INGEST_WARNING = "<span>Warning, this report was run before ingest services completed!</span>";
    private final String HTML_META = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
                                   + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">\n";
    private final String CSS = "<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n";
    private final String TABLE_FOOT = "</tbody></table>";
    private final String HTML_FOOT = "</div>\n</body>\n</html>";
    
    private ReportConfiguration config;
    private int reportSize;
    private static ReportHTML instance = null;
    private Case currentCase = Case.getCurrentCase();
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();
    
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> bookmarks;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> cookies;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> history;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> downloads;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> recent;
    //private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> trackpoint;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> installed;
    //private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> hash;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> devices;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> search;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> exif;
    private Set<Entry<BlackboardArtifact, List<BlackboardAttribute>>> userBookmarks;
    
    private int countBookmarks;
    private int countCookies;
    private int countHistory;
    private int countDownloads;
    private int countRecent;
    //private int countTrackpoint;
    private int countInstalled;
    private int countKeywords;
    private int countHash;
    private int countDevices;
    private int countSearch;
    private int countExif;
    private int countUserBookmarks;

    ReportHTML() {
    }

    public static synchronized ReportHTML getDefault() {
        if (instance == null) {
            instance = new ReportHTML();
        }
        return instance;
    }
    
    /**
     * Iterates through two artifacts' attributes, and compares them in order until
     * it finds one which is different from the other. If no differing attribute is
     * found, the comparator compares file unique paths, and then lastly the
     * artifact ID.
     */
    private class ArtifactComparator implements Comparator<Map.Entry<BlackboardArtifact, List<BlackboardAttribute>>> {

        @Override
        public int compare(Map.Entry<BlackboardArtifact, List<BlackboardAttribute>> art1, Map.Entry<BlackboardArtifact, List<BlackboardAttribute>> art2) {
            // Get all the attributes for each artifact
            int size = BlackboardAttribute.ATTRIBUTE_TYPE.values().length;
            TreeMap<Integer, String> att1 = getAttributes(art1.getValue());
            TreeMap<Integer, String> att2 = getAttributes(art2.getValue());
            
            // Compare the attributes one-by-one looking for differences
            for(int i=0; i < size; i++) {
                String a1 = att1.get(i);
                String a2 = att2.get(i);
                if((!a1.equals("") && !a2.equals("")) && a1.compareTo(a2) != 0) {
                    return a1.compareTo(a2);
                }
            }
            
            // If there are no differenct artifacts, compare the file path
            Long objId = art1.getKey().getObjectID();
            AbstractFile file1 = getFile(objId);
            objId = art2.getKey().getObjectID();
            AbstractFile file2 = getFile(objId);
            
            if(file1 != null && file2 !=null) {
                try {
                    int result = file1.getUniquePath().compareTo(file2.getUniquePath());
                    if(result != 0) {
                        return result;
                    }
                } catch (TskCoreException ex) { // Not a big deal, we'll compare artifact IDs
                }
            }
            
            // If that's the same, use the artifact ID
            if(art1.getKey().getArtifactID() < art2.getKey().getArtifactID()) {
                return -1;
            } else if(art1.getKey().getArtifactID() > art2.getKey().getArtifactID()) {
                return 1;
            } else {
                return 0;
            }
        }
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
        ArtifactComparator c = new ArtifactComparator();
        bookmarks = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        cookies = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        history = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        downloads = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        recent = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        //trackpoint = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        installed = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        //hash = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        devices = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        search = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        exif = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        userBookmarks = new TreeSet<Entry<BlackboardArtifact, List<BlackboardAttribute>>>(c);
        
        for (Entry<BlackboardArtifact, List<BlackboardAttribute>> entry : report.entrySet()) {
            if (ReportFilter.cancel == true) {
                break;
            }
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
            //} else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getTypeID()) {
            //    trackpoint.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                installed.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                countKeywords++;
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                countHash++;
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                devices.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
                search.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()){
                exif.add(entry);
            } else if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() ||
                    entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()){
                userBookmarks.add(entry);
            }
        }
        
        
        
        // Get the sizes for each type
        countBookmarks = bookmarks.size();
        countCookies = cookies.size();
        countHistory = history.size();
        countDownloads = downloads.size();
        countRecent = recent.size();
        //countTrackpoint = trackpoint.size();
        countInstalled = installed.size();
        //countHash = hash.size();
        countDevices = devices.size();
        countSearch = search.size();
        countExif = exif.size();
        countUserBookmarks = userBookmarks.size();

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
            String css = "body {margin: 0px; padding: 0px; background: #FFFFFF; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353;}\n" +
                         "#content {padding: 30px;}\n" +
                         "#header {width:100%; padding: 10px; line-height: 25px; background: #07A; color: #FFF; font-size: 20px;}\n" +
                         "h1 {font-size: 20px; font-weight: normal; color: #07A; padding: 0 0 7px 0; border-bottom: 1px solid #D6D6D6;}\n" +
                         "h2 {font-size: 20px; font-weight: bolder; color: #07A;}\n" +
                         "h3 {font-size: 16px; color: #07A;}\n" +
                         "ul.nav {list-style-type: none; line-height: 35px; padding: 0px; margin-left: 15px;}\n" +
                         "ul li a {font-size: 14px; color: #444; text-decoration: none; padding-left: 25px;}\n" +
                         "ul li a:hover {text-decoration: underline;}\n" +
                         "p {margin: 0 0 20px 0;}\n" +
                         ".keyword_list td.keyword {background: #07A; color: #FFF; font-size: 16px; padding: 3px; padding-left: 15px;}\n" +
                         ".keyword_list td.blank {background: #FFF; padding: 20px; border-top: 1px solid #07A;}\n" +
                         "table {max-width: 100%; min-width: 700px; padding: 0; margin: 0; border-collapse: collapse; border-bottom: 1px solid #e5e5e5;}\n" +
                         ".keyword_list table {margin-left: 25px;}\n" +
                         "table th {display: table-cell; text-align: left; padding: 8px 16px; background: #e5e5e5; color: #777; font-size: 11px; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede;}\n" +
                         "table td {display: table-cell; padding: 8px 16px; font: 13px/20px Arial, Helvetica, sans-serif; max-width: 500px; min-width: 125px; word-break: break-all; overflow: auto;}\n" +
                         "table tr:nth-child(even) td {background: #f3f3f3;}";
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
            StringBuilder head = new StringBuilder();
            head.append("<head>\n" + CSS + "<title>Autopsy Report for case ").append(currentCase.getName()).append("</title>\n");
            head.append("<link rel=\"icon\" type=\"image/ico\" href=\"favicon.ico\" />\n");
            head.append("</head>\n");
            out.write(head.toString());
            StringBuilder index = new StringBuilder();
            
            index.append("<frameset cols=\"300px,*\">\n");
            index.append("<frame src=\"nav.html\" name=\"nav\">\n");
            index.append("<frame src=\"summary.html\" name=\"content\">\n");
            index.append("<noframes>Your browser is not compatible with our frame setup.<br />\n");
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
            nav.append("<div id=\"content\">\n<h1>Report Navigation</h1>\n");
            nav.append("<ul class=\"nav\">\n");
            nav.append("<li style=\"background: url(summary.png) left center no-repeat;\"><a href=\"summary.html\" target=\"content\">Case Summary</a></li>\n");
            if(countBookmarks > 0) {
                nav.append("<li style=\"background: url(bookmarks.png) left center no-repeat;\"><a href=\"bookmarks.html\" target=\"content\">Web Bookmarks (").append(countBookmarks).append(")</a></li>\n");
            }
            if(countCookies > 0) {
                nav.append("<li style=\"background: url(cookies.png) left center no-repeat;\"><a href=\"cookies.html\" target=\"content\">Web Cookies (").append(countCookies).append(")</a></li>\n");
            }
            if(countHistory > 0) {
                nav.append("<li style=\"background: url(history.png) left center no-repeat;\"><a href=\"history.html\" target=\"content\">Web History (").append(countHistory).append(")</a></li>\n");
            }
            if(countDownloads > 0) {
                nav.append("<li style=\"background: url(downloads.png) left center no-repeat;\"><a href=\"downloads.html\" target=\"content\">Web Downloads (").append(countDownloads).append(")</a></li>\n");
            }
            if(countSearch > 0) {
                nav.append("<li style=\"background: url(search.png) left center no-repeat;\"><a href=\"search.html\" target=\"content\">Web Search Queries (").append(countSearch).append(")</a></li>\n");
            }
            if(countRecent > 0) {
                nav.append("<li style=\"background: url(recent.png) left center no-repeat;\"><a href=\"recent.html\" target=\"content\">Recent Documents (").append(countRecent).append(")</a></li>\n");
            }
            //if(countTrackpoint > 0) {
            //    nav.append("<li style=\"background: url(trackpoint.png) left center no-repeat;\"><a href=\"trackpoint.html\" target=\"content\">Trackpoint (").append(countTrackpoint).append(")</a></li>\n");
            //}
            if(countInstalled > 0) {
                nav.append("<li style=\"background: url(installed.png) left center no-repeat;\"><a href=\"installed.html\" target=\"content\">Installed Programs (").append(countInstalled).append(")</a></li>\n");
            }
            if(countKeywords > 0) {
                nav.append("<li style=\"background: url(keywords.png) left center no-repeat;\"><a href=\"keywords.html\" target=\"content\">Keyword Hits (").append(countKeywords).append(")</a></li>\n");
            }
            if(countHash > 0) {
                nav.append("<li style=\"background: url(hash.png) left center no-repeat;\"><a href=\"hash.html\" target=\"content\">Hashset Hits (").append(countHash).append(")</a></li>\n");
            }
            if(countDevices > 0) {
                nav.append("<li style=\"background: url(devices.png) left center no-repeat;\"><a href=\"devices.html\" target=\"content\">Devices Attached (").append(countDevices).append(")</a></li>\n");
            }
            if(countExif > 0) {
                nav.append("<li style=\"background: url(exif.png) left center no-repeat;\"><a href=\"exif.html\" target=\"content\">Exif Metadata (").append(countExif).append(")</a></li>\n");
            }
            if(countUserBookmarks > 0) {
                nav.append("<li style=\"background: url(userbookmarks.png) left center no-repeat;\"><a href=\"userbookmarks.html\" target=\"content\">User Bookmarks (").append(countUserBookmarks).append(")</a></li>\n");
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
            StringBuilder head = new StringBuilder(HTML_META);
            head.append("<head>\n<title>Case Summary</title>\n");
            head.append("<style type=\"text/css\">\n");
            head.append("body { padding: 0px; margin: 0px; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353; }\n");
            head.append("#wrapper { width: 90%; margin: 0px auto; margin-top: 35px; }\n");
            head.append("h1 { color: #07A; font-size: 36px; line-height: 42px; font-weight: normal; margin: 0px; border-bottom: 1px solid #81B9DB; }\n");
            head.append("h1 span { color: #F00; display: block; font-size: 16px; font-weight: bold; line-height: 22px; }\n");
            head.append("h2 { padding: 0 0 3px 0; margin: 0px; color: #07A; font-weight: normal; border-bottom: 1px dotted #81B9DB; }\n");
            head.append("table td { padding-right: 25px; }\n");
            head.append("p.subheadding { padding: 0px; margin: 0px; font-size: 11px; color: #B5B5B5; }\n");
            head.append(".title { width: 660px; margin-bottom: 50px; }\n");
            head.append(".left { float: left; width: 250px; margin-top: 20px; text-align: center; }\n");
            head.append(".left img { max-width: 250px; max-height: 250px; min-width: 200px; min-height: 200px; }\n");
            head.append(".right { float: right; width: 385px; margin-top: 25px; font-size: 14px; }\n");
            head.append(".clear { clear: both; }\n");
            head.append(".info p { padding: 3px 10px; background: #e5e5e5; color: #777; font-size: 12px; font-weight: bold; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede; }\n");
            head.append(".info table { margin: 0 25px 20px 25px; }\n");
            head.append("</style>\n");
            head.append("</head>\n<body>\n");
            out.write(head.toString());
            
            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            String datetime = datetimeFormat.format(date);
            
            String caseName = currentCase.getName();
            String caseNumber = currentCase.getNumber();
            String examiner = currentCase.getExaminer();
            int imagecount = currentCase.getImageIDs().length;
            
            
            StringBuilder summary = new StringBuilder();
            boolean running = false;
            if (IngestManager.getDefault().isIngestRunning() || IngestManager.getDefault().areModulesRunning()) {
                running = true;
            }
            
            summary.append("<div id=\"wrapper\">\n");
            summary.append("<h1>Autopsy Forensic Report").append(running ? INGEST_WARNING : "").append("</h1>\n");
            summary.append("<p class=\"subheadding\">HTML Report Generated on ").append(datetime).append("</p>\n");
            summary.append("<div class=\"title\">\n");
            summary.append("<div class=\"left\">\n");
            summary.append("<img src=\"logo.png\" />\n");
            summary.append("</div>\n");
            summary.append("<div class=\"right\">\n");
            summary.append("<table>\n");
            summary.append("<tr><td>Case:</td><td>").append(caseName).append("</td></tr>\n");
            summary.append("<tr><td>Case Number:</td><td>").append(!caseNumber.isEmpty() ? caseNumber : "<i>No case number</i>").append("</td></tr>\n");
            summary.append("<tr><td>Examiner:</td><td>").append(!examiner.isEmpty() ? examiner : "<i>No examiner</i>").append("</td></tr>\n");
            summary.append("<tr><td># of Images:</td><td>").append(imagecount).append("</td></tr>\n");
            summary.append("</table>\n");
            summary.append("</div>\n");
            summary.append("<div class=\"clear\"></div>\n");
            summary.append("</div>\n");
            summary.append("<h2>Image Information:</h2>\n");
            summary.append("<div class=\"info\">\n");
            try {
                Image[] images = new Image[imagecount];
                for(int i=0; i<imagecount; i++) {
                    images[i] = skCase.getImageById(currentCase.getImageIDs()[i]);
                }
                for(Image img : images) {
                    summary.append("<p>").append(img.getName()).append("</p>\n");
                    summary.append("<table>\n");
                    summary.append("<tr><td>Timezone:</td><td>").append(img.getTimeZone()).append("</td></tr>\n");
                    for(String path : img.getPaths()) {
                        summary.append("<tr><td>Path:</td><td>").append(path).append("</td></tr>\n");
                    }
                    summary.append("</table>\n");
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get image information for the HTML report.");
            }
            summary.append("</div>\n");
            summary.append("</div>\n");
            summary.append("</body></html>");
            out.write(summary.toString());
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
            } else if(type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()) ||
                    type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()) ||
                    type.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID())) {
                value = Double.toString(tempatt.getValueDouble());
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
            String title = "<div id=\"header\">Web Bookmarks (" + countBookmarks + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("URL", "Title", "Date Accessed", "Program", "Source File");
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
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID())).append("</td>\n");
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
            String title = "<div id=\"header\">Web Cookies (" + countCookies + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("URL", "Date/Time", "Name", "Value", "Program", "Source File");
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
            String title = "<div id=\"header\">Web History (" + countHistory + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("URL", "Date Accessed", "Referrer", "Name", "Program", "Source File");
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
            String title = "<div id=\"header\">Web Downloads (" + countDownloads + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Destination", "Source URL", "Date Accessed", "Program", "Source File");
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
            String title = "<div id=\"header\">Recent Documents (" + countRecent + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Path", "Source File");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: recent) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID())).append("</td>\n");
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
     
    private void writeTrackpoint(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "trackpoint.html"), "UTF-8"));
            out.write(generateHead("Track Point Artifacts (" + countTrackpoint + ")"));
            String title = "<div id=\"header\">Track Points (" + countTrackpoint + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Object ID", "Name", "Size", "Source File");
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
                row.append("<td>").append(file != null ? file.getName().toString() : "").append("</td>\n");
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
    }*/
    
    /**
     * Write the installed.html file.
     * @param folder path to output folder
     */
    private void writeInstalled(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "installed.html"), "UTF-8"));
            out.write(generateHead("Installed Program Artifacts (" + countInstalled + ")"));
            String title = "<div id=\"header\">Installed Programs (" + countInstalled + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Program Name", "Install Date/Time", "Source File");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: installed) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
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
            String title = "<div id=\"header\">Keyword Hits (" + countKeywords + ")</div>\n<div id=\"content\">\n";
            out.write(title);
            
            ResultSet lists = skCase.runQuery("SELECT att.value_text AS list " +
                                              "FROM blackboard_attributes AS att, blackboard_artifacts AS art " +
                                              "WHERE att.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " " +
                                                    "AND art.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + " " +
                                                    "AND att.artifact_id = art.artifact_id " + 
                                              "GROUP BY list");
            StringBuilder keywordLists = new StringBuilder();
            keywordLists.append("<h3>Keyword Lists:</h3>\n<ul>");
            while(lists.next()) {
                if (ReportFilter.cancel == true) { break; }
                String list = lists.getString("list");
                if(list.isEmpty()) {
                    keywordLists.append("<li><a href=\"#User Searches\">User Searches</a></li>\n");
                } else {
                    keywordLists.append("<li><a href=\"#").append(list).append("\">").append(list).append("</a></li>\n");
                }
            }
            keywordLists.append("</ul>");
            out.write(keywordLists.toString());
            
            ResultSet rs = skCase.runQuery("SELECT art.obj_id, att1.value_text AS keyword, att2.value_text AS preview, att3.value_text AS list " +
                                           "FROM blackboard_artifacts AS art, blackboard_attributes AS att1, blackboard_attributes AS att2, blackboard_attributes AS att3 " +
                                           "WHERE (att1.artifact_id = art.artifact_id) " +
                                                 "AND (att2.artifact_id = art.artifact_id) " + 
                                                 "AND (att3.artifact_id = art.artifact_id) " + 
                                                 "AND (att1.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID() + ") " +
                                                 "AND (att2.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID() + ") " +
                                                 "AND (att3.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") " +
                                                 "AND (art.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + ") " +
                                           "ORDER BY list, keyword");
            String currentKeyword = "";
            String currentList = "";
            while (rs.next()) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = rs.getLong("obj_id");
                String keyword = rs.getString("keyword");
                String preview = rs.getString("preview");
                String list = rs.getString("list");
                
                AbstractFile file = null;
                try {
                    file = skCase.getAbstractFileById(objId);
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Could not get AbstractFile from TSK ", ex);
                }
                StringBuilder table = new StringBuilder();
                
                if((!list.equals(currentList) && !list.isEmpty()) || (list.isEmpty() && !currentList.equals("User Searches"))) {
                    if(!currentList.isEmpty()) {
                        table.append("</table></div>");
                    }
                    currentList = list.isEmpty() ? "User Searches" : list;
                    currentKeyword = ""; // reset the current keyword because it's a new list
                    table.append("<br /><br />\n");
                    table.append("<h1><a name=\"").append(currentList).append("\">").append(currentList).append("</a></h1>\n");
                    table.append("<div class=\"keyword_list\"><table style=\"border-bottom: 1px solid #07A;\">");
                }
                if (!keyword.equals(currentKeyword)) {
                    if(!currentKeyword.equals("")) {
                        table.append("<tr><td colspan=\"3\" class=\"blank\"></td></tr>\n");
                    }
                    currentKeyword = keyword;
                    table.append("<tr><td colspan=\"3\" class=\"keyword\">").append(currentKeyword).append("</td></tr>\n");
                    table.append("<tr><th>File Name</th><th>Preview</th><th>Path</th></tr>\n");
                }
                table.append("<tr><td>").append(file.getName()).append("</td>\n");
                String previewreplace = EscapeUtil.escapeHtml(preview);
                table.append("<td>").append(previewreplace.replaceAll("<!", "")).append("</td>").append("<td>").append(file != null ? file.getUniquePath() : "").append("</td>").append("</tr>\n");
                out.write(table.toString());
            }
            out.write("</table><br /><br />");
            
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get tsk file information for keywords.html.");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Unable to query database for keyword hits.");
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
            String title = "<div id=\"header\">Attached Devices (" + countDevices + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Name", "Device ID", "Date/Time", "Source File");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: devices) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID())).append("</td>\n");
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
            String title = "<div id=\"header\">Web Search Queries (" + countSearch + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Text", "Domain", "Date Accessed", "Program Name", "Source File");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: search) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID())).append("</td>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())).append("</td>\n");
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
            String title = "<div id=\"header\">Exif Metadata (" + countExif + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("File Name", "Date Taken", "Device Manufacturer", "Device Model", "Latitude", "Longitude", "Source File");
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
     * Write the userbookmarks.html file.
     * @param folder path to output folder
     */
    private void writeUserBookmarks(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "userbookmarks.html"), "UTF-8"));
            out.write(generateHead("User Bookmarks (" + countUserBookmarks + ")"));
            String title = "<div id=\"header\">User Bookmarks (" + countUserBookmarks + ")</div>\n<div id=\"content\">\n";
            String tableHeader = getTableHead("Comment", "File Name", "Source File");
            out.write(title);
            out.write(tableHeader);
            
            for(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry: userBookmarks) {
                if (ReportFilter.cancel == true) { break; }
                Long objId = entry.getKey().getObjectID();
                AbstractFile file = getFile(objId);
                
                TreeMap<Integer, String> attributes = getAttributes(entry.getValue());
                StringBuilder row = new StringBuilder();
                row.append("<tr>\n");
                row.append("<td>").append(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID())).append("</td>\n");
                row.append("<td>").append(file != null ? file.getName() : "").append("</td>\n");
                row.append("<td>").append(file !=null ? file.getUniquePath() : "").append("</td>\n");
                row.append("</tr>\n");
                out.write(row.toString());
            }
            out.write(TABLE_FOOT);
            out.write(HTML_FOOT);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to get file's path for userbookmarks.html.");
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find userbookmarks.html file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing userbookmarks.hmtl.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for userbookmarks.html.");
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
     * Write the hashhits.html file
     * @param folder path to output folder
     */
    private void writeHashHits(String folder) {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "hash.html"), "UTF-8"));
            out.write(generateHead("Hash Hit Artifacts (" + countHash + ")"));
            String title = "<div id=\"header\">Hash Hits (" + countHash + ")</div>\n<div id=\"content\">\n";
            out.write(title);

            Report key = new Report();
            String HashsetTable = key.getGroupedHashsetHit();
            out.write(HashsetTable);

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
                if (out != null) {
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
        writeSearch(path);
        writeRecent(path);
        //writeTrackpoint(path);
        writeInstalled(path);
        writeKeyword(path);
        writeDevice(path);
        writeExif(path);
        writeUserBookmarks(path);
        writeHashHits(path);
        try {
            String dir = PlatformUtil.getUserConfigDirectory() + File.separator;
            
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "favicon.ico");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "logo.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "summary.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "bookmarks.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "cookies.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "history.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "downloads.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "search.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "recent.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "installed.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "keywords.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "devices.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "exif.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "userbookmarks.png");
            PlatformUtil.extractResourceToUserConfigDir(ReportHTML.class, "hash.png");
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "favicon.ico", path, "favicon", ".ico", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "logo.png", path, "logo", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "summary.png", path, "summary", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "bookmarks.png", path, "bookmarks", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "cookies.png", path, "cookies", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "history.png", path, "history", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "downloads.png", path, "downloads", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "search.png", path, "search", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "recent.png", path, "recent", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "installed.png", path, "installed", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "keywords.png", path, "keywords", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "devices.png", path, "devices", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "exif.png", path, "exif", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "userbookmarks.png", path, "userbookmarks", ".png", true);
            org.sleuthkit.autopsy.coreutils.FileUtil.copyFile(dir + "hash.png", path, "hash", ".png", true);
        } catch (IOException ex) {
            System.out.println("Failed to extract images for HTML report.");
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