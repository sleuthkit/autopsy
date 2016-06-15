/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

public class ExplorerNodeActionVisitor extends ContentVisitor.Default<List<? extends Action>> {

    private static ExplorerNodeActionVisitor instance = new ExplorerNodeActionVisitor();

    public static List<Action> getActions(Content c) {
        List<Action> actions = new ArrayList<>();

        actions.addAll(c.accept(instance));
        //TODO: fix this
        /*
         * while (c.isOnto()) { try { List<? extends Content> children =
         * c.getChildren(); if (!children.isEmpty()) { c =
         * c.getChildren().get(0); } else { return actions; } } catch
         * (TskException ex) {
         * Log.get(ExplorerNodeActionVisitor.class).log(Level.WARNING, "Error
         * getting show detail actions.", ex); return actions; }
         * actions.addAll(c.accept(instance));
         }
         */
        return actions;
    }

    ExplorerNodeActionVisitor() {
    }

    @Override
    public List<? extends Action> visit(final Image img) {
        List<Action> lst = new ArrayList<>();
        //TODO lst.add(new ExtractAction("Extract Image", img));
        lst.add(new ExtractUnallocAction(
                NbBundle.getMessage(this.getClass(), "ExplorerNodeActionVisitor.action.extUnallocToSingleFiles"), img));
        return lst;
    }

    @Override
    public List<? extends Action> visit(final FileSystem fs) {
        return new ArrayList<>();
    }

    @Override
    public List<? extends Action> visit(final Volume vol) {
        List<AbstractAction> lst = new ArrayList<>();
        lst.add(new ExtractUnallocAction(
                NbBundle.getMessage(this.getClass(), "ExplorerNodeActionVisitor.action.extUnallocToSingleFile"), vol));
        return lst;
    }

    @Override
    public List<? extends Action> visit(final Directory d) {
        List<Action> actions = new ArrayList<>();
        actions.add(AddContentTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    @Override
    public List<? extends Action> visit(final VirtualDirectory d) {
        List<Action> actions = new ArrayList<>();
        if (!d.isDataSource()) {
            actions.add(AddContentTagAction.getInstance());
        }
        actions.add(ExtractAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    @Override
    public List<? extends Action> visit(final DerivedFile d) {
        List<Action> actions = new ArrayList<>();
        actions.add(ExtractAction.getInstance());
        actions.add(AddContentTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    @Override
    public List<? extends Action> visit(final LocalFile d) {
        List<Action> actions = new ArrayList<>();
        actions.add(ExtractAction.getInstance());
        actions.add(AddContentTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    @Override
    public List<? extends Action> visit(final org.sleuthkit.datamodel.File d) {
        List<Action> actions = new ArrayList<>();
        actions.add(ExtractAction.getInstance());
        actions.add(AddContentTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    @Override
    protected List<? extends Action> defaultVisit(Content di) {
        return Collections.<Action>emptyList();
    }

}
