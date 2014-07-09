/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * Generates a KML file based on geo coordinates store in blackboard.
 */
class ReportKML implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportKML.class.getName());
    private static ReportKML instance = null;
    private Case currentCase;
    private SleuthkitCase skCase;
    private String reportPath;

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
     * @param path path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    public void generateReport(String path, ReportProgressPanel progressPanel) {

        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.querying"));
        reportPath = path + "ReportKML.kml"; //NON-NLS
        String reportPath2 = path + "ReportKML.txt"; //NON-NLS
        currentCase = Case.getCurrentCase();
        skCase = currentCase.getSleuthkitCase();

        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.loading"));
        // Check if ingest has finished
        String ingestwarning = "";
        if (IngestManager.getInstance().isIngestRunning()) {
            ingestwarning = NbBundle.getMessage(this.getClass(), "ReportBodyFile.ingestWarning.text");
        }
        progressPanel.setMaximumProgress(5);
        progressPanel.increment();


        // @@@ BC: I don't get why we do this in two passes.  
        // Why not just print the coordinates as we find them and make some utility methods to do the printing?
        // Should pull out time values for all of these points and store in TimeSpan element
        try {

            BufferedWriter out = null;
            try {
                out = new BufferedWriter(new FileWriter(reportPath2));

                double lat = 0; // temp latitude
                double lon = 0; //temp longitude
                AbstractFile aFile;
                String geoPath = ""; // will hold values of images to put in kml
                String imageName = "";


                File f;
                for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF)) {
                    lat = 0;
                    lon = 0;
                    geoPath = "";
                    String extractedToPath;
                    for (BlackboardAttribute attribute : artifact.getAttributes()) {
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()) //latitude
                        {

                            lat = attribute.getValueDouble();
                        }
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()) //longitude
                        {
                            lon = attribute.getValueDouble();
                        }
                    }
                    if (lon != 0 && lat != 0) {
                        aFile = artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());

                        extractedToPath = reportPath + aFile.getName();
                        geoPath = extractedToPath;
                        f = new File(extractedToPath);
                        f.createNewFile();
                        copyFileUsingStream(aFile, f);
                        imageName = aFile.getName();
                        out.write(String.valueOf(lat));
                        out.write(";");
                        out.write(String.valueOf(lon));
                        out.write(";");
                        out.write(String.valueOf(geoPath));
                        out.write(";");
                        out.write(String.valueOf(imageName));
                        out.write("\n");
                        // lat lon path name
                    }
                }
                
                for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT)) {
                    lat = 0;
                    lon = 0;
                    for (BlackboardAttribute attribute : artifact.getAttributes()) {
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()) //latitude
                        {
                            lat = attribute.getValueDouble();
                        }
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()) //longitude
                        {
                            lon = attribute.getValueDouble();
                        }
                    }
                    if (lon != 0 && lat != 0) {
                        out.write(lat + ";" + lon + "\n");
                    }
                }
                
                for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)) {
                    lat = 0;
                    lon = 0;
                    double destlat = 0;
                    double destlon = 0;
                    String name = "";
                    String location = "";
                    for (BlackboardAttribute attribute : artifact.getAttributes()) {
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID()) //latitude
                        {
                            lat = attribute.getValueDouble();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END.getTypeID()) //longitude
                        {
                            destlat = attribute.getValueDouble();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START.getTypeID()) //longitude
                        {
                            lon = attribute.getValueDouble();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END.getTypeID()) //longitude 
                        {
                            destlon = attribute.getValueDouble();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID()) //longitude 
                        {
                            name = attribute.getValueString();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()) //longitude 
                        {
                            location = attribute.getValueString();
                        }
                    }
                    
                    // @@@ Shoudl do something more fancy with these in KML and store them as a single point. 
                    String display = name;
                    if (display.isEmpty()) 
                        display = location;
                    
                    if (lon != 0 && lat != 0) {
                        out.write(lat + ";" + lon + ";;" + display + " (Start)\n");
                    }
                    if (destlat != 0 && destlon != 0) {
                        out.write(destlat + ";" + destlon + ";;" + display + " (End)\n");
                    }
                }
                
                out.flush();
                out.close();
                
                progressPanel.increment();
                /*
                 * Step 1: generate XML stub
                 */
                Namespace ns = Namespace.getNamespace("", "http://earth.google.com/kml/2.2"); //NON-NLS
                // kml
                Element kml = new Element("kml", ns); //NON-NLS
                Document kmlDocument = new Document(kml);

                // Document
                Element document = new Element("Document", ns); //NON-NLS
                kml.addContent(document);

                // name
                Element name = new Element("name", ns); //NON-NLS
                name.setText("Java Generated KML Document"); //NON-NLS
                document.addContent(name);

                /*
                 * Step 2: add in Style elements
                 */

                // Style
                Element style = new Element("Style", ns); //NON-NLS
                style.setAttribute("id", "redIcon"); //NON-NLS
                document.addContent(style);

                // IconStyle
                Element iconStyle = new Element("IconStyle", ns); //NON-NLS
                style.addContent(iconStyle);

                // color
                Element color = new Element("color", ns); //NON-NLS
                color.setText("990000ff"); //NON-NLS
                iconStyle.addContent(color);

                // Icon
                Element icon = new Element("Icon", ns); //NON-NLS
                iconStyle.addContent(icon);

                // href
                Element href = new Element("href", ns); //NON-NLS
                href.setText("http://www.cs.mun.ca/~hoeber/teaching/cs4767/notes/02.1-kml/circle.png"); //NON-NLS
                icon.addContent(href);
                progressPanel.increment();
                /*
                 * Step 3: read data from source location and
                 * add in a Placemark for each data element
                 */

                File file = new File(reportPath2);
                BufferedReader reader;

                reader = new BufferedReader(new FileReader(file));

                String line = reader.readLine();
                while (line != null) {
                    String[] lineParts = line.split(";");
                    if (lineParts.length > 1) {
                        String coordinates = lineParts[1].trim() + "," + lineParts[0].trim(); //lat,lon
                        // Placemark
                        Element placemark = new Element("Placemark", ns); //NON-NLS
                        document.addContent(placemark);

                        if (lineParts.length == 4) {
                            // name
                            Element pmName = new Element("name", ns); //NON-NLS
                            pmName.setText(lineParts[3].trim());
                            placemark.addContent(pmName);

                            String savedPath = lineParts[2].trim();
                            if (savedPath.isEmpty() == false) {
                                // Path
                                Element pmPath = new Element("Path", ns); //NON-NLS
                                pmPath.setText(savedPath);
                                placemark.addContent(pmPath);

                                // description
                                Element pmDescription = new Element("description", ns); //NON-NLS
                                String xml = "<![CDATA[  \n" + " <img src='file:///" + savedPath + "' width='400' /><br/&gt;  \n"; //NON-NLS
                                StringEscapeUtils.unescapeXml(xml);
                                pmDescription.setText(xml);
                                placemark.addContent(pmDescription);
                            }
                        }

                        // styleUrl
                        Element pmStyleUrl = new Element("styleUrl", ns); //NON-NLS
                        pmStyleUrl.setText("#redIcon"); //NON-NLS
                        placemark.addContent(pmStyleUrl);

                        // Point
                        Element pmPoint = new Element("Point", ns); //NON-NLS
                        placemark.addContent(pmPoint);

                        // coordinates
                        Element pmCoordinates = new Element("coordinates", ns); //NON-NLS

                        pmCoordinates.setText(coordinates);
                        pmPoint.addContent(pmCoordinates);

                    }
                    
                    // read the next line
                    line = reader.readLine();
                }
                progressPanel.increment();
                /*
                 * Step 4: write the XML file
                 */
                try {
                    XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
                    FileOutputStream writer = new FileOutputStream(reportPath);
                    outputter.output(kmlDocument, writer);
                    writer.close();
                    Case.getCurrentCase().addReport(reportPath, NbBundle.getMessage(this.getClass(),
                                                                                    "ReportKML.genReport.srcModuleName.text"), "");
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not write the KML file.", ex); //NON-NLS
                } catch (TskCoreException ex) {
                    String errorMessage = String.format("Error adding %s to case as a report", reportPath); //NON-NLS
                    logger.log(Level.SEVERE, errorMessage, ex);
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write the KML report.", ex); //NON-NLS
            }
            progressPanel.complete();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get the unique path.", ex); //NON-NLS
        }
        progressPanel.increment();
        progressPanel.complete();
    }

    public static void copyFileUsingStream(AbstractFile file, File jFile) throws IOException {
        InputStream is = new ReadContentInputStream(file);
        OutputStream os = new FileOutputStream(jFile);
        byte[] buffer = new byte[8192];
        int length;
        try {
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }

        } finally {
            is.close();
            os.close();
        }
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ReportKML.getName.text");
        return name;
    }

    @Override
    public String getFilePath() {
        return NbBundle.getMessage(this.getClass(), "ReportKML.getFilePath.text");
    }

    @Override
    public String getExtension() {
        String ext = ".txt"; //NON-NLS
        return ext;
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
}
