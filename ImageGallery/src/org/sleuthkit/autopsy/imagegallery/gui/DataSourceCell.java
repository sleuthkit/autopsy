package org.sleuthkit.autopsy.imagegallery.gui;

import java.util.Map;
import java.util.Optional;
import javafx.scene.control.ListCell;
import org.sleuthkit.datamodel.DataSource;

/**
 * Cell used to represent a DataSource in the dataSourceComboBoxes
 */
public class DataSourceCell extends ListCell<Optional<DataSource>> {

    private final Map<DataSource, Boolean> dataSourcesViewable;

    public DataSourceCell(Map<DataSource, Boolean> dataSourcesViewable) {
        this.dataSourcesViewable = dataSourcesViewable;
    }

    @Override
    protected void updateItem(Optional<DataSource> item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText("");
        } else {
            DataSource dataSource = item.orElse(null);
            String text = (dataSource == null) ? "All" : dataSource.getName() + " (Id: " + dataSource.getId() + ")";
            Boolean tooManyFilesInDataSource = dataSourcesViewable.getOrDefault(dataSource, false);
            if (tooManyFilesInDataSource) {
                text += " - Too many files";
                setStyle("-fx-opacity : .5");
            } else {
                setGraphic(null);
                setStyle("-fx-opacity : 1");
            }
            setDisable(tooManyFilesInDataSource);
            setText(text);
        }
    }
}
