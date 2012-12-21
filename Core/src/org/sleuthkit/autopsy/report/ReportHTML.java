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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class ReportHTML implements TableReportModule {
    private static final Logger logger = Logger.getLogger(ReportHTML.class.getName());
    private static ReportHTML instance;
    private Case currentCase;
    private SleuthkitCase skCase;
    
    private Map<String, Integer> dataTypes;
    private String path;
    private String currentDataType; // name of current data type
    private Integer rowCount;       // number of rows (aka artifacts or tags) for the current data type
    private Writer out;
    
    // Get the default instance of this report
    public static synchronized ReportHTML getDefault() {
        if (instance == null) {
            instance = new ReportHTML();
        }
        return instance;
    }
    
    // Hidden constructor
    private ReportHTML() {
    }
    
    // Refesh the member variables
    private void refresh() {
        currentCase = Case.getCurrentCase();
        skCase = currentCase.getSleuthkitCase();
        
        dataTypes = new TreeMap<String, Integer>();
        
        path = "";
        currentDataType = "";
        rowCount = 0;
        
        if (out != null) {
            try {
                out.close();
            } catch (IOException ex) {
            }
        }
        out = null;
    }

    /**
     * Start this report by setting the path, refreshing member variables,
     * and writing the skeleton for the HTML report.
     * @param path path to save the report
     * @param info map of info to display in the summary
     */
    @Override
    public void startReport(String path) {
        // Refresh the HTML report
        refresh();
        // Setup the path for the HTML report
        this.path = path + "HTML Report" + File.separator;
        try {
            FileUtil.createFolder(new File(this.path));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to make HTML report folder.");
        }
        // Write the basic files
        writeCss();
        writeIndex();
        writeSummary();
    }

    /**
     * End this report. Close the output stream if necessary, and write the
     * navigation menu with the data types given throughout the report.
     */
    @Override
    public void endReport() {
        writeNav();
        if (out != null) {
            try {
                out.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not close the output writer when ending report.", ex);
            }
        }
    }

    /**
     * Start a new HTML page for the given data type. Update the output stream to this page,
     * and setup the webpage header.
     * @param title title of the data type
     */
    @Override
    public void startDataType(String title) {        
        // Make a new out for this page
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + title + getExtension()), "UTF-8"));
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "File not found: {0}", ex);
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Unrecognized encoding");
        }
        
        // Write the beginnings of a page
        // Like <html>, header, title, any content divs
        try {
            StringBuilder page = new StringBuilder();
            page.append("<html>\n<head>\n\t<title>").append(title).append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n</head>\n<body>\n");
            page.append("<div id=\"header\">").append(title).append("</div>\n<div id=\"content\">\n");
            out.write(page.toString());
            currentDataType = title;
            rowCount = 0;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write page head: {0}", ex);
        }
    }
    
    /**
     * End the current data type. Write the end of the webpage and close the
     * output stream.
     */
    @Override
    public void endDataType() {
        dataTypes.put(currentDataType, rowCount);
        try {
            out.write("</div>\n</body>\n</html>\n");
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            if(out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close the output writer when ending data type.", ex);
                }
                out = null;
            }
        }
    }

    /**
     * Start a new set under the current data type.
     * @param setName name of the new set
     */
    @Override
    public void startSet(String setName) {   
        StringBuilder set = new StringBuilder();
        set.append("<h1><a name=\"").append(setName).append("\">").append(setName).append("</a></h1>\n");
        set.append("<div class=\"keyword_list\">\n");
        
        try {
            out.write(set.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write set: {0}", ex);
        }
    }
    
    /**
     * End the current set.
     */
    @Override
    public void endSet() {
        try {
            out.write("</div>\n");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of set: {0}", ex);
        }
    }

    /**
     * Add an index to the current page for all the sets about to be added.
     * @param sets list of set names to be added
     */
    @Override
    public void addSetIndex(List<String> sets) {
        StringBuilder index = new StringBuilder();
        index.append("<ul>\n");
        for (String set : sets) {
            index.append("\t<li><a href=\"#").append(set).append("\">").append(set).append("</a></li>\n");
        }
        index.append("</ul>\n");
        try {
            out.write(index.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to add set index: {0}", ex);
        }
    }

    /**
     * Add a new element to the current set.
     * @param elementName name of the element
     */
    @Override
    public void addSetElement(String elementName) {
        try {
            out.write("<h4>" + elementName + "</h4>\n");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write set element: {0}", ex);
        }
    }

    /**
     * Start a new table with the given column titles.
     * @param titles column titles
     */
    @Override
    public void startTable(List<String> titles) {
        StringBuilder ele = new StringBuilder();        
        ele.append("<table>\n<thead>\n\t<tr>\n");
        for(String title : titles) {
            ele.append("\t\t<th>").append(title).append("</th>\n");
        }
        ele.append("\t</tr>\n</thead>\n");
        
        try {
            out.write(ele.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write table start: {0}", ex);
        }
    }

    /**
     * End the current table.
     */
    @Override
    public void endTable() {
        try {
            out.write("</table>\n");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of table: {0}", ex);
        }
    }

    /**
     * Add a row to the current table.
     * @param row values for each cell in the row
     */
    @Override
    public void addRow(List<String> row) {
        StringBuilder builder = new StringBuilder();
        builder.append("\t<tr>\n");
        for (String cell : row) {
            builder.append("\t\t<td>").append(cell).append("</td>\n");
        }
        builder.append("\t</tr>\n");
        rowCount++;
        
        try {
            out.write(builder.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write row to out.");
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, "Output writer is null. Page was not initialized before writing.");
        }
    }

    /**
     * Return a String date for the long date given.
     * @param date date as a long
     * @return String date as a String
     */
    @Override
    public String dateToString(long date) {
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return sdf.format(new java.util.Date(date * 1000));
    }

    
    @Override
    public String getFilePath() {
        return "HTML Report" + File.separator + "index.html";
    }

    
    @Override
    public String getName() {
        return "HTML";
    }
    
    @Override
    public String getDescription() {
        return "An HTML formatted report, designed to be viewed in a modern browser.";
    }

    
    @Override
    public String getExtension() {
        return ".html";
    }
    
    /**
     * Write the stylesheet for this report.
     */
    private void writeCss() {
        Writer cssOut = null;
        try {
            cssOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + "index.css"), "UTF-8"));
            String css = "body {margin: 0px; padding: 0px; background: #FFFFFF; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353;}\n" +
                         "#content {padding: 30px;}\n" +
                         "#header {width:100%; padding: 10px; line-height: 25px; background: #07A; color: #FFF; font-size: 20px;}\n" +
                         "h1 {font-size: 20px; font-weight: normal; color: #07A; padding: 0 0 7px 0; margin-top: 25px; border-bottom: 1px solid #D6D6D6;}\n" +
                         "h2 {font-size: 20px; font-weight: bolder; color: #07A;}\n" +
                         "h3 {font-size: 16px; color: #07A;}\n" +
                         "h4 {background: #07A; color: #FFF; font-size: 16px; margin: 0 0 0 25px; padding: 0; padding-left: 15px;}\n" + 
                         "ul.nav {list-style-type: none; line-height: 35px; padding: 0px; margin-left: 15px;}\n" +
                         "ul li a {font-size: 14px; color: #444; text-decoration: none; padding-left: 25px;}\n" +
                         "ul li a:hover {text-decoration: underline;}\n" +
                         "p {margin: 0 0 20px 0;}\n" +
                         "table {max-width: 100%; min-width: 700px; padding: 0; margin: 0; border-collapse: collapse; border-bottom: 2px solid #e5e5e5;}\n" +
                         ".keyword_list table {width: 100%; margin: 0 0 25px 25px; border-bottom: 2px solid #dedede;}\n" +
                         "table th {display: table-cell; text-align: left; padding: 8px 16px; background: #e5e5e5; color: #777; font-size: 11px; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #e5e5e5;}\n" +
                         "table td {display: table-cell; padding: 8px 16px; font: 13px/20px Arial, Helvetica, sans-serif; max-width: 500px; min-width: 125px; word-break: break-all; overflow: auto;}\n" +
                         "table tr:nth-child(even) td {background: #f3f3f3;}";
            cssOut.write(css);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find index.css file to write to.");
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing index.css.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.css.");
        } finally {
            try {
                if(cssOut != null) {
                    cssOut.flush();
                    cssOut.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the index page for this report.
     */
    private void writeIndex() {
        Writer indexOut = null;
        try {
            indexOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + "index.html"), "UTF-8"));
            StringBuilder index = new StringBuilder();
            index.append("<head>\n<title>Autopsy Report for case ").append(currentCase.getName()).append("</title>\n");
            index.append("<link rel=\"icon\" type=\"image/ico\" href=\"favicon.ico\" />\n");
            index.append("</head>\n");
            index.append("<frameset cols=\"350px,*\">\n");
            index.append("<frame src=\"nav.html\" name=\"nav\">\n");
            index.append("<frame src=\"summary.html\" name=\"content\">\n");
            index.append("<noframes>Your browser is not compatible with our frame setup.<br />\n");
            index.append("Please see <a href=\"nav.html\">the navigation page</a> for artifact links,<br />\n");
            index.append("and <a href=\"summary.html\">the summary page</a> for a case summary.</noframes>\n");
            index.append("</frameset>\n");
            index.append("</html>");
            indexOut.write(index.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.html: {0}", ex);
        } finally {
            try {
                if(indexOut != null) {
                    indexOut.flush();
                    indexOut.close();
                }
            } catch (IOException ex) {
            }
        }
    }
    
    /**
     * Write the navigation menu for this report.
     */
    private void writeNav() {
        Writer navOut = null;
        try {
            navOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + "nav.html"), "UTF-8"));
            StringBuilder nav = new StringBuilder();
            nav.append("<html>\n<head>\n\t<title>Report Navigation</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n</head>\n<body>\n");
            nav.append("<div id=\"content\">\n<h1>Report Navigation</h1>\n");
            nav.append("<ul class=\"nav\">\n");
            nav.append("<li style=\"background: url(summary.png) left center no-repeat;\"><a href=\"summary.html\" target=\"content\">Case Summary</a></li>\n");
            
            for (String dataType : dataTypes.keySet()) {
                nav.append("<li style=\"background: url('").append(dataType).append(".png') left center no-repeat;\"><a href=\"").append(dataType).append(".html\" target=\"content\">").append(dataType).append(" (").append(dataTypes.get(dataType)).append(")</a></li>\n");
            }
            nav.append("</ul>\n");
            nav.append("</div>\n</body>\n</html>");
            navOut.write(nav.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of report navigation menu: {0}", ex);
        } finally {
            if (navOut != null) {
                try {
                    navOut.flush();
                    navOut.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close navigation out writer.");
                }
            }
        }
        
        InputStream in = null;
        OutputStream output = null;
        try {
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/favicon.ico");
            output = new FileOutputStream(new File(path + File.separator + "favicon.ico"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/logo.png");
            output = new FileOutputStream(new File(path + File.separator + "logo.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/summary.png");
            output = new FileOutputStream(new File(path + File.separator + "summary.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/bookmarks.png");
            output = new FileOutputStream(new File(path + File.separator + "Bookmarks.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/cookies.png");
            output = new FileOutputStream(new File(path + File.separator + "Cookies.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/history.png");
            output = new FileOutputStream(new File(path + File.separator + "Web History.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/downloads.png");
            output = new FileOutputStream(new File(path + File.separator + "Downloads.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/search.png");
            output = new FileOutputStream(new File(path + File.separator + "Web Search Engine Queries.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/recent.png");
            output = new FileOutputStream(new File(path + File.separator + "Recent Documents.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/installed.png");
            output = new FileOutputStream(new File(path + File.separator + "Installed Programs.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/keywords.png");
            output = new FileOutputStream(new File(path + File.separator + "Keyword Hits.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/devices.png");
            output = new FileOutputStream(new File(path + File.separator + "Devices Attached.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/exif.png");
            output = new FileOutputStream(new File(path + File.separator + "EXIF Metadata.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/userbookmarks.png");
            output = new FileOutputStream(new File(path + File.separator + "File Tags.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/userbookmarks.png");
            output = new FileOutputStream(new File(path + File.separator + "Result Tags.png"));
            FileUtil.copy(in, output);
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/hash.png");
            output = new FileOutputStream(new File(path + File.separator + "Hashset Hits.png"));
            FileUtil.copy(in, output);
        } catch (IOException ex) {
            System.out.println("Failed to extract images for HTML report.");
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException ex) {
                }
            } if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                }
            }
        }
    }
    
    /**
     * Write the summary of the current case for this report.
     */
    private void writeSummary() {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + "summary.html"), "UTF-8"));
            StringBuilder head = new StringBuilder();
            head.append("<html>\n<head>\n<title>Case Summary</title>\n");
            head.append("<style type=\"text/css\">\n");
            head.append("body { padding: 0px; margin: 0px; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353; }\n");
            head.append("#wrapper { width: 90%; margin: 0px auto; margin-top: 35px; }\n");
            head.append("h1 { color: #07A; font-size: 36px; line-height: 42px; font-weight: normal; margin: 0px; border-bottom: 1px solid #81B9DB; }\n");
            head.append("h1 span { color: #F00; display: block; font-size: 16px; font-weight: bold; line-height: 22px;}\n");
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
            summary.append("<h1>Autopsy Forensic Report").append(running ? "<span>Warning, this report was run before ingest services completed!</span>" : "").append("</h1>\n");
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
                    for(String imgPath : img.getPaths()) {
                        summary.append("<tr><td>Path:</td><td>").append(imgPath).append("</td></tr>\n");
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
}
