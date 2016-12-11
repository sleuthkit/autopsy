/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.TimeZone;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * StringContent object for a blackboard artifact, that can be looked up and
 * used to display text for the DataContent viewers. Displays values in artifact
 * in HTML. Note that it has no style associated with it and assumes that the
 * pane showing the HTML has styles set (such as with HTMLEditorKit).
 */
public class ArtifactStringContent implements StringContent {

    BlackboardArtifact artifact;
    private String stringContent = "";
    static final Logger logger = Logger.getLogger(ArtifactStringContent.class.getName());
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ArtifactStringContent(BlackboardArtifact art) {
        artifact = art;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getString() {
        if (stringContent.isEmpty()) {
            try {
                StringBuilder buffer = new StringBuilder();
                buffer.append("<html>\n"); //NON-NLS
                buffer.append("<body>\n"); //NON-NLS

                // artifact name header
                buffer.append("<h4>"); //NON-NLS
                buffer.append(artifact.getDisplayName());
                buffer.append("</h4>\n"); //NON-NLS

                // start table for attributes
                buffer.append("<table border='0'>"); //NON-NLS
                buffer.append("<tr>"); //NON-NLS
                buffer.append("</tr>\n"); //NON-NLS

                // cycle through each attribute and display in a row in the table.
                for (BlackboardAttribute attr : artifact.getAttributes()) {

                    // name column
                    buffer.append("<tr><td>"); //NON-NLS
                    buffer.append(attr.getAttributeType().getDisplayName());
                    buffer.append("</td>"); //NON-NLS

                    // value column
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
                            if (epoch != 0) {
                                dateFormatter.setTimeZone(getTimeZone(artifact));
                                time = dateFormatter.format(new java.util.Date(epoch * 1000));
                            }
                            buffer.append(time);
                            break;
                    }
                    if (!"".equals(attr.getContext())) {
                        buffer.append(" (");
                        buffer.append(attr.getContext());
                        buffer.append(")");
                    }
                    buffer.append("</td>"); //NON-NLS
                    buffer.append("</tr>\n"); //NON-NLS
                }

                final Content content = getAssociatedContent(artifact);

                String path = "";
                try {
                    path = content.getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Exception while calling Content.getUniquePath() on {0} : {1}", new Object[]{content, ex.getLocalizedMessage()}); //NON-NLS
                }

                //add file path
                buffer.append("<tr>"); //NON-NLS
                buffer.append("<td>"); //NON-NLS
                buffer.append(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.srcFilePath.text"));
                buffer.append("</td>"); //NON-NLS
                buffer.append("<td>"); //NON-NLS
                buffer.append(path);
                buffer.append("</td>"); //NON-NLS
                buffer.append("</tr>\n"); //NON-NLS

                // add artifact ID (useful for debugging)
                buffer.append("<tr><td>"); //NON-NLS
                buffer.append(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.artifactId.text"));
                buffer.append("</td><td>"); //NON-NLS
                buffer.append(artifact.getArtifactID());
                buffer.append("</td>"); //NON-NLS
                buffer.append("</tr>\n"); //NON-NLS

                buffer.append("</table>"); //NON-NLS
                buffer.append("</html>\n"); //NON-NLS

                stringContent = buffer.toString();
            } catch (TskException ex) {
                stringContent = NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.err");
            }
        }

        return stringContent;
    }

    private static Content getAssociatedContent(BlackboardArtifact artifact) {
        try {
            return artifact.getSleuthkitCase().getContentById(artifact.getObjectID());
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Getting file failed", ex); //NON-NLS
        }
        throw new IllegalArgumentException(NbBundle.getMessage(ArtifactStringContent.class, "ArtifactStringContent.exception.msg"));
    }

    private static TimeZone getTimeZone(BlackboardArtifact artifact) {
        return ContentUtils.getTimeZone(getAssociatedContent(artifact));

    }
}
