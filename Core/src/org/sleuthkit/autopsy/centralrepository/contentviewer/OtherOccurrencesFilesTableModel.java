/*
 * Central Repository
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import org.sleuthkit.autopsy.centralrepository.application.NodeData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.apache.commons.io.FilenameUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.application.OtherOccurrences;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Model for cells in the files section of the other occurrences data content
 * viewer
 */
public class OtherOccurrencesFilesTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OtherOccurrencesFilesTableModel.class.getName());
    private final List<String> nodeKeys = new ArrayList<>();
    private final Map<String, List<NodeData>> nodeMap = new HashMap<>();

    /**
     * Create a table model for displaying file names
     */
    OtherOccurrencesFilesTableModel() {
        // This constructor is intentionally empty.
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public int getRowCount() {
        return nodeKeys.size();
    }

    @Messages({"OtherOccurrencesFilesTableModel.fileName=File Name",
        "OtherOccurrencesFilesTableModel.noData=No Data."})
    @Override
    public String getColumnName(int colIdx) {
        return Bundle.OtherOccurrencesFilesTableModel_fileName();
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        //if anything would prevent this from working we will consider it no data for the sake of simplicity
        if (nodeMap.isEmpty() || nodeKeys.isEmpty() || rowIdx < 0
                || rowIdx >= nodeKeys.size() || nodeKeys.get(rowIdx) == null
                || nodeMap.get(nodeKeys.get(rowIdx)) == null
                || nodeMap.get(nodeKeys.get(rowIdx)).isEmpty()) {
            return Bundle.OtherOccurrencesFilesTableModel_noData();
        }
        return FilenameUtils.getName( nodeMap.get(nodeKeys.get(rowIdx)).get(0).getFilePath());
    }

    /**
     * Get a list of OtherOccurrenceNodeData that exist for the file which
     * corresponds to the Index
     *
     * @param rowIdx the index of the file to get data for
     *
     * @return a list of OtherOccurrenceNodeData for the specified index or an
     *         empty list if no data was found
     */
    List<NodeData> getListOfNodesForFile(int rowIdx) {
        //if anything would prevent this from working return an empty list
        if (nodeMap.isEmpty() || nodeKeys.isEmpty() || rowIdx < 0
                || rowIdx >= nodeKeys.size() || nodeKeys.get(rowIdx) == null
                || nodeMap.get(nodeKeys.get(rowIdx)) == null) {
            return new ArrayList<>();
        }
        return nodeMap.get(nodeKeys.get(rowIdx));
    }

    @Override
    public Class<String> getColumnClass(int colIdx) {
        return String.class;
    }

    /**
     * Add one correlated instance object to the table
     *
     * @param newNodeData data to add to the table
     */
    void addNodeData(NodeData newNodeData) {
        String newNodeKey = createNodeKey(newNodeData);
        List<NodeData> nodeList = nodeMap.get(newNodeKey);
        if (nodeList == null) {
            nodeKeys.add(newNodeKey);
            nodeList = new ArrayList<>();
        }
        nodeList.add(newNodeData);
        nodeMap.put(newNodeKey, nodeList);
        fireTableDataChanged();
    }

    private String createNodeKey(NodeData nodeData) {
        String caseUUID;
        try {
            caseUUID = nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID();
        } catch (CentralRepoException ignored) {
            //non central repo nodeData won't have a correlation case
            try {
                caseUUID = Case.getCurrentCaseThrows().getName();
                //place holder value will be used since correlation attribute was unavailble
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get current case", ex);
                caseUUID = OtherOccurrences.getPlaceholderUUID();
            }
        }
        return nodeData.getCaseName() + nodeData.getDataSourceName() + nodeData.getDeviceID() + nodeData.getFilePath() + caseUUID;
    }

    /**
     * Clear the node data table.
     */
    void clearTable() {
        nodeKeys.clear();
        nodeMap.clear();
        fireTableDataChanged();
    }

}
