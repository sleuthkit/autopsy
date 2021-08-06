/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

public class ExplorerNodeActionVisitor extends ContentVisitor.Default<List<? extends Action>> {

    private final static ExplorerNodeActionVisitor instance = new ExplorerNodeActionVisitor();

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
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(AddContentTagAction.getInstance());
        
        final Collection<AbstractFile> selectedFilesList =
                new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if(selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    @Override
    public List<? extends Action> visit(final VirtualDirectory d) {
        List<Action> actionsList = new ArrayList<>();
        if (!d.isDataSource()) {
            actionsList.add(AddContentTagAction.getInstance());
            
            final Collection<AbstractFile> selectedFilesList =
                    new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
            if(selectedFilesList.size() == 1) {
                actionsList.add(DeleteFileContentTagAction.getInstance());
            }
        }
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }
    
    @Override
    public List<? extends Action> visit(final LocalDirectory d) {
        List<Action> actionsList = new ArrayList<>();
        if (!d.isDataSource()) {
            actionsList.add(AddContentTagAction.getInstance());
            
            final Collection<AbstractFile> selectedFilesList =
                    new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
            if(selectedFilesList.size() == 1) {
                actionsList.add(DeleteFileContentTagAction.getInstance());
            }
        }
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    @Override
    public List<? extends Action> visit(final DerivedFile d) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(AddContentTagAction.getInstance());
        
        final Collection<AbstractFile> selectedFilesList =
                new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if(selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    @Override
    public List<? extends Action> visit(final LocalFile d) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(AddContentTagAction.getInstance());
        
        final Collection<AbstractFile> selectedFilesList =
                new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if(selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    @Override
    public List<? extends Action> visit(final org.sleuthkit.datamodel.File d) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(AddContentTagAction.getInstance());
        
        final Collection<AbstractFile> selectedFilesList =
                new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if(selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    @Override
    protected List<? extends Action> defaultVisit(Content di) {
        return Collections.<Action>emptyList();
    }

}
