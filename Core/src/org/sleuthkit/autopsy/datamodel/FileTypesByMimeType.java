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
 * Node for Root of the 'By Mime Type' view located in the File Types view,
 * shows all files with a mime type. Will intially be empty until file type
 * identification has been performed.
 */
class FileTypesByMimeType extends Observable implements AutopsyVisitableItem {

    static SleuthkitCase skCase;
    /**
     * The nodes of this tree will be determined dynamically by the mimetypes
     * which exist in the database. This hashmap will store them with the media
     * type as the key and a list of media subtypes as the value.
     */
    private final HashMap<String, List<String>> existingMimeTypes = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger(FileTypesByMimeType.class.getName());

    /*
         * The pcl is in the class because it has the easiest mechanisms to add
         * and remove itself during its life cycles.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                    || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                    //             || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())
                    || eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
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
            }
        }
    };

    private List<String> getMediaTypeList() {
        synchronized (existingMimeTypes) {
            List<String> mediaTypes = new ArrayList<>(existingMimeTypes.keySet());
            Collections.sort(mediaTypes);
            return mediaTypes;
        }
    }

    private void populateHashMap() {
        StringBuilder allDistinctMimeTypesQuery = new StringBuilder();
        allDistinctMimeTypesQuery.append("SELECT DISTINCT mime_type from tsk_files where mime_type NOT null");
        allDistinctMimeTypesQuery.append(" AND dir_type = ").append(TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()).append(";"); //NON-NLS
        synchronized (existingMimeTypes) {
            existingMimeTypes.clear();
        }

        if (skCase == null) {

            return;
        }
        try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery(allDistinctMimeTypesQuery.toString())) {
            ResultSet resultSet = dbQuery.getResultSet();
            synchronized (existingMimeTypes) {
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
            }
        } catch (TskCoreException | SQLException ex) {
            LOGGER.log(Level.WARNING, "Unable to populate File Types by MIME Type tree view from DB: ", ex); //NON-NLS
        }
        setChanged();
        notifyObservers();
    }

    FileTypesByMimeType(SleuthkitCase skCase) {
        IngestManager.getInstance().addIngestJobEventListener(pcl);
        IngestManager.getInstance().addIngestModuleEventListener(pcl);
//        Case.addPropertyChangeListener(pcl);
        //       populateHashMap();
        FileTypesByMimeType.skCase = skCase;
    }

    SleuthkitCase getSleuthkitCase() {
        return skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    class FileTypesByMimeTypeNode extends DisplayableItemNode {

        @NbBundle.Messages("FileTypesByMimeType.name.text=By MIME Type")
        final String NAME = Bundle.FileTypesByMimeType_name_text();

        FileTypesByMimeTypeNode(SleuthkitCase sleuthkitCase) {
            super(Children.create(new FileTypesByMimeTypeNodeChildren(skCase), true));
            setName(NAME);
            setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png");
        }

        @Override
        public boolean isLeafTypeNode() {
//            if (!existingMimeTypes.isEmpty()) {
            return false;
//            }
//            else {
//                return true;
//            }
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

    class FileTypesByMimeTypeNodeChildren extends ChildFactory<String> implements Observer {

        private SleuthkitCase skCase;
        static final String EMPTY_MIME_TREE_STRING = "Data not available. Run file type identification module.";

        /**
         *
         * @param skCase (or null if one needs to be created)
         */
        public FileTypesByMimeTypeNodeChildren(SleuthkitCase skCase) {
            super();
            addObserver(this);
            this.skCase = skCase;
        }

        @Override
        protected boolean createKeys(List<String> mediaTypeNodes) {
            if (!existingMimeTypes.isEmpty()) {
                mediaTypeNodes.addAll(getMediaTypeList());
            } else {
                mediaTypeNodes.add(EMPTY_MIME_TREE_STRING);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            if (!existingMimeTypes.isEmpty()) {
                return new MediaTypeNode(key);
            } else {
                return new EmptyNode(EMPTY_MIME_TREE_STRING);
            }

        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

    }

    class EmptyNode extends DisplayableItemNode {

        EmptyNode(String name) {
            super(Children.LEAF);
            super.setName(name);
            setName(name);
            setDisplayName(name);
        }

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
    }

    class MediaTypeNode extends DisplayableItemNode {

        String mediaType;

        MediaTypeNode(String name) {
            super(Children.create(new MediaTypeChildren(name), true));
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

    class MediaTypeChildren extends ChildFactory<String> implements Observer {

        String mediaType;

        MediaTypeChildren(String name) {
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

    class MediaSubTypeNode extends DisplayableItemNode {

        private MediaSubTypeNode(String mimeType) {
            super(Children.create(new MediaSubTypeNodeChildren(mimeType), true));
            init(mimeType);
        }

        private void init(String mimeType) {
            super.setName(mimeType);
            updateDisplayName(mimeType);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS
        }

        private void updateDisplayName(String mimeType) {
            final long count = MediaSubTypeNodeChildren.calculateItems(skCase, mimeType);
            super.setDisplayName(mimeType.split("/")[1] + " (" + count + ")");
        }

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

    }

    private static class MediaSubTypeNodeChildren extends ChildFactory.Detachable<Content> {

        private final String mimeType;

        MediaSubTypeNodeChildren(String mimeType) {
            super();
            this.mimeType = mimeType;
        }

        /**
         * Get children count without actually loading all nodes
         *
         * @return
         */
        private static long calculateItems(SleuthkitCase sleuthkitCase, String mimeType) {
            try {
                return sleuthkitCase.countFilesWhere(createQuery(mimeType));
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error getting file search view count", ex); //NON-NLS
                return 0;
            }
        }

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

        private static String createQuery(String mimeType) {
            StringBuilder query = new StringBuilder();
            query.append("(dir_type = ").append(TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()).append(")"); //NON-NLS
            if (UserPreferences.hideKnownFilesInViewsTree()) {
                query.append(" AND (known IS NULL OR known != ").append(TskData.FileKnown.KNOWN.getFileKnownValue()).append(")"); //NON-NLS
            }
            query.append(" AND mime_type = '").append(mimeType).append("'");
            return query.toString();
        }

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
