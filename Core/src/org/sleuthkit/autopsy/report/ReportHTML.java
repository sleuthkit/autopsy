/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import java.awt.image.BufferedImage;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

class ReportHTML implements TableReportModule {

    private static final Logger logger = Logger.getLogger(ReportHTML.class.getName());
    private static final String THUMBS_REL_PATH = "thumbs" + File.separator; //NON-NLS
    private static ReportHTML instance;
    private static final int MAX_THUMBS_PER_PAGE = 1000;
    private static final String HTML_SUBDIR = "content";
    private Case currentCase;
    private SleuthkitCase skCase;
    static Integer THUMBNAIL_COLUMNS = 5;

    private Map<String, Integer> dataTypes;
    private String path;
    private String thumbsPath;
    private String subPath;
    private String currentDataType; // name of current data type
    private Integer rowCount;       // number of rows (aka artifacts or tags) for the current data type
    private Writer out;

    private final ReportBranding reportBranding;

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
    private void refresh() throws NoCurrentCaseException {
        currentCase = Case.getOpenCase();
        skCase = currentCase.getSleuthkitCase();

        dataTypes = new TreeMap<>();

        path = "";
        thumbsPath = "";
        subPath = "";
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
     * Generate a file name for the given datatype, by replacing any undesirable
     * chars, like /, or spaces
     *
     * @param dataType data type for which to generate a file name
     */
    private String dataTypeToFileName(String dataType) {

        String fileName = org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName(dataType);
        // replace all ' ' with '_'
        fileName = fileName.replaceAll(" ", "_");

        return fileName;
    }

    /**
     * Copies a suitable icon for the given data type in the output directory
     * and returns the icon file name to use for the given data type.
     */
    private String useDataTypeIcon(String dataType) {
        String iconFilePath;
        String iconFileName;
        InputStream in;
        OutputStream output = null;

        logger.log(Level.INFO, "useDataTypeIcon: dataType = {0}", dataType); //NON-NLS

        // find the artifact with matching display name
        BlackboardArtifact.ARTIFACT_TYPE artifactType = null;
        for (ARTIFACT_TYPE v : ARTIFACT_TYPE.values()) {
            if (v.getDisplayName().equals(dataType)) {
                artifactType = v;
            }
        }

        if (null != artifactType) {
            // set the icon file name
            iconFileName = dataTypeToFileName(artifactType.getDisplayName()) + ".png"; //NON-NLS
            iconFilePath = subPath + File.separator + iconFileName;

            // determine the source image to use
            switch (artifactType) {
                case TSK_WEB_BOOKMARK:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/bookmarks.png"); //NON-NLS
                    break;
                case TSK_WEB_COOKIE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/cookies.png"); //NON-NLS
                    break;
                case TSK_WEB_HISTORY:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/history.png"); //NON-NLS
                    break;
                case TSK_WEB_DOWNLOAD:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/downloads.png"); //NON-NLS
                    break;
                case TSK_RECENT_OBJECT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/recent.png"); //NON-NLS
                    break;
                case TSK_INSTALLED_PROG:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/installed.png"); //NON-NLS
                    break;
                case TSK_KEYWORD_HIT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/keywords.png"); //NON-NLS
                    break;
                case TSK_HASHSET_HIT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/hash.png"); //NON-NLS
                    break;
                case TSK_DEVICE_ATTACHED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/devices.png"); //NON-NLS
                    break;
                case TSK_WEB_SEARCH_QUERY:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/search.png"); //NON-NLS
                    break;
                case TSK_METADATA_EXIF:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/exif.png"); //NON-NLS
                    break;
                case TSK_TAG_FILE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/userbookmarks.png"); //NON-NLS
                    break;
                case TSK_TAG_ARTIFACT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/userbookmarks.png"); //NON-NLS
                    break;
                case TSK_SERVICE_ACCOUNT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/account-icon-16.png"); //NON-NLS
                    break;
                case TSK_CONTACT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/contact.png"); //NON-NLS
                    break;
                case TSK_MESSAGE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/message.png"); //NON-NLS
                    break;
                case TSK_CALLLOG:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/calllog.png"); //NON-NLS
                    break;
                case TSK_CALENDAR_ENTRY:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/calendar.png"); //NON-NLS
                    break;
                case TSK_SPEED_DIAL_ENTRY:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/speeddialentry.png"); //NON-NLS
                    break;
                case TSK_BLUETOOTH_PAIRING:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/bluetooth.png"); //NON-NLS
                    break;
                case TSK_GPS_BOOKMARK:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gpsfav.png"); //NON-NLS
                    break;
                case TSK_GPS_LAST_KNOWN_LOCATION:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gps-lastlocation.png"); //NON-NLS
                    break;
                case TSK_GPS_SEARCH:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gps-search.png"); //NON-NLS
                    break;
                case TSK_OS_INFO:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/computer.png"); //NON-NLS
                    break;
                case TSK_GPS_TRACKPOINT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gps_trackpoint.png"); //NON-NLS
                    break;
                case TSK_GPS_ROUTE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/gps_trackpoint.png"); //NON-NLS
                    break;
                case TSK_EMAIL_MSG:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/mail-icon-16.png"); //NON-NLS
                    break;
                case TSK_ENCRYPTION_SUSPECTED:
                case TSK_ENCRYPTION_DETECTED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/encrypted-file.png"); //NON-NLS
                    break;
                case TSK_EXT_MISMATCH_DETECTED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/mismatch-16.png"); //NON-NLS
                    break;
                case TSK_INTERESTING_ARTIFACT_HIT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
                    break;
                case TSK_INTERESTING_FILE_HIT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
                    break;
                case TSK_PROG_RUN:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/installed.png"); //NON-NLS
                    break;
                case TSK_REMOTE_DRIVE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/drive_network.png"); //NON-NLS
                    break;
                case TSK_ACCOUNT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/accounts.png"); //NON-NLS
                    break;
                default:
                    logger.log(Level.WARNING, "useDataTypeIcon: unhandled artifact type = " + dataType); //NON-NLS
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/star.png"); //NON-NLS
                    iconFileName = "star.png"; //NON-NLS
                    iconFilePath = subPath + File.separator + iconFileName;
                    break;
            }
        } else if (dataType.startsWith(ARTIFACT_TYPE.TSK_ACCOUNT.getDisplayName())) {
            /*
             * TSK_ACCOUNT artifacts get separated by their TSK_ACCOUNT_TYPE
             * attribute, with a synthetic compound dataType name, so they are
             * not caught by the switch statement above. For now we just give
             * them all the general account icon, but we could do something else
             * in the future.
             */
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/accounts.png"); //NON-NLS
            iconFileName = "accounts.png"; //NON-NLS
            iconFilePath = subPath + File.separator + iconFileName;
        } else {  // no defined artifact found for this dataType
            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/star.png"); //NON-NLS
            iconFileName = "star.png"; //NON-NLS
            iconFilePath = subPath + File.separator + iconFileName;
        }

        try {
            output = new FileOutputStream(iconFilePath);
            FileUtil.copy(in, output);
            in.close();
            output.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to extract images for HTML report.", ex); //NON-NLS
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException ex) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                }
            }
        }

        return iconFileName;
    }

    /**
     * Start this report by setting the path, refreshing member variables, and
     * writing the skeleton for the HTML report.
     *
     * @param baseReportDir path to save the report
     */
    @Override
    public void startReport(String baseReportDir) {
        // Refresh the HTML report
        try {
            refresh();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case."); //NON-NLS
            return;
        }
        // Setup the path for the HTML report
        this.path = baseReportDir; //NON-NLS
        this.subPath = this.path + HTML_SUBDIR + File.separator;
        this.thumbsPath = this.subPath + THUMBS_REL_PATH; //NON-NLS
        try {
            FileUtil.createFolder(new File(this.subPath));
            FileUtil.createFolder(new File(this.thumbsPath));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to make HTML report folder."); //NON-NLS
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
                logger.log(Level.WARNING, "Could not close the output writer when ending report.", ex); //NON-NLS
            }
        }
    }

    /**
     * Start a new HTML page for the given data type. Update the output stream
     * to this page, and setup the web page header. Note: This method is a
     * temporary workaround to avoid modifying the TableReportModule interface.
     *
     * @param name    Name of the data type
     * @param comment Comment on the data type, may be the empty string
     */
    @Override
    public void startDataType(String name, String description) {
        String title = dataTypeToFileName(name);
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(subPath + title + ".html"), "UTF-8")); //NON-NLS
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "File not found: {0}", ex); //NON-NLS
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Unrecognized encoding"); //NON-NLS
        }

        try {
            StringBuilder page = new StringBuilder();
            page.append("<html>\n<head>\n\t<title>").append(name).append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n</head>\n<body>\n"); //NON-NLS
            page.append("<div id=\"header\">").append(name).append("</div>\n<div id=\"content\">\n"); //NON-NLS
            if (!description.isEmpty()) {
                page.append("<p><strong>"); //NON-NLS
                page.append(description);
                page.append("</string></p>\n"); //NON-NLS
            }
            out.write(page.toString());
            currentDataType = name;
            rowCount = 0;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write page head: {0}", ex); //NON-NLS
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
            out.write("</div>\n</body>\n</html>\n"); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of HTML report.", ex); //NON-NLS
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close the output writer when ending data type.", ex); //NON-NLS
                }
                out = null;
            }
        }
    }

    /**
     * Start a new set under the current data type.
     *
     * @param setName name of the new set
     */
    @Override
    public void startSet(String setName) {
        StringBuilder set = new StringBuilder();
        set.append("<h1><a name=\"").append(setName).append("\">").append(setName).append("</a></h1>\n"); //NON-NLS
        set.append("<div class=\"keyword_list\">\n"); //NON-NLS

        try {
            out.write(set.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write set: {0}", ex); //NON-NLS
        }
    }

    /**
     * End the current set.
     */
    @Override
    public void endSet() {
        try {
            out.write("</div>\n"); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of set: {0}", ex); //NON-NLS
        }
    }

    /**
     * Add an index to the current page for all the sets about to be added.
     *
     * @param sets list of set names to be added
     */
    @Override
    public void addSetIndex(List<String> sets) {
        StringBuilder index = new StringBuilder();
        index.append("<ul>\n"); //NON-NLS
        for (String set : sets) {
            index.append("\t<li><a href=\"#").append(set).append("\">").append(set).append("</a></li>\n"); //NON-NLS
        }
        index.append("</ul>\n"); //NON-NLS
        try {
            out.write(index.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to add set index: {0}", ex); //NON-NLS
        }
    }

    /**
     * Add a new element to the current set.
     *
     * @param elementName name of the element
     */
    @Override
    public void addSetElement(String elementName) {
        try {
            out.write("<h4>" + elementName + "</h4>\n"); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write set element: {0}", ex); //NON-NLS
        }
    }

    /**
     * Start a new table with the given column titles.
     *
     * @param titles column titles
     */
    @Override
    public void startTable(List<String> titles) {
        StringBuilder ele = new StringBuilder();
        ele.append("<table>\n<thead>\n\t<tr>\n"); //NON-NLS
        for (String title : titles) {
            ele.append("\t\t<th>").append(title).append("</th>\n"); //NON-NLS
        }
        ele.append("\t</tr>\n</thead>\n"); //NON-NLS

        try {
            out.write(ele.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write table start: {0}", ex); //NON-NLS
        }
    }

    /**
     * Start a new table with the given column headers. Note: This method is a
     * temporary workaround to avoid modifying the TableReportModule interface.
     *
     * @param columnHeaders  column headers
     * @param sourceArtifact source blackboard artifact for the table data
     */
    public void startContentTagsTable(List<String> columnHeaders) {
        StringBuilder htmlOutput = new StringBuilder();
        htmlOutput.append("<table>\n<thead>\n\t<tr>\n"); //NON-NLS

        // Add the specified columns.
        for (String columnHeader : columnHeaders) {
            htmlOutput.append("\t\t<th>").append(columnHeader).append("</th>\n"); //NON-NLS
        }

        // Add a column for a hyperlink to a local copy of the tagged content.
        htmlOutput.append("\t\t<th></th>\n"); //NON-NLS

        htmlOutput.append("\t</tr>\n</thead>\n"); //NON-NLS

        try {
            out.write(htmlOutput.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write table start: {0}", ex); //NON-NLS
        }
    }

    /**
     * End the current table.
     */
    @Override
    public void endTable() {
        try {
            out.write("</table>\n"); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of table: {0}", ex); //NON-NLS
        }
    }

    /**
     * Add a row to the current table.
     *
     * @param row values for each cell in the row
     */
    @Override
    public void addRow(List<String> row) {
        StringBuilder builder = new StringBuilder();
        builder.append("\t<tr>\n"); //NON-NLS
        for (String cell : row) {
            String escapeHTMLCell = EscapeUtil.escapeHtml(cell);
            builder.append("\t\t<td>").append(escapeHTMLCell).append("</td>\n"); //NON-NLS
        }
        builder.append("\t</tr>\n"); //NON-NLS
        rowCount++;

        try {
            out.write(builder.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write row to out.", ex); //NON-NLS
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, "Output writer is null. Page was not initialized before writing.", ex); //NON-NLS
        }
    }

    /**
     * Saves a local copy of a tagged file and adds a row with a hyper link to
     * the file. The content of the hyperlink is provided in linkHTMLContent.
     *
     * @param row             Values for each data cell in the row
     * @param file            The file to link to in the report.
     * @param tagName         the name of the tag that the content was flagged
     *                        by
     * @param linkHTMLContent the html that will be the body of the link
     */
    public void addRowWithTaggedContentHyperlink(List<String> row, ContentTag contentTag) {
        Content content = contentTag.getContent();
        if (content instanceof AbstractFile == false) {
            addRow(row);
            return;
        }
        AbstractFile file = (AbstractFile) content;
        // Add the hyperlink to the row. A column header for it was created in startTable().
        StringBuilder localFileLink = new StringBuilder();
        // Don't make a local copy of the file if it is a directory or unallocated space.
        if (!(file.isDir()
                || file.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || file.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {
            localFileLink.append("<a href=\""); //NON-NLS
            // save it in a folder based on the tag name
            String localFilePath = saveContent(file, contentTag.getName().getDisplayName());
            localFileLink.append(localFilePath);
            localFileLink.append("\" target=\"_top\">");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\t<tr>\n"); //NON-NLS
        int positionCounter = 0;
        for (String cell : row) {
            // position-dependent code used to format this report. Not great, but understandable for formatting.
            if (positionCounter == 1) { // Convert the file name to a hyperlink and left-align it
                builder.append("\t\t<td class=\"left_align_cell\">").append(localFileLink.toString()).append(cell).append("</a></td>\n"); //NON-NLS
            } else if (positionCounter == 7) { // Right-align the bytes column.
                builder.append("\t\t<td class=\"right_align_cell\">").append(cell).append("</td>\n"); //NON-NLS
            } else { // Regular case, not a file name nor a byte count
                builder.append("\t\t<td>").append(cell).append("</td>\n"); //NON-NLS
            }
            ++positionCounter;
        }
        builder.append("\t</tr>\n"); //NON-NLS
        rowCount++;

        try {
            out.write(builder.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write row to out.", ex); //NON-NLS
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, "Output writer is null. Page was not initialized before writing.", ex); //NON-NLS
        }
    }

    /**
     * Add the body of the thumbnails table.
     *
     * @param images
     */
    public void addThumbnailRows(Set<Content> images) {
        List<String> currentRow = new ArrayList<>();
        int totalCount = 0;
        int pages = 1;
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
            String contentPath = saveContent(file, "thumbs_fullsize"); //NON-NLS
            String nameInImage;
            try {
                nameInImage = file.getUniquePath();
            } catch (TskCoreException ex) {
                nameInImage = file.getName();
            }

            StringBuilder linkToThumbnail = new StringBuilder();
            linkToThumbnail.append("<div id='thumbnail_link'>");
            linkToThumbnail.append("<a href=\""); //NON-NLS
            linkToThumbnail.append(contentPath);
            linkToThumbnail.append("\" target=\"_top\">");
            linkToThumbnail.append("<img src=\"").append(thumbnailPath).append("\" title=\"").append(nameInImage).append("\"/>"); //NON-NLS
            linkToThumbnail.append("</a><br>"); //NON-NLS
            linkToThumbnail.append(file.getName()).append("<br>"); //NON-NLS

            Services services = currentCase.getServices();
            TagsManager tagsManager = services.getTagsManager();
            try {
                List<ContentTag> tags = tagsManager.getContentTagsByContent(content);
                if (tags.size() > 0) {
                    linkToThumbnail.append(NbBundle.getMessage(this.getClass(), "ReportHTML.thumbLink.tags"));
                }
                for (int i = 0; i < tags.size(); i++) {
                    ContentTag tag = tags.get(i);
                    String notableString = tag.getName().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                    linkToThumbnail.append(tag.getName().getDisplayName() + notableString);
                    if (i != tags.size() - 1) {
                        linkToThumbnail.append(", ");
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Could not find get tags for file.", ex); //NON-NLS
            }
            linkToThumbnail.append("</div>");
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
        if (file.isDir()
                || file.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || file.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) {
            return true;
        }
        return false;
    }

    /**
     * Save a local copy of the given file in the reports folder.
     *
     * @param file    File to save
     * @param dirName Custom top-level folder to use to store the files in (tag
     *                name, etc.)
     *
     * @return Path to where file was stored (relative to root of HTML folder)
     */
    public String saveContent(AbstractFile file, String dirName) {
        // clean up the dir name passed in
        String dirName2 = org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName(dirName);

        // Make a folder for the local file with the same tagName as the tag.
        StringBuilder localFilePath = new StringBuilder();  // full path

        localFilePath.append(subPath);
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
        } else {
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
        return localFilePath.toString().substring(subPath.length());
    }

    /**
     * Return a String date for the long date given.
     *
     * @param date date as a long
     *
     * @return String date as a String
     */
    @Override
    public String dateToString(long date) {
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return sdf.format(new java.util.Date(date * 1000));
    }

    @Override
    public String getRelativeFilePath() {
        return "report.html"; //NON-NLS
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "ReportHTML.getName.text");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "ReportHTML.getDesc.text");
    }

    /**
     * Write the stylesheet for this report.
     */
    private void writeCss() {
        Writer cssOut = null;
        try {
            cssOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(subPath + "index.css"), "UTF-8")); //NON-NLS NON-NLS
            String css = "body {margin: 0px; padding: 0px; background: #FFFFFF; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353;}\n"
                    + //NON-NLS
                    "#content {padding: 30px;}\n"
                    + //NON-NLS
                    "#header {width:100%; padding: 10px; line-height: 25px; background: #07A; color: #FFF; font-size: 20px;}\n"
                    + //NON-NLS
                    "h1 {font-size: 20px; font-weight: normal; color: #07A; padding: 0 0 7px 0; margin-top: 25px; border-bottom: 1px solid #D6D6D6;}\n"
                    + //NON-NLS
                    "h2 {font-size: 20px; font-weight: bolder; color: #07A;}\n"
                    + //NON-NLS
                    "h3 {font-size: 16px; color: #07A;}\n"
                    + //NON-NLS
                    "h4 {background: #07A; color: #FFF; font-size: 16px; margin: 0 0 0 25px; padding: 0; padding-left: 15px;}\n"
                    + //NON-NLS
                    "ul.nav {list-style-type: none; line-height: 35px; padding: 0px; margin-left: 15px;}\n"
                    + //NON-NLS
                    "ul li a {font-size: 14px; color: #444; text-decoration: none; padding-left: 25px;}\n"
                    + //NON-NLS
                    "ul li a:hover {text-decoration: underline;}\n"
                    + //NON-NLS
                    "p {margin: 0 0 20px 0;}\n"
                    + //NON-NLS
                    "table {white-space:nowrap; min-width: 700px; padding: 2; margin: 0; border-collapse: collapse; border-bottom: 2px solid #e5e5e5;}\n"
                    + //NON-NLS
                    ".keyword_list table {margin: 0 0 25px 25px; border-bottom: 2px solid #dedede;}\n"
                    + //NON-NLS
                    "table th {white-space:nowrap; display: table-cell; text-align: center; padding: 2px 4px; background: #e5e5e5; color: #777; font-size: 11px; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #e5e5e5;}\n"
                    + //NON-NLS
                    "table .left_align_cell{display: table-cell; padding: 2px 4px; font: 13px/20px Arial, Helvetica, sans-serif; min-width: 125px; overflow: auto; text-align: left; }\n"
                    + //NON-NLS
                    "table .right_align_cell{display: table-cell; padding: 2px 4px; font: 13px/20px Arial, Helvetica, sans-serif; min-width: 125px; overflow: auto; text-align: right; }\n"
                    + //NON-NLS
                    "table td {white-space:nowrap; display: table-cell; padding: 2px 3px; font: 13px/20px Arial, Helvetica, sans-serif; min-width: 125px; overflow: auto; text-align:left; vertical-align: text-top;}\n"
                    + //NON-NLS
                    "table tr:nth-child(even) td {background: #f3f3f3;}\n"
                    + //NON-NLS 
                    "div#thumbnail_link {max-width: 200px; white-space: pre-wrap; white-space: -moz-pre-wrap; white-space: -pre-wrap; white-space: -o-pre-wrap; word-wrap: break-word;}";
            cssOut.write(css);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find index.css file to write to.", ex); //NON-NLS
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing index.css.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for index.css.", ex); //NON-NLS
        } finally {
            try {
                if (cssOut != null) {
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
        String indexFilePath = path + "report.html"; //NON-NLS
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        try {
            indexOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(indexFilePath), "UTF-8")); //NON-NLS
            StringBuilder index = new StringBuilder();
            final String reportTitle = reportBranding.getReportTitle();
            String iconPath = reportBranding.getAgencyLogoPath();
            if (iconPath == null) {
                // use default Autopsy icon if custom icon is not set
                iconPath = HTML_SUBDIR + "favicon.ico";
            } else {
                iconPath = Paths.get(reportBranding.getAgencyLogoPath()).getFileName().toString(); //ref to writeNav() for agency_logo
            }
            index.append("<head>\n<title>").append(reportTitle).append(" ").append(
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.title", currentCase.getDisplayName())).append(
                    "</title>\n"); //NON-NLS
            index.append("<link rel=\"icon\" type=\"image/ico\" href=\"")
                    .append(iconPath).append("\" />\n"); //NON-NLS
            index.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n"); //NON-NLS
            index.append("</head>\n"); //NON-NLS
            index.append("<frameset cols=\"350px,*\">\n"); //NON-NLS
            index.append("<frame src=\"" + HTML_SUBDIR).append(File.separator).append("nav.html\" name=\"nav\">\n"); //NON-NLS
            index.append("<frame src=\"" + HTML_SUBDIR).append(File.separator).append("summary.html\" name=\"content\">\n"); //NON-NLS
            index.append("<noframes>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.noFrames.msg")).append("<br />\n"); //NON-NLS
            index.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.noFrames.seeNav")).append("<br />\n"); //NON-NLS
            index.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeIndex.seeSum")).append("</noframes>\n"); //NON-NLS
            index.append("</frameset>\n"); //NON-NLS
            index.append("</html>"); //NON-NLS
            indexOut.write(index.toString());
            openCase.addReport(indexFilePath, NbBundle.getMessage(this.getClass(),
                    "ReportHTML.writeIndex.srcModuleName.text"), "");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for report.html: {0}", ex); //NON-NLS
        } catch (TskCoreException ex) {
            String errorMessage = String.format("Error adding %s to case as a report", indexFilePath); //NON-NLS
            logger.log(Level.SEVERE, errorMessage, ex);
        } finally {
            try {
                if (indexOut != null) {
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
            navOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(subPath + "nav.html"), "UTF-8")); //NON-NLS
            StringBuilder nav = new StringBuilder();
            nav.append("<html>\n<head>\n\t<title>").append( //NON-NLS
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeNav.title"))
                    .append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n"); //NON-NLS
            nav.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n</head>\n<body>\n"); //NON-NLS
            nav.append("<div id=\"content\">\n<h1>").append( //NON-NLS
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeNav.h1")).append("</h1>\n"); //NON-NLS
            nav.append("<ul class=\"nav\">\n"); //NON-NLS
            nav.append("<li style=\"background: url(summary.png) left center no-repeat;\"><a href=\"summary.html\" target=\"content\">") //NON-NLS
                    .append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeNav.summary")).append("</a></li>\n"); //NON-NLS

            for (String dataType : dataTypes.keySet()) {
                String dataTypeEsc = dataTypeToFileName(dataType);
                String iconFileName = useDataTypeIcon(dataType);
                nav.append("<li style=\"background: url('").append(iconFileName) //NON-NLS
                        .append("') left center no-repeat;\"><a href=\"") //NON-NLS
                        .append(dataTypeEsc).append(".html\" target=\"content\">") //NON-NLS
                        .append(dataType).append(" (").append(dataTypes.get(dataType))
                        .append(")</a></li>\n"); //NON-NLS
            }
            nav.append("</ul>\n"); //NON-NLS
            nav.append("</div>\n</body>\n</html>"); //NON-NLS
            navOut.write(nav.toString());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write end of report navigation menu: {0}", ex); //NON-NLS
        } finally {
            if (navOut != null) {
                try {
                    navOut.flush();
                    navOut.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close navigation out writer."); //NON-NLS
                }
            }
        }

        InputStream in = null;
        OutputStream output = null;
        try {

            //pull generator and agency logo from branding, and the remaining resources from the core jar
            String generatorLogoPath = reportBranding.getGeneratorLogoPath();
            if (generatorLogoPath != null && !generatorLogoPath.isEmpty()) {
                File from = new File(generatorLogoPath);
                File to = new File(subPath);
                FileUtil.copyFile(FileUtil.toFileObject(from), FileUtil.toFileObject(to), "generator_logo"); //NON-NLS
            }

            String agencyLogoPath = reportBranding.getAgencyLogoPath();
            if (agencyLogoPath != null && !agencyLogoPath.isEmpty()) {
                Path destinationPath = Paths.get(subPath);
                Files.copy(Files.newInputStream(Paths.get(agencyLogoPath)), destinationPath.resolve(Paths.get(agencyLogoPath).getFileName())); //NON-NLS     
            }

            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/favicon.ico"); //NON-NLS
            output = new FileOutputStream(new File(subPath + "favicon.ico"));
            FileUtil.copy(in, output);
            in.close();
            output.close();

            in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/summary.png"); //NON-NLS
            output = new FileOutputStream(new File(subPath + "summary.png"));
            FileUtil.copy(in, output);
            in.close();
            output.close();

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to extract images for HTML report.", ex); //NON-NLS
        } finally {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException ex) {
                }
            }
            if (in != null) {
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
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(subPath + "summary.html"), "UTF-8")); //NON-NLS
            StringBuilder head = new StringBuilder();
            head.append("<html>\n<head>\n<title>").append( //NON-NLS
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.title")).append("</title>\n"); //NON-NLS
            head.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n"); //NON-NLS
            head.append("<style type=\"text/css\">\n"); //NON-NLS
            head.append("body { padding: 0px; margin: 0px; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353; }\n"); //NON-NLS
            head.append("#wrapper { width: 90%; margin: 0px auto; margin-top: 35px; }\n"); //NON-NLS
            head.append("h1 { color: #07A; font-size: 36px; line-height: 42px; font-weight: normal; margin: 0px; border-bottom: 1px solid #81B9DB; }\n"); //NON-NLS
            head.append("h1 span { color: #F00; display: block; font-size: 16px; font-weight: bold; line-height: 22px;}\n"); //NON-NLS
            head.append("h2 { padding: 0 0 3px 0; margin: 0px; color: #07A; font-weight: normal; border-bottom: 1px dotted #81B9DB; }\n"); //NON-NLS
            head.append("table td { padding-right: 25px; }\n"); //NON-NLS
            head.append("p.subheadding { padding: 0px; margin: 0px; font-size: 11px; color: #B5B5B5; }\n"); //NON-NLS
            head.append(".title { width: 660px; margin-bottom: 50px; }\n"); //NON-NLS
            head.append(".left { float: left; width: 250px; margin-top: 20px; text-align: center; }\n"); //NON-NLS
            head.append(".left img { max-width: 250px; max-height: 250px; min-width: 200px; min-height: 200px; }\n"); //NON-NLS
            head.append(".right { float: right; width: 385px; margin-top: 25px; font-size: 14px; }\n"); //NON-NLS
            head.append(".clear { clear: both; }\n"); //NON-NLS
            head.append(".info p { padding: 3px 10px; background: #e5e5e5; color: #777; font-size: 12px; font-weight: bold; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede; }\n"); //NON-NLS
            head.append(".info table { margin: 0 25px 20px 25px; }\n"); //NON-NLS
            head.append("</style>\n"); //NON-NLS
            head.append("</head>\n<body>\n"); //NON-NLS
            out.write(head.toString());

            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            String datetime = datetimeFormat.format(date);

            String caseName = currentCase.getDisplayName();
            String caseNumber = currentCase.getNumber();
            String examiner = currentCase.getExaminer();
            int imagecount;
            try {
                imagecount = currentCase.getDataSources().size();
            } catch (TskCoreException ex) {
                imagecount = 0;
            }

            StringBuilder summary = new StringBuilder();
            boolean running = false;
            if (IngestManager.getInstance().isIngestRunning()) {
                running = true;
            }

            final String reportTitle = reportBranding.getReportTitle();
            final String reportFooter = reportBranding.getReportFooter();
            final boolean agencyLogoSet = reportBranding.getAgencyLogoPath() != null && !reportBranding.getAgencyLogoPath().isEmpty();
            final boolean generatorLogoSet = reportBranding.getGeneratorLogoPath() != null && !reportBranding.getGeneratorLogoPath().isEmpty();

            summary.append("<div id=\"wrapper\">\n"); //NON-NLS
            summary.append("<h1>").append(reportTitle) //NON-NLS
                    .append(running ? NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.warningMsg") : "")
                    .append("</h1>\n"); //NON-NLS
            summary.append("<p class=\"subheadding\">").append( //NON-NLS
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.reportGenOn.text", datetime)).append("</p>\n"); //NON-NLS
            summary.append("<div class=\"title\">\n"); //NON-NLS
            if (agencyLogoSet) {
                summary.append("<div class=\"left\">\n"); //NON-NLS
                summary.append("<img src=\"");
                summary.append(Paths.get(reportBranding.getAgencyLogoPath()).getFileName().toString());
                summary.append("\" />\n"); //NON-NLS
                summary.append("</div>\n"); //NON-NLS
            }
            final String align = agencyLogoSet ? "right" : "left"; //NON-NLS NON-NLS
            summary.append("<div class=\"").append(align).append("\">\n"); //NON-NLS
            summary.append("<table>\n"); //NON-NLS
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.caseName")) //NON-NLS
                    .append("</td><td>").append(caseName).append("</td></tr>\n"); //NON-NLS NON-NLS
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.caseNum")) //NON-NLS
                    .append("</td><td>").append(!caseNumber.isEmpty() ? caseNumber : NbBundle //NON-NLS
                    .getMessage(this.getClass(), "ReportHTML.writeSum.noCaseNum")).append("</td></tr>\n"); //NON-NLS
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.examiner")).append("</td><td>") //NON-NLS
                    .append(!examiner.isEmpty() ? examiner : NbBundle
                            .getMessage(this.getClass(), "ReportHTML.writeSum.noExaminer"))
                    .append("</td></tr>\n"); //NON-NLS
            summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.numImages")) //NON-NLS
                    .append("</td><td>").append(imagecount).append("</td></tr>\n"); //NON-NLS
            summary.append("</table>\n"); //NON-NLS
            summary.append("</div>\n"); //NON-NLS
            summary.append("<div class=\"clear\"></div>\n"); //NON-NLS
            summary.append("</div>\n"); //NON-NLS
            summary.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.imageInfoHeading"));
            summary.append("<div class=\"info\">\n"); //NON-NLS
            try {
                for (Content c : currentCase.getDataSources()) {
                    summary.append("<p>").append(c.getName()).append("</p>\n"); //NON-NLS
                    if (c instanceof Image) {
                        Image img = (Image) c;

                        summary.append("<table>\n"); //NON-NLS
                        summary.append("<tr><td>").append( //NON-NLS
                                NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.timezone"))
                                .append("</td><td>").append(img.getTimeZone()).append("</td></tr>\n"); //NON-NLS
                        for (String imgPath : img.getPaths()) {
                            summary.append("<tr><td>").append( //NON-NLS
                                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.path"))
                                    .append("</td><td>").append(imgPath).append("</td></tr>\n"); //NON-NLS
                        }
                        summary.append("</table>\n"); //NON-NLS
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get image information for the HTML report."); //NON-NLS
            }
            summary.append("</div>\n"); //NON-NLS
            if (generatorLogoSet) {
                summary.append("<div class=\"left\">\n"); //NON-NLS
                summary.append("<img src=\"generator_logo.png\" />\n"); //NON-NLS
                summary.append("</div>\n"); //NON-NLS
            }
            summary.append("<div class=\"clear\"></div>\n"); //NON-NLS
            if (reportFooter != null) {
                summary.append("<p class=\"subheadding\">").append(reportFooter).append("</p>\n"); //NON-NLS
            }
            summary.append("</div>\n"); //NON-NLS
            summary.append("</body></html>"); //NON-NLS
            out.write(summary.toString());
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find summary.html file to write to."); //NON-NLS
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing summary.hmtl."); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for summary.html."); //NON-NLS
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

    private String prepareThumbnail(AbstractFile file) {
        BufferedImage bufferedThumb = ImageUtils.getThumbnail(file, ImageUtils.ICON_SIZE_MEDIUM);
        File thumbFile = Paths.get(thumbsPath, file.getName() + ".png").toFile();
        if (bufferedThumb == null) {
            return null;
        }
        try {
            ImageIO.write(bufferedThumb, "png", thumbFile);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to write thumb file to report directory.", ex); //NON-NLS
            return null;
        }
        if (thumbFile.exists()
                == false) {
            return null;
        }
        return THUMBS_REL_PATH
                + thumbFile.getName();
    }

}
