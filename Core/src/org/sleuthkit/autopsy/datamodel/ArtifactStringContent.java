/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.text.SimpleDateFormat;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An HTML representation of an artifact. The representation is plain vanilla
 * HTML, so any styling needs to be supplied by the display mechansim. For
 * example, GUI components such as content viewers might use HTMLEditorKit to
 * add styling.
 */
public class ArtifactStringContent implements StringContent {

    private final static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
        "ArtifactStringContent.attrsTableHeader.attribute=Attribute",
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
            buffer.append("<tr>"); //NON-NLS
            buffer.append("<td>"); //NON-NLS
            buffer.append(Bundle.ArtifactStringContent_attrsTableHeader_attribute());
            buffer.append("</td>"); //NON-NLS
            buffer.append("<td>"); //NON-NLS
            buffer.append(Bundle.ArtifactStringContent_attrsTableHeader_value());
            buffer.append("</td>"); //NON-NLS
            buffer.append("<td>"); //NON-NLS
            buffer.append(Bundle.ArtifactStringContent_attrsTableHeader_sources());
            buffer.append("</td>"); //NON-NLS
            buffer.append("</tr>\n"); //NON-NLS
            try {
                Content content = artifact.getSleuthkitCase().getContentById(artifact.getObjectID());

                /*
                 * Add rows for each attribute.
                 */
                for (BlackboardAttribute attr : artifact.getAttributes()) {

                    /*
                     * Attribute display name column.
                     */
                    buffer.append("<tr><td>"); //NON-NLS
                    buffer.append(attr.getAttributeType().getDisplayName());
                    buffer.append("</td>"); //NON-NLS

                    /*
                     * Attribute value column.
                     */
                    buffer.append("<td>"); //NON-NLS
                    switch (attr.getAttributeType().getValueType()) {
                        case STRING:
                            String str = attr.getValueString();
                            str = str.replaceAll(" ", "&nbsp;"); //NON-NLS
                            str = str.replaceAll("<", "&lt;"); //NON-NLS
                            str = str.replaceAll(">", "&gt;"); //NON-NLS
                            str = str.replaceAll("(\r\n|\n)", "<br />"); //NON-NLS
                            buffer.append(str);
                            break;
                        case INTEGER:
                        case LONG:
                        case DOUBLE:
                        case BYTE:
                            buffer.append(attr.getDisplayString());
                            break;
                        case DATETIME:
                            long epoch = attr.getValueLong();
                            String time = "0000-00-00 00:00:00";
                            if (null != content && 0 != epoch) {
                                dateFormatter.setTimeZone(ContentUtils.getTimeZone(content));
                                time = dateFormatter.format(new java.util.Date(epoch * 1000));
                            }
                            buffer.append(time);
                            break;
                    }
                    buffer.append("</td>"); //NON-NLS

                    /*
                     * Attribute source modules column.
                     */
                    buffer.append("<td>"); //NON-NLS
                    buffer.append(StringUtils.join(attr.getSources(), ", "));
                    buffer.append("</td>"); //NON-NLS

                    buffer.append("</tr>\n"); //NON-NLS
                }

                /*
                 * Add a row for the source content path.
                 */
                buffer.append("<tr>"); //NON-NLS
                buffer.append("<td>"); //NON-NLS
                buffer.append(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.srcFilePath.text"));
                buffer.append("</td>"); //NON-NLS
                buffer.append("<td>"); //NON-NLS
                String path = "";
                try {
                    if (null != content) {
                        path = content.getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting source content path for artifact (artifact_id=%d, obj_id=%d)", artifact.getArtifactID(), artifact.getObjectID()), ex);
                    path = Bundle.ArtifactStringContent_failedToGetSourcePath_message();
                }
                buffer.append(path);
                buffer.append("</td>"); //NON-NLS
                buffer.append("</tr>\n"); //NON-NLS

                /*
                 * Add a row for the artifact id.
                 */
                buffer.append("<tr><td>"); //NON-NLS
                buffer.append(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.artifactId.text"));
                buffer.append("</td><td>"); //NON-NLS
                buffer.append(artifact.getArtifactID());
                buffer.append("</td>"); //NON-NLS
                buffer.append("</tr>\n"); //NON-NLS

                /*
                 * Finish the document
                 */
                buffer.append("</table>"); //NON-NLS
                buffer.append("</html>\n"); //NON-NLS
                stringContent = buffer.toString();

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error getting data for artifact (artifact_id=%d)", artifact.getArtifactID()), ex);
                buffer.append("<tr><td>"); //NON-NLS
                buffer.append(Bundle.ArtifactStringContent_failedToGetAttributes_message());
                buffer.append("</td>"); //NON-NLS
                buffer.append("</tr>\n"); //NON-NLS
                buffer.append("</table>"); //NON-NLS
                buffer.append("</html>\n"); //NON-NLS
                stringContent = buffer.toString();
            }
        }

        return stringContent;
    }

}
