/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;
/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
 * ReportBodyFile generates a report in the body file format specified on
 * The Sleuth Kit wiki as MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime.
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
     * @param path path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    @SuppressWarnings("deprecation")
     public void generateReport(String path, ReportProgressPanel progressPanel) {

         // Start the progress bar and setup the report
         progressPanel.setIndeterminate(false);
         progressPanel.start();
         progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportKML.progress.querying"));
         reportPath = path + "ReportKML.kml";
         String reportPath2 = path + "ReportKML.txt";
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


         try {

             BufferedWriter out = null;
             try {
                 out = new BufferedWriter(new FileWriter(reportPath2));

                 double lat = 0; // temp latitude
                 double lon = 0; //temp longitude
                 AbstractFile aFile;
                 String geoPath = ""; // will hold values of images to put in kml
                 String imageName="";
               

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
                        aFile=artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
                                    
                        extractedToPath = reportPath +aFile.getName();
                        geoPath=extractedToPath;
                        f = new File(extractedToPath);
                        f.createNewFile();
                        copyFileUsingStream(aFile,f);
                         imageName= aFile.getName();
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
                 out.flush();
                 out.close();
                 progressPanel.increment();
                 /*
                  * Step 1: generate XML stub
                  */
                 Namespace ns = Namespace.getNamespace("", "http://earth.google.com/kml/2.2");
                 // kml
                 Element kml = new Element("kml", ns);
                 Document kmlDocument = new Document(kml);

                 // Document
                 Element document = new Element("Document", ns);
                 kml.addContent(document);

                 // name
                 Element name = new Element("name", ns);
                 name.setText("Java Generated KML Document");
                 document.addContent(name);

                 /*
                  * Step 2: add in Style elements
                  */

                 // Style
                 Element style = new Element("Style", ns);
                 style.setAttribute("id", "redIcon");
                 document.addContent(style);

                 // IconStyle
                 Element iconStyle = new Element("IconStyle", ns);
                 style.addContent(iconStyle);

                 // color
                 Element color = new Element("color", ns);
                 color.setText("990000ff");
                 iconStyle.addContent(color);

                 // Icon
                 Element icon = new Element("Icon", ns);
                 iconStyle.addContent(icon);

                 // href
                 Element href = new Element("href", ns);
                 href.setText("http://www.cs.mun.ca/~hoeber/teaching/cs4767/notes/02.1-kml/circle.png");
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
                     if (lineParts.length == 4) {
                         String coordinates = lineParts[1].trim() + "," + lineParts[0].trim(); //lat,lon
                         // Placemark
                         Element placemark = new Element("Placemark", ns);
                         document.addContent(placemark);

                         // name
                         Element pmName = new Element("name", ns);
                         pmName.setText(lineParts[3].trim());
                         placemark.addContent(pmName);

                         // Path
                         Element pmPath = new Element("Path", ns);
                         pmPath.setText(lineParts[2].trim());
                         placemark.addContent(pmPath);
                         
                         // description
                         Element pmDescription = new Element("description", ns);
                         String xml= "<![CDATA[  \n" +" <img src='file:///"+lineParts[2].trim()+"' width='400' /><br/&gt;  \n" ;
                         StringEscapeUtils.unescapeXml(xml);
                         pmDescription.setText(xml);
                         placemark.addContent(pmDescription);

                         // styleUrl
                         Element pmStyleUrl = new Element("styleUrl", ns);
                         pmStyleUrl.setText("#redIcon");
                         placemark.addContent(pmStyleUrl);

                         // Point
                         Element pmPoint = new Element("Point", ns);
                         placemark.addContent(pmPoint);

                         // coordinates
                         Element pmCoordinates = new Element("coordinates", ns);

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
                 } catch (IOException ex) {
                     logger.log(Level.WARNING, "Could not write the KML file.", ex);
                 }


             } catch (IOException ex) {
                 logger.log(Level.WARNING, "Could not write the KML report.", ex);
             }
             progressPanel.complete();
         } catch (TskCoreException ex) {
             logger.log(Level.WARNING, "Failed to get the unique path.", ex);
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
                 while ((length = is.read(buffer)) != -1) 
                 {
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
        String ext = ".txt";
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
