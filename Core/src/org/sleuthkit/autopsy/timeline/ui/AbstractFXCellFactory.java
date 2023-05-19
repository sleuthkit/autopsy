/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.timeline.ui;

import java.util.function.Supplier;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

/**
 * An abstract base class for Cell factories. This class provides the basic
 * infrastructure for implementations to be able to create similar cells for
 * ListView, TableViews or TreeTableViews via the appropriate method call.
 * Implementations need only implement the abstract configureCell method in the
 * same spirit as IndexedCell.updateItem
 */
public abstract class AbstractFXCellFactory<X, Y> {

    public TreeTableCell< X, Y> forTreeTable(TreeTableColumn< X, Y> column) {
        return new AbstractTreeTableCell();
    }

    public TableCell<X, Y> forTable(TableColumn<X, Y> column) {
        return new AbstractTableCell();
    }

    public ListCell< Y> forList() {
        return new AbstractListCell();
    }

    protected abstract void configureCell(IndexedCell<? extends Y> cell, Y item, boolean empty, Supplier<X> supplier);

    private class AbstractTableCell extends TableCell<X, Y> {

        @Override
        @SuppressWarnings({"unchecked"}) //we know it will be X but there is a flaw in getTableRow return type
        protected void updateItem(Y item, boolean empty) {
            super.updateItem(item, empty);
            configureCell(this, item, empty, (() -> (X) this.getTableRow().getItem()));
        }
    }

    private class AbstractTreeTableCell extends TreeTableCell<X, Y> {

        @Override
        protected void updateItem(Y item, boolean empty) {
            super.updateItem(item, empty);
            // Due to a JavaFX issue in Java 10+,
            // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8231644
            // the arrows to expand the tree were removed (FilterTable.css)
            // and the following code was added to indent the subnodes.
            TreeTableView<X> treeTableView = this.treeTableViewProperty().get();
            this.setTranslateX(treeTableView.getTreeItemLevel(treeTableView.getTreeItem(getIndex())) << 4);
            configureCell(this, item, empty, (() -> this.getTreeTableRow().getItem()));
        }
    }

    private class AbstractListCell extends ListCell< Y> {

        @Override
        @SuppressWarnings("unchecked") //for a list X should always equal Y
        protected void updateItem(Y item, boolean empty) {
            super.updateItem(item, empty);
            configureCell(this, item, empty, () -> (X) this.getItem());
        }
    }
}
