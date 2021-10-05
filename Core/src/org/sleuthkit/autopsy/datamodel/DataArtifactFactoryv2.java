/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DataArtifactFactoryv2.ChildData;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactRow;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactTableDTO;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author gregd
 */
public class DataArtifactFactoryv2 extends ChildFactory.Detachable<DataArtifactFactoryv2.ChildData> {

    public static class ChildData {

        private final DataArtifactTableDTO tableData;
        private final DataArtifactRow artifactRow;

        public ChildData(DataArtifactTableDTO tableData, DataArtifactRow artifactRow) {
            this.tableData = tableData;
            this.artifactRow = artifactRow;
        }

        public DataArtifactTableDTO getTableData() {
            return tableData;
        }

        public DataArtifactRow getArtifactRow() {
            return artifactRow;
        }
    }

    private static final Logger logger = Logger.getLogger(DataArtifactFactoryv2.class.getName());

    private final BlackboardArtifact.Type type;
    private final Long filteringDSObjId;
    private final ThreePanelDAO dao = ThreePanelDAO.getInstance();

    DataArtifactFactoryv2(BlackboardArtifact.Type type, Long filteringDSObjId) {
        this.type = type;
        this.filteringDSObjId = filteringDSObjId;
    }

    @Override
    protected boolean createKeys(List<ChildData> toPopulate) {
        try {
            DataArtifactTableDTO table = dao.getDataArtifactsForTable(type, filteringDSObjId);
            List<ChildData> keys = table.getRows().stream()
                    .map((row) -> new ChildData(table, row))
                    .collect(Collectors.toList());
            toPopulate.addAll(keys);
            return true;
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "There was an error fetching data for type: " + type, ex);
            return false;
        }
    }

    @Override
    protected Node createNodeForKey(ChildData key) {
        return new DataArtifactNodev2(key.getTableData(), key.getArtifactRow());
    }
}
