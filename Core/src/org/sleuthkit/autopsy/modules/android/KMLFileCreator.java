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
package org.sleuthkit.autopsy.modules.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class KMLFileCreator {

    private Case currentCase;
    private SleuthkitCase skCase;
    private String reportPath;

    public void createKml() {

        reportPath = Case.getCurrentCase().getTempDirectory() + "ReportKML.kml"; //NON-NLS
        String reportPath2 = Case.getCurrentCase().getTempDirectory() + "ReportKML.txt"; //NON-NLS
        currentCase = Case.getCurrentCase();
        skCase = currentCase.getSleuthkitCase();

        try {

            BufferedWriter out = null;
            try {
                out = new BufferedWriter(new FileWriter(reportPath2));

                String lat = ""; // temp latitude
                String lon = ""; //temp longitude
                String destlon = "";
                String destlat = "";
                for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT)) {
                    lat = "";
                    lon = "";
                    for (BlackboardAttribute attribute : artifact.getAttributes()) {
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()) //latitude
                        {
                            lat = attribute.getValueString();
                        }
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()) //longitude
                        {
                            lon = attribute.getValueString();
                        }
                    }
                    if (!lon.isEmpty() && !lat.isEmpty()) {
                        out.write(lat + ";" + lon + "\n");

                    }
                }
                for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE)) {
                    lat = "";
                    lon = "";
                    for (BlackboardAttribute attribute : artifact.getAttributes()) {
                        if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START.getTypeID()) //latitude
                        {
                            lat = attribute.getValueString();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END.getTypeID()) //longitude
                        {
                            destlat = attribute.getValueString();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START.getTypeID()) //longitude
                        {
                            lon = attribute.getValueString();
                        } else if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END.getTypeID()) //longitude
                        {
                            destlon = attribute.getValueString();
                        }

                    }
                    if (!lon.isEmpty() && !lat.isEmpty()) {
                        out.write(lat + ";" + lon + "\n");
                    }
                    if (!destlon.isEmpty() && !destlat.isEmpty()) {
                        out.write(destlat + ";" + destlon + "\n");

                    }
                }
                out.flush();
                out.close();
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
                    if (lineParts.length == 2) {
                        String coordinates = lineParts[1].trim() + "," + lineParts[0].trim(); //lat,lon
                        // Placemark
                        Element placemark = new Element("Placemark", ns); //NON-NLS
                        document.addContent(placemark);

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
                /*
                 * Step 4: write the XML file
                 */
                try {
                    XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
                    FileOutputStream writer = new FileOutputStream(reportPath);
                    outputter.output(kmlDocument, writer);
                    writer.close();
                } catch (IOException ex) {
                }
            } catch (IOException ex) {
            }
        } catch (TskCoreException ex) {
        }

    }
}
