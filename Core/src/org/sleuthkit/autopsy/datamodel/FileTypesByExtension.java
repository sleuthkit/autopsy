/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesKey;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Filters database results by file extension.
 */
public final class FileTypesByExtension implements AutopsyVisitableItem {

    private final static Logger logger = Logger.getLogger(FileTypesByExtension.class.getName());
    private final SleuthkitCase skCase;
    private final FileTypes typesRoot;

    public FileTypesByExtension(FileTypes typesRoot) {
        this.skCase = typesRoot.getSleuthkitCase();
        this.typesRoot = typesRoot;
    }

    public SleuthkitCase getSleuthkitCase() {
        return this.skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Listens for case and ingest invest. Updates observers when events are
     * fired. FileType and FileTypes nodes are all listening to this.
     */
    private class FileTypesByExtObservable extends Observable {

        private final PropertyChangeListener pcl;
        private final Set<Case.Events> CASE_EVENTS_OF_INTEREST;

        private FileTypesByExtObservable() {
            super();
            this.CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.DATA_SOURCE_ADDED, Case.Events.CURRENT_CASE);
            this.pcl = (PropertyChangeEvent evt) -> {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                        || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
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

            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
        }

        private void removeListeners() {
            deleteObservers();
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
        }

        private void update() {
            setChanged();
            notifyObservers();
        }
    }
    private static final String FNAME = NbBundle.getMessage(FileTypesByExtNode.class, "FileTypesByExtNode.fname.text");

    /**
     * Node for root of file types view. Children are nodes for specific types.
     */
    class FileTypesByExtNode extends DisplayableItemNode {

        private final FileTypesByExtension.RootFilter filter;

        /**
         *
         * @param skCase
         * @param filter null to display root node of file type tree, pass in
         *               something to provide a sub-node.
         */
        FileTypesByExtNode(SleuthkitCase skCase, FileTypesByExtension.RootFilter filter) {
            this(skCase, filter, null);
        }

        /**
         *
         * @param skCase
         * @param filter
         * @param o      Observable that was created by a higher-level node that
         *               provides updates on events
         */
        private FileTypesByExtNode(SleuthkitCase skCase, FileTypesByExtension.RootFilter filter, FileTypesByExtObservable o) {

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
            if (filter != null && (filter.equals(FileTypesByExtension.RootFilter.TSK_DOCUMENT_FILTER) || filter.equals(FileTypesByExtension.RootFilter.TSK_EXECUTABLE_FILTER))) {
                String extensions = "";
                for (String ext : filter.getFilter()) {
                    extensions += "'" + ext + "', ";
                }
                extensions = extensions.substring(0, extensions.lastIndexOf(','));
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.name"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.desc"), extensions));
            } else {
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.name"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.desc"), getDisplayName()));
            }
            return s;
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
            if (filter.equals(FileTypesByExtension.RootFilter.TSK_DOCUMENT_FILTER) || filter.equals(FileTypesByExtension.RootFilter.TSK_EXECUTABLE_FILTER)) {
                return getClass().getName() + filter.getName();
            }
            return getClass().getName();
        }

    }

    private class FileTypesByExtNodeChildren extends ChildFactory<FileTypesByExtension.SearchFilterInterface> {

        private final SleuthkitCase skCase;
        private final FileTypesByExtension.RootFilter filter;
        private final FileTypesByExtObservable notifier;

        /**
         *
         * @param skCase
         * @param filter Is null for root node
         * @param o      Observable that provides updates based on events being
         *               fired (or null if one needs to be created)
         */
        private FileTypesByExtNodeChildren(SleuthkitCase skCase, FileTypesByExtension.RootFilter filter, FileTypesByExtObservable o) {
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
        protected boolean createKeys(List<FileTypesByExtension.SearchFilterInterface> list) {
            // root node
            if (filter == null) {
                list.addAll(Arrays.asList(FileTypesByExtension.RootFilter.values()));
            } // document and executable has another level of nodes
            else if (filter.equals(FileTypesByExtension.RootFilter.TSK_DOCUMENT_FILTER)) {
                list.addAll(Arrays.asList(FileTypesByExtension.DocumentFilter.values()));
            } else if (filter.equals(FileTypesByExtension.RootFilter.TSK_EXECUTABLE_FILTER)) {
                list.addAll(Arrays.asList(FileTypesByExtension.ExecutableFilter.values()));
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileTypesByExtension.SearchFilterInterface key) {
            // make new nodes for the sub-nodes
            if (key.getName().equals(FileTypesByExtension.RootFilter.TSK_DOCUMENT_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileTypesByExtension.RootFilter.TSK_DOCUMENT_FILTER, notifier);
            } else if (key.getName().equals(FileTypesByExtension.RootFilter.TSK_EXECUTABLE_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileTypesByExtension.RootFilter.TSK_EXECUTABLE_FILTER, notifier);
            } else {
                return new FileExtensionNode(key, skCase, notifier);
            }
        }
    }

    /**
     * Node for a specific file type / extension. Children of it will be the
     * files of that type.
     */
    class FileExtensionNode extends FileTypes.BGCountUpdatingNode {

        private final FileTypesByExtension.SearchFilterInterface filter;

        /**
         *
         * @param filter Extensions that will be shown for this node
         * @param skCase
         * @param o      Observable that sends updates when the child factories
         *               should refresh
         */
        FileExtensionNode(FileTypesByExtension.SearchFilterInterface filter, SleuthkitCase skCase, FileTypesByExtObservable o) {
            super(typesRoot, Children.create(new FileExtensionNodeChildren(filter, skCase, o), true),
                    Lookups.singleton(filter.getDisplayName()));
            this.filter = filter;
            super.setName(filter.getDisplayName());
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS

            o.addObserver(this);
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
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.name"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.desc"),
                    filter.getDisplayName()));

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.name"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.desc"),
                    String.join(", ", filter.getFilter())));
            return s;
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
            return skCase.countFilesWhere(createQuery(filter));
        }
    }

    private String createQuery(FileTypesByExtension.SearchFilterInterface filter) {
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
                + " AND (extension IN (" + filter.getFilter().stream()
                        .map(String::toLowerCase)
                        .map(s -> "'"+StringUtils.substringAfter(s, ".")+"'")
                        .collect(Collectors.joining(", ")) + "))";
    }

    /**
     * Child node factory for a specific file type - does the database query.
     */
    private class FileExtensionNodeChildren extends ChildFactory.Detachable<FileTypesKey> implements Observer {

        private final SleuthkitCase skCase;
        private final FileTypesByExtension.SearchFilterInterface filter;
        private final Observable notifier;

        /**
         *
         * @param filter Extensions to display
         * @param skCase
         * @param o      Observable that will notify when there could be new
         *               data to display
         */
        private FileExtensionNodeChildren(FileTypesByExtension.SearchFilterInterface filter, SleuthkitCase skCase, Observable o) {
            super();
            this.filter = filter;
            this.skCase = skCase;
            notifier = o;
        }

        @Override
        protected void addNotify() {
            if (notifier != null) {
                notifier.addObserver(this);
            }
        }

        @Override
        protected void removeNotify() {
            if (notifier != null) {
                notifier.deleteObserver(this);
            }
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<FileTypesKey> list) {
            try {
                list.addAll(skCase.findAllFilesWhere(createQuery(filter))
                        .stream().map(f -> new FileTypesKey(f)).collect(Collectors.toList()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Couldn't get search results", ex); //NON-NLS
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileTypesKey key) {
            return key.accept(new FileTypes.FileNodeCreationVisitor());
        }
    }

    // root node filters
    @Messages({"FileTypeExtensionFilters.tskDatabaseFilter.text=Databases"})
    public static enum RootFilter implements AutopsyVisitableItem, SearchFilterInterface {

        TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskImgFilter.text"),
                FileTypeExtensions.getImageExtensions()),
        TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskVideoFilter.text"),
                FileTypeExtensions.getVideoExtensions()),
        TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskAudioFilter.text"),
                FileTypeExtensions.getAudioExtensions()),
        TSK_ARCHIVE_FILTER(3, "TSK_ARCHIVE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskArchiveFilter.text"),
                FileTypeExtensions.getArchiveExtensions()),
        TSK_DATABASE_FILTER(4, "TSK_DATABASE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskDatabaseFilter.text"),
                FileTypeExtensions.getDatabaseExtensions()),
        TSK_DOCUMENT_FILTER(5, "TSK_DOCUMENT_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskDocumentFilter.text"),
                Arrays.asList(".htm", ".html", ".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf", ".txt", ".rtf")), //NON-NLS
        TSK_EXECUTABLE_FILTER(6, "TSK_EXECUTABLE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskExecFilter.text"),
                FileTypeExtensions.getExecutableExtensions()); //NON-NLS

        private final int id;
        private final String name;
        private final String displayName;
        private final List<String> filter;

        private RootFilter(int id, String name, String displayName, List<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public List<String> getFilter() {
            return this.filter;
        }
    }

    // document sub-node filters
    public static enum DocumentFilter implements AutopsyVisitableItem, SearchFilterInterface {

        AUT_DOC_HTML(0, "AUT_DOC_HTML", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocHtmlFilter.text"),
                Arrays.asList(".htm", ".html")), //NON-NLS
        AUT_DOC_OFFICE(1, "AUT_DOC_OFFICE", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocOfficeFilter.text"),
                Arrays.asList(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx")), //NON-NLS
        AUT_DOC_PDF(2, "AUT_DOC_PDF", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autoDocPdfFilter.text"),
                Arrays.asList(".pdf")), //NON-NLS
        AUT_DOC_TXT(3, "AUT_DOC_TXT", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocTxtFilter.text"),
                Arrays.asList(".txt")), //NON-NLS
        AUT_DOC_RTF(4, "AUT_DOC_RTF", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocRtfFilter.text"),
                Arrays.asList(".rtf")); //NON-NLS

        private final int id;
        private final String name;
        private final String displayName;
        private final List<String> filter;

        private DocumentFilter(int id, String name, String displayName, List<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public List<String> getFilter() {
            return this.filter;
        }
    }

    // executable sub-node filters
    public static enum ExecutableFilter implements AutopsyVisitableItem, SearchFilterInterface {

        ExecutableFilter_EXE(0, "ExecutableFilter_EXE", ".exe", Arrays.asList(".exe")), //NON-NLS
        ExecutableFilter_DLL(1, "ExecutableFilter_DLL", ".dll", Arrays.asList(".dll")), //NON-NLS
        ExecutableFilter_BAT(2, "ExecutableFilter_BAT", ".bat", Arrays.asList(".bat")), //NON-NLS
        ExecutableFilter_CMD(3, "ExecutableFilter_CMD", ".cmd", Arrays.asList(".cmd")), //NON-NLS
        ExecutableFilter_COM(4, "ExecutableFilter_COM", ".com", Arrays.asList(".com")); //NON-NLS

        private final int id;
        private final String name;
        private final String displayName;
        private final List<String> filter;

        private ExecutableFilter(int id, String name, String displayName, List<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public List<String> getFilter() {
            return this.filter;
        }
    }

    interface SearchFilterInterface {

        public String getName();

        public int getId();

        public String getDisplayName();

        public List<String> getFilter();

    }
}
