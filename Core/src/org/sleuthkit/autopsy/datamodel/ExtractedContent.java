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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;

/**
 * Parent of the "extracted content" artifacts to be displayed in the tree.
 * Other artifacts are displayed under other more specific parents.
 */
public class ExtractedContent implements AutopsyVisitableItem {

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    public static final String NAME = NbBundle.getMessage(RootNode.class, "ExtractedContentNode.name.text");
    private final long filteringDSObjId; // 0 if not filtering/grouping by data source
    private SleuthkitCase skCase;   // set to null after case has been closed
    private Blackboard blackboard;

    /**
     * Constructs extracted content object
     *
     * @param skCase Case DB
     */
    public ExtractedContent(SleuthkitCase skCase) {
        this(skCase, 0);
    }

    /**
     * Constructs extracted content object
     *
     * @param skCase Case DB
     * @param objId  Object id of the parent datasource
     */
    public ExtractedContent(SleuthkitCase skCase, long objId) {
        this.skCase = skCase;
        this.filteringDSObjId = objId;
        this.blackboard = skCase.getBlackboard();
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public SleuthkitCase getSleuthkitCase() {
        return skCase;
    }

    public class RootNode extends DisplayableItemNode {

        public RootNode(SleuthkitCase skCase) {
            super(Children.create(new TypeFactory(), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/extracted_content.png"); //NON-NLS
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
                    NAME));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Creates the children for the ExtractedContent area of the results tree.
     * This area has all of the blackboard artifacts that are not displayed in a
     * more specific form elsewhere in the tree.
     */
    private class TypeFactory extends ChildFactory.Detachable<BlackboardArtifact.Type> implements RefreshThrottler.Refresher {

        private final ArrayList<BlackboardArtifact.Type> doNotShow = new ArrayList<>();
        // maps the artifact type to its child node 
        private final HashMap<BlackboardArtifact.Type, TypeNode> typeNodeList = new HashMap<>();

        /**
         * RefreshThrottler is used to limit the number of refreshes performed
         * when CONTENT_CHANGED and DATA_ADDED ingest module events are
         * received.
         */
        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);

        @SuppressWarnings("deprecation")
        TypeFactory() {
            super();

            // these are shown in other parts of the UI
            doNotShow.add(new BlackboardArtifact.Type(TSK_GEN_INFO));
            doNotShow.add(new BlackboardArtifact.Type(TSK_EMAIL_MSG));
            doNotShow.add(new BlackboardArtifact.Type(TSK_HASHSET_HIT));
            doNotShow.add(new BlackboardArtifact.Type(TSK_KEYWORD_HIT));
            doNotShow.add(new BlackboardArtifact.Type(TSK_INTERESTING_FILE_HIT));
            doNotShow.add(new BlackboardArtifact.Type(TSK_INTERESTING_ARTIFACT_HIT));
            doNotShow.add(new BlackboardArtifact.Type(TSK_ACCOUNT));
            doNotShow.add(new BlackboardArtifact.Type(TSK_DATA_SOURCE_USAGE));
            doNotShow.add(new BlackboardArtifact.Type(TSK_DOWNLOAD_SOURCE));
            doNotShow.add(new BlackboardArtifact.Type(TSK_TL_EVENT));

            //This is not meant to be shown in the UI at all. It is more of a meta artifact.
            doNotShow.add(new BlackboardArtifact.Type(TSK_ASSOCIATED_OBJECT));
        }

        private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                if (evt.getNewValue() == null) {
                    removeNotify();
                    skCase = null;
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
            typeNodeList.clear();
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact.Type> list) {
            if (skCase != null) {
                try {
                    List<BlackboardArtifact.Type> types = (filteringDSObjId > 0)
                            ? blackboard.getArtifactTypesInUse(filteringDSObjId)
                            : skCase.getArtifactTypesInUse();

                    types.removeAll(doNotShow);
                    Collections.sort(types,
                            new Comparator<BlackboardArtifact.Type>() {
                        @Override
                        public int compare(BlackboardArtifact.Type a, BlackboardArtifact.Type b) {
                            return a.getDisplayName().compareTo(b.getDisplayName());
                        }
                    });
                    list.addAll(types);

                    // the create node method will get called only for new types
                    // refresh the counts if we already created them from a previous update
                    for (BlackboardArtifact.Type art : types) {
                        TypeNode node = typeNodeList.get(art);
                        if (node != null) {
                            node.updateDisplayName();
                        }
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(TypeFactory.class.getName()).log(Level.SEVERE, "Error getting list of artifacts in use: " + ex.getLocalizedMessage()); //NON-NLS
                }
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact.Type key) {
            TypeNode node = new TypeNode(key);
            typeNodeList.put(key, node);
            return node;
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
                    if (null != event && !(this.doNotShow.contains(event.getBlackboardArtifactType()))) {
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
     * Node encapsulating blackboard artifact type. This is used on the
     * left-hand navigation side of the Autopsy UI as the parent node for all of
     * the artifacts of a given type. Its children will be
     * BlackboardArtifactNode objects.
     */
    public class TypeNode extends DisplayableItemNode {

        private final BlackboardArtifact.Type type;
        private long childCount = 0;

        TypeNode(BlackboardArtifact.Type type) {
            super(Children.create(new ArtifactFactory(type), true), Lookups.singleton(type.getDisplayName()));
            super.setName(type.getTypeName());
            this.type = type;
            String iconPath = IconsUtil.getIconFilePath(type.getTypeID());
            setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
            updateDisplayName();
        }

        final void updateDisplayName() {
            if (skCase == null) {
                return;
            }

            // NOTE: This completely destroys our lazy-loading ideal
            //    a performance increase might be had by adding a 
            //    "getBlackboardArtifactCount()" method to skCase
            try {
                this.childCount = (filteringDSObjId > 0)
                        ? blackboard.getArtifactsCount(type.getTypeID(), filteringDSObjId)
                        : skCase.getBlackboardArtifactsTypeCount(type.getTypeID());
            } catch (TskCoreException ex) {
                Logger.getLogger(TypeNode.class.getName())
                        .log(Level.WARNING, "Error getting child count", ex); //NON-NLS
            }
            super.setDisplayName(type.getDisplayName() + " \u200E(\u200E" + childCount + ")\u200E");
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
                    childCount));

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
    private class ArtifactFactory extends BaseChildFactory<BlackboardArtifact> implements RefreshThrottler.Refresher {

        private final BlackboardArtifact.Type type;

        /**
         * RefreshThrottler is used to limit the number of refreshes performed
         * when CONTENT_CHANGED and DATA_ADDED ingest module events are
         * received.
         */
        private final RefreshThrottler refreshThrottler = new RefreshThrottler(this);

        ArtifactFactory(BlackboardArtifact.Type type) {
            super(type.getTypeName());
            this.type = type;
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
            if (skCase != null) {
                try {
                    List<BlackboardArtifact> arts;
                    if (filteringDSObjId > 0) {
                        arts = blackboard.getArtifacts(type.getTypeID(), filteringDSObjId);
                    } else {
                        arts = skCase.getBlackboardArtifacts(type.getTypeID());
                    }
                    for (BlackboardArtifact art : arts) {
                        //Cache attributes while we are off the EDT.
                        //See JIRA-5969
                        art.getAttributes();
                    }
                    return arts;
                } catch (TskCoreException ex) {
                    Logger.getLogger(ArtifactFactory.class.getName()).log(Level.SEVERE, "Couldn't get blackboard artifacts from database", ex); //NON-NLS
                }
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
