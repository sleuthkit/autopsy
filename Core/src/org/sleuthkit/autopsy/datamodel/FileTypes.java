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
package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.AnalysisResultAdded;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * File Types node support
 */
public final class FileTypes implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(FileTypes.class.getName());
    @NbBundle.Messages("FileTypes.name.text=File Types")
    private static final String NAME = Bundle.FileTypes_name_text();
    /**
     * Threshold used to limit db queries for child node counts. When the
     * tsk_files table has more than this number of rows, we don't query for the
     * child node counts, and since we don't have an accurate number we don't
     * show the counts.
     */
    private static final int NODE_COUNT_FILE_TABLE_THRESHOLD = 1_000_000;
    /**
     * Used to keep track of whether we have hit
     * NODE_COUNT_FILE_TABLE_THRESHOLD. If we have, we stop querying for the
     * number of rows in tsk_files, since it is already too large.
     */
    private boolean showCounts = true;

    private final long datasourceObjId;


    FileTypes(long dsObjId) {
        this.datasourceObjId = dsObjId;
        updateShowCounts();
    }
    
    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    long filteringDataSourceObjId() {
        return this.datasourceObjId;
    }
    /**
     * Check the db to determine if the nodes should show child counts.
     */
    void updateShowCounts() {
        /*
         * once we have passed the threshold, we don't need to keep checking the
         * number of rows in tsk_files
         */
        if (showCounts) {
            try {
                if (Case.getCurrentCaseThrows().getSleuthkitCase().countFilesWhere("1=1") > NODE_COUNT_FILE_TABLE_THRESHOLD) { //NON-NLS
                    showCounts = false;
                }
            } catch (NoCurrentCaseException | TskCoreException tskCoreException) {
                showCounts = false;
                logger.log(Level.SEVERE, "Error counting files.", tskCoreException); //NON-NLS
            }
        }
    }

    /**
     * Node which will contain By Mime Type and By Extension nodes.
     */
    public final class FileTypesNode extends DisplayableItemNode {

        FileTypesNode() {
            super(new RootContentChildren(Arrays.asList(
                    new FileTypesByExtension(FileTypes.this),
                    new FileTypesByMimeType(FileTypes.this))),
                    Lookups.singleton(NAME));
            this.setName(NAME);
            this.setDisplayName(NAME);
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
        @NbBundle.Messages({
            "FileTypes.createSheet.name.name=Name",
            "FileTypes.createSheet.name.displayName=Name",
            "FileTypes.createSheet.name.desc=no description"})
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(Bundle.FileTypes_createSheet_name_name(),
                    Bundle.FileTypes_createSheet_name_displayName(),
                    Bundle.FileTypes_createSheet_name_desc(),
                    NAME
            ));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

    }

    static class FileNodeCreationVisitor extends ContentVisitor.Default<AbstractNode> {

        FileNodeCreationVisitor() {
        }

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
        public SlackFileNode visit(SlackFile sf) {
            return new SlackFileNode(sf, false);
        }

        @Override
        protected AbstractNode defaultVisit(Content di) {
            throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(), "FileTypeChildren.exception.notSupported.msg", di.toString()));
        }
    }

    static abstract class BGCountUpdatingNode extends DisplayableItemNode implements Observer {

        private long childCount = -1;
        private FileTypes typesRoot;

        BGCountUpdatingNode(FileTypes typesRoot, Children children) {
            this(typesRoot, children, null);
        }

        BGCountUpdatingNode(FileTypes typesRoot, Children children, Lookup lookup) {
            super(children, lookup);
            this.typesRoot = typesRoot;
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        abstract String getDisplayNameBase();

        /**
         * Calculate the number of children of this node, possibly by querying
         * the DB.
         *
         * @return @throws TskCoreException if there was an error querying the
         *         DB to calculate the number of children.
         */
        abstract long calculateChildCount() throws TskCoreException;

        /**
         * Updates the display name of the mediaSubTypeNode to include the count
         * of files which it represents.
         */
        @NbBundle.Messages("FileTypes.bgCounting.placeholder= (counting...)")
        void updateDisplayName() {
            if (typesRoot.showCounts) {
                //only show "(counting...)" the first time, otherwise it is distracting.
                setDisplayName(getDisplayNameBase() + ((childCount < 0) ? Bundle.FileTypes_bgCounting_placeholder()
                        : (" (" + childCount + ")"))); //NON-NLS
                new SwingWorker<Long, Void>() {
                    @Override
                    protected Long doInBackground() throws Exception {
                        return calculateChildCount();
                    }

                    @Override
                    protected void done() {
                        try {
                            childCount = get();
                            setDisplayName(getDisplayNameBase() + " (" + childCount + ")"); //NON-NLS
                        } catch (InterruptedException | ExecutionException ex) {
                            setDisplayName(getDisplayNameBase());
                            logger.log(Level.WARNING, "Failed to get count of files for " + getDisplayNameBase(), ex); //NON-NLS
                        }
                    }
                }.execute();
            } else {
                setDisplayName(getDisplayNameBase() + ((childCount < 0) ? "" : (" (" + childCount + "+)"))); //NON-NLS
            }
        }
    }

    /**
     * Class that is used as a key by NetBeans for creating result nodes. This
     * is a wrapper around a Content object and is being put in place as an
     * optimization to avoid the Content.hashCode() implementation which issues
     * a database query to get the number of children when determining whether 2
     * Content objects represent the same thing. TODO: This is a temporary
     * solution that can hopefully be removed once we address the issue of
     * determining how many children a Content has (JIRA-2823).
     */
    static class FileTypesKey implements Content {

        private final Content content;

        public FileTypesKey(Content content) {
            this.content = content;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FileTypesKey other = (FileTypesKey) obj;

            return this.content.getId() == other.content.getId();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 101 * hash + (int)(this.content.getId() ^ (this.content.getId() >>> 32));
            return hash;
        }

        @Override
        public <T> T accept(SleuthkitItemVisitor<T> v) {
            return content.accept(v);
        }

        @Override
        public int read(byte[] buf, long offset, long len) throws TskCoreException {
            return content.read(buf, offset, len);
        }

        @Override
        public void close() {
            content.close();
        }

        @Override
        public long getSize() {
            return content.getSize();
        }

        @Override
        public <T> T accept(ContentVisitor<T> v) {
            return content.accept(v);
        }

        @Override
        public String getName() {
            return content.getName();
        }

        @Override
        public String getUniquePath() throws TskCoreException {
            return content.getUniquePath();
        }

        @Override
        public long getId() {
            return content.getId();
        }

        @Override
        public Content getDataSource() throws TskCoreException {
            return content.getDataSource();
        }

        @Override
        public List<Content> getChildren() throws TskCoreException {
            return content.getChildren();
        }

        @Override
        public boolean hasChildren() throws TskCoreException {
            return content.hasChildren();
        }

        @Override
        public int getChildrenCount() throws TskCoreException {
            return content.getChildrenCount();
        }

        @Override
        public Content getParent() throws TskCoreException {
            return content.getParent();
        }

        @Override
        public List<Long> getChildrenIds() throws TskCoreException {
            return content.getChildrenIds();
        }

        @Deprecated
        @SuppressWarnings("deprecation")
        @Override
        public BlackboardArtifact newArtifact(int artifactTypeID) throws TskCoreException {
            return content.newArtifact(artifactTypeID);
        }

        @Deprecated
        @SuppressWarnings("deprecation")
        @Override
        public BlackboardArtifact newArtifact(BlackboardArtifact.ARTIFACT_TYPE type) throws TskCoreException {
            return content.newArtifact(type);
        }
        
        @Override
        public DataArtifact newDataArtifact(BlackboardArtifact.Type artifactType, Collection<BlackboardAttribute> attributesList, Long osAccountId) throws TskCoreException {
            return content.newDataArtifact(artifactType, attributesList, osAccountId);
        }
        
        @Override
        public DataArtifact newDataArtifact(BlackboardArtifact.Type artifactType, Collection<BlackboardAttribute> attributesList, Long osAccountId, long dataSourceId) throws TskCoreException {
            return content.newDataArtifact(artifactType, attributesList, osAccountId, dataSourceId);
        }
        
        @Override
        public DataArtifact newDataArtifact(BlackboardArtifact.Type artifactType, Collection<BlackboardAttribute> attributesList) throws TskCoreException {
            return content.newDataArtifact(artifactType, attributesList);
        }

        @Override
        public ArrayList<BlackboardArtifact> getArtifacts(String artifactTypeName) throws TskCoreException {
            return content.getArtifacts(artifactTypeName);
        }

        @Override
        public BlackboardArtifact getGenInfoArtifact() throws TskCoreException {
            return content.getGenInfoArtifact();
        }

        @Override
        public BlackboardArtifact getGenInfoArtifact(boolean create) throws TskCoreException {
            return content.getGenInfoArtifact(create);
        }

        @Override
        public ArrayList<BlackboardAttribute> getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE attr_type) throws TskCoreException {
            return content.getGenInfoAttributes(attr_type);
        }

        @Override
        public ArrayList<BlackboardArtifact> getArtifacts(int artifactTypeID) throws TskCoreException {
            return content.getArtifacts(artifactTypeID);
        }

        @Override
        public ArrayList<BlackboardArtifact> getArtifacts(BlackboardArtifact.ARTIFACT_TYPE type) throws TskCoreException {
            return content.getArtifacts(type);
        }

        @Override
        public ArrayList<BlackboardArtifact> getAllArtifacts() throws TskCoreException {
            return content.getAllArtifacts();
        }

        @Override
        public Set<String> getHashSetNames() throws TskCoreException {
            return content.getHashSetNames();
        }

        @Override
        public long getArtifactsCount(String artifactTypeName) throws TskCoreException {
            return content.getArtifactsCount(artifactTypeName);
        }

        @Override
        public long getArtifactsCount(int artifactTypeID) throws TskCoreException {
            return content.getArtifactsCount(artifactTypeID);
        }

        @Override
        public long getArtifactsCount(BlackboardArtifact.ARTIFACT_TYPE type) throws TskCoreException {
            return content.getArtifactsCount(type);
        }

        @Override
        public long getAllArtifactsCount() throws TskCoreException {
            return content.getAllArtifactsCount();
        }

        @Override
        public AnalysisResultAdded newAnalysisResult(BlackboardArtifact.Type type, Score score, String string, String string1, String string2, Collection<BlackboardAttribute> clctn) throws TskCoreException {
            return content.newAnalysisResult(type, score, string, string1, string2, clctn);
        }

        @Override
        public AnalysisResultAdded newAnalysisResult(BlackboardArtifact.Type type, Score score, String string, String string1, String string2, Collection<BlackboardAttribute> clctn, long dataSourceId) throws TskCoreException {
            return content.newAnalysisResult(type, score, string, string1, string2, clctn, dataSourceId);
        }

        @Override
        public Score getAggregateScore() throws TskCoreException {
            return content.getAggregateScore();
        }

        @Override
        public List<AnalysisResult> getAnalysisResults(BlackboardArtifact.Type type) throws TskCoreException {
            return content.getAnalysisResults(type);
        }

        @Override
        public List<AnalysisResult> getAllAnalysisResults() throws TskCoreException {
            return content.getAllAnalysisResults();
        }

        @Override
        public List<DataArtifact> getAllDataArtifacts() throws TskCoreException {
            return content.getAllDataArtifacts();
        }
    }
}
