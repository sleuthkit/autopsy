/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering;

import java.util.Arrays;
import java.util.function.BiPredicate;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableRow;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionGroup;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;

/**
 *
 */
class FilterTreeTableRow extends TreeTableRow<FilterState<?>> {

    @Override
    protected void updateItem(FilterState<?> item, boolean empty) {
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
                (treeItem, row) -> treeItem == row.getItem()),
        OTHER(Bundle.Timeline_ui_filtering_menuItem_others(),
                (treeItem, row) -> treeItem != row.getItem()),
        SELECT(Bundle.Timeline_ui_filtering_menuItem_select(),
                (treeItem, row) -> false == row.isSelected());

        private final BiPredicate<FilterState<?>, TreeTableRow<FilterState<?>>> selectionPredicate;

        private final String displayName;

        private SelectionAction(String displayName, BiPredicate<FilterState<?>, TreeTableRow<FilterState<?>>> predicate) {
            this.selectionPredicate = predicate;
            this.displayName = displayName;
        }

        public void doSelection(FilterState<?> treeItem, TreeTableRow<FilterState<?>> row) {
            treeItem.setSelected(selectionPredicate.test(treeItem, row));
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final class SelectActionsGroup extends ActionGroup {

        SelectActionsGroup(TreeTableRow<FilterState<?>> row) {
            super(Bundle.Timeline_ui_filtering_menuItem_select(),
                    new Select(SelectionAction.ALL, row),
                    new Select(SelectionAction.NONE, row),
                    new Select(SelectionAction.ONLY, row),
                    new Select(SelectionAction.OTHER, row));
            setEventHandler(new Select(SelectionAction.SELECT, row)::handle);
        }
    }

    private static final class Select extends Action {

        public TreeTableRow<FilterState<?>> getRow() {
            return row;
        }
        private final TreeTableRow<FilterState<?>> row;
        private final SelectionAction selectionAction;

        Select(SelectionAction strategy, TreeTableRow<FilterState<?>> row) {
            super(strategy.getDisplayName());
            this.row = row;
            this.selectionAction = strategy;
            setEventHandler(actionEvent -> row.getTreeItem().getParent().getChildren().stream()
                    .map(TreeItem::getValue)
                    .forEach(this::doSelection));
        }

        private void doSelection(FilterState<?> treeItem) {
            selectionAction.doSelection(treeItem, getRow());
        }
    }
}
