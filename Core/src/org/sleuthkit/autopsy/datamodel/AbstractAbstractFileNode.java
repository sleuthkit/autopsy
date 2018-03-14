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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType.*;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract node that encapsulates AbstractFile data
 *
 * @param <T> type of the AbstractFile to encapsulate
 */
public abstract class AbstractAbstractFileNode<T extends AbstractFile> extends AbstractContentNode<T> {

    private static final Logger logger = Logger.getLogger(AbstractAbstractFileNode.class.getName());
    @NbBundle.Messages("AbstractAbstractFileNode.addFileProperty.desc=no description")
    private static final String NO_DESCR = AbstractAbstractFileNode_addFileProperty_desc();

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.CURRENT_CASE,
            Case.Events.CONTENT_TAG_ADDED, Case.Events.CONTENT_TAG_DELETED);

    /**
     * @param abstractFile file to wrap
     */
    AbstractAbstractFileNode(T abstractFile) {
        super(abstractFile);
        String ext = abstractFile.getNameExtension();
        if (StringUtils.isNotBlank(ext)) {
            ext = "." + ext;
            // If this is an archive file we will listen for ingest events
            // that will notify us when new content has been identified.
            if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
                IngestManager.getInstance().addIngestModuleEventListener(weakPcl);
            }
        }
        // Listen for case events so that we can detect when the case is closed
        // or when tags are added.
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
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
        IngestManager.getInstance().removeIngestModuleEventListener(weakPcl);
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
    }

    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        String eventType = evt.getPropertyName();

        // Is this a content changed event?
        if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
            if ((evt.getOldValue() instanceof ModuleContentEvent) == false) {
                return;
            }
            ModuleContentEvent moduleContentEvent = (ModuleContentEvent) evt.getOldValue();
            if ((moduleContentEvent.getSource() instanceof Content) == false) {
                return;
            }
            Content newContent = (Content) moduleContentEvent.getSource();

            // Does the event indicate that content has been added to *this* file?
            if (getContent().getId() == newContent.getId()) {
                // If so, refresh our children.
                try {
                    Children parentsChildren = getParentNode().getChildren();
                    // We only want to refresh our parents children if we are in the
                    // data sources branch of the tree. The parent nodes in other
                    // branches of the tree (e.g. File Types and Deleted Files) do
                    // not need to be refreshed.
                    if (parentsChildren instanceof ContentChildren) {
                        ((ContentChildren) parentsChildren).refreshChildren();
                        parentsChildren.getNodesCount();
                    }
                } catch (NullPointerException ex) {
                    // Skip
                }
            }
        } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
            if (evt.getNewValue() == null) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                removeListeners();
            }
        } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
            ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
            if (event.getAddedTag().getContent().equals(content)) {
                updateSheet();
            }
        } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            if (event.getDeletedTagInfo().getContentID() == content.getId()) {
                updateSheet();
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

    private void updateSheet() {
        this.setSheet(createSheet());
    }

    @NbBundle.Messages({"AbstractAbstractFileNode.nameColLbl=Name",
        "AbstractAbstractFileNode.locationColLbl=Location",
        "AbstractAbstractFileNode.modifiedTimeColLbl=Modified Time",
        "AbstractAbstractFileNode.changeTimeColLbl=Change Time",
        "AbstractAbstractFileNode.accessTimeColLbl=Access Time",
        "AbstractAbstractFileNode.createdTimeColLbl=Created Time",
        "AbstractAbstractFileNode.sizeColLbl=Size",
        "AbstractAbstractFileNode.flagsDirColLbl=Flags(Dir)",
        "AbstractAbstractFileNode.flagsMetaColLbl=Flags(Meta)",
        "AbstractAbstractFileNode.modeColLbl=Mode",
        "AbstractAbstractFileNode.useridColLbl=UserID",
        "AbstractAbstractFileNode.groupidColLbl=GroupID",
        "AbstractAbstractFileNode.metaAddrColLbl=Meta Addr.",
        "AbstractAbstractFileNode.attrAddrColLbl=Attr. Addr.",
        "AbstractAbstractFileNode.typeDirColLbl=Type(Dir)",
        "AbstractAbstractFileNode.typeMetaColLbl=Type(Meta)",
        "AbstractAbstractFileNode.knownColLbl=Known",
        "AbstractAbstractFileNode.inHashsetsColLbl=In Hashsets",
        "AbstractAbstractFileNode.md5HashColLbl=MD5 Hash",
        "AbstractAbstractFileNode.objectId=Object ID",
        "AbstractAbstractFileNode.mimeType=MIME Type",
        "AbstractAbstractFileNode.extensionColLbl=Extension"})
    public enum AbstractFilePropertyType {

        NAME(AbstractAbstractFileNode_nameColLbl()),
        LOCATION(AbstractAbstractFileNode_locationColLbl()),
        MOD_TIME(AbstractAbstractFileNode_modifiedTimeColLbl()),
        CHANGED_TIME(AbstractAbstractFileNode_changeTimeColLbl()),
        ACCESS_TIME(AbstractAbstractFileNode_accessTimeColLbl()),
        CREATED_TIME(AbstractAbstractFileNode_createdTimeColLbl()),
        SIZE(AbstractAbstractFileNode_sizeColLbl()),
        FLAGS_DIR(AbstractAbstractFileNode_flagsDirColLbl()),
        FLAGS_META(AbstractAbstractFileNode_flagsMetaColLbl()),
        MODE(AbstractAbstractFileNode_modeColLbl()),
        USER_ID(AbstractAbstractFileNode_useridColLbl()),
        GROUP_ID(AbstractAbstractFileNode_groupidColLbl()),
        META_ADDR(AbstractAbstractFileNode_metaAddrColLbl()),
        ATTR_ADDR(AbstractAbstractFileNode_attrAddrColLbl()),
        TYPE_DIR(AbstractAbstractFileNode_typeDirColLbl()),
        TYPE_META(AbstractAbstractFileNode_typeMetaColLbl()),
        KNOWN(AbstractAbstractFileNode_knownColLbl()),
        HASHSETS(AbstractAbstractFileNode_inHashsetsColLbl()),
        MD5HASH(AbstractAbstractFileNode_md5HashColLbl()),
        ObjectID(AbstractAbstractFileNode_objectId()),
        MIMETYPE(AbstractAbstractFileNode_mimeType()),
        EXTENSION(AbstractAbstractFileNode_extensionColLbl());

        final private String displayString;

        private AbstractFilePropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map     map with preserved ordering, where property names/values
     *                are put
     * @param content The content to get properties for.
     */
    static public void fillPropertyMap(Map<String, Object> map, AbstractFile content) {
        map.put(NAME.toString(), getContentDisplayName(content));
        map.put(LOCATION.toString(), getContentPath(content));
        map.put(MOD_TIME.toString(), ContentUtils.getStringTime(content.getMtime(), content));
        map.put(CHANGED_TIME.toString(), ContentUtils.getStringTime(content.getCtime(), content));
        map.put(ACCESS_TIME.toString(), ContentUtils.getStringTime(content.getAtime(), content));
        map.put(CREATED_TIME.toString(), ContentUtils.getStringTime(content.getCrtime(), content));
        map.put(SIZE.toString(), content.getSize());
        map.put(FLAGS_DIR.toString(), content.getDirFlagAsString());
        map.put(FLAGS_META.toString(), content.getMetaFlagsAsString());
        map.put(MODE.toString(), content.getModesAsString());
        map.put(USER_ID.toString(), content.getUid());
        map.put(GROUP_ID.toString(), content.getGid());
        map.put(META_ADDR.toString(), content.getMetaAddr());
        map.put(ATTR_ADDR.toString(), content.getAttrType().getValue() + "-" + content.getAttributeId());
        map.put(TYPE_DIR.toString(), content.getDirType().getLabel());
        map.put(TYPE_META.toString(), content.getMetaType().toString());
        map.put(KNOWN.toString(), content.getKnown().getName());
        map.put(HASHSETS.toString(), getHashSetHitsForFile(content));
        map.put(MD5HASH.toString(), StringUtils.defaultString(content.getMd5Hash()));
        map.put(ObjectID.toString(), content.getId());
        map.put(MIMETYPE.toString(), StringUtils.defaultString(content.getMIMEType()));
        map.put(EXTENSION.toString(), content.getNameExtension());
    }

    /**
     * Used by subclasses of AbstractAbstractFileNode to add the tags property
     * to their sheets.
     *
     * @param ss the modifiable Sheet.Set returned by
     *           Sheet.get(Sheet.PROPERTIES)
     */
    @NbBundle.Messages("AbstractAbstractFileNode.tagsProperty.displayName=Tags")
    protected void addTagProperty(Sheet.Set ss) {
        List<ContentTag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getOpenCase().getServices().getTagsManager().getContentTagsByContent(content));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for content " + content.getName(), ex);
        }
        ss.put(new NodeProperty<>("Tags", AbstractAbstractFileNode_tagsProperty_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName())
                        .distinct()
                        .collect(Collectors.joining(", "))));
    }

    private static String getContentPath(AbstractFile file) {
        try {
            return file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + file, ex); //NON-NLS
            return "";            //NON-NLS
        }
    }

    static String getContentDisplayName(AbstractFile file) {
        String name = file.getName();
        switch (name) {
            case "..":
                return DirectoryNode.DOTDOTDIR;

            case ".":
                return DirectoryNode.DOTDIR;
            default:
                return name;
        }
    }

    private static String getHashSetHitsForFile(AbstractFile file) {
        try {
            return StringUtils.join(file.getHashSetNames(), ", ");
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.WARNING, "Error getting hashset hits: ", tskCoreException); //NON-NLS
            return "";
        }
    }
}
