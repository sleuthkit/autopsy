/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.Score.Priority;
import org.sleuthkit.datamodel.Score.Significance;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Score content view nodes.
 */
public class ScoreContent implements AutopsyVisitableItem {

    private SleuthkitCase skCase;
    private final long filteringDSObjId;    // 0 if not filtering/grouping by data source

    @NbBundle.Messages({"ScoreContent_badFilter_text=Bad Items",
        "ScoreContent_susFilter_text=Suspicious Items"})
    public enum ScoreContentFilter implements AutopsyVisitableItem {

        BAD_ITEM_FILTER(0, "BAD_ITEM_FILTER", //NON-NLS
                Bundle.ScoreContent_badFilter_text()),
        SUS_ITEM_FILTER(1, "SUS_ITEM_FILTER", //NON-NLS
                Bundle.ScoreContent_susFilter_text());

        private int id;
        private String name;
        private String displayName;

        private ScoreContentFilter(int id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;

        }

        public String getName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public ScoreContent(SleuthkitCase skCase) {
        this(skCase, 0);
    }

    public ScoreContent(SleuthkitCase skCase, long dsObjId) {
        this.skCase = skCase;
        this.filteringDSObjId = dsObjId;
    }

    long filteringDataSourceObjId() {
        return this.filteringDSObjId;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public SleuthkitCase getSleuthkitCase() {
        return this.skCase;
    }

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.DATA_SOURCE_ADDED,
            Case.Events.CURRENT_CASE,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED
    );
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestModuleEvent.CONTENT_CHANGED);

    private static PropertyChangeListener getPcl(final Runnable onRefresh, final Runnable onRemove) {
        return (PropertyChangeEvent evt) -> {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
                /**
                 * + // @@@ COULD CHECK If the new file is deleted before
                 * notifying... Checking for a current case is a stop gap
                 * measure	+ update(); until a different way of handling the
                 * closing of cases is worked out. Currently, remote events may
                 * be received for a case that is already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    // new file was added
                    // @@@ COULD CHECK If the new file is deleted before notifying...
                    if (onRefresh != null) {
                        onRefresh.run();
                    }
                } catch (NoCurrentCaseException notUsed) {
                    /**
                     * Case is closed, do nothing.
                     */
                }
            } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                if (evt.getNewValue() == null && onRemove != null) {
                    onRemove.run();
                }
            } else if (CASE_EVENTS_OF_INTEREST.contains(eventType)) {
                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    if (onRefresh != null) {
                        onRefresh.run();
                    }
                } catch (NoCurrentCaseException notUsed) {
                    /**
                     * Case is closed, do nothing.
                     */
                }
            }
        };
    }

    static private String getFileFilter(ScoreContent.ScoreContentFilter filter, long filteringDSObjId) throws IllegalArgumentException {
        String aggregateScoreFilter = "";
        switch (filter) {
            case SUS_ITEM_FILTER:
                aggregateScoreFilter = " tsk_aggregate_score.significance = " + Significance.LIKELY_NOTABLE.getId() + " AND (tsk_aggregate_score.priority = " + Priority.NORMAL.getId() + " OR tsk_aggregate_score.priority = " + Priority.OVERRIDE.getId() + " )";

                break;
            case BAD_ITEM_FILTER:
                aggregateScoreFilter = " tsk_aggregate_score.significance = " + Significance.NOTABLE.getId() + " AND (tsk_aggregate_score.priority = " + Priority.NORMAL.getId() + " OR tsk_aggregate_score.priority = " + Priority.OVERRIDE.getId() + " )";
                break;

            default:
                throw new IllegalArgumentException(MessageFormat.format("Unsupported filter type to get suspect content: {0}", filter));

        }

        String query = " obj_id IN (SELECT tsk_aggregate_score.obj_id FROM tsk_aggregate_score WHERE " + aggregateScoreFilter + ") ";

        if (filteringDSObjId > 0) {
            query += " AND data_source_obj_id = " + filteringDSObjId;
        }
        return query;
    }

    /**
     * Checks for analysis results added to the case that could affect the
     * aggregate score of the file.
     *
     * @param evt The event.
     * @return True if has an analysis result.
     */
    private static boolean isRefreshRequired(PropertyChangeEvent evt) {
        String eventType = evt.getPropertyName();
        if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
            /**
             * This is a stop gap measure until a different way of handling the
             * closing of cases is worked out. Currently, remote events may be
             * received for a case that is already closed.
             */
            try {
                Case.getCurrentCaseThrows();
                /**
                 * Due to some unresolved issues with how cases are closed, it
                 * is possible for the event to have a null oldValue if the
                 * event is a remote event.
                 */
                final ModuleDataEvent event = (ModuleDataEvent) evt.getOldValue();
                if (null != event && Category.ANALYSIS_RESULT.equals(event.getBlackboardArtifactType().getCategory())) {
                    return true;
                }
            } catch (NoCurrentCaseException notUsed) {
                /**
                 * Case is closed, do nothing.
                 */
            }
        }
        return false;
    }

    public static class ScoreContentsNode extends DisplayableItemNode {

        @NbBundle.Messages("ScoreContent_ScoreContentNode_name=Score")
        private static final String NAME = Bundle.ScoreContent_ScoreContentNode_name();

        ScoreContentsNode(SleuthkitCase skCase, long datasourceObjId) {
            super(Children.create(new ScoreContentsChildren(skCase, datasourceObjId), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/red-circle-exclamation.png"); //NON-NLS
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
            "ScoreContent_createSheet_name_displayName=Name",
            "ScoreContent_createSheet_name_desc=no description"})
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>("Name", //NON-NLS
                    Bundle.ScoreContent_createSheet_name_displayName(),
                    Bundle.ScoreContent_createSheet_name_desc(),
                    NAME));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    public static class ScoreContentsChildren extends ChildFactory.Detachable<ScoreContent.ScoreContentFilter> implements RefreshThrottler.Refresher {

        private SleuthkitCase skCase;
        private final long datasourceObjId;

        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);

        private final PropertyChangeListener pcl = getPcl(
                () -> ScoreContentsChildren.this.refresh(false),
                () -> ScoreContentsChildren.this.removeNotify());

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

        private final Map<ScoreContentFilter, ScoreContentsChildren.ScoreContentNode> typeNodeMap = new HashMap<>();

        public ScoreContentsChildren(SleuthkitCase skCase, long dsObjId) {
            this.skCase = skCase;
            this.datasourceObjId = dsObjId;
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            refreshThrottler.registerForIngestModuleEvents();
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
        }

        @Override
        protected void removeNotify() {
            refreshThrottler.unregisterEventListener();
            IngestManager.getInstance().removeIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            IngestManager.getInstance().removeIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
            typeNodeMap.clear();
        }

        @Override
        public void refresh() {
            refresh(false);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            return ScoreContent.isRefreshRequired(evt);
        }

        @Override
        protected boolean createKeys(List<ScoreContent.ScoreContentFilter> list) {
            list.addAll(Arrays.asList(ScoreContent.ScoreContentFilter.values()));
            typeNodeMap.values().forEach(nd -> nd.updateDisplayName());
            return true;
        }

        @Override
        protected Node createNodeForKey(ScoreContent.ScoreContentFilter key) {
            ScoreContentsChildren.ScoreContentNode nd = new ScoreContentsChildren.ScoreContentNode(skCase, key, datasourceObjId);
            typeNodeMap.put(key, nd);
            return nd;
        }

        public class ScoreContentNode extends DisplayableItemNode {

            private static final Logger logger = Logger.getLogger(ScoreContentNode.class.getName());
            private final ScoreContent.ScoreContentFilter filter;
            private final long datasourceObjId;

            ScoreContentNode(SleuthkitCase skCase, ScoreContent.ScoreContentFilter filter, long dsObjId) {
                super(Children.create(new ScoreContentChildren(filter, skCase, dsObjId), true), Lookups.singleton(filter.getDisplayName()));
                this.filter = filter;
                this.datasourceObjId = dsObjId;
                init();
            }

            private void init() {
                super.setName(filter.getName());

                String tooltip = filter.getDisplayName();
                this.setShortDescription(tooltip);
                switch (this.filter) {
                    case SUS_ITEM_FILTER:
                        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/yellow-circle-yield.png"); //NON-NLS
                        break;
                    default:
                    case BAD_ITEM_FILTER:
                        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/red-circle-exclamation.png"); //NON-NLS
                        break;
                }

                updateDisplayName();
            }

            void updateDisplayName() {
                //get count of children without preloading all children nodes
                long count = 0;
                try {
                    count = calculateItems(skCase, filter, datasourceObjId);
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "An error occurred while fetching file counts", ex);
                }
                //final long count = getChildren().getNodesCount(true);
                super.setDisplayName(filter.getDisplayName() + " (" + count + ")");
            }

            /**
             * Get children count without actually loading all nodes
             *
             * @param sleuthkitCase
             * @param filter
             *
             * @return
             */
            private static long calculateItems(SleuthkitCase sleuthkitCase, ScoreContent.ScoreContentFilter filter, long datasourceObjId) throws TskCoreException {
                return sleuthkitCase.countFilesWhere(getFileFilter(filter, datasourceObjId));
            }

            @Override
            public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
                return visitor.visit(this);
            }

            @Override
            @NbBundle.Messages({
                "ScoreContent_createSheet_filterType_displayName=Type",
                "ScoreContent_createSheet_filterType_desc=no description"})
            protected Sheet createSheet() {
                Sheet sheet = super.createSheet();
                Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
                if (sheetSet == null) {
                    sheetSet = Sheet.createPropertiesSet();
                    sheet.put(sheetSet);
                }

                sheetSet.put(new NodeProperty<>("Type", //NON_NLS
                        Bundle.ScoreContent_createSheet_filterType_displayName(),
                        Bundle.ScoreContent_createSheet_filterType_desc(),
                        filter.getDisplayName()));

                return sheet;
            }

            @Override
            public boolean isLeafTypeNode() {
                return true;
            }

            @Override
            public String getItemType() {
                /**
                 * Return getClass().getName() + filter.getName() if custom
                 * settings are desired for different filters.
                 */
                return DisplayableItemNode.FILE_PARENT_NODE_KEY;
            }
        }

        static class ScoreContentChildren extends BaseChildFactory<AbstractFile> implements RefreshThrottler.Refresher {

            private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);

            private final PropertyChangeListener pcl = getPcl(
                    () -> ScoreContentChildren.this.refresh(false),
                    () -> ScoreContentChildren.this.removeNotify());

            private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

            private final SleuthkitCase skCase;
            private final ScoreContent.ScoreContentFilter filter;
            private static final Logger logger = Logger.getLogger(ScoreContentChildren.class.getName());

            private final long datasourceObjId;

            ScoreContentChildren(ScoreContent.ScoreContentFilter filter, SleuthkitCase skCase, long datasourceObjId) {
                super(filter.getName(), new ViewsKnownAndSlackFilter<>());
                this.skCase = skCase;
                this.filter = filter;
                this.datasourceObjId = datasourceObjId;
            }

            @Override
            protected void onAdd() {
                refreshThrottler.registerForIngestModuleEvents();
                IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
                IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
                Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
            }

            @Override
            protected void onRemove() {
                refreshThrottler.unregisterEventListener();
                IngestManager.getInstance().removeIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
                IngestManager.getInstance().removeIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
                Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
            }

            @Override
            public void refresh() {
                refresh(false);
            }

            @Override
            public boolean isRefreshRequired(PropertyChangeEvent evt) {
                return ScoreContent.isRefreshRequired(evt);
            }

            private List<AbstractFile> runFsQuery() {
                List<AbstractFile> ret = new ArrayList<>();

                String query = null;
                try {
                    query = getFileFilter(filter, datasourceObjId);
                    ret = skCase.findAllFilesWhere(query);
                } catch (TskCoreException | IllegalArgumentException e) {
                    logger.log(Level.SEVERE, "Error getting files for the deleted content view using: " + StringUtils.defaultString(query, "<null>"), e); //NON-NLS
                }

                return ret;

            }

            @Override
            protected List<AbstractFile> makeKeys() {
                return runFsQuery();
            }

            @Override
            protected Node createNodeForKey(AbstractFile key) {
                return key.accept(new ContentVisitor.Default<AbstractNode>() {
                    public FileNode visit(AbstractFile f) {
                        return new FileNode(f, false);
                    }

                    public FileNode visit(FsContent f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(LayoutFile f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(File f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(Directory f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(VirtualDirectory f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    protected AbstractNode defaultVisit(Content di) {
                        throw new UnsupportedOperationException("Not supported for this type of Displayable Item: " + di.toString());
                    }
                });
            }
        }
    }
}
