/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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

import javax.swing.JPanel;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.*;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.BlackboardArtifact;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.CDATA;
import org.openide.filesystems.FileUtil;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;

/**
 * Generates a KML file based on geospatial information from the BlackBoard.
 */
class ReportKML implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportKML.class.getName());
    private static final String KML_STYLE_FILE = "style.kml";
    private static final String REPORT_KML = "ReportKML.kml";
    private static final String STYLESHEETS_PATH = "/org/sleuthkit/autopsy/report/stylesheets/";
    private static ReportKML instance = null;
    private Case currentCase;
    private SleuthkitCase skCase;
    private final SimpleDateFormat kmlDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private Namespace ns;
    private final String SEP = "<br>";

    private enum FeatureColor {
        RED("style.kml#redFeature"),
        GREEN("style.kml#greenFeature"),
        BLUE("style.kml#blueFeature"),
        PURPLE("style.kml#purpleFeature"),
        WHITE("style.kml#whiteFeature"),
        YELLOW("style.kml#yellowFeature");
        private final String color;

        FeatureColor(String color) {
            this.color = color;
        }

        String getColor() {
            return this.color;
        }
    }

    // Hidden constructor for the report
    private ReportKML() {
    }

    // Get the default implementation of this report
    public static synchronized ReportKML getDefault() {
        if (instance == null) {
            instance = new ReportKML();
        }
        return instance;
    }

    /**
     * Generates a body file format report for use with the MAC time tool.
     *
     * @param baseReportDir path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        try {
            currentCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.querying"));
        String kmlFileFullPath = baseReportDir + REPORT_KML; //NON-NLS
        
        skCase = currentCase.getSleuthkitCase();

        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.loading"));

        ns = Namespace.getNamespace("", "http://www.opengis.net/kml/2.2"); //NON-NLS

        Element kml = new Element("kml", ns); //NON-NLS
        kml.addNamespaceDeclaration(Namespace.getNamespace("gx", "http://www.google.com/kml/ext/2.2")); //NON-NLS
        kml.addNamespaceDeclaration(Namespace.getNamespace("kml", "http://www.opengis.net/kml/2.2")); //NON-NLS
        kml.addNamespaceDeclaration(Namespace.getNamespace("atom", "http://www.w3.org/2005/Atom")); //NON-NLS
        Document kmlDocument = new Document(kml);

        Element document = new Element("Document", ns); //NON-NLS
        kml.addContent(document);

        Element name = new Element("name", ns); //NON-NLS
        ReportBranding rb = new ReportBranding();
        name.setText(rb.getReportTitle() + " KML"); //NON-NLS
        document.addContent(name);

        // Check if ingest has finished
        if (IngestManager.getInstance().isIngestRunning()) {
            Element ingestwarning = new Element("snippet", ns); //NON-NLS
            ingestwarning.addContent(NbBundle.getMessage(this.getClass(), "ReportBodyFile.ingestWarning.text")); //NON-NLS
            document.addContent(ingestwarning);
        }

        // Create folder structure
        Element gpsExifMetadataFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataExifMetadataFolder = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/camera-icon-16.png"); //NON-NLS
        Element hrefExifMetadata = new Element("href", ns).addContent(cdataExifMetadataFolder); //NON-NLS
        gpsExifMetadataFolder.addContent(new Element("Icon", ns).addContent(hrefExifMetadata)); //NON-NLS

        Element gpsBookmarksFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataBookmarks = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gpsfav.png"); //NON-NLS
        Element hrefBookmarks = new Element("href", ns).addContent(cdataBookmarks); //NON-NLS
        gpsBookmarksFolder.addContent(new Element("Icon", ns).addContent(hrefBookmarks)); //NON-NLS

        Element gpsLastKnownLocationFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataLastKnownLocation = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-lastlocation.png"); //NON-NLS
        Element hrefLastKnownLocation = new Element("href", ns).addContent(cdataLastKnownLocation); //NON-NLS
        gpsLastKnownLocationFolder.addContent(new Element("Icon", ns).addContent(hrefLastKnownLocation)); //NON-NLS

        Element gpsRouteFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataRoute = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-trackpoint.png"); //NON-NLS
        Element hrefRoute = new Element("href", ns).addContent(cdataRoute); //NON-NLS
        gpsRouteFolder.addContent(new Element("Icon", ns).addContent(hrefRoute)); //NON-NLS

        Element gpsSearchesFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataSearches = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-search.png"); //NON-NLS
        Element hrefSearches = new Element("href", ns).addContent(cdataSearches); //NON-NLS
        gpsSearchesFolder.addContent(new Element("Icon", ns).addContent(hrefSearches)); //NON-NLS

        Element gpsTrackpointsFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataTrackpoints = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-trackpoint.png"); //NON-NLS
        Element hrefTrackpoints = new Element("href", ns).addContent(cdataTrackpoints); //NON-NLS
        gpsTrackpointsFolder.addContent(new Element("Icon", ns).addContent(hrefTrackpoints)); //NON-NLS

        gpsExifMetadataFolder.addContent(new Element("name", ns).addContent("EXIF Metadata")); //NON-NLS
        gpsBookmarksFolder.addContent(new Element("name", ns).addContent("GPS Bookmarks")); //NON-NLS
        gpsLastKnownLocationFolder.addContent(new Element("name", ns).addContent("GPS Last Known Location")); //NON-NLS
        gpsRouteFolder.addContent(new Element("name", ns).addContent("GPS Routes")); //NON-NLS
        gpsSearchesFolder.addContent(new Element("name", ns).addContent("GPS Searches")); //NON-NLS
        gpsTrackpointsFolder.addContent(new Element("name", ns).addContent("GPS Trackpoints")); //NON-NLS

        document.addContent(gpsExifMetadataFolder);
        document.addContent(gpsBookmarksFolder);
        document.addContent(gpsLastKnownLocationFolder);
        document.addContent(gpsRouteFolder);
        document.addContent(gpsSearchesFolder);
        document.addContent(gpsTrackpointsFolder);

        ReportProgressPanel.ReportStatus result = ReportProgressPanel.ReportStatus.COMPLETE;

        /**
         * In the following code, nulls are okay, and are handled when we go to
         * write out the KML feature. Nulls are expected to be returned from any
         * method where the artifact is not found and is handled in the
         * individual feature creation methods. This is done because we don't
         * know beforehand which attributes will be included for which artifact,
         * as anyone could write a module that adds additional attributes to an
         * artifact.
         *
         * If there are any issues reading the database getting artifacts and
         * attributes, or any exceptions thrown during this process, a severe
         * error is logged, the report is marked as "Incomplete KML Report", and
         * we use a best-effort method to generate KML information on everything
         * we can successfully pull out of the database.
         */
        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF)) {
                String fileName = "";
                long fileId = 0;
                try {
                    Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED);
                    String desc = getDescriptionFromArtifact(artifact, "EXIF Metadata With Locations"); //NON-NLS
                    Double lat = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
                    Double lon = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
                    Element point = makePoint(lat, lon, getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE));

                    if (lat != null && lat != 0.0 && lon != null && lon != 0.0) {
                        AbstractFile abstractFile = artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
                        fileName = abstractFile.getName();
                        fileId = abstractFile.getId();
                        Path path;
                        copyFileUsingStream(abstractFile, Paths.get(baseReportDir, abstractFile.getName()).toFile());
                        try {
                            path = Paths.get(removeLeadingImgAndVol(abstractFile.getUniquePath()));
                        } catch (TskCoreException ex) {
                            path = Paths.get(abstractFile.getParentPath(), abstractFile.getName());
                        }
                        String formattedCoordinates = String.format("%.2f, %.2f", lat, lon);
                        if (path == null) {
                            path = Paths.get(abstractFile.getName());
                        }
                        gpsExifMetadataFolder.addContent(makePlacemarkWithPicture(abstractFile.getName(), FeatureColor.RED, desc, timestamp, point, path, formattedCoordinates));
                    }
                } catch (ReadContentInputStreamException ex) {
                    logger.log(Level.WARNING, String.format("Error reading file '%s' (id=%d).", fileName, fileId), ex);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Could not extract photo information.", ex); //NON-NLS
                    result = ReportProgressPanel.ReportStatus.ERROR;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not extract photos with EXIF metadata.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK)) {
                try {
                    Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
                    String desc = getDescriptionFromArtifact(artifact, "GPS Bookmark"); //NON-NLS
                    Double lat = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
                    Double lon = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
                    Element point = makePoint(lat, lon, getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE));
                    String bookmarkName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
                    String formattedCoordinates = String.format("%.2f, %.2f", lat, lon);
                    gpsBookmarksFolder.addContent(makePlacemark(bookmarkName, FeatureColor.BLUE, desc, timestamp, point, formattedCoordinates));
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Could not extract Bookmark information.", ex); //NON-NLS
                    result = ReportProgressPanel.ReportStatus.ERROR;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not get GPS Bookmarks from database.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION)) {
                try {
                    Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
                    String desc = getDescriptionFromArtifact(artifact, "GPS Last Known Location"); //NON-NLS
                    Double lat = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
                    Double lon = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
                    Double alt = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
                    Element point = makePoint(lat, lon, alt);
                    String formattedCoordinates = String.format("%.2f, %.2f", lat, lon);
                    gpsLastKnownLocationFolder.addContent(makePlacemark("Last Known Location", FeatureColor.PURPLE, desc, timestamp, point, formattedCoordinates)); //NON-NLS
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Could not extract Last Known Location information.", ex); //NON-NLS
                    result = ReportProgressPanel.ReportStatus.ERROR;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not get GPS Last Known Location from database.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)) {
                try {
                    Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
                    String desc = getDescriptionFromArtifact(artifact, "GPS Route");
                    Double latitudeStart = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
                    Double longitudeStart = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);
                    Double latitudeEnd = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
                    Double longitudeEnd = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);
                    Double altitude = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);

                    Element route = makeLineString(latitudeStart, longitudeStart, altitude, latitudeEnd, longitudeEnd, altitude);
                    Element startingPoint = makePoint(latitudeStart, longitudeStart, altitude);
                    Element endingPoint = makePoint(latitudeEnd, longitudeEnd, altitude);

                    String formattedCoordinates = String.format("%.2f, %.2f to %.2f, %.2f", latitudeStart, longitudeStart, latitudeEnd, longitudeEnd);
                    gpsRouteFolder.addContent(makePlacemark("As-the-crow-flies Route", FeatureColor.GREEN, desc, timestamp, route, formattedCoordinates)); //NON-NLS

                    formattedCoordinates = String.format("%.2f, %.2f", latitudeStart, longitudeStart);
                    gpsRouteFolder.addContent(makePlacemark("Start", FeatureColor.GREEN, desc, timestamp, startingPoint, formattedCoordinates)); //NON-NLS

                    formattedCoordinates = String.format("%.2f, %.2f", latitudeEnd, longitudeEnd);
                    gpsRouteFolder.addContent(makePlacemark("End", FeatureColor.GREEN, desc, timestamp, endingPoint, formattedCoordinates)); //NON-NLS
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Could not extract GPS Route information.", ex); //NON-NLS
                    result = ReportProgressPanel.ReportStatus.ERROR;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not get GPS Routes from database.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH)) {
                Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
                String desc = getDescriptionFromArtifact(artifact, "GPS Search"); //NON-NLS
                Double lat = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
                Double lon = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
                Double alt = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
                Element point = makePoint(lat, lon, alt);
                String formattedCoordinates = String.format("%.2f, %.2f", lat, lon);
                String searchName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
                if (searchName == null || searchName.isEmpty()) {
                    searchName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
                }
                if (searchName == null || searchName.isEmpty()) {
                    searchName = "GPS Search";
                }
                gpsSearchesFolder.addContent(makePlacemark(searchName, FeatureColor.WHITE, desc, timestamp, point, formattedCoordinates)); //NON-NLS
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not get GPS Searches from database.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT)) {
                try {
                    Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
                    String desc = getDescriptionFromArtifact(artifact, "GPS Trackpoint"); //NON-NLS
                    Double lat = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
                    Double lon = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
                    Double alt = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
                    Element point = makePoint(lat, lon, alt);
                    String formattedCoordinates = String.format("%.2f, %.2f, %.2f", lat, lon, alt);
                    String trackName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
                    if (trackName == null || trackName.isEmpty()) {
                        trackName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
                    }
                    if (trackName == null || trackName.isEmpty()) {
                        trackName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG);
                    }
                    if (trackName == null || trackName.isEmpty()) {
                        trackName = "GPS Trackpoint";
                    }
                    gpsTrackpointsFolder.addContent(makePlacemark(trackName, FeatureColor.YELLOW, desc, timestamp, point, formattedCoordinates));
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Could not extract Trackpoint information.", ex); //NON-NLS
                    result = ReportProgressPanel.ReportStatus.ERROR;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not get GPS Trackpoints from database.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        // Copy the style sheet
        try {
            InputStream input = getClass().getResourceAsStream(STYLESHEETS_PATH + KML_STYLE_FILE); // Preserve slash direction
            OutputStream output = new FileOutputStream(baseReportDir + KML_STYLE_FILE); // Preserve slash direction
            FileUtil.copy(input, output);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error placing KML stylesheet. The .KML file will not function properly.", ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try (FileOutputStream writer = new FileOutputStream(kmlFileFullPath)) {
            XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
            outputter.output(kmlDocument, writer);
            String prependedStatus = "";
            if (result == ReportProgressPanel.ReportStatus.ERROR) {
                prependedStatus = "Incomplete ";
            }
            Case.getOpenCase().addReport(kmlFileFullPath,
                    NbBundle.getMessage(this.getClass(), "ReportKML.genReport.srcModuleName.text"),
                    prependedStatus + NbBundle.getMessage(this.getClass(), "ReportKML.genReport.reportName"));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not write the KML file.", ex); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
        } catch (TskCoreException ex) {
            String errorMessage = String.format("Error adding %s to case as a report", kmlFileFullPath); //NON-NLS
            logger.log(Level.SEVERE, errorMessage, ex);
            result = ReportProgressPanel.ReportStatus.ERROR;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        progressPanel.complete(result);
    }

    /**
     * Get a Double from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The Double if it exists, or null if not
     */
    private Double getDouble(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        Double returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                Double value = bba.getValueDouble();
                returnValue = value;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting Double value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * Get a Long from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The Long if it exists, or null if not
     */
    private Long getLong(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        Long returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                Long value = bba.getValueLong();
                returnValue = value;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting Long value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * Get an Integer from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The Integer if it exists, or null if not
     */
    private Integer getInteger(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        Integer returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                Integer value = bba.getValueInt();
                returnValue = value;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting Integer value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * Get a String from an artifact if it exists, return null otherwise.
     *
     * @param artifact The artifact to query
     * @param type     The attribute type we're looking for
     *
     * @return The String if it exists, or null if not
     */
    private String getString(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) {
        String returnValue = null;
        try {
            BlackboardAttribute bba = artifact.getAttribute(new BlackboardAttribute.Type(type));
            if (bba != null) {
                String value = bba.getValueString();
                if (value != null && !value.isEmpty()) {
                    returnValue = value;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting String value: " + type.toString(), ex); //NON-NLS
        }
        return returnValue;
    }

    /**
     * This method creates a text description for a map feature using all the
     * geospatial and time data we can for the Artifact. It queries the
     * following attributes:
     *
     * TSK_GEO_LATITUDE 54; TSK_GEO_LONGITUDE 55; TSK_GEO_LATITUDE_START 98;
     * TSK_GEO_LATITUDE_END 99; TSK_GEO_LONGITUDE_START 100;
     * TSK_GEO_LONGITUDE_END 101; TSK_GEO_VELOCITY 56; TSK_GEO_ALTITUDE 57;
     * TSK_GEO_BEARING 58; TSK_GEO_HPRECISION 59; TSK_GEO_VPRECISION 60;
     * TSK_GEO_MAPDATUM 61; TSK_DATETIME_START 83; TSK_DATETIME_END 84;
     * TSK_LOCATION 86; TSK_PATH_SOURCE 94;
     *
     * @param artifact    the artifact to query.
     * @param featureType the type of Artifact we're working on.
     *
     * @return a String with the information we have available
     */
    private String getDescriptionFromArtifact(BlackboardArtifact artifact, String featureType) {
        StringBuilder result = new StringBuilder("<h3>" + featureType + "</h3>"); //NON-NLS

        String name = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        if (name != null && !name.isEmpty()) {
            result.append("<b>Name:</b> ").append(name).append(SEP); //NON-NLS
        }

        String location = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
        if (location != null && !location.isEmpty()) {
            result.append("<b>Location:</b> ").append(location).append(SEP); //NON-NLS
        }

        Long timestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME);
        if (timestamp != null) {
            result.append("<b>Timestamp:</b> ").append(getTimeStamp(timestamp)).append(SEP); //NON-NLS
            result.append("<b>Unix timestamp:</b> ").append(timestamp).append(SEP); //NON-NLS
        }

        Long startingTimestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START);
        if (startingTimestamp != null) {
            result.append("<b>Starting Timestamp:</b> ").append(getTimeStamp(startingTimestamp)).append(SEP); //NON-NLS
            result.append("<b>Starting Unix timestamp:</b> ").append(startingTimestamp).append(SEP); //NON-NLS
        }

        Long endingTimestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END);
        if (endingTimestamp != null) {
            result.append("<b>Ending Timestamp:</b> ").append(getTimeStamp(endingTimestamp)).append(SEP); //NON-NLS
            result.append("<b>Ending Unix timestamp:</b> ").append(endingTimestamp).append(SEP); //NON-NLS
        }

        Long createdTimestamp = getLong(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED);
        if (createdTimestamp != null) {
            result.append("<b>Created Timestamp:</b> ").append(getTimeStamp(createdTimestamp)).append(SEP); //NON-NLS
            result.append("<b>Created Unix timestamp:</b> ").append(createdTimestamp).append(SEP); //NON-NLS
        }

        Double latitude = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE);
        if (latitude != null) {
            result.append("<b>Latitude:</b> ").append(latitude).append(SEP); //NON-NLS
        }

        Double longitude = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE);
        if (longitude != null) {
            result.append("<b>Longitude:</b> ").append(longitude).append(SEP); //NON-NLS
        }

        Double latitudeStart = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START);
        if (latitudeStart != null) {
            result.append("<b>Latitude Start:</b> ").append(latitudeStart).append(SEP); //NON-NLS
        }

        Double longitudeStart = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START);
        if (longitudeStart != null) {
            result.append("<b>Longitude Start:</b> ").append(longitudeStart).append(SEP); //NON-NLS
        }

        Double latitudeEnd = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END);
        if (latitudeEnd != null) {
            result.append("<b>Latitude End:</b> ").append(latitudeEnd).append(SEP); //NON-NLS
        }

        Double longitudeEnd = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END);
        if (longitudeEnd != null) {
            result.append("<b>Longitude End:</b> ").append(longitudeEnd).append(SEP); //NON-NLS
        }

        Double velocity = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VELOCITY);
        if (velocity != null) {
            result.append("<b>Velocity:</b> ").append(velocity).append(SEP); //NON-NLS
        }

        Double altitude = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE);
        if (altitude != null) {
            result.append("<b>Altitude:</b> ").append(altitude).append(SEP); //NON-NLS
        }

        Double bearing = getDouble(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_BEARING);
        if (bearing != null) {
            result.append("<b>Bearing:</b> ").append(bearing).append(SEP); //NON-NLS
        }

        Integer hPrecision = getInteger(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_HPRECISION);
        if (hPrecision != null) {
            result.append("<b>Horizontal Precision Figure of Merit:</b> ").append(hPrecision).append(SEP); //NON-NLS
        }

        Integer vPrecision = getInteger(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VPRECISION);
        if (vPrecision != null) {
            result.append("<b>Vertical Precision Figure of Merit:</b> ").append(vPrecision).append(SEP); //NON-NLS
        }

        String mapDatum = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_MAPDATUM);
        if (mapDatum != null && !mapDatum.isEmpty()) {
            result.append("<b>Map Datum:</b> ").append(mapDatum).append(SEP); //NON-NLS
        }

        String programName = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        if (programName != null && !programName.isEmpty()) {
            result.append("<b>Reported by:</b> ").append(programName).append(SEP); //NON-NLS
        }

        String flag = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FLAG);
        if (flag != null && !flag.isEmpty()) {
            result.append("<b>Flag:</b> ").append(flag).append(SEP); //NON-NLS
        }

        String pathSource = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_SOURCE);
        if (pathSource != null && !pathSource.isEmpty()) {
            result.append("<b>Source:</b> ").append(pathSource).append(SEP); //NON-NLS
        }

        String deviceMake = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
        if (deviceMake != null && !deviceMake.isEmpty()) {
            result.append("<b>Device Make:</b> ").append(deviceMake).append(SEP); //NON-NLS
        }

        String deviceModel = getString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
        if (deviceModel != null && !deviceModel.isEmpty()) {
            result.append("<b>Device Model:</b> ").append(deviceModel).append(SEP); //NON-NLS
        }

        return result.toString();
    }

    private String getTimeStamp(long timeStamp) {
        return kmlDateFormat.format(new java.util.Date(timeStamp * 1000));
    }

    /**
     * Create a Point for use in a Placemark. Note in this method altitude is
     * ignored, as Google Earth apparently has trouble using altitudes for
     * LineStrings, though the parameters are still in the call. Also note that
     * any null value passed in will be set to 0.0, under the idea that it is
     * better to show some data with gaps, than to show nothing at all.
     *
     * @param latitude  point latitude
     * @param longitude point longitude
     * @param altitude  point altitude. Currently ignored.
     *
     * @return the Point as an Element
     */
    private Element makePoint(Double latitude, Double longitude, Double altitude) {
        if (latitude == null) {
            latitude = 0.0;
        }
        if (longitude == null) {
            longitude = 0.0;
        }
        if (altitude == null) {
            altitude = 0.0;
        }
        Element point = new Element("Point", ns); //NON-NLS

        // KML uses lon, lat. Deliberately reversed.
        Element coordinates = new Element("coordinates", ns).addContent(longitude + "," + latitude + "," + altitude); //NON-NLS

        if (altitude != 0) {
            /*
             * Though we are including a non-zero altitude, clamp it to the
             * ground because inaccuracies from the GPS data can cause the
             * terrain to occlude points when zoomed in otherwise. Show the
             * altitude, but keep the point clamped to the ground. We may change
             * this later for flying GPS sensors.
             */
            Element altitudeMode = new Element("altitudeMode", ns).addContent("clampToGround"); //NON-NLS
            point.addContent(altitudeMode);
        }
        point.addContent(coordinates);

        return point;
    }

    /**
     * Create a LineString for use in a Placemark. Note in this method, start
     * and stop altitudes get ignored, as Google Earth apparently has trouble
     * using altitudes for LineStrings, though the parameters are still in the
     * call. Also note that any null value passed in will be set to 0.0, under
     * the idea that it is better to show some data with gaps, than to show
     * nothing at all.
     *
     * @param startLatitude  Starting latitude
     * @param startLongitude Starting longitude
     * @param startAltitude  Starting altitude. Currently ignored.
     * @param stopLatitude   Ending latitude
     * @param stopLongitude  Ending longitude
     * @param stopAltitude   Ending altitude. Currently ignored.
     *
     * @return the Line as an Element
     */
    private Element makeLineString(Double startLatitude, Double startLongitude, Double startAltitude, Double stopLatitude, Double stopLongitude, Double stopAltitude) {
        if (startLatitude == null) {
            startLatitude = 0.0;
        }
        if (startLongitude == null) {
            startLongitude = 0.0;
        }
        if (startAltitude == null) {
            startAltitude = 0.0;
        }
        if (stopLatitude == null) {
            stopLatitude = 0.0;
        }
        if (stopLongitude == null) {
            stopLongitude = 0.0;
        }
        if (stopAltitude == null) {
            stopAltitude = 0.0;
        }

        Element lineString = new Element("LineString", ns); //NON-NLS
        lineString.addContent(new Element("extrude", ns).addContent("1")); //NON-NLS
        lineString.addContent(new Element("tessellate", ns).addContent("1")); //NON-NLS
        lineString.addContent(new Element("altitudeMode", ns).addContent("clampToGround")); //NON-NLS
        // KML uses lon, lat. Deliberately reversed.
        lineString.addContent(new Element("coordinates", ns).addContent(
                startLongitude + "," + startLatitude + ",0.0,"
                + stopLongitude + "," + stopLatitude + ",0.0")); //NON-NLS
        return lineString;
    }

    /**
     * Make a Placemark for use in displaying features. Takes a
     * coordinate-bearing feature (Point, LineString, etc) and places it in the
     * Placemark element.
     *
     * @param name        Placemark name
     * @param color       Placemark color
     * @param description Description for the info bubble on the map
     * @param timestamp   Placemark timestamp
     * @param feature     The feature to show. Could be Point, LineString, etc.
     * @param coordinates The coordinates to display in the list view snippet
     *
     * @return the entire KML placemark
     */
    private Element makePlacemark(String name, FeatureColor color, String description, Long timestamp, Element feature, String coordinates) {
        Element placemark = new Element("Placemark", ns); //NON-NLS
        if (name != null && !name.isEmpty()) {
            placemark.addContent(new Element("name", ns).addContent(name)); //NON-NLS
        } else if (timestamp != null) {
            placemark.addContent(new Element("name", ns).addContent(getTimeStamp(timestamp))); //NON-NLS
        } else {
            placemark.addContent(new Element("name", ns).addContent("")); //NON-NLS
        }
        placemark.addContent(new Element("styleUrl", ns).addContent(color.getColor())); //NON-NLS
        placemark.addContent(new Element("description", ns).addContent(description)); //NON-NLS
        if (timestamp != null) {
            Element time = new Element("TimeStamp", ns); //NON-NLS
            time.addContent(new Element("when", ns).addContent(getTimeStamp(timestamp))); //NON-NLS
            placemark.addContent(time);
        }
        placemark.addContent(feature);
        if (coordinates != null && !coordinates.isEmpty()) {
            placemark.addContent(new Element("snippet", ns).addContent(coordinates)); //NON-NLS
        }
        return placemark;
    }

    /**
     * Make a Placemark for use in displaying features. Takes a
     * coordinate-bearing feature (Point, LineString, etc) and places it in the
     * Placemark element.
     *
     * @param name        Placemark file name
     * @param color       Placemark color
     * @param description Description for the info bubble on the map
     * @param timestamp   Placemark timestamp
     * @param feature     The feature to show. Could be Point, LineString, etc.
     * @param path        The path to the file in the source image
     * @param coordinates The coordinates to display in the list view snippet
     *
     * @return the entire KML Placemark, including a picture.
     */
    private Element makePlacemarkWithPicture(String name, FeatureColor color, String description, Long timestamp, Element feature, Path path, String coordinates) {
        Element placemark = new Element("Placemark", ns); //NON-NLS
        Element desc = new Element("description", ns); //NON-NLS
        if (name != null && !name.isEmpty()) {
            placemark.addContent(new Element("name", ns).addContent(name)); //NON-NLS
            String image = "<img src='" + name + "' width='400'/>"; //NON-NLS
            desc.addContent(image);
        }
        placemark.addContent(new Element("styleUrl", ns).addContent(color.getColor())); //NON-NLS
        if (path != null) {
            String pathAsString = path.toString();
            if (pathAsString != null && !pathAsString.isEmpty()) {
                desc.addContent(description + "<b>Source Path:</b> " + pathAsString);
            }
        }
        placemark.addContent(desc);

        if (timestamp != null) {
            Element time = new Element("TimeStamp", ns); //NON-NLS
            time.addContent(new Element("when", ns).addContent(getTimeStamp(timestamp))); //NON-NLS
            placemark.addContent(time);
        }
        placemark.addContent(feature);
        if (coordinates != null && !coordinates.isEmpty()) {
            placemark.addContent(new Element("snippet", ns).addContent(coordinates)); //NON-NLS
        }
        return placemark;
    }

    /**
     * Extracts the file to the output folder.
     *
     * @param inputFile  The input AbstractFile to copy
     * @param outputFile the output file
     *
     * @throws ReadContentInputStreamException When a read error occurs.
     * @throws IOException                     When a general file exception
     *                                         occurs.
     */
    private void copyFileUsingStream(AbstractFile inputFile, File outputFile) throws ReadContentInputStreamException, IOException {
        byte[] buffer = new byte[65536];
        int length;
        outputFile.createNewFile();
        try (InputStream is = new ReadContentInputStream(inputFile);
                OutputStream os = new FileOutputStream(outputFile)) {
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
        }
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ReportKML.getName.text");
        return name;
    }

    @Override
    public String getRelativeFilePath() {
        return "ReportKML.kml"; //NON-NLS
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "ReportKML.getDesc.text");
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }

    /**
     * This is a smash-n-grab from AbstractFile.createNonUniquePath(String).
     * This method is intended to be removed when img_ and vol_ are no longer
     * added to images and volumes respectively, OR when AbstractFile is sorted
     * out with respect to this.
     *
     * @param uniquePath The path to sanitize.
     *
     * @return path without leading img_/vol_ in position 0 or 1 respectively.
     */
    private static String removeLeadingImgAndVol(String uniquePath) {
        // split the path into parts
        String[] pathSegments = uniquePath.replaceFirst("^/*", "").split("/"); //NON-NLS

        // Replace image/volume name if they exist in specific entries
        if (pathSegments.length > 0) {
            pathSegments[0] = pathSegments[0].replaceFirst("^img_", ""); //NON-NLS
        }
        if (pathSegments.length > 1) {
            pathSegments[1] = pathSegments[1].replaceFirst("^vol_", ""); //NON-NLS
        }

        // Assemble the path
        StringBuilder strbuf = new StringBuilder();
        for (String segment : pathSegments) {
            if (!segment.isEmpty()) {
                strbuf.append("/").append(segment);
            }
        }
        return strbuf.toString();
    }
}
