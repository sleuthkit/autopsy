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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.Score;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import static org.sleuthkit.autopsy.datamodel.DisplayableItemNode.findLinked;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Node wrapping a blackboard artifact object. This is generated from several
 * places in the tree.
 */
public class BlackboardArtifactNode extends AbstractContentNode<BlackboardArtifact> {

    private static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());
    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED,
            Case.Events.CURRENT_CASE,
            Case.Events.CR_COMMENT_CHANGED);

    private static Cache<Long, Content> contentCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES).
            build();

    private final BlackboardArtifact artifact;
    private Content associated = null;

    private List<NodeProperty<? extends Object>> customProperties;

    private final static String NO_DESCR = NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.noDesc.text");


    /*
     * Artifact types which should have the full unique path of the associated
     * content as a property.
     */
    private static final Integer[] SHOW_UNIQUE_PATH = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),};

    // TODO (RC): This is an unattractive alternative to subclassing BlackboardArtifactNode,
    // cut from the same cloth as the equally unattractive SHOW_UNIQUE_PATH array
    // above. It should be removed when and if the subclassing is implemented.
    private static final Integer[] SHOW_FILE_METADATA = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),};

    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString())) {
                BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
                if (event.getAddedTag().getArtifact().equals(artifact)) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())) {
                BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
                if (event.getDeletedTagInfo().getArtifactID() == artifact.getArtifactID()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
                ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
                if (event.getAddedTag().getContent().equals(associated)) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
                if (event.getDeletedTagInfo().getContentID() == associated.getId()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
                CommentChangedEvent event = (CommentChangedEvent) evt;
                if (event.getContentID() == associated.getId()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                if (evt.getNewValue() == null) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    removeListeners();
                    contentCache.invalidateAll();
                }
            }
        }
    };

    /**
     * We pass a weak reference wrapper around the listener to the event
     * publisher. This allows Netbeans to delete the node when the user
     * navigates to another part of the tree (previously, nodes were not being
     * deleted because the event publisher was holding onto a strong reference
     * to the listener. We need to hold onto the weak reference here to support
     * unregistering of the listener in removeListeners() below.
     */
    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

    /**
     * Construct blackboard artifact node from an artifact, overriding the
     * standard icon with the one at the path provided.
     *
     *
     * @param artifact artifact to encapsulate
     * @param iconPath icon to use for the artifact
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(artifact, createLookup(artifact));

        this.artifact = artifact;

        // Look for associated Content  i.e. the source file for the artifact
        for (Content lookupContent : this.getLookup().lookupAll(Content.class)) {
            if ((lookupContent != null) && (!(lookupContent instanceof BlackboardArtifact))) {
                this.associated = lookupContent;
                break;
            }
        }

        this.setName(Long.toString(artifact.getArtifactID()));
        this.setDisplayName();
        this.setIconBaseWithExtension(iconPath);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
    }

    /**
     * Construct blackboard artifact node from an artifact and using default
     * icon for artifact type
     *
     * @param artifact artifact to encapsulate
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        this(artifact, ExtractedContent.getIconFilePath(artifact.getArtifactTypeID()));
    }

    /**
     * The finalizer removes event listeners as the BlackboardArtifactNode is
     * being garbage collected. Yes, we know that finalizers are considered to
     * be "bad" but since the alternative also relies on garbage collection
     * being run and we know that finalize will be called when the object is
     * being GC'd it seems like this is a reasonable solution.
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        removeListeners();
    }

    private void removeListeners() {
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
    }

    public BlackboardArtifact getArtifact() {
        return this.artifact;
    }

    @Override
    @NbBundle.Messages({
        "BlackboardArtifactNode.getAction.errorTitle=Error getting actions",
        "BlackboardArtifactNode.getAction.resultErrorMessage=There was a problem getting actions for the selected result."
        + "  The 'View Result in Timeline' action will not be available.",
        "BlackboardArtifactNode.getAction.linkedFileMessage=There was a problem getting actions for the selected result. "
        + " The 'View File in Timeline' action will not be available."})
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(context)));
        AbstractFile file = getLookup().lookup(AbstractFile.class);

        //if this artifact has a time stamp add the action to view it in the timeline
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                actionsList.add(new ViewArtifactInTimelineAction(artifact));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting arttribute(s) from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.BlackboardArtifactNode_getAction_errorTitle(), Bundle.BlackboardArtifactNode_getAction_resultErrorMessage());
        }

        // if the artifact links to another file, add an action to go to that file
        try {
            AbstractFile c = findLinked(artifact);
            if (c != null) {
                actionsList.add(ViewFileInTimelineAction.createViewFileAction(c));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting linked file from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.BlackboardArtifactNode_getAction_errorTitle(), Bundle.BlackboardArtifactNode_getAction_linkedFileMessage());
        }

        //if the artifact has associated content, add the action to view the content in the timeline
        if (null != file) {
            actionsList.add(ViewFileInTimelineAction.createViewSourceFileAction(file));
        }

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @NbBundle.Messages({"# {0} - artifactDisplayName", "BlackboardArtifactNode.displayName.artifact={0} Artifact"})
    /**
     * Set the filter node display name. The value will either be the file name
     * or something along the lines of e.g. "Messages Artifact" for keyword hits
     * on artifacts.
     */
    private void setDisplayName() {
        String displayName = ""; //NON-NLS

        // If this is a node for a keyword hit on an artifact, we set the
        // display name to be the artifact type name followed by " Artifact"
        // e.g. "Messages Artifact".
        if (artifact != null
                && (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
                || artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID())) {
            try {
                for (BlackboardAttribute attribute : artifact.getAttributes()) {
                    if (attribute.getAttributeType().getTypeID() == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                        BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                        if (associatedArtifact != null) {
                            if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
                                artifact.getDisplayName();
                            } else {
                                displayName = NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.displayName.artifact", associatedArtifact.getDisplayName());
                            }
                        }
                    }
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                // Do nothing since the display name will be set to the file name.
            }
        }

        if (displayName.isEmpty() && artifact != null) {
            displayName = artifact.getName();
        }

        this.setDisplayName(displayName);

    }

    /**
     * Return the name of the associated source file/content
     *
     * @return source file/content name
     */
    public String getSourceName() {

        String srcName = "";
        if (associated != null) {
            srcName = associated.getName();
        }
        return srcName;
    }
    
    @NbBundle.Messages({
        "BlackboardArtifactNode.createSheet.artifactType.displayName=Artifact Type",
        "BlackboardArtifactNode.createSheet.artifactType.name=Artifact Type",
        "BlackboardArtifactNode.createSheet.artifactDetails.displayName=Artifact Details",
        "BlackboardArtifactNode.createSheet.artifactDetails.name=Artifact Details",
        "BlackboardArtifactNode.artifact.displayName=Artifact",
        "BlackboardArtifactNode.createSheet.artifactMD5.displayName=MD5 Hash",
        "BlackboardArtifactNode.createSheet.artifactMD5.name=MD5 Hash",
        "BlackboardArtifactNode.createSheet.fileSize.name=Size",
        "BlackboardArtifactNode.createSheet.fileSize.displayName=Size",
        "BlackboardArtifactNode.createSheet.path.displayName=Path",
        "BlackboardArtifactNode.createSheet.path.name=Path"})

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        List<Tag> tags = getAllTagsFromDatabase();

        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, artifact);

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.srcFile.name"),
                NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.srcFile.displayName"),
                NO_DESCR,
                this.getSourceName()));

        addScoreProperty(sheetSet, tags);

        CorrelationAttributeInstance correlationAttribute = null;
        if (EamDbUtil.useCentralRepo() && UserPreferences.hideCentralRepoCommentsAndOccurrences() == false) {
            correlationAttribute = getCorrelationAttributeInstance();
        }
        addCommentProperty(sheetSet, tags, correlationAttribute);

        if (EamDbUtil.useCentralRepo() && UserPreferences.hideCentralRepoCommentsAndOccurrences() == false) {
            addCountProperty(sheetSet, correlationAttribute);
        }
        if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
            try {
                BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactType.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactType.displayName"),
                            NO_DESCR,
                            associatedArtifact.getDisplayName() + " " + NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.artifact.displayName")));
                    sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactDetails.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactDetails.displayName"),
                            NO_DESCR,
                            associatedArtifact.getShortDescription()));
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                // Do nothing since the display name will be set to the file name.
            }
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sheetSet.put(new NodeProperty<>(entry.getKey(),
                    entry.getKey(),
                    NO_DESCR,
                    entry.getValue()));
        }

        //append custom node properties
        if (customProperties != null) {
            for (NodeProperty<? extends Object> np : customProperties) {
                sheetSet.put(np);
            }
        }

        final int artifactTypeId = artifact.getArtifactTypeID();

        // If mismatch, add props for extension and file type
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()) {
            String ext = ""; //NON-NLS
            String actualMimeType = ""; //NON-NLS
            if (associated instanceof AbstractFile) {
                AbstractFile af = (AbstractFile) associated;
                ext = af.getNameExtension();
                actualMimeType = af.getMIMEType();
                if (actualMimeType == null) {
                    actualMimeType = ""; //NON-NLS
                }
            }
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.ext.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.ext.displayName"),
                    NO_DESCR,
                    ext));
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.mimeType.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.mimeType.displayName"),
                    NO_DESCR,
                    actualMimeType));
        }

        if (Arrays.asList(SHOW_UNIQUE_PATH).contains(artifactTypeId)) {
            String sourcePath = ""; //NON-NLS
            try {
                sourcePath = associated.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get unique path from: {0}", associated.getName()); //NON-NLS
            }

            if (sourcePath.isEmpty() == false) {
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.filePath.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.filePath.displayName"),
                        NO_DESCR,
                        sourcePath));
            }

            if (Arrays.asList(SHOW_FILE_METADATA).contains(artifactTypeId)) {
                AbstractFile file = associated instanceof AbstractFile ? (AbstractFile) associated : null;
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileModifiedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getMtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileChangedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileChangedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getCtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileAccessedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getAtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileCreatedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getCrtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileSize.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileSize.displayName"),
                        "",
                        associated.getSize()));
                sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_artifactMD5_name(),
                        Bundle.BlackboardArtifactNode_createSheet_artifactMD5_displayName(),
                        "",
                        file == null ? "" : StringUtils.defaultString(file.getMd5Hash())));
            }
        } else {
            String dataSourceStr = "";
            try {
                Content dataSource = associated.getDataSource();
                if (dataSource != null) {
                    dataSourceStr = dataSource.getName();
                } else {
                    dataSourceStr = getRootParentName();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get image name from {0}", associated.getName()); //NON-NLS
            }

            if (dataSourceStr.isEmpty() == false) {
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.dataSrc.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.dataSrc.displayName"),
                        NO_DESCR,
                        dataSourceStr));
            }
        }

        // If EXIF, add props for file size and path
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {

            long size = 0;
            String path = ""; //NON-NLS
            if (associated instanceof AbstractFile) {
                AbstractFile af = (AbstractFile) associated;
                size = af.getSize();
                try {
                    path = af.getUniquePath();
                } catch (TskCoreException ex) {
                    path = af.getParentPath();
                }
            }
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.fileSize.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.fileSize.displayName"),
                    NO_DESCR,
                    size));
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.path.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.path.displayName"),
                    NO_DESCR,
                    path));
        }

        return sheet;
    }

    /**
     * Get all tags from the case database relating to the artifact and the file
     * it is associated with.
     *
     * @return a list of tags which on the artifact or the file it is associated
     *         with
     */
    protected final List<Tag> getAllTagsFromDatabase() {
        List<Tag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(associated));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifact.getDisplayName(), ex);
        }
        return tags;
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the tags property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     */
    @NbBundle.Messages({
        "BlackboardArtifactNode.createSheet.tags.displayName=Tags"})
    @Deprecated
    protected void addTagProperty(Sheet.Set sheetSet) throws MissingResourceException {
        // add properties for tags
        List<Tag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(associated));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifact.getDisplayName(), ex);
        }
        sheetSet.put(new NodeProperty<>("Tags", Bundle.BlackboardArtifactNode_createSheet_tags_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName()).collect(Collectors.joining(", "))));
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the tags property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     * @param tags     the list of tags which should appear as the value for the
     *                 property
     */
    @Deprecated
    protected final void addTagProperty(Sheet.Set sheetSet, List<Tag> tags) {
        sheetSet.put(new NodeProperty<>("Tags", Bundle.BlackboardArtifactNode_createSheet_tags_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName()).collect(Collectors.joining(", "))));
    }

    protected final CorrelationAttributeInstance getCorrelationAttributeInstance() {
        CorrelationAttributeInstance correlationAttribute = null;
        if (EamDbUtil.useCentralRepo()) {
            correlationAttribute = EamArtifactUtil.getInstanceFromContent(associated);
        }
        return correlationAttribute;
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the comment
     * property to their sheets.
     *
     * @param sheetSet  the modifiable Sheet.Set returned by
     *                  Sheet.get(Sheet.PROPERTIES)
     * @param tags      the list of tags associated with the file
     * @param attribute the correlation attribute associated with this
     *                  artifact's associated file, null if central repo is not
     *                  enabled
     */
    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.comment.name=C",
        "BlackboardArtifactNode.createSheet.comment.displayName=C"})
    protected final void addCommentProperty(Sheet.Set sheetSet, List<Tag> tags, CorrelationAttributeInstance attribute) {
        HasCommentStatus status = tags.size() > 0 ? HasCommentStatus.TAG_NO_COMMENT : HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        //currently checks for a comment on the associated file in the central repo not the artifact itself 
        //what we want the column property to reflect should be revisted when we have added a way to comment
        //on the artifact itself
        if (attribute != null && !StringUtils.isBlank(attribute.getComment())) {
            if (status == HasCommentStatus.TAG_COMMENT) {
                status = HasCommentStatus.CR_AND_TAG_COMMENTS;
            } else {
                status = HasCommentStatus.CR_COMMENT;
            }
        }
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_comment_name(), Bundle.BlackboardArtifactNode_createSheet_comment_displayName(), NO_DESCR,
                status));
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the Score property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     * @param tags     the list of tags associated with the file
     */
    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.score.name=S",
        "BlackboardArtifactNode.createSheet.score.displayName=S",
        "BlackboardArtifactNode.createSheet.notableFile.description=Associated file recognized as notable.",
        "BlackboardArtifactNode.createSheet.interestingResult.description=Result has an interesting result associated with it.",
        "BlackboardArtifactNode.createSheet.taggedItem.description=Result or associated file has been tagged.",
        "BlackboardArtifactNode.createSheet.notableTaggedItem.description=Result or associated file tagged with notable tag.",
        "BlackboardArtifactNode.createSheet.noScore.description=No score"})
    protected final void addScoreProperty(Sheet.Set sheetSet, List<Tag> tags) {
        Score score = Score.NO_SCORE;
        String description = Bundle.BlackboardArtifactNode_createSheet_noScore_description();
        if (associated instanceof AbstractFile) {
            if (((AbstractFile) associated).getKnown() == TskData.FileKnown.BAD) {
                score = Score.NOTABLE_SCORE;
                description = Bundle.BlackboardArtifactNode_createSheet_notableFile_description();
            }
        }
        //if the artifact being viewed is a hashhit check if the hashset is notable 
        if ((score == Score.NO_SCORE || score == Score.INTERESTING_SCORE) && content.getArtifactTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
            try {
                BlackboardAttribute attr = content.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME));
                List<HashDbManager.HashDb> notableHashsets = HashDbManager.getInstance().getKnownBadFileHashSets();
                for (HashDbManager.HashDb hashDb : notableHashsets) {
                    if (hashDb.getHashSetName().equals(attr.getValueString())) {
                        score = Score.NOTABLE_SCORE;
                        description = Bundle.BlackboardArtifactNode_createSheet_notableFile_description();
                        break;
                    }
                }
            } catch (TskCoreException ex) {
                //unable to get the attribute so we can not update the status based on the attribute
                logger.log(Level.WARNING, "Unable to get TSK_SET_NAME attribute for artifact of type TSK_HASHSET_HIT with artifact ID " + content.getArtifactID(), ex);
            }
        }
        try {
            if (score == Score.NO_SCORE && !content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT).isEmpty()) {
                score = Score.INTERESTING_SCORE;
                description = Bundle.BlackboardArtifactNode_createSheet_interestingResult_description();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting artifacts for artifact: " + content.getName(), ex);
        }
        if (tags.size() > 0 && (score == Score.NO_SCORE || score == Score.INTERESTING_SCORE)) {
            score = Score.INTERESTING_SCORE;
            description = Bundle.BlackboardArtifactNode_createSheet_taggedItem_description();
            for (Tag tag : tags) {
                if (tag.getName().getKnownStatus() == TskData.FileKnown.BAD) {
                    score = Score.NOTABLE_SCORE;
                    description = Bundle.BlackboardArtifactNode_createSheet_notableTaggedItem_description();
                    break;
                }
            }
        }
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_score_name(), Bundle.BlackboardArtifactNode_createSheet_score_displayName(), description, score));
    }

    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.count.name=O",
        "BlackboardArtifactNode.createSheet.count.displayName=O",
        "BlackboardArtifactNode.createSheet.count.noCentralRepo.description=Central repository was not enabled when this column was populated",
        "BlackboardArtifactNode.createSheet.count.hashLookupNotRun.description=Hash lookup had not been run on this artifact's associated file when the column was populated",
        "# {0} - occuranceCount",
        "BlackboardArtifactNode.createSheet.count.description=There were {0} datasource(s) found with occurances of the correlation value"})

    protected final void addCountProperty(Sheet.Set sheetSet, CorrelationAttributeInstance attribute) {
        Long count = -1L;  //The column renderer will not display negative values, negative value used when count unavailble to preserve sorting
        String description = Bundle.BlackboardArtifactNode_createSheet_count_noCentralRepo_description();
        try {
            //don't perform the query if there is no correlation value
            if (attribute != null && StringUtils.isNotBlank(attribute.getCorrelationValue())) {
                count = EamDb.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(attribute.getCorrelationType(), attribute.getCorrelationValue());
                description = Bundle.BlackboardArtifactNode_createSheet_count_description(count);
            } else if (attribute != null) {
                description = Bundle.BlackboardArtifactNode_createSheet_count_hashLookupNotRun_description();
            }
        } catch (EamDbException ex) {
            logger.log(Level.WARNING, "Error getting count of datasources with correlation attribute", ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, "Unable to normalize data to get count of datasources with correlation attribute", ex);
        }
        sheetSet.put(
                new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_count_name(), Bundle.BlackboardArtifactNode_createSheet_count_displayName(), description, count));
    }

    private void updateSheet() {
        this.setSheet(createSheet());
    }

    private String getRootParentName() {
        String parentName = associated.getName();
        Content parent = associated;
        try {
            while ((parent = parent.getParent()) != null) {
                parentName = parent.getName();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get parent name from {0}", associated.getName()); //NON-NLS
            return "";
        }
        return parentName;
    }

    /**
     * Add an additional custom node property to that node before it is
     * displayed
     *
     * @param np NodeProperty to add
     */
    public void addNodeProperty(NodeProperty<?> np) {
        if (null == customProperties) {
            //lazy create the list
            customProperties = new ArrayList<>();
        }
        customProperties.add(np);
    }

    /**
     * Fill map with Artifact properties
     *
     * @param map      map with preserved ordering, where property names/values
     *                 are put
     * @param artifact to extract properties from
     */
    @SuppressWarnings("deprecation")
    private void fillPropertyMap(Map<String, Object> map, BlackboardArtifact artifact) {
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                final int attributeTypeID = attribute.getAttributeType().getTypeID();
                //skip some internal attributes that user shouldn't see
                if (attributeTypeID == ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID()) {
                } else if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
                    addEmailMsgProperty(map, attribute);
                } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                    map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), associated));
                } else if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID()
                        && attributeTypeID == ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()) {
                    /*
                     * This was added because the RegRipper output would often
                     * cause the UI to get a black line accross it and hang if
                     * you hovered over large output or selected it. This
                     * reduces the amount of data in the table. Could consider
                     * doing this for all fields in the UI.
                     */
                    String value = attribute.getDisplayString();
                    if (value.length() > 512) {
                        value = value.substring(0, 512);
                    }
                    map.put(attribute.getAttributeType().getDisplayName(), value);
                } else {
                    map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString());
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Getting attributes failed", ex); //NON-NLS
        }
    }

    /**
     * Fill map with EmailMsg properties, not all attributes are filled
     *
     * @param map       map with preserved ordering, where property names/values
     *                  are put
     * @param attribute attribute to check/fill as property
     */
    private void addEmailMsgProperty(Map<String, Object> map, BlackboardAttribute attribute) {

        final int attributeTypeID = attribute.getAttributeType().getTypeID();

        // Skip certain Email msg attributes
        if (attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_HEADERS.getTypeID()) {

            // do nothing
        } else if (attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID()) {

            String value = attribute.getDisplayString();
            if (value.length() > 160) {
                value = value.substring(0, 160) + "...";
            }
            map.put(attribute.getAttributeType().getDisplayName(), value);
        } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), associated));
        } else {
            map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString());
        }

    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Create a Lookup based on what is in the passed in artifact.
     *
     * @param artifact The artifact to make a look up for.
     *
     * @return A lookup with the artifact and possibly any associated content in
     *         it.
     */
    private static Lookup createLookup(BlackboardArtifact artifact) {
        // Add the content the artifact is associated with
        final long objectID = artifact.getObjectID();
        try {
            Content content = contentCache.get(objectID, () -> artifact.getSleuthkitCase().getContentById(objectID));
            if (content == null) {
                return Lookups.fixed(artifact);
            } else {
                return Lookups.fixed(artifact, content);
            }
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "Getting associated content for artifact failed", ex); //NON-NLS
            return Lookups.fixed(artifact);
        }
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
