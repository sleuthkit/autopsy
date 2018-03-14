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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import static org.sleuthkit.autopsy.core.UserPreferences.hideKnownFilesInViewsTree;
import static org.sleuthkit.autopsy.core.UserPreferences.hideSlackFilesInViewsTree;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesKey;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Class which contains the Nodes for the 'By Mime Type' view located in the
 * File Types view, shows all files with a mime type. Will initially be empty
 * until file type identification has been performed. Contains a Property Change
 * Listener which is checking for changes in IngestJobEvent Completed or
 * Canceled and IngestModuleEvent Content Changed.
 */
public final class FileTypesByMimeType extends Observable implements AutopsyVisitableItem {

    private final static Logger logger = Logger.getLogger(FileTypesByMimeType.class.getName());

    private final SleuthkitCase skCase;
    /**
     * The nodes of this tree will be determined dynamically by the mimetypes
     * which exist in the database. This hashmap will store them with the media
     * type as the key and a Map, from media subtype to count, as the value.
     */
    private final HashMap<String, Map<String, Long>> existingMimeTypeCounts = new HashMap<>();
    /**
     * Root of the File Types tree. Used to provide single answer to question:
     * Should the child counts be shown next to the nodes?
     */
    private final FileTypes typesRoot;

    /**
     * The pcl is in the class because it has the easiest mechanisms to add and
     * remove itself during its life cycles.
     */
    private final PropertyChangeListener pcl;

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.DATA_SOURCE_ADDED, Case.Events.CURRENT_CASE);

    /**
     * Create the base expression used as the where clause in the queries for
     * files by mime type. Filters out certain kinds of files and directories,
     * and known/slack files based on user preferences.
     *
     * @return The base expression to be used in the where clause of queries for
     *         files by mime type.
     */
    static private String createBaseWhereExpr() {
        return "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + " AND (type IN ("
                + TskData.TSK_DB_FILES_TYPE_ENUM.FS.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.ordinal()
                + (hideSlackFilesInViewsTree() ? "" : ("," + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.ordinal()))
                + "))"
                + (hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")") : "");
    }

    private void removeListeners() {
        deleteObservers();
        IngestManager.getInstance().removeIngestJobEventListener(pcl);
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
    }

    /**
     * Performs the query on the database to get all distinct MIME types of
     * files in it, and populate the hashmap with those results.
     */
    private void populateHashMap() {
        String query = "SELECT mime_type, count(*) AS count FROM tsk_files "
                + " WHERE mime_type IS NOT null "
                + " AND " + createBaseWhereExpr()
                + " GROUP BY mime_type";
        synchronized (existingMimeTypeCounts) {
            existingMimeTypeCounts.clear();

            if (skCase == null) {
                return;
            }
            try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                while (resultSet.next()) {
                    final String mime_type = resultSet.getString("mime_type"); //NON-NLS
                    if (!mime_type.isEmpty()) {
                        //if the mime_type contained multiple slashes then everything after the first slash will become the subtype
                        final String mediaType = StringUtils.substringBefore(mime_type, "/");
                        final String subType = StringUtils.removeStart(mime_type, mediaType + "/");
                        if (!mediaType.isEmpty() && !subType.isEmpty()) {
                            final long count = resultSet.getLong("count");
                            existingMimeTypeCounts.computeIfAbsent(mediaType, t -> new HashMap<>())
                                    .put(subType, count);
                        }
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.SEVERE, "Unable to populate File Types by MIME Type tree view from DB: ", ex); //NON-NLS
            }
        }

        setChanged();
        notifyObservers();
    }

    FileTypesByMimeType(FileTypes typesRoot) {
        this.skCase = typesRoot.getSleuthkitCase();
        this.typesRoot = typesRoot;
        this.pcl = (PropertyChangeEvent evt) -> {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())
                    || eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                    || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                    || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getOpenCase();
                    typesRoot.updateShowCounts();
                    populateHashMap();
                } catch (NoCurrentCaseException notUsed) {
                    /**
                     * Case is closed, do nothing.
                     */
                }
            } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                if (evt.getNewValue() == null) {
                    removeListeners();
                }
            }
        };
        IngestManager.getInstance().addIngestJobEventListener(pcl);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
        populateHashMap();
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Method to check if the node in question is a ByMimeTypeNode which is
     * empty.
     *
     * @param node the Node which you wish to check.
     *
     * @return True if originNode is an instance of ByMimeTypeNode and is empty,
     *         false otherwise.
     */
    public static boolean isEmptyMimeTypeNode(Node node) {
        boolean isEmptyMimeNode = false;
        if (node instanceof FileTypesByMimeType.ByMimeTypeNode && ((FileTypesByMimeType.ByMimeTypeNode) node).isEmpty()) {
            isEmptyMimeNode = true;
        }
        return isEmptyMimeNode;

    }

    /**
     * Class which represents the root node of the "By MIME Type" tree, will
     * have children of each media type present in the database or no children
     * when the file detection module has not been run and MIME type is
     * currently unknown.
     */
    class ByMimeTypeNode extends DisplayableItemNode {

        @NbBundle.Messages({"FileTypesByMimeType.name.text=By MIME Type"})

        final String NAME = Bundle.FileTypesByMimeType_name_text();

        ByMimeTypeNode() {
            super(Children.create(new ByMimeTypeNodeChildren(), true), Lookups.singleton(Bundle.FileTypesByMimeType_name_text()));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png");
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
        public String getItemType() {
            return getClass().getName();
        }

        boolean isEmpty() {
            synchronized (existingMimeTypeCounts) {
                return existingMimeTypeCounts.isEmpty();
            }
        }
    }

    /**
     * Creates the children for the "By MIME Type" node these children will each
     * represent a distinct media type present in the DB
     */
    private class ByMimeTypeNodeChildren extends ChildFactory<String> implements Observer {

        private ByMimeTypeNodeChildren() {
            super();
            addObserver(this);
        }

        @Override
        protected boolean createKeys(List<String> mediaTypeNodes) {
            final List<String> keylist;
            synchronized (existingMimeTypeCounts) {
                keylist = new ArrayList<>(existingMimeTypeCounts.keySet());
            }
            Collections.sort(keylist);
            mediaTypeNodes.addAll(keylist);

            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new MediaTypeNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * The Media type node created by the ByMimeTypeNodeChildren and contains
     * one of the unique media types present in the database for this case.
     */
    class MediaTypeNode extends DisplayableItemNode {

        @NbBundle.Messages({"FileTypesByMimeTypeNode.createSheet.mediaType.name=Type",
            "FileTypesByMimeTypeNode.createSheet.mediaType.displayName=Type",
            "FileTypesByMimeTypeNode.createSheet.mediaType.desc=no description"})

        MediaTypeNode(String name) {
            super(Children.create(new MediaTypeNodeChildren(name), true), Lookups.singleton(name));
            setName(name);
            setDisplayName(name);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png");
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
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByMimeTypeNode.createSheet.mediaType.name"), NbBundle.getMessage(this.getClass(), "FileTypesByMimeTypeNode.createSheet.mediaType.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByMimeTypeNode.createSheet.mediaType.desc"), getDisplayName()));
            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

    }

    /**
     * Creates children for media type nodes, children will be MediaSubTypeNodes
     * and represent one of the subtypes which are present in the database of
     * their media type.
     */
    private class MediaTypeNodeChildren extends ChildFactory<String> implements Observer {

        String mediaType;

        MediaTypeNodeChildren(String name) {
            addObserver(this);
            this.mediaType = name;
        }

        @Override
        protected boolean createKeys(List<String> mediaTypeNodes) {
            mediaTypeNodes.addAll(existingMimeTypeCounts.get(mediaType).keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String subtype) {
            String mimeType = mediaType + "/" + subtype;
            return new MediaSubTypeNode(mimeType);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

    }

    /**
     * Node which represents the media sub type in the By MIME type tree, the
     * media subtype is the portion of the MIME type following the /.
     */
    class MediaSubTypeNode extends FileTypes.BGCountUpdatingNode {

        @NbBundle.Messages({"FileTypesByMimeTypeNode.createSheet.mediaSubtype.name=Subtype",
            "FileTypesByMimeTypeNode.createSheet.mediaSubtype.displayName=Subtype",
            "FileTypesByMimeTypeNode.createSheet.mediaSubtype.desc=no description"})
        private final String mimeType;
        private final String subType;

        private MediaSubTypeNode(String mimeType) {
            super(typesRoot, Children.create(new MediaSubTypeNodeChildren(mimeType), true), Lookups.singleton(mimeType));
            this.mimeType = mimeType;
            this.subType = StringUtils.substringAfter(mimeType, "/");
            super.setName(mimeType);
            super.setDisplayName(subType);
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS
            addObserver(this);
        }

        /**
         * This returns true because any MediaSubTypeNode that exists is going
         * to be a bottom level node in the Tree view on the left of Autopsy.
         *
         * @return true
         */
        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor< T> v) {
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
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByMimeTypeNode.createSheet.mediaSubtype.name"), NbBundle.getMessage(this.getClass(), "FileTypesByMimeTypeNode.createSheet.mediaSubtype.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByMimeTypeNode.createSheet.mediaSubtype.desc"), getDisplayName()));
            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        @Override
        String getDisplayNameBase() {
            return subType;
        }

        @Override
        long calculateChildCount() {
            return existingMimeTypeCounts.get(StringUtils.substringBefore(mimeType, "/")).get(subType);
        }
    }

    /**
     * Factory for populating the contents of the Media Sub Type Node with the
     * files that match MimeType which is represented by this position in the
     * tree.
     */
    private class MediaSubTypeNodeChildren extends ChildFactory.Detachable<FileTypesKey> implements Observer {

        private final String mimeType;

        private MediaSubTypeNodeChildren(String mimeType) {
            super();
            addObserver(this);
            this.mimeType = mimeType;
        }

        @Override
        protected boolean createKeys(List<FileTypesKey> list) {
            try {
                list.addAll(skCase.findAllFilesWhere(createBaseWhereExpr() + " AND mime_type = '" + mimeType + "'")
                        .stream().map(f -> new FileTypesKey(f)).collect(Collectors.toList())); //NON-NLS
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Couldn't get search results", ex); //NON-NLS
            }
            return true;
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        @Override
        protected Node createNodeForKey(FileTypesKey key) {
            return key.accept(new FileTypes.FileNodeCreationVisitor());
        }
    }
}
