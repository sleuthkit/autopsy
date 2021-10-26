/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParams;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtDocumentFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtExecutableFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtRootFilter;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtSearchFilter;
import org.sleuthkit.autopsy.mainui.nodes.SelectionResponder;

/**
 * Filters database results by file extension.
 */
public final class FileTypesByExtension implements AutopsyVisitableItem {

    private final static Logger logger = Logger.getLogger(FileTypesByExtension.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private final FileTypes typesRoot;

    public FileTypesByExtension(FileTypes typesRoot) {
        this.typesRoot = typesRoot;
    }

    public SleuthkitCase getSleuthkitCase() {
        try {
            return Case.getCurrentCaseThrows().getSleuthkitCase();
        } catch (NoCurrentCaseException ex) {
            return null;
        }
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    long filteringDataSourceObjId() {
        return typesRoot.filteringDataSourceObjId();
    }

    /**
     * Listens for case and ingest invest. Updates observers when events are
     * fired. FileType and FileTypes nodes are all listening to this.
     */
    private class FileTypesByExtObservable extends Observable implements RefreshThrottler.Refresher {

        private final PropertyChangeListener pcl;
        private final Set<Case.Events> CASE_EVENTS_OF_INTEREST;
        /**
         * RefreshThrottler is used to limit the number of refreshes performed
         * when CONTENT_CHANGED and DATA_ADDED ingest module events are
         * received.
         */
        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);

        private FileTypesByExtObservable() {
            super();
            this.CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.DATA_SOURCE_ADDED, Case.Events.CURRENT_CASE);
            this.pcl = (PropertyChangeEvent evt) -> {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                        || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {

                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getCurrentCaseThrows();
                        typesRoot.updateShowCounts();
                        update();
                    } catch (NoCurrentCaseException notUsed) {
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
            };

            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, pcl);
            refreshThrottler.registerForIngestModuleEvents();
            Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
        }

        private void removeListeners() {
            deleteObservers();
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            refreshThrottler.unregisterEventListener();
            Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
        }

        private void update() {
            setChanged();
            notifyObservers();
        }

        @Override
        public void refresh() {
            typesRoot.updateShowCounts();
            update();
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {

                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /**
                     * If a new file has been added but does not have an
                     * extension there is nothing to do.
                     */
                    if ((evt.getOldValue() instanceof ModuleContentEvent) == false) {
                        return false;
                    }
                    ModuleContentEvent moduleContentEvent = (ModuleContentEvent) evt.getOldValue();
                    if ((moduleContentEvent.getSource() instanceof AbstractFile) == false) {
                        return false;
                    }
                    AbstractFile abstractFile = (AbstractFile) moduleContentEvent.getSource();
                    if (!abstractFile.getNameExtension().isEmpty()) {
                        return true;
                    }
                } catch (NoCurrentCaseException ex) {
                    /**
                     * Case is closed, no refresh needed.
                     */
                    return false;
                }
            }
            return false;
        }
    }

    private static final String FNAME = NbBundle.getMessage(FileTypesByExtNode.class, "FileTypesByExtNode.fname.text");

    /**
     * Node for root of file types view. Children are nodes for specific types.
     */
    class FileTypesByExtNode extends DisplayableItemNode {

        private final FileExtRootFilter filter;

        /**
         *
         * @param skCase
         * @param filter null to display root node of file type tree, pass in
         *               something to provide a sub-node.
         */
        FileTypesByExtNode(SleuthkitCase skCase, FileExtRootFilter filter) {
            this(skCase, filter, null);
        }

        /**
         *
         * @param skCase
         * @param filter
         * @param o      Observable that was created by a higher-level node that
         *               provides updates on events
         */
        private FileTypesByExtNode(SleuthkitCase skCase, FileExtRootFilter filter, FileTypesByExtObservable o) {

            super(Children.create(new FileTypesByExtNodeChildren(skCase, filter, o), true),
                    Lookups.singleton(filter == null ? FNAME : filter.getDisplayName()));
            this.filter = filter;

            // root node of tree
            if (filter == null) {
                super.setName(FNAME);
                super.setDisplayName(FNAME);
            } // sub-node in file tree (i.e. documents, exec, etc.)
            else {
                super.setName(filter.getDisplayName());
                super.setDisplayName(filter.getDisplayName());
            }
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }
            if (filter != null && (filter.equals(FileExtRootFilter.TSK_DOCUMENT_FILTER) || filter.equals(FileExtRootFilter.TSK_EXECUTABLE_FILTER))) {
                String extensions = "";
                for (String ext : filter.getFilter()) {
                    extensions += "'" + ext + "', ";
                }
                extensions = extensions.substring(0, extensions.lastIndexOf(','));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.name"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.desc"), extensions));
            } else {
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.name"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.desc"), getDisplayName()));
            }
            return sheet;
        }

        @Override
        public String getItemType() {
            /**
             * Because Documents and Executable are further expandable, their
             * column order settings should be stored separately.
             */
            if (filter == null) {
                return getClass().getName();
            }
            if (filter.equals(FileExtRootFilter.TSK_DOCUMENT_FILTER) || filter.equals(FileExtRootFilter.TSK_EXECUTABLE_FILTER)) {
                return getClass().getName() + filter.getName();
            }
            return getClass().getName();
        }

    }

    private class FileTypesByExtNodeChildren extends ChildFactory<FileExtSearchFilter> {

        private final SleuthkitCase skCase;
        private final FileExtRootFilter filter;
        private final FileTypesByExtObservable notifier;

        /**
         *
         * @param skCase
         * @param filter Is null for root node
         * @param o      Observable that provides updates based on events being
         *               fired (or null if one needs to be created)
         */
        private FileTypesByExtNodeChildren(SleuthkitCase skCase, FileExtRootFilter filter, FileTypesByExtObservable o) {
            super();
            this.skCase = skCase;
            this.filter = filter;
            if (o == null) {
                this.notifier = new FileTypesByExtObservable();
            } else {
                this.notifier = o;
            }
        }

        @Override
        protected boolean createKeys(List<FileExtSearchFilter> list) {
            // root node
            if (filter == null) {
                list.addAll(Arrays.asList(FileExtRootFilter.values()));
            } // document and executable has another level of nodes
            else if (filter.equals(FileExtRootFilter.TSK_DOCUMENT_FILTER)) {
                list.addAll(Arrays.asList(FileExtDocumentFilter.values()));
            } else if (filter.equals(FileExtRootFilter.TSK_EXECUTABLE_FILTER)) {
                list.addAll(Arrays.asList(FileExtExecutableFilter.values()));
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileExtSearchFilter key) {
            // make new nodes for the sub-nodes
            if (key.getName().equals(FileExtRootFilter.TSK_DOCUMENT_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileExtRootFilter.TSK_DOCUMENT_FILTER, notifier);
            } else if (key.getName().equals(FileExtRootFilter.TSK_EXECUTABLE_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileExtRootFilter.TSK_EXECUTABLE_FILTER, notifier);
            } else {
                return new FileExtensionNode(key, skCase, notifier);
            }
        }
    }

    /**
     * Node for a specific file type / extension. Children of it will be the
     * files of that type.
     */
    final class FileExtensionNode extends FileTypes.BGCountUpdatingNode implements SelectionResponder {

        private final FileExtSearchFilter filter;

        /**
         *
         * @param filter Extensions that will be shown for this node
         * @param skCase
         * @param o      Observable that sends updates when the child factories
         *               should refresh
         */
        FileExtensionNode(FileExtSearchFilter filter, SleuthkitCase skCase, FileTypesByExtObservable o) {
            super(typesRoot, Children.LEAF,
                    Lookups.fixed(filter.getDisplayName()));
            
            this.filter = filter;
            super.setName(filter.getDisplayName());
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS

            o.addObserver(this);
        }
        
        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayFileExtensions(new FileTypeExtensionsSearchParams(
                    filter,
                    filteringDataSourceObjId() > 0 ? filteringDataSourceObjId() : null));
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.name"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.desc"),
                    filter.getDisplayName()));

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.name"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.desc"),
                    String.join(", ", filter.getFilter())));
            return sheet;
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        /**
         * Consider allowing different configurations for Images, Videos, etc
         * (in which case we'd return getClass().getName() + filter.getName()
         * for all filters).
         */
        @Override
        public String getItemType() {
            return DisplayableItemNode.FILE_PARENT_NODE_KEY;
        }

        @Override
        String getDisplayNameBase() {
            return filter.getDisplayName();
        }

        @Override
        long calculateChildCount() throws TskCoreException {
            try {
                return Case.getCurrentCaseThrows().getSleuthkitCase().countFilesWhere(createQuery(filter));
            } catch (NoCurrentCaseException ex) {
                throw new TskCoreException("No open case.", ex);
            }
        }
    }

    private String createQuery(FileExtSearchFilter filter) {
        if (filter.getFilter().isEmpty()) {
            // We should never be given a search filter without extensions
            // but if we are it is clearly a programming error so we throw 
            // an IllegalArgumentException.
            throw new IllegalArgumentException("Empty filter list passed to createQuery()"); // NON-NLS
        }

        return "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + (UserPreferences.hideKnownFilesInViewsTree()
                ? " AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")"
                : " ")
                + (filteringDataSourceObjId() > 0
                        ? " AND data_source_obj_id = " + filteringDataSourceObjId()
                        : " ")
                + " AND (extension IN (" + filter.getFilter().stream()
                        .map(String::toLowerCase)
                        .map(s -> "'" + StringUtils.substringAfter(s, ".") + "'")
                        .collect(Collectors.joining(", ")) + "))";
    }
}
