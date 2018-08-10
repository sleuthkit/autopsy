
package org.sleuthkit.autopsy.casemodule.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.events.AutopsyEvent;


public class CommentChangedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private final long contentID;
    
    public CommentChangedEvent(long id, String newComment) {
        super(Case.Events.CR_COMMENT_CHANGED.toString(), null, newComment);
        contentID = id;
    }
    
    public long getContentID(){
        return contentID;
    }
}
