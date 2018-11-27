/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui;

import java.util.Map;
import java.util.Optional;
import javafx.scene.control.ListCell;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB.DrawableDbBuildStatusEnum;
import org.sleuthkit.datamodel.DataSource;

/**
 * Cell used to represent a DataSource in the dataSourceComboBoxes
 */
public class DataSourceCell extends ListCell<Optional<DataSource>> {

    private final Map<DataSource, Boolean> dataSourcesTooManyFiles;
    private final Map<Long, DrawableDB.DrawableDbBuildStatusEnum> dataSourcesDrawableDBStatus;

    /**
     * 
     * @param dataSourcesTooManyFiles: a map of too many files indicator for 
     *      each data source.  
     *      Data sources with too many files may substantially slow down 
     *      the system and hence are disabled for selection.
     * @param dataSourcesDrawableDBStatus a map of drawable DB status for 
     *      each data sources.
     *      Data sources in DEFAULT state are not fully analyzed yet and are 
     *      disabled for selection.
     */
    public DataSourceCell(Map<DataSource, Boolean> dataSourcesTooManyFiles, Map<Long, DrawableDB.DrawableDbBuildStatusEnum> dataSourcesDrawableDBStatus) {
        this.dataSourcesTooManyFiles = dataSourcesTooManyFiles;
        this.dataSourcesDrawableDBStatus = dataSourcesDrawableDBStatus;
        
    }

    /**
     * 
     * @param item  
     * @param empty 
     */
    @Override
    protected void updateItem(Optional<DataSource> item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText("");
        } else {
            DataSource dataSource = item.orElse(null);
            String dataSourceName;
            boolean shouldEnable = true;  // false if user should not be able to select the item
            
            if (dataSource == null) {
                dataSourceName = "All";
                // NOTE: openAction verifies that there is at least one data source with data.  
                // So, at this point, "All" should never need to be disabled because none of the data sources
                // are analyzed.
            }
            else {
                dataSourceName = dataSource.getName() + " (Id: " + dataSource.getId() + ")";
                if (dataSourcesDrawableDBStatus.get(dataSource.getId()) == DrawableDbBuildStatusEnum.UNKNOWN) {
                    dataSourceName += " - Not Analyzed";
                    shouldEnable = false;
                }
            }
            
            // if it's analyzed, then make sure there aren't too many files
            if (shouldEnable) {
                if (dataSourcesTooManyFiles.getOrDefault(dataSource, false)) {
                    dataSourceName += " - Too Many Files";
                    shouldEnable = false;
                } 
            }
            
            // check if item should be disabled
            if (shouldEnable) {
                setGraphic(null);
                setStyle("-fx-opacity : 1");
            }
            else {
                setDisable(true);
                setStyle("-fx-opacity : .5");
            }
           
            setText(dataSourceName);
        }
    }
}
