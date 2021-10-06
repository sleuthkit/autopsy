/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactTableDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactTableSearchResultsDTO;

/**
 *
 * @author gregd
 */
public class DataArtifactFactoryv2 extends SearchResultChildFactory<DataArtifactTableSearchResultsDTO, DataArtifactTableDTO> {

    public DataArtifactFactoryv2() {
    }

    public DataArtifactFactoryv2(DataArtifactTableSearchResultsDTO initialResults) {
        super(initialResults);
    }

    @Override
    protected Node createNodeForKey(DataArtifactTableSearchResultsDTO searchResults, DataArtifactTableDTO itemData) {
        return new DataArtifactNodev2(searchResults, itemData);
    }
}
