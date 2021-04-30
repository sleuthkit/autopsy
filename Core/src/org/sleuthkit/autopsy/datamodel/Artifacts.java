/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import org.python.google.common.collect.Sets;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT;

/**
 * Classes for creating nodes for BlackboardArtifacts.
 */
public class Artifacts {

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST
            = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);

    /**
     * Parent node of all analysis results.
     */
    @Messages({
        "AnalysisResultsNode_name=Analysis Results",})
    static class AnalysisResultsNode extends BaseArtifactNode {

        /**
         * Main constructor.
         *
         * @param filteringDSObjId The data source object id for which results
         *                         should be filtered. If no filtering should
         *                         occur, this number should be <= 0.
         */
        AnalysisResultsNode(long filteringDSObjId) {
            super(Children.create(new TypeFactory(Category.ANALYSIS_RESULT, filteringDSObjId), true),
                    "org/sleuthkit/autopsy/images/analysis_result.png",
                    Bundle.AnalysisResultsNode_name(),
                    Bundle.AnalysisResultsNode_name());
        }
    }

    /**
     * Parent node of all data artifacts.
     */
    @Messages({
        "DataArtifactsNode_name=Data Artifacts",})
    static class DataArtifactsNode extends BaseArtifactNode {

        /**
         * Main constructor.
         *
         * @param filteringDSObjId The data source object id for which results
         *                         should be filtered. If no filtering should
         *                         occur, this number should be <= 0.
         */
        DataArtifactsNode(long filteringDSObjId) {
            super(Children.create(new TypeFactory(Category.DATA_ARTIFACT, filteringDSObjId), true),
                    "org/sleuthkit/autopsy/images/extracted_content.png",
                    Bundle.DataArtifactsNode_name(),
                    Bundle.DataArtifactsNode_name());
        }
    }

    /**
     * Base class for a parent node of artifacts.
     */
    private static class BaseArtifactNode extends DisplayableItemNode {

        /**
         * Main constructor.
         *
         * @param children    The children of the node.
         * @param icon        The icon for the node.
         * @param name        The name identifier of the node.
         * @param displayName The display name for the node.
         */
        BaseArtifactNode(Children children, String icon, String name, String displayName) {
            super(children, Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(displayName);
            this.setIconBaseWithExtension(icon); //NON-NLS        
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

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ExtractedContentNode.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "ExtractedContentNode.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "ExtractedContentNode.createSheet.name.desc"),
                    super.getDisplayName()));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * A key to be used with the type factory.
     */
    private static class TypeNodeKey {

        private final UpdatableCountTypeNode node;
        private final Set<BlackboardArtifact.Type> applicableTypes;

        /**
         * Constructor generating a generic TypeNode for a given artifact type.
         *
         * @param type    The type for the key.
         * @param dsObjId The data source object id if filtering should occur.
         *                If no filtering should occur, this number should be <=
         *                0.
         */
        TypeNodeKey(BlackboardArtifact.Type type, long dsObjId) {
            this(new TypeNode(type, dsObjId), type);
        }

        /**
         * Constructor for any UpdatableCountTypeNode.
         *
         * @param typeNode The UpdatableCountTypeNode.
         * @param types    The blackboard artifact types corresponding to this
         *                 node.
         */
        TypeNodeKey(UpdatableCountTypeNode typeNode, BlackboardArtifact.Type... types) {
            this.node = typeNode;
            this.applicableTypes = Stream.of(types)
                    .filter(t -> t != null)
                    .collect(Collectors.toSet());
        }

        /**
         * Returns the node associated with this key.
         * @return The node associated with this key.
         */
        UpdatableCountTypeNode getNode() {
            return node;
        }

        /**
         * Returns the blackboard artifact types associated with this key.
         * @return The blackboard artifact types associated with this key.
         */
        Set<BlackboardArtifact.Type> getApplicableTypes() {
            return applicableTypes;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 61 * hash + Objects.hashCode(this.applicableTypes);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TypeNodeKey other = (TypeNodeKey) obj;
            if (!Objects.equals(this.applicableTypes, other.applicableTypes)) {
                return false;
            }
            return true;
        }

    }

    /**
     * 
     */
    private static class TypeFactory extends ChildFactory.Detachable<TypeNodeKey> implements RefreshThrottler.Refresher {

        private static final Logger logger = Logger.getLogger(TypeNode.class.getName());

        @SuppressWarnings("deprecation")
        private static final Set<BlackboardArtifact.Type> IGNORED_TYPES = Sets.newHashSet(
                // these are shown in other parts of the UI (and different node types)
                new BlackboardArtifact.Type(TSK_DATA_SOURCE_USAGE),
                new BlackboardArtifact.Type(TSK_GEN_INFO),
                new BlackboardArtifact.Type(TSK_DOWNLOAD_SOURCE),
                new BlackboardArtifact.Type(TSK_TL_EVENT),
                //This is not meant to be shown in the UI at all. It is more of a meta artifact.
                new BlackboardArtifact.Type(TSK_ASSOCIATED_OBJECT)
        );

        
        private static TypeNodeKey getRecord(BlackboardArtifact.Type type, SleuthkitCase skCase, long dsObjId) {
            int typeId = type.getTypeID();
            if (TSK_EMAIL_MSG.getTypeID() == typeId) {
                EmailExtracted.RootNode emailNode = new EmailExtracted(skCase, dsObjId).new RootNode();
                return new TypeNodeKey(emailNode, new BlackboardArtifact.Type(TSK_EMAIL_MSG));

            } else if (TSK_ACCOUNT.getTypeID() == typeId) {
                Accounts.AccountsRootNode accountsNode = new Accounts(skCase, dsObjId).new AccountsRootNode();
                return new TypeNodeKey(accountsNode, new BlackboardArtifact.Type(TSK_ACCOUNT));

            } else if (TSK_KEYWORD_HIT.getTypeID() == typeId) {
                KeywordHits.RootNode keywordsNode = new KeywordHits(skCase, dsObjId).new RootNode();
                return new TypeNodeKey(keywordsNode, new BlackboardArtifact.Type(TSK_KEYWORD_HIT));

            } else if (TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == typeId
                    || TSK_INTERESTING_FILE_HIT.getTypeID() == typeId) {

                InterestingHits.RootNode interestingHitsNode = new InterestingHits(skCase, dsObjId).new RootNode();
                return new TypeNodeKey(interestingHitsNode,
                        new BlackboardArtifact.Type(TSK_INTERESTING_ARTIFACT_HIT),
                        new BlackboardArtifact.Type(TSK_INTERESTING_FILE_HIT));

            } else {
                return new TypeNodeKey(type, dsObjId);
            }
        }

        // maps the artifact type to its child node 
        private final HashMap<BlackboardArtifact.Type, TypeNodeKey> typeNodeMap = new HashMap<>();
        private final long filteringDSObjId;

        /**
         * RefreshThrottler is used to limit the number of refreshes performed
         * when CONTENT_CHANGED and DATA_ADDED ingest module events are
         * received.
         */
        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);
        private final Category category;

        @SuppressWarnings("deprecation")
        TypeFactory(Category category, long filteringDSObjId) {
            super();
            this.filteringDSObjId = filteringDSObjId;
            this.category = category;
        }

        private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                if (evt.getNewValue() == null) {
                    removeNotify();
                }
            } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                    || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                /**
                 * This is a stop gap measure until a different way of handling
                 * the closing of cases is worked out. Currently, remote events
                 * may be received for a case that is already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    refresh(false);
                } catch (NoCurrentCaseException notUsed) {
                    /**
                     * Case is closed, do nothing.
                     */
                }
            }
        };

        @Override
        protected void addNotify() {
            refreshThrottler.registerForIngestModuleEvents();
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, pcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
        }

        @Override
        protected void removeNotify() {
            refreshThrottler.unregisterEventListener();
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            typeNodeMap.clear();
        }

        @Override
        protected boolean createKeys(List<TypeNodeKey> list) {
            try {

                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                List<BlackboardArtifact.Type> types = (this.filteringDSObjId > 0)
                        ? skCase.getBlackboard().getArtifactTypesInUse(this.filteringDSObjId)
                        : skCase.getArtifactTypesInUse();

                List<TypeNodeKey> allKeysSorted = types.stream()
                        .filter(tp -> category.equals(tp.getCategory()) && !IGNORED_TYPES.contains(tp))
                        .map(tp -> {
                            if (typeNodeMap.containsKey(tp)) {
                                TypeNodeKey record = typeNodeMap.get(tp);
                                record.getNode().updateDisplayName();
                                return record;
                            } else {
                                TypeNodeKey newRecord = getRecord(tp, skCase, filteringDSObjId);
                                for (BlackboardArtifact.Type recordType : newRecord.getApplicableTypes()) {
                                    typeNodeMap.put(recordType, newRecord);
                                }
                                return newRecord;
                            }
                        })
                        .filter(record -> record != null)
                        .distinct()
                        .sorted((a, b) -> {
                            String aSafe = (a.getNode() == null || a.getNode().getDisplayName() == null) ? "" : a.getNode().getDisplayName();
                            String bSafe = (b.getNode() == null || b.getNode().getDisplayName() == null) ? "" : b.getNode().getDisplayName();
                            return aSafe.compareToIgnoreCase(bSafe);
                        })
                        .collect(Collectors.toList());

                list.addAll(allKeysSorted);

            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Trying to access case when no case is open.", ex); //NON-NLS
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting list of artifacts in use: " + ex.getLocalizedMessage()); //NON-NLS
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(TypeNodeKey key) {
            return key.getNode();
        }

        @Override
        public void refresh() {
            refresh(false);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                /**
                 * This is a stop gap measure until a different way of handling
                 * the closing of cases is worked out. Currently, remote events
                 * may be received for a case that is already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /**
                     * Due to some unresolved issues with how cases are closed,
                     * it is possible for the event to have a null oldValue if
                     * the event is a remote event.
                     */
                    final ModuleDataEvent event = (ModuleDataEvent) evt.getOldValue();
                    if (null != event && category.equals(event.getBlackboardArtifactType().getCategory())
                            && !(IGNORED_TYPES.contains(event.getBlackboardArtifactType()))) {
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
    }

    public static abstract class UpdatableCountTypeNode extends DisplayableItemNode {

        private static final Logger logger = Logger.getLogger(UpdatableCountTypeNode.class.getName());

        private final Set<BlackboardArtifact.Type> types;
        private final long filteringDSObjId;
        private long childCount = 0;
        private final String baseName;

        /**
         * Constructs a node that is eligible for display in the tree view or
         * results view. Capabilitites include accepting a
         * DisplayableItemNodeVisitor, indicating whether or not the node is a
         * leaf node, providing an item type string suitable for use as a key,
         * and storing information about a child node that is to be selected if
         * the node is selected in the tree view.
         *
         * @param children The Children object for the node.
         * @param lookup   The Lookup object for the node.
         */
        public UpdatableCountTypeNode(Children children, Lookup lookup, String baseName, long filteringDSObjId, BlackboardArtifact.Type... types) {
            super(children, lookup);
            this.types = Stream.of(types).collect(Collectors.toSet());
            this.filteringDSObjId = filteringDSObjId;
            this.baseName = baseName;
            updateDisplayName();
        }

        protected long getChildCount() {
            return this.childCount;
        }

        void updateDisplayName() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();

                int count = 0;
                for (BlackboardArtifact.Type type : this.types) {
                    if (filteringDSObjId > 0) {
                        count += skCase.getBlackboard().getArtifactsCount(type.getTypeID(), filteringDSObjId);
                    } else {
                        count += skCase.getBlackboardArtifactsTypeCount(type.getTypeID());
                    }
                }

                this.childCount = count;
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Error fetching data when case closed.", ex);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error getting child count", ex); //NON-NLS
            }
            super.setDisplayName(this.baseName + " \u200E(\u200E" + this.childCount + ")\u200E");
        }
    }

    /**
     * Node encapsulating blackboard artifact type. This is used on the
     * left-hand navigation side of the Autopsy UI as the parent node for all of
     * the artifacts of a given type. Its children will be
     * BlackboardArtifactNode objects.
     */
    public static class TypeNode extends UpdatableCountTypeNode {

        private static final Logger logger = Logger.getLogger(TypeNode.class.getName());

        private final BlackboardArtifact.Type type;

        TypeNode(BlackboardArtifact.Type type, long filteringDSObjId) {
            super(Children.create(new ArtifactFactory(type, filteringDSObjId), true),
                    Lookups.singleton(type.getDisplayName()),
                    type.getDisplayName(),
                    filteringDSObjId,
                    type);

            super.setName(type.getTypeName());
            this.type = type;
            String iconPath = IconsUtil.getIconFilePath(type.getTypeID());
            setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.name"),
                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.displayName"),
                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.desc"),
                    type.getDisplayName()));

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.name"),
                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.displayName"),
                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.desc"),
                    getChildCount()));

            return sheet;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public String getItemType() {
            return getClass().getName() + type.getDisplayName();
        }
    }

    /**
     * Creates children for a given artifact type
     */
    private static class ArtifactFactory extends BaseChildFactory<BlackboardArtifact> implements RefreshThrottler.Refresher {

        private static final Logger logger = Logger.getLogger(ArtifactFactory.class.getName());
        private final BlackboardArtifact.Type type;

        /**
         * RefreshThrottler is used to limit the number of refreshes performed
         * when CONTENT_CHANGED and DATA_ADDED ingest module events are
         * received.
         */
        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);
        private final long filteringDSObjId;

        ArtifactFactory(BlackboardArtifact.Type type, long filteringDSObjId) {
            super(type.getTypeName());
            this.type = type;
            this.filteringDSObjId = filteringDSObjId;
        }

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
                    Case.getCurrentCaseThrows();
                    refresh(false);
                } catch (NoCurrentCaseException notUsed) {
                    /**
                     * Case is closed, do nothing.
                     */
                }
            }
        };

        @Override
        protected void onAdd() {
            refreshThrottler.registerForIngestModuleEvents();
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, pcl);
        }

        @Override
        protected void onRemove() {
            refreshThrottler.unregisterEventListener();
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact key) {
            return new BlackboardArtifactNode(key);
        }

        @Override
        protected List<BlackboardArtifact> makeKeys() {
            try {
                List<BlackboardArtifact> arts;
                arts = (filteringDSObjId > 0)
                        ? Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard().getArtifacts(type.getTypeID(), filteringDSObjId)
                        : Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifacts(type.getTypeID());

                for (BlackboardArtifact art : arts) {
                    //Cache attributes while we are off the EDT.
                    //See JIRA-5969
                    art.getAttributes();
                }
                return arts;
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Trying to access case when no case is open.", ex); //NON-NLS
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Couldn't get blackboard artifacts from database", ex); //NON-NLS
            }
            return Collections.emptyList();
        }

        @Override
        public void refresh() {
            refresh(false);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {

                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /**
                     * Even with the check above, it is still possible that the
                     * case will be closed in a different thread before this
                     * code executes. If that happens, it is possible for the
                     * event to have a null oldValue.
                     */
                    final ModuleDataEvent event = (ModuleDataEvent) evt.getOldValue();
                    if (null != event && event.getBlackboardArtifactType().equals(type)) {
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
    }
}
