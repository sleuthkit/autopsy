/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Event published when a central repsoitory comment is changed
 */
public class CommentChangedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long contentID;
    private final String caseName; //included so we can eventually use this event between cases
    
    /**
     * Constructs a CommentChangedEvent which is published when a central
     * repository comment is changed.
     *
     * @param contentId the objectId of the Content which has had its central repository comment changed
     * @param newComment the new value of the comment
     * @throws org.sleuthkit.autopsy.casemodule.NoCurrentCaseException if there is no current case open when this event is sent
     */
    public CommentChangedEvent(long contentId, String newComment) throws NoCurrentCaseException {
        super(Case.Events.CR_COMMENT_CHANGED.toString(), null, newComment);
         contentID = contentId;
        //get the caseName as well so that this event could be used for notifications between cases without method signature change
        caseName = Case.getCurrentCaseThrows().getName(); 
       
    }

    /**
     * Get the object id of the content which this event is associated with.
     * 
     * @return the objectId of the content this event is associated with 
     */
    public long getContentID() {
        return contentID;
    }
}
