/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering;

import java.util.Arrays;
import java.util.function.BiPredicate;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableRow;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionGroup;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.timeline.filters.Filter;

/**
 *
 */
class FilterTreeTableRow extends TreeTableRow<Filter> {

    @Override
    protected void updateItem(Filter item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setContextMenu(null);
        } else {
            setContextMenu(ActionUtils.createContextMenu(Arrays.asList(new SelectActionsGroup(this))));
        }
    }

    @NbBundle.Messages(value = {
        "Timeline.ui.filtering.menuItem.select=select",
        "Timeline.ui.filtering.menuItem.all=all",
        "Timeline.ui.filtering.menuItem.none=none",
        "Timeline.ui.filtering.menuItem.only=only",
        "Timeline.ui.filtering.menuItem.others=others"})
    private static enum SelectionAction {
        ALL(Bundle.Timeline_ui_filtering_menuItem_all(),
                (treeItem, row) -> true),

        NONE(Bundle.Timeline_ui_filtering_menuItem_none(),
                (treeItem, row) -> false),

        ONLY(Bundle.Timeline_ui_filtering_menuItem_only(),
                (treeItem, row) -> treeItem == row.getTreeItem()),

        OTHER(Bundle.Timeline_ui_filtering_menuItem_others(),
                (treeItem, row) -> treeItem != row.getTreeItem()),

        SELECT(Bundle.Timeline_ui_filtering_menuItem_select(),
                (treeItem, row) -> false == row.getItem().isSelected());

        private final BiPredicate<TreeItem<Filter>, TreeTableRow<Filter>> selectionPredicate;

        private final String displayName;

        private SelectionAction(String displayName, BiPredicate<TreeItem<Filter>, TreeTableRow<Filter>> predicate) {
            this.selectionPredicate = predicate;
            this.displayName = displayName;
        }

        public void doSelection(TreeItem<Filter> treeItem, TreeTableRow<Filter> row) {
            treeItem.getValue().setSelected(selectionPredicate.test(treeItem, row));
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final class SelectActionsGroup extends ActionGroup {

        SelectActionsGroup(TreeTableRow<Filter> row) {
            super(Bundle.Timeline_ui_filtering_menuItem_select(),
                    new Select(SelectionAction.ALL, row),
                    new Select(SelectionAction.NONE, row),
                    new Select(SelectionAction.ONLY, row),
                    new Select(SelectionAction.OTHER, row));
            setEventHandler(new Select(SelectionAction.SELECT, row)::handle);
        }
    }

    private static final class Select extends Action {

        public TreeTableRow<Filter> getRow() {
            return row;
        }
        private final TreeTableRow<Filter> row;
        private final SelectionAction selectionAction;

        Select(SelectionAction strategy, TreeTableRow<Filter> row) {
            super(strategy.getDisplayName());
            this.row = row;
            this.selectionAction = strategy;
            setEventHandler(actionEvent -> row.getTreeItem().getParent().getChildren().forEach(this::doSelection));
        }

        private void doSelection(TreeItem<Filter> treeItem) {
            selectionAction.doSelection(treeItem, getRow());
        }
    }
}
