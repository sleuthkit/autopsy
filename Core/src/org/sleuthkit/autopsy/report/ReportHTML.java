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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.openide.filesystems.FileUtil;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

 class ReportHTML implements TableReportModule {
    private static final Logger logger = Logger.getLogger(ReportHTML.class.getName());
    private static final String THUMBS_REL_PATH = "thumbs" + File.separator;
    private static ReportHTML instance;
    private static final int MAX_THUMBS_PER_PAGE = 1000;
    private Case currentCase;
    private SleuthkitCase skCase;
    static Integer THUMBNAIL_COLUMNS = 5;
    
    private Map<String, Integer> dataTypes;
    private String path;
    private String thumbsPath;
    private String currentDataType; // name of current data type
    private Integer rowCount;       // number of rows (aka artifacts or tags) for the current data type
    private Writer out;
    

    private ReportBranding reportBranding;
    
    // Get the default instance of this report
    public static synchronized ReportHTML getDefault() {
        if (instance == null) {
            instance = new ReportHTML();
        }
        return instance;
    }
    
    // Hidden constructor
    private ReportHTML() {
        reportBranding = new ReportBranding();
    }
    
    // Refesh the member variables
    private void refresh() {
        currentCase = Case.getCurrentCase();
        skCase = currentCase.getSleuthkitCase();
        
        dataTypes = new TreeMap<>();
        
        path = "";
        thumbsPath = "";
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
     * Generate a file name for the given datatype, by replacing any 
     * undesirable chars, like /, or spaces
     * @param dataType data type for which to generate a file name
     */
    private String dataTypeToFileName(String dataType) {
        
        String fileName = org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName(dataType);
        // replace all ' ' with '_'
        fileName = fileName.replaceAll(" ", "_");
            
        return fileName; 
    }

    
    /**
     * Copies a suitable icon for the given data type in the output directory and 
     * returns the icon file name to use for the given data type.
     */
    private String useDataTypeIcon(String dataType)
    {
        String iconFilePath;
        String iconFileName;
        InputStream in;
        OutputStream output = null;
        
        logger.log(Level.INFO, "useDataTypeIcon: dataType = {0}", dataType);
        
        // find the artifact with matching display name
        BlackboardArtifact.ARTIFACT_TYPE artifactType = null;
        for (ARTIFACT_TYPE v : ARTIFACT_TYPE.values()) {
            if (v.getDisplayName().equals(dataType)) {
                artifactType = v;
            }
        }
        
        if (null != artifactType)
        {        
            // set the icon file name
            iconFileName = dataTypeToFileName(artifactType.getDisplayName()) + ".png";
            iconFilePath = path + File.separator + iconFileName;

            // determine the source image to use
             switch (artifactType) {
                case TSK_WEB_BOOKMARK:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/bookmarks.png");
                    break;   
                case TSK_WEB_COOKIE:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/cookies.png");
                     break;
                case TSK_WEB_HISTORY:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/history.png");
                    break;
                case TSK_WEB_DOWNLOAD:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/downloads.png");
                    break;     
                case TSK_RECENT_OBJECT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/recent.png");
                    break;
                case TSK_INSTALLED_PROG:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/installed.png");
                    break;
                case TSK_KEYWORD_HIT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/keywords.png");
                    break;
                case TSK_HASHSET_HIT:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/hash.png");
                    break;
                case TSK_DEVICE_ATTACHED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/devices.png");
                    break;
                case TSK_WEB_SEARCH_QUERY:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/search.png");
                    break;
                case TSK_METADATA_EXIF:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/exif.png");
                    break;
                case TSK_TAG_FILE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/userbookmarks.png");
                    break;
                case TSK_TAG_ARTIFACT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/userbookmarks.png");
                    break;        
                case TSK_SERVICE_ACCOUNT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/account-icon-16.png");
                    break;
                case TSK_CONTACT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/contact.png");
                    break;
                case TSK_MESSAGE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/message.png");
                    break;
                case TSK_CALLLOG:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/calllog.png");
                    break;
                case TSK_CALENDAR_ENTRY:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/calendar.png");
                    break;
                case TSK_SPEED_DIAL_ENTRY:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/speeddialentry.png");
                    break;
                case TSK_BLUETOOTH_PAIRING:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/bluetooth.png");
                     break;
                case TSK_GPS_BOOKMARK:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gpsfav.png");
                     break;
                case TSK_GPS_LAST_KNOWN_LOCATION:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gps-lastlocation.png");
                     break;
                case TSK_GPS_SEARCH:
                     in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gps-search.png");
                     break;

                default:
                    logger.log(Level.WARNING, "useDataTypeIcon: unhandled artifact type = " + dataType);
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/star.png");
                    iconFileName = "star.png";
                    iconFilePath = path + File.separator +  iconFileName;
                    break;
             }
        } 
        else {  // no defined artifact found for this dataType 
            logger.log(Level.WARNING, "useDataTypeIcon: no artifact found for data type = " + dataType);
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/star.png");
            iconFileName = "star.png";
            iconFilePath = path + File.separator +  iconFileName;   
        }
        
        try {
             output = new FileOutputStream(iconFilePath);
             FileUtil.copy(in, output);
             in.close();
             output.close();
          } catch (IOException ex) {
             logger.log(Level.SEVERE, "Failed to extract images for HTML report.", ex);
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
        
        return iconFileName; 
    }
    /**
     * Start this report by setting the path, refreshing member variables,
     * and writing the skeleton for the HTML report.
     * @param path path to save the report
     */
    @Override
    public void startReport(String path) {
        // Refresh the HTML report
        refresh();
        // Setup the path for the HTML report
        this.path = path + "HTML Report" + File.separator;
        this.thumbsPath = this.path + "thumbs" + File.separator;
        try {
            FileUtil.createFolder(new File(this.path));
            FileUtil.createFolder(new File(this.thumbsPath));
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
     * and setup the web page header.
     * Note: This method is a temporary workaround to avoid modifying the TableReportModule interface.     
     * 
     * @param name Name of the data type
     * @param comment Comment on the data type, may be the empty string
     */
    @Override
    public void startDataType(String name, String description) {
        String title = dataTypeToFileName(name);
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + title + getExtension()), "UTF-8"));
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "File not found: {0}", ex);
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Unrecognized encoding");
        }
        
        try {
            StringBuilder page = new StringBuilder();
            page.append("<html>\n<head>\n\t<title>").append(name).append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n</head>\n<body>\n");
            page.append("<div id=\"header\">").append(name).append("</div>\n<div id=\"content\">\n");
            if (!description.isEmpty()) {
                page.append("<p><strong>");
                page.append(description);
                page.append("</string></p>\n");
            }
            out.write(page.toString());
            currentDataType = name;
            rowCount = 0;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write page head: {0}", ex);
        }
    }
              
    /**
     * End the current data type. Write the end of the web page and close the
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
     * Start a new table with the given column headers.
     * Note: This method is a temporary workaround to avoid modifying the TableReportModule interface.
     * 
     * @param columnHeaders column headers
     * @param sourceArtifact source blackboard artifact for the table data 
     */
    public void startContentTagsTable(List<String> columnHeaders) {
        StringBuilder htmlOutput = new StringBuilder();        
        htmlOutput.append("<table>\n<thead>\n\t<tr>\n");
       
        // Add the specified columns.
        for(String columnHeader : columnHeaders) {
            htmlOutput.append("\t\t<th>").append(columnHeader).append("</th>\n");
        }
        
        // Add a column for a hyperlink to a local copy of the tagged content.
        htmlOutput.append("\t\t<th></th>\n");        
        
        htmlOutput.append("\t</tr>\n</thead>\n");
        
        try {
            out.write(htmlOutput.toString());
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
            logger.log(Level.SEVERE, "Failed to write row to out.", ex);
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, "Output writer is null. Page was not initialized before writing.", ex);
        }
    }
    
    /**
     * Saves a local copy of a tagged file and adds a row with a hyper link to 
     * the file. The content of the hyperlink is provided in linkHTMLContent.
     * 
     * @param row Values for each data cell in the row
     * @param file The file to link to in the report.
     * @param tagName the name of the tag that the content was flagged by
     * @param linkHTMLContent the html that will be the body of the link
     */
    public void addRowWithTaggedContentHyperlink(List<String> row, ContentTag contentTag) {
        Content content = contentTag.getContent();
        if (content instanceof AbstractFile == false) {
            addRow(row);
            return;
        }
        
        AbstractFile file = (AbstractFile) content;
        // Don't make a local copy of the file if it is a directory or unallocated space.
        if (file.isDir() ||
            file.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS ||
            file.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) {
            row.add("");
            return;
        }

        // save it in a folder based on the tag name
        String localFilePath = saveContent(file, contentTag.getName().getDisplayName());
        
        // Add the hyperlink to the row. A column header for it was created in startTable().
        StringBuilder localFileLink = new StringBuilder();
        localFileLink.append("<a href=\"");
        localFileLink.append(localFilePath);
        localFileLink.append("\">").append(NbBundle.getMessage(this.getClass(), "ReportHTML.link.viewFile")).append("</a>");
        row.add(localFileLink.toString());              
        
        StringBuilder builder = new StringBuilder();
        builder.append("\t<tr>\n");
        for (String cell : row) {
            builder.append("\t\t<td>").append(cell).append("</td>\n");
        }
        builder.append("\t</tr>\n");
        rowCount++;
        
        try {
            out.write(builder.toString());
        } 
        catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write row to out.", ex);
        } 
        catch (NullPointerException ex) {
            logger.log(Level.SEVERE, "Output writer is null. Page was not initialized before writing.", ex);
        }
    }
    
    /**
     * Add the body of the thumbnails table.
     * @param images 
     */
    public void addThumbnailRows(List<Content> images) {
        List<String> currentRow = new ArrayList<>();
        int totalCount = 0;
        int pages = 0;
        for (Content content : images) {
            if (currentRow.size() == THUMBNAIL_COLUMNS) {
                addRow(currentRow);
                currentRow.clear();
            }
            
            if (totalCount == MAX_THUMBS_PER_PAGE) {
                // manually set the row count so the count of items shown in the 
                // navigation page reflects the number of thumbnails instead of
                // the number of rows.
                rowCount = totalCount;
                totalCount = 0;
                pages++;
                endTable();
                endDataType();
                startDataType(NbBundle.getMessage(this.getClass(), "ReportHTML.addThumbRows.dataType.title", pages),
                              NbBundle.getMessage(this.getClass(), "ReportHTML.addThumbRows.dataType.msg"));
                List<String> emptyHeaders = new ArrayList<>();
                for (int i = 0; i < THUMBNAIL_COLUMNS; i++) {
                    emptyHeaders.add("");
                }
                startTable(emptyHeaders);
            }
            
            if (failsContentCheck(content)) {
                continue;
            }
            
            AbstractFile file = (AbstractFile) content; 

            // save copies of the orginal image and thumbnail image
            String thumbnailPath = prepareThumbnail(file);
            if (thumbnailPath == null) {
                continue;
            }
            String contentPath = saveContent(file, "thumbs_fullsize");
            String nameInImage;
            try {
                nameInImage = file.getUniquePath();
            } catch (TskCoreException ex) {
                nameInImage = file.getName();
            }
            
            StringBuilder linkToThumbnail = new StringBuilder();
            linkToThumbnail.append("<a href=\"");
            linkToThumbnail.append(contentPath);
            linkToThumbnail.append("\">");
            linkToThumbnail.append("<img src=\"").append(thumbnailPath).append("\" title=\"").append(nameInImage).append("\"/>");
            linkToThumbnail.append("</a><br>");
            linkToThumbnail.append(file.getName()).append("<br>");
            
            Services services = currentCase.getServices();
            TagsManager tagsManager = services.getTagsManager();
            try {
                List<ContentTag> tags = tagsManager.getContentTagsByContent(content);
                if (tags.size() > 0) {
                    linkToThumbnail.append(NbBundle.getMessage(this.getClass(), "ReportHTML.thumbLink.tags") );
                }
                for (int i = 0; i < tags.size(); i++) {
                    ContentTag tag = tags.get(i);
                    linkToThumbnail.append(tag.getName().getDisplayName());
                    if (i != tags.size() - 1) {
                        linkToThumbnail.append(", ");
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Could not find get tags for file.", ex);
            }

            currentRow.add(linkToThumbnail.toString());
            
            totalCount++;
        }
        
        if (currentRow.isEmpty() == false) {
            int extraCells = THUMBNAIL_COLUMNS - currentRow.size();
            for (int i = 0; i < extraCells; i++) {
                // Finish out the row.
                currentRow.add("");
            }
            addRow(currentRow);
        }
        
        // manually set rowCount to be the total number of images.
        rowCount = totalCount;
    }
    
    private boolean failsContentCheck(Content c) {
        if (c instanceof AbstractFile == false) {
            return true;
        }
        AbstractFile file = (AbstractFile) c;
        if (file.isDir() ||
            file.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS ||
            file.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) {
            return true;
        }
        return false;
    }
    
    /**
     * Save a local copy of the given file in the reports folder.
     * @param file File to save
     * @param dirName Custom top-level folder to use to store the files in (tag name, etc.)
     * @return Path to where file was stored (relative to root of HTML folder)
     */
    public String saveContent(AbstractFile file, String dirName) {
        // clean up the dir name passed in
        String dirName2 = dirName.replace("/", "_");
        dirName2 = dirName2.replace("\\", "_");
        
        // Make a folder for the local file with the same tagName as the tag.
        StringBuilder localFilePath = new StringBuilder();  // full path
        
        localFilePath.append(path);
        localFilePath.append(dirName2);
        File localFileFolder = new File(localFilePath.toString());
        if (!localFileFolder.exists()) { 
            localFileFolder.mkdirs();
        }

        // Construct a file tagName for the local file that incorporates the file id to ensure uniqueness.
        String fileName = file.getName();
        String objectIdSuffix = "_" + file.getId();
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex != -1 && lastDotIndex != 0) {
            // The file tagName has a conventional extension. Insert the object id before the '.' of the extension.
            fileName = fileName.substring(0, lastDotIndex) + objectIdSuffix + fileName.substring(lastDotIndex, fileName.length());
        }
        else {
            // The file has no extension or the only '.' in the file is an initial '.', as in a hidden file.
            // Add the object id to the end of the file tagName.
            fileName += objectIdSuffix;
        }                                
        localFilePath.append(File.separator);
        localFilePath.append(fileName);

        // If the local file doesn't already exist, create it now. 
        // The existence check is necessary because it is possible to apply multiple tags with the same tagName to a file.
        File localFile = new File(localFilePath.toString());
        if (!localFile.exists()) {
            ExtractFscContentVisitor.extract(file, localFile, null, null);
        }
        
        // get the relative path
        return localFilePath.toString().substring(path.length());
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
        return NbBundle.getMessage(this.getClass(), "ReportHTML.getName.text");
    }
    
    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "ReportHTML.getDesc.text");
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
            logger.log(Level.SEVERE, "Could not find index.css file to write to.", ex);
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing index.css.", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.css.", ex);
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
            index.append("<head>\n<title>").append(
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.title", currentCase.getName())).append(
                    "</title>\n");
            index.append("<link rel=\"icon\" type=\"image/ico\" href=\"favicon.ico\" />\n");
            index.append("</head>\n");
            index.append("<frameset cols=\"350px,*\">\n");
            index.append("<frame src=\"nav.html\" name=\"nav\">\n");
            index.append("<frame src=\"summary.html\" name=\"content\">\n");
            index.append("<noframes>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.noFrames.msg")).append("<br />\n");
            index.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.noFrames.seeNav")).append("<br />\n");
            index.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.seeSum")).append("</noframes>\n");
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
            nav.append("<html>\n<head>\n\t<title>").append(
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeNav.title"))
               .append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n</head>\n<body>\n");
            nav.append("<div id=\"content\">\n<h1>").append(
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeNav.h1")).append("</h1>\n");
            nav.append("<ul class=\"nav\">\n");
            nav.append("<li style=\"background: url(summary.png) left center no-repeat;\"><a href=\"summary.html\" target=\"content\">")
               .append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeNav.summary")).append("</a></li>\n");
            
            for (String dataType : dataTypes.keySet()) {
                String dataTypeEsc = dataTypeToFileName(dataType);
                String iconFileName = useDataTypeIcon(dataType);
                nav.append("<li style=\"background: url('").append(iconFileName)
                        .append("') left center no-repeat;\"><a href=\"")
                        .append(dataTypeEsc).append(".html\" target=\"content\">")
                        .append(dataType).append(" (").append(dataTypes.get(dataType))
                        .append(")</a></li>\n");
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
            
            //pull generator and agency logo from branding, and the remaining resources from the core jar
            String generatorLogoPath = reportBranding.getGeneratorLogoPath();
            if (generatorLogoPath != null && ! generatorLogoPath.isEmpty()) {
                File from = new File(generatorLogoPath);
                File to = new File(path);
                FileUtil.copyFile(FileUtil.toFileObject(from), FileUtil.toFileObject(to), "generator_logo");
            }
            
            String agencyLogoPath = reportBranding.getAgencyLogoPath();
            if (agencyLogoPath != null && ! agencyLogoPath.isEmpty() ) {
                File from = new File(agencyLogoPath);
                File to = new File(path);
                FileUtil.copyFile(FileUtil.toFileObject(from), FileUtil.toFileObject(to), "agency_logo");
            }
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/favicon.ico");
            output = new FileOutputStream(new File(path + File.separator + "favicon.ico"));
            FileUtil.copy(in, output);
            in.close();
            output.close();
            
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/summary.png");
            output = new FileOutputStream(new File(path + File.separator + "summary.png"));
            FileUtil.copy(in, output);
            in.close();
            output.close();
            
           
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to extract images for HTML report.", ex);
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
            head.append("<html>\n<head>\n<title>").append(
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.title")).append("</title>\n");
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
            
            final String reportTitle = reportBranding.getReportTitle();
            final String reportFooter = reportBranding.getReportFooter();
            final boolean agencyLogoSet = reportBranding.getAgencyLogoPath() != null && !reportBranding.getAgencyLogoPath().isEmpty();
            final boolean generatorLogoSet = reportBranding.getGeneratorLogoPath() != null && !reportBranding.getGeneratorLogoPath().isEmpty();
            
            summary.append("<div id=\"wrapper\">\n");
            summary.append("<h1>").append(reportTitle)
                   .append(running ? NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.warningMsg") : "")
                   .append("</h1>\n");
            summary.append("<p class=\"subheadding\">").append(
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.reportGenOn.text", datetime)).append("</p>\n");
            summary.append("<div class=\"title\">\n");
            if (agencyLogoSet) {
                summary.append("<div class=\"left\">\n");
                summary.append("<img src=\"agency_logo.png\" />\n");
                summary.append("</div>\n");
            }
            final String align = agencyLogoSet?"right":"left";
            summary.append("<div class=\"").append(align).append("\">\n");
            summary.append("<table>\n");
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.caseName"))
                   .append("</td><td>").append(caseName).append("</td></tr>\n");
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.caseNum"))
                   .append("</td><td>").append(!caseNumber.isEmpty() ? caseNumber : "<i>No case number</i>").append("</td></tr>\n");
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.examiner")).append("</td><td>")
                   .append(!examiner.isEmpty() ? examiner : NbBundle
                           .getMessage(this.getClass(), "ReportHTML.writeSum.noExaminer"))
                   .append("</td></tr>\n");
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.numImages"))
                   .append("</td><td>").append(imagecount).append("</td></tr>\n");
            summary.append("</table>\n");
            summary.append("</div>\n");
            summary.append("<div class=\"clear\"></div>\n");
            summary.append("</div>\n");
            summary.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.imageInfoHeading"));
            summary.append("<div class=\"info\">\n");
            try {
                Image[] images = new Image[imagecount];
                for(int i=0; i<imagecount; i++) {
                    images[i] = skCase.getImageById(currentCase.getImageIDs()[i]);
                }
                for(Image img : images) {
                    summary.append("<p>").append(img.getName()).append("</p>\n");
                    summary.append("<table>\n");
                    summary.append("<tr><td>").append(
                            NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.timezone"))
                           .append("</td><td>").append(img.getTimeZone()).append("</td></tr>\n");
                    for(String imgPath : img.getPaths()) {
                        summary.append("<tr><td>").append(
                                NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.path"))
                               .append("</td><td>").append(imgPath).append("</td></tr>\n");
                    }
                    summary.append("</table>\n");
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get image information for the HTML report.");
            }
            summary.append("</div>\n");
            if (generatorLogoSet) {
                summary.append("<div class=\"left\">\n");
                summary.append("<img src=\"generator_logo.png\" />\n");
                summary.append("</div>\n");
            }
            summary.append("<div class=\"clear\"></div>\n");
            if (reportFooter != null) {
                summary.append("<p class=\"subheadding\">").append(reportFooter).append("</p>\n");
            }
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

    private String prepareThumbnail(AbstractFile file) {
        File thumbFile = ImageUtils.getIconFile(file, ImageUtils.ICON_SIZE_MEDIUM);
        if (thumbFile.exists() == false) {
            return null;
        }
        try {
            File to = new File(thumbsPath);
            FileObject from = FileUtil.toFileObject(thumbFile);
            FileObject dest = FileUtil.toFileObject(to);
            FileUtil.copyFile(from, dest, thumbFile.getName(), "");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write thumb file to report directory.", ex);
        }
        
        return THUMBS_REL_PATH + thumbFile.getName();
    }

}
