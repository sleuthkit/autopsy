/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import java.util.Map;
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
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import org.python.google.common.collect.Sets;
import org.sleuthkit.datamodel.Blackboard;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_ACCOUNT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_DATA_SOURCE_USAGE;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_HASHSET_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_GEN_INFO;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_INTERESTING_ITEM;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_TL_EVENT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_KEYWORD_HIT;

/**
 * Classes for creating nodes for BlackboardArtifacts.
 */
public class Artifacts {

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST
            = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);

    /**
     * Base class for a parent node of artifacts.
     */
    static class BaseArtifactNode extends DisplayableItemNode {

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
         *                If no filtering should occur, this number should be
         *                less than or equal to 0.
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
         *
         * @return The node associated with this key.
         */
        UpdatableCountTypeNode getNode() {
            return node;
        }

        /**
         * Returns the blackboard artifact types associated with this key.
         *
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
     * Factory for showing a list of artifact types (i.e. all the data artifact
     * types).
     */
    static class TypeFactory extends ChildFactory.Detachable<TypeNodeKey> implements RefreshThrottler.Refresher {

        private static final Logger logger = Logger.getLogger(TypeNode.class.getName());

        /**
         * Types that should not be shown in the tree.
         */
        @SuppressWarnings("deprecation")
        private static final Set<BlackboardArtifact.Type> IGNORED_TYPES = Sets.newHashSet(
                // these are shown in other parts of the UI (and different node types)
                TSK_DATA_SOURCE_USAGE,
                TSK_GEN_INFO,
                new BlackboardArtifact.Type(TSK_DOWNLOAD_SOURCE),
                TSK_TL_EVENT,
                //This is not meant to be shown in the UI at all. It is more of a meta artifact.
                TSK_ASSOCIATED_OBJECT
        );

        /**
         * Returns a Children key to be use for a particular artifact type.
         *
         * @param type    The artifact type.
         * @param skCase  The relevant Sleuthkit case in order to create the
         *                node.
         * @param dsObjId The data source object id to use for filtering. If id
         *                is less than or equal to 0, no filtering will occur.
         *
         * @return The generated key.
         *
         * @SuppressWarnings("deprecation") - we need to support already
         * existing interesting file and artifact hits.
         */
        @SuppressWarnings("deprecation")
        private static TypeNodeKey getTypeKey(BlackboardArtifact.Type type, SleuthkitCase skCase, long dsObjId) {
            int typeId = type.getTypeID();
            if (TSK_EMAIL_MSG.getTypeID() == typeId) {
                EmailExtracted.RootNode emailNode = new EmailExtracted(skCase, dsObjId).new RootNode();
                return new TypeNodeKey(emailNode, TSK_EMAIL_MSG);

            } else if (TSK_ACCOUNT.getTypeID() == typeId) {
                Accounts.AccountsRootNode accountsNode = new Accounts(dsObjId).new AccountsRootNode();
                return new TypeNodeKey(accountsNode, TSK_ACCOUNT);

            } else if (TSK_KEYWORD_HIT.getTypeID() == typeId) {
                KeywordHits.RootNode keywordsNode = new KeywordHits(skCase, dsObjId).new RootNode();
                return new TypeNodeKey(keywordsNode, TSK_KEYWORD_HIT);

            } else if (TSK_INTERESTING_ITEM.getTypeID() == typeId) {
                InterestingHits.RootNode interestingHitsNode = new InterestingHits(skCase, TSK_INTERESTING_ITEM, dsObjId).new RootNode();
                return new TypeNodeKey(interestingHitsNode, TSK_INTERESTING_ITEM);
            } else if (TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == typeId) {
                InterestingHits.RootNode interestingHitsNode = new InterestingHits(skCase, TSK_INTERESTING_ARTIFACT_HIT, dsObjId).new RootNode();
                return new TypeNodeKey(interestingHitsNode, TSK_INTERESTING_ARTIFACT_HIT);
            } else if (TSK_INTERESTING_FILE_HIT.getTypeID() == typeId) {
                InterestingHits.RootNode interestingHitsNode = new InterestingHits(skCase, TSK_INTERESTING_FILE_HIT, dsObjId).new RootNode();
                return new TypeNodeKey(interestingHitsNode, TSK_INTERESTING_FILE_HIT);
            } else if (TSK_HASHSET_HIT.getTypeID() == typeId) {
                HashsetHits.RootNode hashsetHits = new HashsetHits(skCase, dsObjId).new RootNode();
                return new TypeNodeKey(hashsetHits, TSK_HASHSET_HIT);

            } else {
                return new TypeNodeKey(type, dsObjId);
            }
        }

        // maps the artifact type to its child node 
        private final Map<BlackboardArtifact.Type, TypeNodeKey> typeNodeMap = new HashMap<>();
        private final long filteringDSObjId;

        /**
         * RefreshThrottler is used to limit the number of refreshes performed
         * when CONTENT_CHANGED and DATA_ADDED ingest module events are
         * received.
         */
        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);
        private final Category category;

        private final PropertyChangeListener weakPcl;

        /**
         * Main constructor.
         *
         * @param category         The category of types to be displayed.
         * @param filteringDSObjId The data source object id to use for
         *                         filtering. If id is less than or equal to 0,
         *                         no filtering will occur.
         */
        TypeFactory(Category category, long filteringDSObjId) {
            super();
            this.filteringDSObjId = filteringDSObjId;
            this.category = category;

            PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
                String eventType = evt.getPropertyName();
                if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * This is a stop gap measure until a different way of
                     * handling the closing of cases is worked out. Currently,
                     * remote events may be received for a case that is already
                     * closed.
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

            weakPcl = WeakListeners.propertyChange(pcl, null);
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            refreshThrottler.registerForIngestModuleEvents();
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            refreshThrottler.unregisterEventListener();
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            typeNodeMap.clear();
        }

        @Override
        protected boolean createKeys(List<TypeNodeKey> list) {
            try {
                // Get all types in use
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                List<BlackboardArtifact.Type> types = (this.filteringDSObjId > 0)
                        ? skCase.getBlackboard().getArtifactTypesInUse(this.filteringDSObjId)
                        : skCase.getArtifactTypesInUse();

                List<TypeNodeKey> allKeysSorted = types.stream()
                        // filter types by category and ensure they are not in the list of ignored types
                        .filter(tp -> category.equals(tp.getCategory()) && !IGNORED_TYPES.contains(tp))
                        .map(tp -> {
                            // if typeNodeMap already contains key, update the relevant node and return the node
                            if (typeNodeMap.containsKey(tp)) {
                                TypeNodeKey typeKey = typeNodeMap.get(tp);
                                typeKey.getNode().updateDisplayName();
                                return typeKey;
                            } else {
                                // if key is not in map, create the type key and add to map
                                TypeNodeKey newTypeKey = getTypeKey(tp, skCase, filteringDSObjId);
                                for (BlackboardArtifact.Type recordType : newTypeKey.getApplicableTypes()) {
                                    typeNodeMap.put(recordType, newTypeKey);
                                }
                                return newTypeKey;
                            }
                        })
                        // ensure record is returned
                        .filter(record -> record != null)
                        // there are potentially multiple types that apply to the same node (i.e. Interesting Files / Artifacts)
                        // ensure the keys are distinct
                        .distinct()
                        // sort by display name
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

    /**
     * Abstract class for type(s) nodes. This class allows for displaying a
     * count artifacts with the type(s) associated with this node.
     */
    public static abstract class UpdatableCountTypeNode extends DisplayableItemNode {

        private static final Logger logger = Logger.getLogger(UpdatableCountTypeNode.class.getName());

        private final Set<BlackboardArtifact.Type> types;
        private final long filteringDSObjId;
        private long childCount = 0;
        private final String baseName;

        /**
         * Main constructor.
         *
         * @param children         The Children to associated with this node.
         * @param lookup           The Lookup to use with this name.
         * @param baseName         The display name. The Node.displayName will
         *                         be of format "[baseName] ([count])".
         * @param filteringDSObjId The data source object id to use for
         *                         filtering. If id is less than or equal to 0,
         *                         no filtering will occur.
         * @param types            The types associated with this type node.
         */
        public UpdatableCountTypeNode(Children children, Lookup lookup, String baseName,
                long filteringDSObjId, BlackboardArtifact.Type... types) {

            super(children, lookup);
            this.types = Stream.of(types).collect(Collectors.toSet());
            this.filteringDSObjId = filteringDSObjId;
            this.baseName = baseName;
            updateDisplayName();
        }

        /**
         * Returns the count of artifacts associated with these type(s).
         *
         * @return The count of artifacts associated with these type(s).
         */
        protected long getChildCount() {
            return this.childCount;
        }

        /**
         * Fetches the count to be displayed from the case.
         *
         * @param skCase The relevant SleuthkitCase.
         *
         * @return The count to be displayed.
         *
         * @throws TskCoreException
         */
        protected long fetchChildCount(SleuthkitCase skCase) throws TskCoreException {
            int count = 0;
            for (BlackboardArtifact.Type type : this.types) {
                if (filteringDSObjId > 0) {
                    count += skCase.getBlackboard().getArtifactsCount(type.getTypeID(), filteringDSObjId);
                } else {
                    count += skCase.getBlackboardArtifactsTypeCount(type.getTypeID());
                }
            }
            return count;
        }

        /**
         * When this method is called, the count to be displayed will be
         * updated.
         */
        void updateDisplayName() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                this.childCount = fetchChildCount(skCase);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Error fetching data when case closed.", ex);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error getting child count", ex); //NON-NLS
            }
            super.setDisplayName(this.baseName + " (" + this.childCount + ")");
        }
    }

    /**
     * Default node encapsulating a blackboard artifact type. This is used on
     * the left-hand navigation side of the Autopsy UI as the parent node for
     * all of the artifacts of a given type. Its children will be
     * BlackboardArtifactNode objects.
     */
    static class TypeNode extends UpdatableCountTypeNode {

        private final BlackboardArtifact.Type type;

        /**
         * Main constructor.
         *
         * @param type             The blackboard artifact type for this node.
         * @param filteringDSObjId The data source object id to use for
         *                         filtering. If id is less than or equal to 0,
         *                         no filtering will occur.
         */
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

        /**
         * Main constructor.
         *
         * @param type             The blackboard artifact type for this node.
         * @param filteringDSObjId The data source object id to use for
         *                         filtering. If id is less than or equal to 0,
         *                         no filtering will occur.
         */
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

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

        @Override
        protected void onAdd() {
            refreshThrottler.registerForIngestModuleEvents();
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
        }

        @Override
        protected void onRemove() {
            if (refreshThrottler != null) {
                refreshThrottler.unregisterEventListener();
            }
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact key) {
            return new BlackboardArtifactNode(key);
        }

        @Override
        protected List<BlackboardArtifact> makeKeys() {
            try {
                List<? extends BlackboardArtifact> arts;
                Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
                switch (this.type.getCategory()) {

                    case ANALYSIS_RESULT:
                        arts = (filteringDSObjId > 0)
                                ? blackboard.getAnalysisResultsByType(type.getTypeID(), filteringDSObjId)
                                : blackboard.getAnalysisResultsByType(type.getTypeID());
                        break;
                    case DATA_ARTIFACT:
                    default:
                        arts = (filteringDSObjId > 0)
                                ? blackboard.getDataArtifacts(type.getTypeID(), filteringDSObjId)
                                : blackboard.getDataArtifacts(type.getTypeID());
                        break;
                }

                for (BlackboardArtifact art : arts) {
                    //Cache attributes while we are off the EDT.
                    //See JIRA-5969
                    art.getAttributes();
                }

                @SuppressWarnings("unchecked")
                List<BlackboardArtifact> toRet = (List<BlackboardArtifact>) (List<?>) arts;
                return toRet;
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
