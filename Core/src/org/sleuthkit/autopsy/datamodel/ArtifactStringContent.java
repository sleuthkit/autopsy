/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.TimeZone;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * StringContent object for a blackboard artifact, that can be looked up and used
 * to display text for the DataContent viewers.  Displays values in artifact in HTML.
 * Note that it has no style associated with it and assumes that the pane showing the 
 * HTML has styles set (such as with HTMLEditorKit).
 */
public class ArtifactStringContent implements StringContent {

    BlackboardArtifact wrapped;
    private String stringContent = "";
    static final Logger logger = Logger.getLogger(ArtifactStringContent.class.getName());
    private static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ArtifactStringContent(BlackboardArtifact art) {
        wrapped = art;
    }

    @Override
    public String getString() {
        if (stringContent.isEmpty()) {
            try {
                StringBuilder buffer = new StringBuilder();
                buffer.append("<html>\n");
                buffer.append("<body>\n");

                // artifact name header
                buffer.append("<h4>");
                buffer.append(wrapped.getDisplayName());
                buffer.append("</h4>\n");

                // start table for attributes
                buffer.append("<table border='0'>");
                buffer.append("<tr>");
                buffer.append("</tr>\n");

                // cycle through each attribute and display in a row in the table. 
                for (BlackboardAttribute attr : wrapped.getAttributes()) {

                    // name column
                    buffer.append("<tr><td>");
                    buffer.append(attr.getAttributeTypeDisplayName());
                    buffer.append("</td>");

                    // value column
                    buffer.append("<td>");
                    if (attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID() 
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED.getTypeID()
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID()
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID() 
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()
                            || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID() ) {
                        long epoch = attr.getValueLong();
                        String time = "0000-00-00 00:00:00";
                        if (epoch != 0) {
                            dateFormatter.setTimeZone(getTimeZone(wrapped));
                            time = dateFormatter.format(new java.util.Date(epoch * 1000));
                        }
                        buffer.append(time);
                    } else {
                        switch (attr.getValueType()) {
                            case STRING:
                                String str = attr.getValueString();
                                str = str.replaceAll(" ", "&nbsp;");
                                str = str.replaceAll("<", "&lt;");
                                str = str.replaceAll(">", "&gt;");
                                str = str.replaceAll("(\r\n|\n)", "<br />");
                                buffer.append(str);
                                break;
                            case INTEGER:
                                buffer.append(attr.getValueInt());
                                break;
                            case LONG:
                                buffer.append(attr.getValueLong());
                                break;
                            case DOUBLE:
                                buffer.append(attr.getValueDouble());
                                break;
                            case BYTE:
                                buffer.append(Arrays.toString(attr.getValueBytes()));
                                break;
                        }
                    }
                    if (!"".equals(attr.getContext())) {
                        buffer.append(" (");
                        buffer.append(attr.getContext());
                        buffer.append(")");
                    }
                    buffer.append("</td>");
                    buffer.append("</tr>\n");
                }

                final Content content = getAssociatedContent(wrapped);

                String path = "";
                try {
                    path = content.getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Exception while calling Content.getUniquePath() on {0} : {1}", new Object[]{content, ex.getLocalizedMessage()});
                }

                //add file path

                buffer.append("<tr>");
                buffer.append("<td>");
                buffer.append(NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.srcFilePath.text"));
                buffer.append("</td>");
                buffer.append("<td>");
                buffer.append(path);
                buffer.append("</td>");
                buffer.append("</tr>\n");

                buffer.append("</table>");
                buffer.append("</html>\n");
                
                stringContent = buffer.toString();
            } catch (TskException ex) {
                stringContent = NbBundle.getMessage(this.getClass(), "ArtifactStringContent.getStr.err");
            }
        }
        
        return stringContent;
    }
    
    private static Content getAssociatedContent(BlackboardArtifact artifact){
        try {
            return artifact.getSleuthkitCase().getContentById(artifact.getObjectID());
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Getting file failed", ex);
        }
        throw new IllegalArgumentException(NbBundle.getMessage(ArtifactStringContent.class, "ArtifactStringContent.exception.msg"));
    }

    private static TimeZone getTimeZone(BlackboardArtifact artifact) {
        return ContentUtils.getTimeZone(getAssociatedContent(artifact));

    }
}
