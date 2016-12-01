/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import org.sleuthkit.autopsy.datamodel.accounts.FileTypeExtensionFilters;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Node for root of file types view. Children are nodes for specific types.
 */
class FileTypesByExtNode extends DisplayableItemNode {

    private static final String FNAME = NbBundle.getMessage(FileTypesByExtNode.class, "FileTypesNode.fname.text");
    private final FileTypeExtensionFilters.RootFilter filter;
    /**
     *
     * @param skCase
     * @param filter null to display root node of file type tree, pass in
     *               something to provide a sub-node.
     */
    FileTypesByExtNode(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter) {
        super(Children.create(new FileTypesByExtChildren(skCase, filter, null), true), Lookups.singleton(filter == null ? FNAME : filter.getName()));
        this.filter = filter;
        init();
    }

    /**
     *
     * @param skCase
     * @param filter
     * @param o      Observable that was created by a higher-level node that
     *               provides updates on events
     */
    private FileTypesByExtNode(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter, Observable o) {
        super(Children.create(new FileTypesByExtChildren(skCase, filter, o), true), Lookups.singleton(filter == null ? FNAME : filter.getName()));
        this.filter = filter;
        init();
    }

    private void init() {
        // root node of tree
        if (filter == null) {
            super.setName(FNAME);
            super.setDisplayName(FNAME);
        } // sub-node in file tree (i.e. documents, exec, etc.)
        else {
            super.setName(filter.getName());
            super.setDisplayName(filter.getDisplayName());
        }
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png"); //NON-NLS    
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "FileTypesNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "FileTypesNode.createSheet.name.desc"),
                getName()));
        return s;
    }

    @Override
    public String getItemType() {
        /**
         * Because Documents and Executable are further expandable, their
         * column order settings should be stored separately.
         */
        if(filter == null)
            return getClass().getName();
        if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER) ||
                filter.equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER))
            return getClass().getName() + filter.getName();
        return getClass().getName();
    }

    /**
     *
     */
    private static class FileTypesByExtChildren extends ChildFactory<FileTypeExtensionFilters.SearchFilterInterface> {

        private SleuthkitCase skCase;
        private FileTypeExtensionFilters.RootFilter filter;
        private Observable notifier;

        /**
         *
         * @param skCase
         * @param filter Is null for root node
         * @param o      Observable that provides updates based on events being
         *               fired (or null if one needs to be created)
         */
        private FileTypesByExtChildren(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter, Observable o) {
            super();
            this.skCase = skCase;
            this.filter = filter;
            if (o == null) {
                this.notifier = new FileTypesByExtChildrenObservable();
            } else {
                this.notifier = o;
            }
        }

        /**
         * Listens for case and ingest invest. Updates observers when events are
         * fired. FileType and FileTypes nodes are all listening to this.
         */
        private final class FileTypesByExtChildrenObservable extends Observable {

            private FileTypesByExtChildrenObservable() {
                IngestManager.getInstance().addIngestJobEventListener(pcl);
                IngestManager.getInstance().addIngestModuleEventListener(pcl);
                Case.addPropertyChangeListener(pcl);
            }

            private void removeListeners() {
                deleteObservers();
                IngestManager.getInstance().removeIngestJobEventListener(pcl);
                IngestManager.getInstance().removeIngestModuleEventListener(pcl);
                Case.removePropertyChangeListener(pcl);
            }

            private final PropertyChangeListener pcl = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String eventType = evt.getPropertyName();
                    if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())
                            || eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                            || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                            || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                        /**
                         * Checking for a current case is a stop gap measure
                         * until a different way of handling the closing of
                         * cases is worked out. Currently, remote events may be
                         * received for a case that is already closed.
                         */
                        try {
                            Case.getCurrentCase();
                            update();
                        } catch (IllegalStateException notUsed) {
                            /**
                             * Case is closed, do nothing.
                             */
                        }
                    } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                        // case was closed. Remove listeners so that we don't get called with a stale case handle
                        if (evt.getNewValue() == null) {
                            removeListeners();
                        }
                    }
                }
            };

            private void update() {
                setChanged();
                notifyObservers();
            }
        }

        @Override
        protected boolean createKeys(List<FileTypeExtensionFilters.SearchFilterInterface> list) {
            // root node
            if (filter == null) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.RootFilter.values()));
            } // document and executable has another level of nodes
            else if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER)) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.DocumentFilter.values()));
            } else if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER)) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.ExecutableFilter.values()));
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileTypeExtensionFilters.SearchFilterInterface key) {
            // make new nodes for the sub-nodes
            if (key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER, notifier);
            } else if (key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER, notifier);
            } else {
                return new FileTypeByExtNode(key, skCase, notifier);
            }
        }
    }
}
