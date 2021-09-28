/*
 * Central Repository
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.application;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.datamodel.TskData;

/**
 * Class for populating the Other Occurrences tab
 */
public class NodeData {

    private static final String CSV_ITEM_SEPARATOR = "\",\"";
    private final String caseName;
    private String deviceID;
    private String dataSourceName;
    private final String filePath;
    private final Type type;
    private final String value;
    private TskData.FileKnown known;
    private String comment;

    private CorrelationAttributeInstance originalCorrelationInstance = null;

    /**
     * Create a node from a central repository instance.
     *
     * @param instance The central repository instance
     * @param type     The type of the instance
     * @param value    The value of the instance
     */
    public NodeData(CorrelationAttributeInstance instance, CorrelationAttributeInstance.Type type, String value) {
        caseName = instance.getCorrelationCase().getDisplayName();
        deviceID = instance.getCorrelationDataSource().getDeviceID();
        dataSourceName = instance.getCorrelationDataSource().getName();
        filePath = instance.getFilePath();
        this.type = type;
        this.value = value;
        known = instance.getKnownStatus();
        comment = instance.getComment();

        originalCorrelationInstance = instance;
    }

   
    /**
     * Check if this node is a "file" type
     *
     * @return true if it is a file type
     */
    boolean isFileType() {
        return type.getId() == CorrelationAttributeInstance.FILES_TYPE_ID;
    }

    /**
     * Update the known status for this node
     *
     * @param newKnownStatus The new known status
     */
    void updateKnown(TskData.FileKnown newKnownStatus) {
        known = newKnownStatus;
    }

    /**
     * Update the comment for this node
     *
     * @param newComment The new comment
     */
    public void updateComment(String newComment) {
        comment = newComment;
    }

    /**
     * Get the case name
     *
     * @return the case name
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * Get the device ID
     *
     * @return the device ID
     */
    public String getDeviceID() {
        return deviceID;
    }

    /**
     * Get the data source name
     *
     * @return the data source name
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Get the file path
     *
     * @return the file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Get the type (as a string)
     *
     * @return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * Get the value (MD5 hash for files)
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Get the known status
     *
     * @return the known status
     */
    public TskData.FileKnown getKnown() {
        return known;
    }

    /**
     * Get the comment
     *
     * @return the comment
     */
    public String getComment() {
        return comment;
    }

    /**
     * Get the backing CorrelationAttributeInstance. Should only be called if
     * isCentralRepoNode() is true
     *
     * @return the original CorrelationAttributeInstance
     *
     * @throws CentralRepoException
     */
    public CorrelationAttributeInstance getCorrelationAttributeInstance() throws CentralRepoException {
        if (originalCorrelationInstance == null) {
            throw new CentralRepoException("CorrelationAttributeInstance is null");
        }
        return originalCorrelationInstance;
    }

    /**
     * Get the string to append between elements when writing the node instance
     * data to a CSV
     *
     * @return the CSV_ITEM_SEPARATOR string
     */
    public static String getCsvItemSeparator() {
        return CSV_ITEM_SEPARATOR;
    }

    /**
     * Create a string representation of the node's data comma separated with a
     * line separator ending
     *
     * @return a comma separated string representation of the node's data
     */
    String toCsvString() {
        StringBuilder line = new StringBuilder("\"");
        line.append(getCaseName()).append(CSV_ITEM_SEPARATOR)
                .append(getDataSourceName()).append(CSV_ITEM_SEPARATOR)
                .append(getType()).append(CSV_ITEM_SEPARATOR)
                .append(getValue()).append(CSV_ITEM_SEPARATOR)
                .append(getKnown().toString()).append(CSV_ITEM_SEPARATOR)
                .append(getFilePath()).append(CSV_ITEM_SEPARATOR)
                .append(getComment()).append('"')
                .append(System.getProperty("line.separator"));
        return line.toString();
    }
}
