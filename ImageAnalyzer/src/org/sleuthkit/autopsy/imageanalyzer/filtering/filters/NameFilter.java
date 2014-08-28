/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.coreutils.FXMLConstructor;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

/**
 *
 */
public class NameFilter extends AtomicFilter<String, String> {

    public NameFilter() {
        super(DrawableAttribute.NAME, SUBSTRING, "");
    }

    @Override
    public FilterRow getUI() {
        return new NameRow(this);
    }

    private void setValue(String text) {
        filterValue.set(text);
    }

    private static class NameRow extends FilterRow<NameFilter> {

        @FXML
        private ResourceBundle resources;
        @FXML
        private URL location;
        @FXML
        private TextField textField;
        private final NameFilter filter;

        @FXML
        void initialize() {
            assert textField != null : "fx:id=\"textField\" was not injected: check your FXML file 'NameFilterRow.fxml'.";

            textField.textProperty().addListener(new InvalidationListener() {
                @Override
                public void invalidated(Observable observable) {
                    filter.setValue(textField.getText());
                }
            });

//            //hack to make listener hear that this filter has changed
//            filter.active.set(!filter.isActive());
//            filter.active.set(!filter.isActive());
        }

        private NameRow(NameFilter filter) {
            this.filter = filter;
            FXMLConstructor.construct(this, "NameFilterRow.fxml");
        }

        @Override
        public NameFilter getFilter() {
            return filter;
        }
    }
}
