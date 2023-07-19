/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.datamodel;

import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An HTML representation of an artifact. The representation is plain vanilla
 * HTML, so any styling needs to be supplied by the display mechansim. For
 * example, GUI components such as content viewers might use HTMLEditorKit to
 * add styling.
 * 
 * @deprecated - No longer used by DataContentViewerArtifact because the table is no longer HTML
 */
@Deprecated
public class ArtifactStringContent implements StringContent {
    
    private final static Logger logger = Logger.getLogger(ArtifactStringContent.class.getName());
    private final BlackboardArtifact artifact;
    private String stringContent = "";

    /**
     * Constructs an HTML representation of an artifact. The representation is
     * plain vanilla HTML, so any styling needs to be supplied by the display
     * mechansim. For example, GUI components such as content viewers might use
     * HTMLEditorKit to add styling.
     *
     * @param artifact The artifact to be represented as HTML.
     */
    public ArtifactStringContent(BlackboardArtifact artifact) {
        this.artifact = artifact;
    }

    /**
     * Gets the HTML representation of the artifact.
     *
     * @return The HTML representation of the artifact as a string.
     */
    @Messages({
        "ArtifactStringContent.attrsTableHeader.type=Type",
        "ArtifactStringContent.attrsTableHeader.value=Value",
        "ArtifactStringContent.attrsTableHeader.sources=Source(s)",
        "ArtifactStringContent.failedToGetSourcePath.message=Failed to get source file path from case database",
        "ArtifactStringContent.failedToGetAttributes.message=Failed to get some or all attributes from case database"
    })
    @Override
    public String getString() {
        if (stringContent.isEmpty()) {
            /*
             * Start the document.
             */
            StringBuilder buffer = new StringBuilder(1024);
            buffer.append("<html>\n"); //NON-NLS
            buffer.append("<body>\n"); //NON-NLS

            /*
             * Use the artifact display name as a header.
             */
            buffer.append("<h3>"); //NON-NLS
            buffer.append(artifact.getDisplayName());
            buffer.append("</h3>\n"); //NON-NLS

            /*
             * Put the attributes, source content path and artifact id in a
             * table.
             */
            buffer.append("<table border='1'>"); //NON-NLS
            
            // header row
            buffer.append("<tr>"); //NON-NLS
            buffer.append("<th><b>"); //NON-NLS
            buffer.append(Bundle.ArtifactStringContent_attrsTableHeader_type());
            buffer.append("</b></th>"); //NON-NLS
            buffer.append("<th><b>"); //NON-NLS
            buffer.append(Bundle.ArtifactStringContent_attrsTableHeader_value());
            buffer.append("</b></th>"); //NON-NLS
            buffer.append("<th><b>"); //NON-NLS
            buffer.append(Bundle.ArtifactStringContent_attrsTableHeader_sources());
            buffer.append("</b></th>"); //NON-NLS
            buffer.append("</tr>\n"); //NON-NLS
            try {
                Content content = artifact.getSleuthkitCase().getContentById(artifact.getObjectID());

                /*
                 * Add rows for each attribute.
                 */
                for (BlackboardAttribute attr : artifact.getAttributes()) {

                    /*
                     * Attribute value column.
                     */
                    String value = "";
                    switch (attr.getAttributeType().getValueType()) {
                        case STRING:
                        case INTEGER:
                        case LONG:
                        case DOUBLE:
                        case BYTE:
                        case JSON:
                        default:
                            value = attr.getDisplayString();
                            break;
                            
                        // Use Autopsy date formatting settings, not TSK defaults
                        case DATETIME:
                            long epoch = attr.getValueLong();
                            value = TimeZoneUtils.getFormattedTime(epoch * 1000);
                            break;
                    }

                    /*
                     * Attribute sources column.
                     */
                    String sources = StringUtils.join(attr.getSources(), ", ");
                    buffer.append(makeTableRow(attr.getAttributeType().getDisplayName(), value, sources));
                }

                /*
                 * Add a row for the source content path.
                 */

                String path = "";
                try {
                    if (null != content) {
                        path = content.getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting source content path for artifact (artifact_id=%d, obj_id=%d)", artifact.getArtifactID(), artifact.getObjectID()), ex);
                    path = Bundle.ArtifactStringContent_failedToGetSourcePath_message();
                }
                
                buffer.append(makeTableRow(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.srcFilePath.text"),
                        path, ""));


                /*
                 * Add a row for the artifact id.
                 */               
                buffer.append(makeTableRow(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.artifactId.text"),
                        Long.toString(artifact.getArtifactID()), ""));

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error getting data for artifact (artifact_id=%d)", artifact.getArtifactID()), ex);
                buffer.append(makeTableRow(Bundle.ArtifactStringContent_failedToGetAttributes_message(), "", ""));                
            } finally {
                /*
                 * Finish the document
                 */
                buffer.append("</table>"); //NON-NLS
                buffer.append("</html>\n"); //NON-NLS
                stringContent = buffer.toString();
            }
        }

        return stringContent;
    }
    
    // escape special HTML characters
    private String escapeHtmlString(String str) {
        str = str.replaceAll(" ", "&nbsp;"); //NON-NLS
        str = str.replaceAll("<", "&lt;"); //NON-NLS
        str = str.replaceAll(">", "&gt;"); //NON-NLS
        str = str.replaceAll("(\r\n|\n)", "<br />"); //NON-NLS
        return str;
    }
    
    /**
     * Make a row in the result table
     * @param type String for column1 (Type of attribute))
     * @param value String for column2 (value of attribute)
     * @param source Column 3 (attribute source)
     * @return  HTML formatted string of these values
     */
    private String makeTableRow(String type, String value, String source) {
        String row = "<tr><td>" + escapeHtmlString(type) + "</td><td>" + escapeHtmlString(value) + "</td><td>" + escapeHtmlString(source) + "</td></tr>";
        return row;
    }

}
