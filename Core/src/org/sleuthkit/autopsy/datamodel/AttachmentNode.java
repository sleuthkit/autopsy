/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.contentviewers.MessageContentViewer;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.FileNode.getIconForFileType;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.blackboardutils.Attachment;
import org.sleuthkit.datamodel.blackboardutils.FileAttachment;
import org.sleuthkit.datamodel.blackboardutils.URLAttachment;

/**
 * Node for a message attachment.
 *
 */
public final class AttachmentNode extends DisplayableItemNode {

    private static final Logger LOGGER = Logger.getLogger(MessageContentViewer.class.getName());
     
    private final Attachment attachment;
    private final AbstractFile attachmentFile;

    public AttachmentNode(Attachment attachment) {

        super(Children.LEAF, createLookup(attachment));

        super.setName(attachment.getLocation());
        super.setDisplayName(attachment.getLocation());  // SET NODE DISPLAY NAME, I.E., TEXT IN FIRST TABLE CELL 

        this.attachment = attachment;
        Long attachmentObjId = attachment.getObjId();
        AbstractFile attchmentAbstractFile = null;

        if (attachmentObjId != null && attachmentObjId > 0) {
            try {
                attchmentAbstractFile = Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(attachmentObjId);
            } catch (TskException | NoCurrentCaseException ex) {
               LOGGER.log(Level.WARNING, "Error loading attachment file with object id " + attachmentObjId, ex); //NON-NLS
            }
        } 
        attachmentFile = attchmentAbstractFile;
        
        // set the icon for node
        setIcon();
    }

    @Override
    @NbBundle.Messages({
        "AttachmentNode.getActions.viewFileInDir.text=View File in Directory",
        "AttachmentNode.getActions.viewInNewWin.text=View in New Window",
        "AttachmentNode.getActions.openInExtViewer.text=Open in External Viewer  Ctrl+E",
        "AttachmentNode.getActions.searchFilesSameMD5.text=Search for files with the same MD5 hash"})
    public Action[] getActions(boolean context) {
    
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        
        // If there is an attachment file
        if (this.attachmentFile != null) {
            actionsList.add(new ViewContextAction(Bundle.AttachmentNode_getActions_viewFileInDir_text(), this.attachmentFile));
            actionsList.add(null); // Creates an item separator
        
            actionsList.add(new NewWindowViewAction(Bundle.AttachmentNode_getActions_viewInNewWin_text(), this));
            final Collection<AbstractFile> selectedFilesList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
            if (selectedFilesList.size() == 1) {
                actionsList.add(new ExternalViewerAction(
                        Bundle.AttachmentNode_getActions_openInExtViewer_text(), this));
            } else {
                actionsList.add(ExternalViewerShortcutAction.getInstance());
            }
            actionsList.add(ViewFileInTimelineAction.createViewFileAction(this.attachmentFile));
            actionsList.add(null); // Creates an item separator

            actionsList.add(ExtractAction.getInstance());
            actionsList.add(ExportCSVAction.getInstance());
            actionsList.add(null); // Creates an item separator

            actionsList.add(AddContentTagAction.getInstance());
            if (1 == selectedFilesList.size()) {
                actionsList.add(DeleteFileContentTagAction.getInstance());
            }
            actionsList.addAll(ContextMenuExtensionPoint.getActions());
            
        }
        return actionsList.toArray(new Action[0]);
    }
    
    @Override
    protected Sheet createSheet() {

        // Create a new property sheet.
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = Sheet.createPropertiesSet();
        sheet.put(sheetSet);

        sheetSet.put(new NodeProperty<>("Location", "Location", "", this.attachment.getLocation()));

        if (attachmentFile != null) {
            long size = attachmentFile.getSize();
            String mimeType = attachmentFile.getMIMEType();

            // @TODO Vik-5762: get SCO Columns
           
            sheetSet.put(new NodeProperty<>("Size", "Size", "", size));
            if (StringUtils.isNotEmpty(mimeType)) {
                sheetSet.put(new NodeProperty<>("Mime type", "Mime type", "", mimeType));
            }
            sheetSet.put(new NodeProperty<>("Known", "Known", "", attachmentFile.getKnown().getName()));
        }

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
        return getClass().getName();
    }

    private static Lookup createLookup(Attachment attachment) {
        Long attachmentObjId = attachment.getObjId();
        if (attachmentObjId != null && attachmentObjId > 0) {
            AbstractFile attachmentFile = null;
            try {
                attachmentFile = Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(attachmentObjId);
                if (attachmentFile != null) {
                    return Lookups.fixed(attachment, attachmentFile);
                } else {
                    return Lookups.fixed(attachment);
                }
            } catch (TskException | NoCurrentCaseException ex) {
                return Lookups.fixed(attachment);
            }
        }
        return Lookups.fixed(attachment);
    }

    /**
     * Set the icon based on attachment type
     */
    private void setIcon() {
        if (attachmentFile != null) {
            this.setIconBaseWithExtension(getIconForFileType(attachmentFile));
        } else if (attachment instanceof FileAttachment) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/document-question-16.png");
        } else if (attachment instanceof URLAttachment) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/url-16.png");
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
        }

    }
}
