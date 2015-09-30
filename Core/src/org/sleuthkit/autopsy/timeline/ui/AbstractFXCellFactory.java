/**
 * *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret *
 * of, Basis Technology Corp. It is given in confidence by Basis Technology *
 * and may only be used as permitted under the license agreement under which *
 * it has been distributed, and in no other way. * * Copyright (c) 2014 Basis
 * Technology Corp. All rights reserved. * * The technical data and information
 * provided herein are provided with * `limited rights', and the computer
 * software provided herein is provided * with `restricted rights' as those
 * terms are defined in DAR and ASPR * 7-104.9(a).
 * *************************************************************************
 */
package org.sleuthkit.autopsy.timeline.ui;

import java.util.function.Supplier;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;

/**
 * an abstract base class for Cell factories. This class provides the basic
 * infrustructure for implementations to be able to create similar cells for
 * listview, tableviews or treetableviews via the appropriate method call.
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
