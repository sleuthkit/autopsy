/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Abstract base class for Actions involving tags.
 */
 abstract class TagAction extends AbstractAction {
    public TagAction(String menuText) {
        super(menuText);
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        doAction(event);
        refreshDirectoryTree();
    }    
    
    /**
     * Derived classes must implement this Template Method for actionPerformed(). 
     * @param event ActionEvent object passed to actionPerformed()  
     */
    abstract protected void doAction(ActionEvent event);
    
    /**
     * Derived classes should call this method any time a tag is created, updated
     * or deleted outside of an actionPerformed() call.
     */
     @SuppressWarnings("deprecation")
    protected void refreshDirectoryTree() {
        
        /* Note: this is a hack.  In an ideal world, TagsManager would fire events so 
         * that the directory tree would refresh. But, we haven't had a chance to add
         * that so, we fire these events and the tree refreshes based on them. 
         */
        IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent("TagAction", BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE)); //NON-NLS
    }        
}
