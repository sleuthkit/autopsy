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
package org.sleuthkit.autopsy.communications;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Factory for creating thumbnail children nodes.
 *
 */
public class ThumbnailChildren extends Children.Keys<AbstractFile> {
    private static final Logger logger = Logger.getLogger(ThumbnailChildren.class.getName());
    
    private final Set<AbstractFile> thumbnails;
    
    /*
     * Creates the list of thumbnails from the given list of BlackboardArtifacts
     */
    ThumbnailChildren(Set<BlackboardArtifact> artifacts) {
        super(false);
        thumbnails = new HashSet<>();
        
        artifacts.forEach((bba) -> {
            try{
                for(Content childContent: bba.getChildren()) {
                    if(childContent instanceof AbstractFile && ImageUtils.thumbnailSupported((AbstractFile)childContent)) {
                        thumbnails.add((AbstractFile)childContent);
                    }
                }
            } catch(TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get children from artifact.", ex); //NON-NLS
            }
        });
    }
    
    @Override
    protected Node[] createNodes(AbstractFile t) {
        return new Node[]{new ThumbnailNode(t)};
    }

    @Override
    protected void addNotify() {
        super.addNotify();
        setKeys(thumbnails);
    }
    
    /**
     * A node for representing a thumbnail.
     */
    private static class ThumbnailNode extends FileNode {
        
       ThumbnailNode(AbstractFile file) {
            super(file, false);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Set<String> keepProps = new HashSet<>(Arrays.asList(
                NbBundle.getMessage(AbstractAbstractFileNode.class,  "AbstractAbstractFileNode.nameColLbl"),
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.score.name"), 
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.comment.name"), 
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.count.name"), 
                NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.sizeColLbl"),
                NbBundle.getMessage(AbstractAbstractFileNode.class,  "AbstractAbstractFileNode.mimeType"),
                NbBundle.getMessage(AbstractAbstractFileNode.class,  "AbstractAbstractFileNode.knownColLbl")));
            
            //Remove all other props except for the  ones above
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            for(Node.Property<?> p : sheetSet.getProperties()) {
                if(!keepProps.contains(p.getName())){
                    sheetSet.remove(p.getName());
                }
            }

            return sheet;
        }
    }
}
