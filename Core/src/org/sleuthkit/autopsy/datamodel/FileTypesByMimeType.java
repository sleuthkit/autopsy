/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Class which contains the Nodes for the 'By Mime Type' view located in the
 * File Types view, shows all files with a mime type. Will initially be empty
 * until file type identification has been performed. Contains a Property Change
 * Listener which is checking for changes in IngestJobEvent Completed or
 * Cancelled and IngestModuleEvent Content Changed.
 */
public final class FileTypesByMimeType extends Observable implements AutopsyVisitableItem {

    private final SleuthkitCase skCase;
    /**
     * The nodes of this tree will be determined dynamically by the mimetypes
     * which exist in the database. This hashmap will store them with the media
     * type as the key and a list of media subtypes as the value.
     */
    private final HashMap<String, List<String>> existingMimeTypes = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger(FileTypesByMimeType.class.getName());

    private void removeListeners() {
        deleteObservers();
        IngestManager.getInstance().removeIngestJobEventListener(pcl);
        Case.removePropertyChangeListener(pcl);
    }

    /*
         * The pcl is in the class because it has the easiest mechanisms to add
         * and remove itself during its life cycles.
     */
    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        String eventType = evt.getPropertyName();
        if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {

            /**
             * Checking for a current case is a stop gap measure until a
             * different way of handling the closing of cases is worked out.
             * Currently, remote events may be received for a case that is
             * already closed.
             */
            try {
                Case.getCurrentCase();
                populateHashMap();
            } catch (IllegalStateException notUsed) {
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

    /**
     * Retrieve the media types by retrieving the keyset from the hashmap.
     *
     * @return mediaTypes - a list of strings representing all distinct media
     * types of files for this case
     */
    private List<String> getMediaTypeList() {
        synchronized (existingMimeTypes) {
            List<String> mediaTypes = new ArrayList<>(existingMimeTypes.keySet());
            Collections.sort(mediaTypes);
            return mediaTypes;
        }
    }

    /**
     * Performs the query on the database to get all distinct MIME types of
     * files in it, and populate the hashmap with those results.
     */
    private void populateHashMap() {
        StringBuilder allDistinctMimeTypesQuery = new StringBuilder();
        allDistinctMimeTypesQuery.append("SELECT DISTINCT mime_type from tsk_files where mime_type IS NOT null");  //NON-NLS
        allDistinctMimeTypesQuery.append(" AND dir_type = ").append(TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()); //NON-NLS
        allDistinctMimeTypesQuery.append(" AND (type IN (").append(TskData.TSK_DB_FILES_TYPE_ENUM.FS.ordinal()).append(","); //NON-NLS
        allDistinctMimeTypesQuery.append(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.ordinal()).append(",");
        allDistinctMimeTypesQuery.append(TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.ordinal()).append(",");
        allDistinctMimeTypesQuery.append(TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.ordinal()).append("))");
        synchronized (existingMimeTypes) {
            existingMimeTypes.clear();

            if (skCase == null) {

                return;
            }
            try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery(allDistinctMimeTypesQuery.toString())) {
                ResultSet resultSet = dbQuery.getResultSet();
                while (resultSet.next()) {
                    final String mime_type = resultSet.getString("mime_type"); //NON-NLS
                    if (!mime_type.isEmpty()) {
                        String mimeType[] = mime_type.split("/");
                        if (!mimeType[0].isEmpty() && !mimeType[1].isEmpty()) {
                            if (!existingMimeTypes.containsKey(mimeType[0])) {
                                existingMimeTypes.put(mimeType[0], new ArrayList<>());
                            }
                            existingMimeTypes.get(mimeType[0]).add(mimeType[1]);
                        }
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Unable to populate File Types by MIME Type tree view from DB: ", ex); //NON-NLS
            }
        }

        setChanged();

        notifyObservers();
    }

    FileTypesByMimeType(SleuthkitCase skCase) {
        IngestManager.getInstance().addIngestJobEventListener(pcl);
        Case.addPropertyChangeListener(pcl);
        this.skCase = skCase;
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
     * @return True if originNode is an instance of ByMimeTypeNode and is empty,
     * false otherwise.
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

        @NbBundle.Messages("FileTypesByMimeType.name.text=By MIME Type")
        final String NAME = Bundle.FileTypesByMimeType_name_text();

        ByMimeTypeNode() {
            super(Children.create(new ByMimeTypeNodeChildren(), true));
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
            return existingMimeTypes.isEmpty();
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
            if (!existingMimeTypes.isEmpty()) {
                mediaTypeNodes.addAll(getMediaTypeList());
            }
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

        MediaTypeNode(String name) {
            super(Children.create(new MediaTypeNodeChildren(name), true));
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
            mediaTypeNodes.addAll(existingMimeTypes.get(mediaType));
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
    class MediaSubTypeNode extends DisplayableItemNode implements Observer {

        private MediaSubTypeNode(String mimeType) {
            super(Children.create(new MediaSubTypeNodeChildren(mimeType), true));
            addObserver(this);
            init(mimeType);
        }

        private void init(String mimeType) {
            super.setName(mimeType);
            updateDisplayName(mimeType);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS
        }

        /**
         * Updates the display name of the mediaSubTypeNode to include the count
         * of files which it represents.
         *
         * @param mimeType - the complete MimeType, needed for accurate query
         * results
         */
        private void updateDisplayName(String mimeType) {

            final long count = new MediaSubTypeNodeChildren(mimeType).calculateItems(skCase, mimeType);

            super.setDisplayName(mimeType.split("/")[1] + " (" + count + ")");
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
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName(getName());
        }
    }

    /**
     * Factory for populating the contents of the Media Sub Type Node with the
     * files that match MimeType which is represented by this position in the
     * tree.
     */
    private class MediaSubTypeNodeChildren extends ChildFactory.Detachable<Content> implements Observer {

        private final String mimeType;

        private MediaSubTypeNodeChildren(String mimeType) {
            super();
            addObserver(this);
            this.mimeType = mimeType;
        }

        /**
         * Get children count without actually loading all nodes
         *
         * @return count(*) - the number of items that will be shown in this
         * items Directory Listing
         */
        private long calculateItems(SleuthkitCase sleuthkitCase, String mime_type) {
            try {
                return sleuthkitCase.countFilesWhere(createQuery(mime_type));
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error getting file search view count", ex); //NON-NLS
                return 0;
            }
        }

        /**
         * Uses the createQuery method to complete the query, Select * from
         * tsk_files WHERE. The results from the database will contain the files
         * which match this mime type and their information.
         *
         * @param list - will contain all files and their attributes from the
         * tsk_files table where mime_type matches the one specified
         * @return true
         */
        @Override
        protected boolean createKeys(List<Content> list) {
            try {
                List<AbstractFile> files = skCase.findAllFilesWhere(createQuery(mimeType));
                list.addAll(files);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Couldn't get search results", ex); //NON-NLS
            }
            return true;
        }

        /**
         * Create the portion of the query following WHERE for a query of the
         * database for each file which matches the complete MIME type
         * represented by this node. Matches against the mime_type column in
         * tsk_files.
         *
         * @param mimeType - the complete mimetype of the file mediatype/subtype
         * @return query.toString - portion of SQL query which will follow a
         * WHERE clause.
         */
        private String createQuery(String mime_type) {
            StringBuilder query = new StringBuilder();
            query.append("(dir_type = ").append(TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()).append(")"); //NON-NLS
            query.append(" AND (type IN (").append(TskData.TSK_DB_FILES_TYPE_ENUM.FS.ordinal()).append(",");  //NON-NLS
            query.append(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.ordinal()).append(",");
            query.append(TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.ordinal()).append(",");
            query.append(TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.ordinal()).append("))");
            if (UserPreferences.hideKnownFilesInViewsTree()) {
                query.append(" AND (known IS NULL OR known != ").append(TskData.FileKnown.KNOWN.getFileKnownValue()).append(")"); //NON-NLS
            }
            query.append(" AND mime_type = '").append(mime_type).append("'");  //NON-NLS
            return query.toString();
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        /**
         * Creates the content to populate the Directory Listing Table view for
         * each file
         *
         * @param key
         * @return
         */
        @Override
        protected Node createNodeForKey(Content key) {
            return key.accept(new ContentVisitor.Default<AbstractNode>() {
                @Override
                public FileNode visit(File f) {
                    return new FileNode(f, false);
                }

                @Override
                public DirectoryNode visit(Directory d) {
                    return new DirectoryNode(d);
                }

                @Override
                public LayoutFileNode visit(LayoutFile lf) {
                    return new LayoutFileNode(lf);
                }

                @Override
                public LocalFileNode visit(DerivedFile df) {
                    return new LocalFileNode(df);
                }

                @Override
                public LocalFileNode visit(LocalFile lf) {
                    return new LocalFileNode(lf);
                }

                @Override
                protected AbstractNode defaultVisit(Content di) {
                    throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(), "FileTypeChildren.exception.notSupported.msg", di.toString()));
                }
            });
        }
    }

}
