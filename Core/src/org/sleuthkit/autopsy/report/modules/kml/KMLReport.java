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
package org.sleuthkit.autopsy.report.modules.kml;

import org.sleuthkit.autopsy.report.GeneralReportModule;
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
import java.util.List;
import java.util.logging.Level;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.CDATA;
import org.openide.filesystems.FileUtil;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.geolocation.datamodel.EXIFMetadataPoint;
import org.sleuthkit.autopsy.geolocation.datamodel.GeolocationManager;
import org.sleuthkit.autopsy.geolocation.datamodel.Route;
import org.sleuthkit.autopsy.report.ReportBranding;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.autopsy.geolocation.datamodel.BlackboardArtifactPoint;

/**
 * Generates a KML file based on geospatial information from the BlackBoard.
 */
class KMLReport implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(KMLReport.class.getName());
    private static final String KML_STYLE_FILE = "style.kml";
    private static final String REPORT_KML = "ReportKML.kml";
    private static final String STYLESHEETS_PATH = "/org/sleuthkit/autopsy/report/stylesheets/";
    private static KMLReport instance = null;
    private Case currentCase;
    private SleuthkitCase skCase;
    private final SimpleDateFormat kmlDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private Namespace ns;
    private final String SEP = "<br>";
    
    private Element gpsExifMetadataFolder;
    private Element gpsBookmarksFolder;
    private Element gpsLastKnownLocationFolder;
    private Element gpsRouteFolder;
    private Element gpsSearchesFolder;
    private Element gpsTrackpointsFolder;

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
    private KMLReport() {
    }

    // Get the default implementation of this report
    public static synchronized KMLReport getDefault() {
        if (instance == null) {
            instance = new KMLReport();
        }
        return instance;
    }

    /**
     * Generates a body file format report for use with the MAC time tool.
     *
     * @param baseReportDir path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @NbBundle.Messages({
        "KMLReport.unableToExtractPhotos=Could not extract photo information.",
        "KMLReport.exifPhotoError=Could not extract photos with EXIF metadata.",
        "KMLReport.bookmarkError=Could not extract Bookmark information.",
        "KMLReport.gpsBookmarkError=Could not get GPS Bookmarks from database.",
        "KMLReport.locationError=Could not extract Last Known Location information.",
        "KMLReport.locationDatabaseError=Could not get GPS Last Known Location from database.",
        "KMLReport.gpsRouteError=Could not extract GPS Route information.",
        "KMLReport.gpsRouteDatabaseError=Could not get GPS Routes from database.",
        "KMLReport.gpsSearchDatabaseError=Could not get GPS Searches from database.",
        "KMLReport.trackpointError=Could not extract Trackpoint information.",        
        "KMLReport.trackpointDatabaseError=Could not get GPS Trackpoints from database.",        
        "KMLReport.stylesheetError=Error placing KML stylesheet. The .KML file will not function properly.",
        "KMLReport.kmlFileWriteError=Could not write the KML file.",
        "# {0} - filePath",
        "KMLReport.errorGeneratingReport=Error adding {0} to case as a report.",
        "KMLReport.unableToOpenCase=Exception while getting open case."
    })
    @Override
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.querying"));
        String kmlFileFullPath = baseReportDir + REPORT_KML; //NON-NLS
        String errorMessage = "";
        
        skCase = currentCase.getSleuthkitCase();

        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.loading"));

        Document kmlDocument = setupReportDocument();

        ReportProgressPanel.ReportStatus result = ReportProgressPanel.ReportStatus.COMPLETE;
            
         try {
             makeRoutes(skCase);
             addLocationsToReport(skCase, baseReportDir);
         } catch(TskCoreException | IOException ex) {
             errorMessage = "Failed to complete report.";
            logger.log(Level.SEVERE, errorMessage, ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
         }

        // Copy the style sheet
        try {
            InputStream input = getClass().getResourceAsStream(STYLESHEETS_PATH + KML_STYLE_FILE); // Preserve slash direction
            OutputStream output = new FileOutputStream(baseReportDir + KML_STYLE_FILE); // Preserve slash direction
            FileUtil.copy(input, output);
        } catch (IOException ex) {
            errorMessage = Bundle.KMLReport_stylesheetError();
            logger.log(Level.SEVERE, errorMessage, ex); //NON-NLS
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        try (FileOutputStream writer = new FileOutputStream(kmlFileFullPath)) {
            XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
            outputter.output(kmlDocument, writer);
            String prependedStatus = "";
            if (result == ReportProgressPanel.ReportStatus.ERROR) {
                prependedStatus = "Incomplete ";
            }
            Case.getCurrentCaseThrows().addReport(kmlFileFullPath,
                    NbBundle.getMessage(this.getClass(), "ReportKML.genReport.srcModuleName.text"),
                    prependedStatus + NbBundle.getMessage(this.getClass(), "ReportKML.genReport.reportName"));
        } catch (IOException ex) {
            errorMessage = Bundle.KMLReport_kmlFileWriteError();
            logger.log(Level.SEVERE, errorMessage, ex); //NON-NLS
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, errorMessage);
        } catch (TskCoreException ex) {
            errorMessage = Bundle.KMLReport_errorGeneratingReport(kmlFileFullPath);
            logger.log(Level.SEVERE, errorMessage, ex);
            result = ReportProgressPanel.ReportStatus.ERROR;
        } catch (NoCurrentCaseException ex) {
            errorMessage = Bundle.KMLReport_unableToOpenCase();
            logger.log(Level.SEVERE, errorMessage, ex);
            result = ReportProgressPanel.ReportStatus.ERROR;
        }

        progressPanel.complete(result, errorMessage);
    }
    
    
    private Document setupReportDocument() {
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
        gpsExifMetadataFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataExifMetadataFolder = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/camera-icon-16.png"); //NON-NLS
        Element hrefExifMetadata = new Element("href", ns).addContent(cdataExifMetadataFolder); //NON-NLS
        gpsExifMetadataFolder.addContent(new Element("Icon", ns).addContent(hrefExifMetadata)); //NON-NLS

        gpsBookmarksFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataBookmarks = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gpsfav.png"); //NON-NLS
        Element hrefBookmarks = new Element("href", ns).addContent(cdataBookmarks); //NON-NLS
        gpsBookmarksFolder.addContent(new Element("Icon", ns).addContent(hrefBookmarks)); //NON-NLS

        gpsLastKnownLocationFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataLastKnownLocation = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-lastlocation.png"); //NON-NLS
        Element hrefLastKnownLocation = new Element("href", ns).addContent(cdataLastKnownLocation); //NON-NLS
        gpsLastKnownLocationFolder.addContent(new Element("Icon", ns).addContent(hrefLastKnownLocation)); //NON-NLS

        gpsRouteFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataRoute = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-trackpoint.png"); //NON-NLS
        Element hrefRoute = new Element("href", ns).addContent(cdataRoute); //NON-NLS
        gpsRouteFolder.addContent(new Element("Icon", ns).addContent(hrefRoute)); //NON-NLS

        gpsSearchesFolder = new Element("Folder", ns); //NON-NLS
        CDATA cdataSearches = new CDATA("https://raw.githubusercontent.com/sleuthkit/autopsy/develop/Core/src/org/sleuthkit/autopsy/images/gps-search.png"); //NON-NLS
        Element hrefSearches = new Element("href", ns).addContent(cdataSearches); //NON-NLS
        gpsSearchesFolder.addContent(new Element("Icon", ns).addContent(hrefSearches)); //NON-NLS

        gpsTrackpointsFolder = new Element("Folder", ns); //NON-NLS
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
        
        return kmlDocument;
    }
    
    
    void addExifMetadataContent(EXIFMetadataPoint location, String baseReportDirectory) throws IOException{
        Element point = makePoint(location);
        if(point == null) {
            return;
        }
        
        AbstractFile abstractFile = location.getImage();
        String details = "<h3>EXIF Metadata With Locations</h3>" + location.getDetails();

        Path path;
        copyFileUsingStream(abstractFile, Paths.get(baseReportDirectory, abstractFile.getName()).toFile());
        try {
            path = Paths.get(removeLeadingImgAndVol(abstractFile.getUniquePath()));
        } catch (TskCoreException ex) {
            path = Paths.get(abstractFile.getParentPath(), abstractFile.getName());
        }
        if (path == null) {
            path = Paths.get(abstractFile.getName());
        } 
        
        gpsExifMetadataFolder.addContent(makePlacemarkWithPicture(abstractFile.getName(), FeatureColor.RED, details, location.getTimestamp(), point, path, location.getFormattedCoordinates()));
    }
    
    void addLocationsToReport(SleuthkitCase skCase, String baseReportDir) throws TskCoreException, IOException {
        List<BlackboardArtifactPoint> points = GeolocationManager.getPoints(skCase, false);

        for (BlackboardArtifactPoint point : points) {
            Element reportPoint = makePoint(point);
            if (reportPoint == null) {
                continue;
            }
            if (point.getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
                addExifMetadataContent((EXIFMetadataPoint) point, baseReportDir);

            } else if (point.getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID()) {
                gpsBookmarksFolder.addContent(makePlacemark(point.getLabel(), FeatureColor.BLUE, point.getDetails(), point.getTimestamp(), reportPoint, point.getFormattedCoordinates()));
            } else if (point.getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()) {
                gpsLastKnownLocationFolder.addContent(makePlacemark(point.getLabel(), FeatureColor.PURPLE, point.getDetails(), point.getTimestamp(), reportPoint, point.getFormattedCoordinates()));
            } else if (point.getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()) {
                gpsSearchesFolder.addContent(makePlacemark(point.getLabel(), FeatureColor.WHITE, point.getDetails(), point.getTimestamp(), reportPoint, point.getFormattedCoordinates()));
            } else if (point.getArtifact().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()) {
                gpsTrackpointsFolder.addContent(makePlacemark(point.getLabel(), FeatureColor.WHITE, point.getDetails(), point.getTimestamp(), reportPoint, point.getFormattedCoordinates()));
            }
        }
    }
    
    void makeRoutes(SleuthkitCase skCase) throws TskCoreException {
        List<Route> routes = GeolocationManager.getGPSRoutes(skCase);

        if (routes != null) {
            for (Route route : routes) {
                List<BlackboardArtifactPoint> routePoints = route.getRoute();
                BlackboardArtifactPoint start = null;
                BlackboardArtifactPoint end = null;
                // This is hardcoded knowledge that there is only two points
                // a start and end.  In the long run it would be nice to 
                // support the idea of a route with multiple points.  The Route
                // class supports that idea.  Would be nice to figure out how to support
                // for report.
                if (routePoints != null && routePoints.size() > 1) {
                    start = routePoints.get(0);
                    end = routePoints.get(1);
                }

                if (start == null || end == null) {
                    continue;
                }

                Element reportRoute = makeLineString(start.getLatitude(), start.getLongitude(), start.getAltitude(), end.getLatitude(), end.getLongitude(), end.getAltitude());
                Element startingPoint = makePoint(start.getLatitude(), start.getLongitude(), start.getAltitude());
                Element endingPoint = makePoint(end.getLatitude(), end.getLongitude(), end.getAltitude());

                String formattedCoordinates = String.format("%s to %s", start.getFormattedCoordinates(), end.getFormattedCoordinates());

                gpsRouteFolder.addContent(makePlacemark("As-the-crow-flies Route", FeatureColor.GREEN, route.getDetails(), route.getTimestamp(), reportRoute, formattedCoordinates)); //NON-NLS
                gpsRouteFolder.addContent(makePlacemark(start.getLabel(), FeatureColor.GREEN, start.getDetails(), start.getTimestamp(), startingPoint, start.getFormattedCoordinates())); //NON-NLS
                gpsRouteFolder.addContent(makePlacemark(end.getLabel(), FeatureColor.GREEN, end.getDetails(), end.getTimestamp(), endingPoint, end.getFormattedCoordinates())); //NON-NLS
            }
        }
    }


    private String getTimeStamp(long timeStamp) {
        return kmlDateFormat.format(new java.util.Date(timeStamp * 1000));
    }
    
    private Element makePoint(BlackboardArtifactPoint location) {
        return makePoint(location.getLatitude(), location.getLongitude(), location.getAltitude());
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
        if (latitude == null || longitude == null) {
          return null;
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
