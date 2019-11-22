/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.FileAttachment;
import org.sleuthkit.datamodel.blackboardutils.MessageAttachments;

/**
 * Factory for creating thumbnail children nodes.
 */
final class AttachmentThumbnailsChildren extends Children.Keys<AbstractFile> {

    private static final Logger LOGGER = Logger.getLogger(AttachmentThumbnailsChildren.class.getName());

    private final Set<BlackboardArtifact> artifacts;

    /*
     * Creates the list of thumbnails from the given list of
     * BlackboardArtifacts.
     *
     * The thumbnails will be initialls sorted by size, then name so that they
     * appear sorted by size by default.
     */
    AttachmentThumbnailsChildren(Set<BlackboardArtifact> artifacts) {
        super(false);

        this.artifacts = artifacts;

    }

    @Override
    protected Node[] createNodes(AbstractFile t) {
        return new Node[]{new AttachementThumbnailNode(t)};
    }

    @Override
    protected void addNotify() {
        super.addNotify();

        Set<AbstractFile> thumbnails = new TreeSet<>((AbstractFile file1, AbstractFile file2) -> {
            int result = Long.compare(file1.getSize(), file2.getSize());
            if (result == 0) {
                result = file1.getName().compareTo(file2.getName());
            }

            return result;
        });

        artifacts.forEach(new Consumer<BlackboardArtifact>() {
            @Override
            public void accept(BlackboardArtifact bba) {
                try {
                    // Get the attachments from TSK_ATTACHMENTS attribute.
                    BlackboardAttribute attachmentsAttr = bba.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS));
                    if (attachmentsAttr != null) {

                        String jsonVal = attachmentsAttr.getValueString();
                        MessageAttachments msgAttachments = new Gson().fromJson(jsonVal, MessageAttachments.class);

                        Collection<FileAttachment> fileAttachments = msgAttachments.getFileAttachments();
                        for (FileAttachment fileAttachment : fileAttachments) {
                            long attachedFileObjId = fileAttachment.getObjectId();
                            if (attachedFileObjId >= 0) {
                                AbstractFile attachedFile = bba.getSleuthkitCase().getAbstractFileById(attachedFileObjId);
                                thumbnails.add(attachedFile);
                            }
                        }
                    } else {    // backward compatibility - email message attachments are derived files, children of the message.
                        for (Content childContent : bba.getChildren()) {
                            if (childContent instanceof AbstractFile) {
                                thumbnails.add((AbstractFile) childContent);
                            }
                        }
                    }

                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "Unable to get children from artifact.", ex); //NON-NLS
                }
            }
        });

        setKeys(thumbnails);
    }

    /**
     * A node for representing a thumbnail.
     */
    static class AttachementThumbnailNode extends FileNode {

        AttachementThumbnailNode(AbstractFile file) {
            super(file, false);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Set<String> keepProps = new HashSet<>(Arrays.asList(
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.score.name"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.comment.name"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.count.name"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.sizeColLbl"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"),
                    NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.knownColLbl")));

            //Remove all other props except for the  ones above
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            for (Node.Property<?> p : sheetSet.getProperties()) {
                if (!keepProps.contains(p.getName())) {
                    sheetSet.remove(p.getName());
                }
            }

            return sheet;
        }
    }
}
