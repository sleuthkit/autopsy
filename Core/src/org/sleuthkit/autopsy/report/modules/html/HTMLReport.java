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
package org.sleuthkit.autopsy.report.modules.html;

import org.sleuthkit.autopsy.report.NoReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager.ContentViewerTag;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagRegion;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagsUtil;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.ContentUtils.ExtractFscContentVisitor;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportBranding;
import org.sleuthkit.autopsy.report.infrastructure.TableReportModule;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

public class HTMLReport implements TableReportModule {

    private static final Logger logger = Logger.getLogger(HTMLReport.class.getName());
    private static final String THUMBS_REL_PATH = "thumbs" + File.separator; //NON-NLS
    private static HTMLReport instance;
    private static final int MAX_THUMBS_PER_PAGE = 1000;
    private static final String HTML_SUBDIR = "content";
    private Case currentCase;
    public static Integer THUMBNAIL_COLUMNS = 5;

    private Map<String, Integer> dataTypes;
    private String path;
    private String thumbsPath;
    private String subPath;
    private String currentDataType; // name of current data type
    private Integer rowCount;       // number of rows (aka artifacts or tags) for the current data type
    private Writer out;

    private HTMLReportConfigurationPanel configPanel;

    private final ReportBranding reportBranding;

    // Get the default instance of this report
    public static synchronized HTMLReport getDefault() {
        if (instance == null) {
            instance = new HTMLReport();
        }
        return instance;
    }

    // Hidden constructor
    private HTMLReport() {
        reportBranding = new ReportBranding();
    }

    @Override
    public JPanel getConfigurationPanel() {
        initializePanel();
        return configPanel;
    }

    private void initializePanel() {
        if (configPanel == null) {
            configPanel = new HTMLReportConfigurationPanel();
        }
    }

    /**
     * Get default configuration for this report module.
     *
     * @return Object which contains default report module settings.
     */
    @Override
    public ReportModuleSettings getDefaultConfiguration() {
        return new HTMLReportModuleSettings();
    }

    /**
     * Get current configuration for this report module.
     *
     * @return Object which contains current report module settings.
     */
    @Override
    public ReportModuleSettings getConfiguration() {
        initializePanel();
        return configPanel.getConfiguration();
    }

    /**
     * Set report module configuration.
     *
     * @param settings Object which contains report module settings.
     */
    @Override
    public void setConfiguration(ReportModuleSettings settings) {
        initializePanel();
        if (settings == null || settings instanceof NoReportModuleSettings) {
            configPanel.setConfiguration((HTMLReportModuleSettings) getDefaultConfiguration());
            return;
        }

        if (settings instanceof HTMLReportModuleSettings) {
            configPanel.setConfiguration((HTMLReportModuleSettings) settings);
            return;
        }

        throw new IllegalArgumentException("Expected settings argument to be an instance of HTMLReportModuleSettings");
    }

    // Refesh the member variables
    private void refresh() throws NoCurrentCaseException {
        currentCase = Case.getCurrentCaseThrows();

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
    @SuppressWarnings("deprecation")
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
                //fall through deprecated type to TSK_INTERESTING_ITEM
                case TSK_INTERESTING_FILE_HIT:
                //fall through deprecated type to TSK_INTERESTING_ITEM
                case TSK_INTERESTING_ITEM:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
                    break;
                case TSK_PROG_RUN:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/installed.png"); //NON-NLS
                    break;
                case TSK_REMOTE_DRIVE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/drive_network.png"); //NON-NLS
                    break;
                case TSK_OS_ACCOUNT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/os-account.png"); //NON-NLS
                    break;
                case TSK_OBJECT_DETECTED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/objects.png"); //NON-NLS
                    break;
                case TSK_WEB_FORM_AUTOFILL:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/web-form.png"); //NON-NLS
                    break;
                case TSK_WEB_CACHE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/cache.png"); //NON-NLS
                    break;
                case TSK_USER_CONTENT_SUSPECTED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/user-content.png"); //NON-NLS
                    break;
                case TSK_METADATA:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/metadata.png"); //NON-NLS
                    break;
                case TSK_CLIPBOARD_CONTENT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/clipboard.png"); //NON-NLS
                    break;
                case TSK_ACCOUNT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/accounts.png"); //NON-NLS
                    break;
                case TSK_WIFI_NETWORK:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/network-wifi.png"); //NON-NLS
                    break;
                case TSK_WIFI_NETWORK_ADAPTER:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/network-wifi.png"); //NON-NLS
                    break;
                case TSK_SIM_ATTACHED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/sim_card.png"); //NON-NLS
                    break;
                case TSK_BLUETOOTH_ADAPTER:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/Bluetooth.png"); //NON-NLS
                    break;
                case TSK_DEVICE_INFO:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/devices.png"); //NON-NLS
                    break;
                case TSK_VERIFICATION_FAILED:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/validationFailed.png"); //NON-NLS
                    break;
                case TSK_WEB_ACCOUNT_TYPE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/web-account-type.png"); //NON-NLS
                    break;
                case TSK_WEB_FORM_ADDRESS:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/web-form-address.png"); //NON-NLS
                    break;
                case TSK_GPS_AREA:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/gps-area.png"); //NON-NLS
                    break;
                case TSK_WEB_CATEGORIZATION:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/domain-16.png"); //NON-NLS
                    break;
                case TSK_YARA_HIT:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/yara_16.png"); //NON-NLS
                    break;
                case TSK_PREVIOUSLY_SEEN:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/previously-seen.png"); //NON-NLS
                    break;
                case TSK_PREVIOUSLY_UNSEEN:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/previously-unseen.png"); //NON-NLS
                    break;
                case TSK_PREVIOUSLY_NOTABLE:
                    in = getClass().getResourceAsStream("/org/sleuthkit/autopsy/images/red-circle-exclamation.png"); //NON-NLS
                    break;
                default:
                    logger.log(Level.WARNING, "useDataTypeIcon: unhandled artifact type = {0}", dataType); //NON-NLS
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
     * @param name        Name of the data type
     * @param description Comment on the data type, may be the empty string
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
            page.append("<html>\n<head>\n\t<title>").append(name).append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n</head>\n<body>\n") //NON-NLS
                    .append(writePageHeader())
                    .append("<div id=\"header\">").append(name).append("</div>\n")
                    .append("<div id=\"content\">\n"); //NON-NLS
            if (!description.isEmpty()) {
                page.append("<p><strong>"); //NON-NLS
                page.append(description);
                page.append("</strong></p>\n"); //NON-NLS
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
            StringBuilder builder = new StringBuilder();
            builder.append(writePageFooter());
            builder.append("</div>\n</body>\n</html>\n"); //NON-NLS
            out.write(builder.toString());
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
     * Write HTML-formatted page header text based on the text provided in the
     * configuration panel.
     *
     * @return The HTML-formatted text.
     */
    private String writePageHeader() {
        StringBuilder output = new StringBuilder();
        String pageHeader = configPanel.getHeader();
        if (pageHeader.isEmpty() == false) {
            output.append("<div id=\"pageHeaderFooter\">")
                    .append(StringEscapeUtils.escapeHtml4(pageHeader))
                    .append("</div>\n"); //NON-NLS
        }
        return output.toString();
    }

    /**
     * Write HTML-formatted page footer text based on the text provided in the
     * configuration panel.
     *
     * @return The HTML-formatted text.
     */
    private String writePageFooter() {
        StringBuilder output = new StringBuilder();
        String pageFooter = configPanel.getFooter();
        if (pageFooter.isEmpty() == false) {
            output.append("<br/><div id=\"pageHeaderFooter\">")
                    .append(StringEscapeUtils.escapeHtml4(pageFooter))
                    .append("</div>"); //NON-NLS
        }
        return output.toString();
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
     * @param columnHeaders column headers
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
     * Add a row to the current table, escaping the text to be contained in the
     * row.
     *
     * @param row values for each cell in the row
     */
    @Override
    public void addRow(List<String> row) {
        addRow(row, true);
    }

    /**
     * Add a row to the current table.
     *
     * @param row        values for each cell in the row
     * @param escapeText whether or not the text of the row should be escaped,
     *                   true for escaped, false for not escaped
     */
    private void addRow(List<String> row, boolean escapeText) {
        StringBuilder builder = new StringBuilder();
        builder.append("\t<tr>\n"); //NON-NLS
        for (String cell : row) {
            String cellText = escapeText ? EscapeUtil.escapeHtml(cell) : cell;
            builder.append("\t\t<td>").append(cellText).append("</td>\n"); //NON-NLS
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
     * the file.
     *
     * @param row        Values for each data cell in the row
     * @param contentTag The tag
     */
    public void addRowWithTaggedContentHyperlink(List<String> row, ContentTag contentTag) {
        Content content = contentTag.getContent();
        if (content instanceof AbstractFile == false) {
            addRow(row, true);
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
            switch (positionCounter) {
                case 1:
                    // Convert the file name to a hyperlink and left-align it
                    builder.append("\t\t<td class=\"left_align_cell\">").append(localFileLink.toString()).append(cell).append("</a></td>\n"); //NON-NLS
                    break;
                case 7:
                    // Right-align the bytes column.
                    builder.append("\t\t<td class=\"right_align_cell\">").append(cell).append("</td>\n"); //NON-NLS
                    break;
                default:
                    // Regular case, not a file name nor a byte count
                    builder.append("\t\t<td>").append(cell).append("</td>\n"); //NON-NLS
                    break;
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
     * Finds all associated image tags.
     *
     * @param contentTags
     *
     * @return
     */
    private List<ImageTagRegion> getTaggedRegions(List<ContentTag> contentTags) {
        ArrayList<ImageTagRegion> tagRegions = new ArrayList<>();
        contentTags.forEach((contentTag) -> {
            try {
                ContentViewerTag<ImageTagRegion> contentViewerTag = ContentViewerTagManager
                        .getTag(contentTag, ImageTagRegion.class);
                if (contentViewerTag != null) {
                    tagRegions.add(contentViewerTag.getDetails());
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Could not get content viewer tag "
                        + "from case db for content_tag with id %d", contentTag.getId());
            }
        });
        return tagRegions;
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
                addRow(currentRow, false);
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
            List<ContentTag> contentTags = new ArrayList<>();

            String thumbnailPath = null;
            String imageWithTagsFullPath = null;
            try {
                //Get content tags and all image tags
                contentTags = Case.getCurrentCase().getServices()
                        .getTagsManager().getContentTagsByContent(file);
                List<ImageTagRegion> imageTags = getTaggedRegions(contentTags);

                if (!imageTags.isEmpty()) {
                    //Write the tags to the fullsize and thumbnail images
                    BufferedImage fullImageWithTags = ImageTagsUtil.getImageWithTags(file, imageTags);

                    BufferedImage thumbnailWithTags = ImageTagsUtil.getThumbnailWithTags(file,
                            imageTags, ImageTagsUtil.IconSize.MEDIUM);

                    String fileName = org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName(file.getName());

                    //Create paths in report to write tagged images
                    File thumbnailImageWithTagsFile = Paths.get(thumbsPath, FilenameUtils.removeExtension(fileName) + ".png").toFile();
                    String fullImageWithTagsPath = makeCustomUniqueFilePath(file, "thumbs_fullsize");
                    fullImageWithTagsPath = FilenameUtils.removeExtension(fullImageWithTagsPath) + ".png";
                    File fullImageWithTagsFile = Paths.get(fullImageWithTagsPath).toFile();

                    //Save images
                    ImageIO.write(thumbnailWithTags, "png", thumbnailImageWithTagsFile);
                    ImageIO.write(fullImageWithTags, "png", fullImageWithTagsFile);

                    thumbnailPath = THUMBS_REL_PATH + thumbnailImageWithTagsFile.getName();
                    //Relative path
                    imageWithTagsFullPath = fullImageWithTagsPath.substring(subPath.length());
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Could not get tags for file.", ex); //NON-NLS
            } catch (IOException | InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Could make marked up thumbnail.", ex); //NON-NLS
            }

            // save copies of the orginal image and thumbnail image
            if (thumbnailPath == null) {
                thumbnailPath = prepareThumbnail(file);
            }

            if (thumbnailPath == null) {
                continue;
            }
            String contentPath = saveContent(file, "original"); //NON-NLS
            String nameInImage;
            try {
                nameInImage = file.getUniquePath();
            } catch (TskCoreException ex) {
                nameInImage = file.getName();
            }

            StringBuilder linkToThumbnail = new StringBuilder();
            linkToThumbnail.append("<div id='thumbnail_link'><a href=\"")
                    .append((imageWithTagsFullPath != null) ? imageWithTagsFullPath : contentPath)
                    .append("\" target=\"_top\"><img src=\"")
                    .append(thumbnailPath).append("\" title=\"").append(nameInImage).append("\"/></a><br>") //NON-NLS
                    .append(file.getName()).append("<br>"); //NON-NLS
            if (imageWithTagsFullPath != null) {
                linkToThumbnail.append("<a href=\"").append(contentPath).append("\" target=\"_top\">View Original</a><br>");
            }

            if (!contentTags.isEmpty()) {
                linkToThumbnail.append(NbBundle.getMessage(this.getClass(), "ReportHTML.thumbLink.tags"));
            }
            for (int i = 0; i < contentTags.size(); i++) {
                ContentTag tag = contentTags.get(i);
                String notableString = tag.getName().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                linkToThumbnail.append(tag.getName().getDisplayName()).append(notableString);
                if (i != contentTags.size() - 1) {
                    linkToThumbnail.append(", ");
                }
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
            addRow(currentRow, false);
        }

        // manually set rowCount to be the total number of images.
        rowCount = totalCount;
    }

    private boolean failsContentCheck(Content c) {
        if (c instanceof AbstractFile == false) {
            return true;
        }
        AbstractFile file = (AbstractFile) c;
        return file.isDir()
                || file.getType() == TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                || file.getType() == TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS;
    }

    private String makeCustomUniqueFilePath(AbstractFile file, String dirName) {
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

        /*
         * Construct a file tagName for the local file that incorporates the
         * file ID to ensure uniqueness.
         *
         * Note: File name is normalized to account for possible attribute name
         * which will be separated by a ':' character.
         */
        String fileName = org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName(file.getName());
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

        return localFilePath.toString();
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

        String localFilePath = makeCustomUniqueFilePath(file, dirName);

        // If the local file doesn't already exist, create it now.
        // The existence check is necessary because it is possible to apply multiple tags with the same tagName to a file.
        File localFile = new File(localFilePath);
        if (!localFile.exists()) {
            ExtractFscContentVisitor.extract(file, localFile, null, null);
        }

        // get the relative path
        return localFilePath.substring(subPath.length());
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
                    "#pageHeaderFooter {width: 100%; padding: 10px; line-height: 25px; text-align: center; font-size: 20px;}\n"
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
            openCase = Case.getCurrentCaseThrows();
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
        Writer output = null;
        try {
            output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(subPath + "summary.html"), "UTF-8")); //NON-NLS
            StringBuilder head = new StringBuilder();
            head.append("<html>\n<head>\n<title>").append( //NON-NLS
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.title")).append("</title>\n"); //NON-NLS
            head.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n"); //NON-NLS
            head.append("<style type=\"text/css\">\n"); //NON-NLS
            head.append("#pageHeaderFooter {width: 100%; padding: 10px; line-height: 25px; text-align: center; font-size: 20px;}\n"); //NON-NLS
            head.append("body { padding: 0px; margin: 0px; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353; }\n"); //NON-NLS
            head.append("#wrapper { width: 90%; margin: 0px auto; margin-top: 35px; }\n"); //NON-NLS
            head.append("h1 { color: #07A; font-size: 36px; line-height: 42px; font-weight: normal; margin: 0px; border-bottom: 1px solid #81B9DB; }\n"); //NON-NLS
            head.append("h1 span { color: #F00; display: block; font-size: 16px; font-weight: bold; line-height: 22px;}\n"); //NON-NLS
            head.append("h2 { padding: 0 0 3px 0; margin: 0px; color: #07A; font-weight: normal; border-bottom: 1px dotted #81B9DB; }\n"); //NON-NLS
            head.append("h3 { padding: 5 0 3px 0; margin: 0px; color: #07A; font-weight: normal; }\n");
            head.append("table td { padding: 5px 25px 5px 0px; vertical-align:top;}\n"); //NON-NLS
            head.append("p.subheadding { padding: 0px; margin: 0px; font-size: 11px; color: #B5B5B5; }\n"); //NON-NLS
            head.append(".title { width: 660px; margin-bottom: 50px; }\n"); //NON-NLS
            head.append(".left { float: left; width: 250px; margin-top: 20px; text-align: center; }\n"); //NON-NLS
            head.append(".left img { max-width: 250px; max-height: 250px; min-width: 200px; min-height: 200px; }\n"); //NON-NLS
            head.append(".right { float: right; width: 385px; margin-top: 25px; font-size: 14px; }\n"); //NON-NLS
            head.append(".clear { clear: both; }\n"); //NON-NLS
            head.append(".info { padding: 10px 0;}\n");
            head.append(".info p { padding: 3px 10px; background: #e5e5e5; color: #777; font-size: 12px; font-weight: bold; text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede; }\n"); //NON-NLS
            head.append(".info table { margin: 10px 25px 10px 25px; }\n"); //NON-NLS
            head.append("ul {padding: 0;margin: 0;list-style-type: none;}");
            head.append("li {padding-bottom: 5px;}");
            head.append("</style>\n"); //NON-NLS
            head.append("</head>\n<body>\n"); //NON-NLS
            output.write(head.toString());

            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            String datetime = datetimeFormat.format(date);

            StringBuilder summary = new StringBuilder();
            boolean running = false;
            if (IngestManager.getInstance().isIngestRunning()) {
                running = true;
            }
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            List<IngestJobInfo> ingestJobs = skCase.getIngestJobs();
            final String reportTitle = reportBranding.getReportTitle();
            final String reportFooter = reportBranding.getReportFooter();
            final boolean generatorLogoSet = reportBranding.getGeneratorLogoPath() != null && !reportBranding.getGeneratorLogoPath().isEmpty();

            summary.append("<div id=\"wrapper\">\n"); //NON-NLS
            summary.append(writePageHeader());
            summary.append("<h1>").append(reportTitle) //NON-NLS
                    .append(running ? NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.warningMsg") : "")
                    .append("</h1>\n"); //NON-NLS
            summary.append("<p class=\"subheadding\">").append( //NON-NLS
                    NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.reportGenOn.text", datetime)).append("</p>\n"); //NON-NLS
            summary.append("<div class=\"title\">\n"); //NON-NLS
            summary.append(writeSummaryCaseDetails());
            summary.append(writeSummaryImageInfo());
            summary.append(writeSummarySoftwareInfo(skCase, ingestJobs));
            summary.append(writeSummaryIngestHistoryInfo(skCase, ingestJobs));
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
            summary.append(writePageFooter());
            summary.append("</body></html>"); //NON-NLS
            output.write(summary.toString());
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find summary.html file to write to."); //NON-NLS
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Did not recognize encoding when writing summary.hmtl."); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Writer for summary.html."); //NON-NLS
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get current sleuthkit Case for the HTML report.");
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    @Messages({
        "ReportHTML.writeSum.case=Case:",
        "ReportHTML.writeSum.caseNumber=Case Number:",
        "ReportHTML.writeSum.caseNumImages=Number of data sources in case:",
        "ReportHTML.writeSum.caseNotes=Notes:",
        "ReportHTML.writeSum.examiner=Examiner:"
    })
    /**
     * Write the case details section of the summary for this report.
     *
     * @return StringBuilder updated html report with case details
     */
    private StringBuilder writeSummaryCaseDetails() {
        StringBuilder summary = new StringBuilder();

        final boolean agencyLogoSet = reportBranding.getAgencyLogoPath() != null && !reportBranding.getAgencyLogoPath().isEmpty();

        // Case
        String caseName = currentCase.getDisplayName();
        String caseNumber = currentCase.getNumber();
        int imagecount;
        try {
            imagecount = currentCase.getDataSources().size();
        } catch (TskCoreException ex) {
            imagecount = 0;
        }
        String caseNotes = currentCase.getCaseNotes();

        // Examiner
        String examinerName = currentCase.getExaminer();

        // Start the layout.
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

        // Case details
        summary.append("<tr><td>").append(Bundle.ReportHTML_writeSum_case()).append("</td><td>") //NON-NLS
                .append(formatHtmlString(caseName)).append("</td></tr>\n"); //NON-NLS

        if (!caseNumber.isEmpty()) {
            summary.append("<tr><td>").append(Bundle.ReportHTML_writeSum_caseNumber()).append("</td><td>") //NON-NLS
                    .append(formatHtmlString(caseNumber)).append("</td></tr>\n"); //NON-NLS
        }

        summary.append("<tr><td>").append(Bundle.ReportHTML_writeSum_caseNumImages()).append("</td><td>") //NON-NLS
                .append(imagecount).append("</td></tr>\n"); //NON-NLS

        if (!caseNotes.isEmpty()) {
            summary.append("<tr><td>").append(Bundle.ReportHTML_writeSum_caseNotes()).append("</td><td>") //NON-NLS
                    .append(formatHtmlString(caseNotes)).append("</td></tr>\n"); //NON-NLS
        }

        // Examiner details
        if (!examinerName.isEmpty()) {
            summary.append("<tr><td>").append(Bundle.ReportHTML_writeSum_examiner()).append("</td><td>") //NON-NLS
                    .append(formatHtmlString(examinerName)).append("</td></tr>\n"); //NON-NLS
        }

        // End the layout.
        summary.append("</table>\n"); //NON-NLS
        summary.append("</div>\n"); //NON-NLS
        summary.append("<div class=\"clear\"></div>\n"); //NON-NLS
        summary.append("</div>\n"); //NON-NLS
        return summary;
    }

    /**
     * Write the Image Information section of the summary for this report.
     *
     * @return StringBuilder updated html report with Image Information
     */
    private StringBuilder writeSummaryImageInfo() {
        StringBuilder summary = new StringBuilder();
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
        return summary;
    }

    /**
     * Write the software information section of the summary for this report.
     *
     * @return StringBuilder updated html report with software information
     */
    private StringBuilder writeSummarySoftwareInfo(SleuthkitCase skCase, List<IngestJobInfo> ingestJobs) {
        StringBuilder summary = new StringBuilder();
        summary.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.softwareInfoHeading"));
        summary.append("<div class=\"info\">\n");
        summary.append("<table>\n");
        summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.autopsyVersion"))
                .append("</td><td>").append(Version.getVersion()).append("</td></tr>\n");
        Map<Long, IngestModuleInfo> moduleInfoHashMap = new HashMap<>();
        for (IngestJobInfo ingestJob : ingestJobs) {
            List<IngestModuleInfo> ingestModules = ingestJob.getIngestModuleInfo();
            for (IngestModuleInfo ingestModule : ingestModules) {
                if (!moduleInfoHashMap.containsKey(ingestModule.getIngestModuleId())) {
                    moduleInfoHashMap.put(ingestModule.getIngestModuleId(), ingestModule);
                }
            }
        }
        TreeMap<String, String> modules = new TreeMap<>();
        for (IngestModuleInfo moduleinfo : moduleInfoHashMap.values()) {
            modules.put(moduleinfo.getDisplayName(), moduleinfo.getVersion());
        }
        for (Map.Entry<String, String> module : modules.entrySet()) {
            summary.append("<tr><td>").append(module.getKey()).append(" Module:")
                    .append("</td><td>").append(module.getValue()).append("</td></tr>\n");
        }
        summary.append("</table>\n");
        summary.append("</div>\n");
        summary.append("<div class=\"clear\"></div>\n"); //NON-NLS
        return summary;
    }

    /**
     * Write the Ingest History section of the summary for this report.
     *
     * @return StringBuilder updated html report with ingest history
     */
    private StringBuilder writeSummaryIngestHistoryInfo(SleuthkitCase skCase, List<IngestJobInfo> ingestJobs) {
        StringBuilder summary = new StringBuilder();
        try {
            summary.append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.ingestHistoryHeading"));
            summary.append("<div class=\"info\">\n");
            int jobnumber = 1;

            for (IngestJobInfo ingestJob : ingestJobs) {
                summary.append("<h3>Job ").append(jobnumber).append(":</h3>\n");
                summary.append("<table>\n");
                summary.append("<tr><td>").append("Data Source:")
                        .append("</td><td>").append(skCase.getContentById(ingestJob.getObjectId()).getName()).append("</td></tr>\n");
                summary.append("<tr><td>").append("Status:")
                        .append("</td><td>").append(ingestJob.getStatus()).append("</td></tr>\n");
                summary.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "ReportHTML.writeSum.modulesEnabledHeading"))
                        .append("</td><td>");
                List<IngestModuleInfo> ingestModules = ingestJob.getIngestModuleInfo();
                summary.append("<ul>\n");
                for (IngestModuleInfo ingestModule : ingestModules) {
                    summary.append("<li>").append(ingestModule.getDisplayName()).append("</li>");
                }
                summary.append("</ul>\n");
                jobnumber++;
                summary.append("</td></tr>\n");
                summary.append("</table>\n");
            }
            summary.append("</div>\n");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get ingest jobs for the HTML report.");
        }
        return summary;
    }

    /**
     * Create a thumbnail of a given file.
     *
     * @param file The file from which to create the thumbnail.
     *
     * @return The path to the thumbnail file, or null if a thumbnail couldn't
     *         be created.
     */
    private String prepareThumbnail(AbstractFile file) {
        BufferedImage bufferedThumb = ImageUtils.getThumbnail(file, ImageUtils.ICON_SIZE_MEDIUM);

        /*
         * File name is normalized to account for possible attribute name which
         * will be separated by a ':' character.
         */
        String fileName = org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName(file.getName());

        File thumbFile = Paths.get(thumbsPath, fileName + ".png").toFile();
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

    /**
     * Apply escape sequence to special characters. Line feed and carriage
     * return character combinations will be converted to HTML line breaks.
     *
     * @param text The text to format.
     *
     * @return The formatted text.
     */
    private String formatHtmlString(String text) {
        String formattedString = StringEscapeUtils.escapeHtml4(text);
        return formattedString.replaceAll("(\r\n|\r|\n|\n\r)", "<br>");
    }

}
