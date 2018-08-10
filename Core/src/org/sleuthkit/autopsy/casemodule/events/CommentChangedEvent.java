
package org.sleuthkit.autopsy.casemodule.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.events.AutopsyEvent;


public class CommentChangedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private final long contentID;
    
    public CommentChangedEvent(long id) {
        super(Case.Events.CR_COMMENT_CHANGED.toString(), true, false);
        contentID = id;
    }
    
    public long getContentID(){
        return contentID;
    }
}
