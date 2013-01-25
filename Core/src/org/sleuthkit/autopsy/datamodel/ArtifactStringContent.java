/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * StringContent object for a blackboard artifact, that can be looked up and used
 * to display text for the DataContent viewers
 * @author alawrence
 */
public class ArtifactStringContent implements StringContent {

    BlackboardArtifact wrapped;
    static final Logger logger = Logger.getLogger(ArtifactStringContent.class.getName());
    private static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ArtifactStringContent(BlackboardArtifact art) {
        wrapped = art;
    }

    @Override
    public String getString() {
        try {
            StringBuilder buffer = new StringBuilder();
            buffer.append("<html>");
            buffer.append("<head>");
            buffer.append("<style type='text/css'>");
            buffer.append("table {table-layout:fixed;}");
            buffer.append("td {font-family:Arial;font-size:10pt;overflow:hidden;padding-right:5px;padding-left:5px;}");
            buffer.append("th {font-family:Arial;font-size:10pt;overflow:hidden;padding-right:5px;padding-left:5px;font-weight:bold;}");
            buffer.append("p {font-family:Arial;font-size:10pt;}");
            buffer.append("</style>");
            buffer.append("</head>");
            buffer.append("<h4>");
            buffer.append(wrapped.getDisplayName());
            buffer.append("</h4>");
            buffer.append("<table border='0'>");
            buffer.append("<tr>");
            buffer.append("</tr>");
            for (BlackboardAttribute attr : wrapped.getAttributes()) {
                buffer.append("<tr><td>");
                buffer.append(attr.getAttributeTypeDisplayName());
                buffer.append("</td>");
                buffer.append("<td>");
                if (attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()
                        || attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()) {
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
                            buffer.append(attr.getValueString());
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
                buffer.append("</tr>");
            }
            
            final Content content = getAssociatedContent(wrapped);
            
            String path = "";
            try {
                path = content.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + content);
            }
            
            //add file path
            buffer.append("<tr>");
            buffer.append("<td>Source File</td>");
            buffer.append("<td>");
            buffer.append(content.getName());
            buffer.append("</td>");
            buffer.append("</tr>");
            buffer.append("<tr>");
            buffer.append("<td>Source File Path</td>");
            buffer.append("<td>");
            buffer.append(path);
            buffer.append("</td>");
            buffer.append("</tr>");
            
            buffer.append("</table>");
            buffer.append("</html>");
            return buffer.toString();
        } catch (TskException ex) {
            return "Error getting content";
        }
    }
    
    private static Content getAssociatedContent(BlackboardArtifact artifact){
        try {
            return artifact.getSleuthkitCase().getContentById(artifact.getObjectID());
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Getting file failed", ex);
        }
        throw new IllegalArgumentException("Couldn't get file from database");
    }

    private static TimeZone getTimeZone(BlackboardArtifact artifact) {
        try {
            return TimeZone.getTimeZone(getAssociatedContent(artifact).getImage().getTimeZone());
        } catch(TskException ex) {
            return TimeZone.getDefault();
        }
    }
}
