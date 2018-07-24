/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 *
 * @author wschaefer
 */
public class TagTreeRefreshEvent extends AutopsyEvent implements Serializable{

    private static final long serialVersionUID = 1L;
    
    public TagTreeRefreshEvent(){
       this(Case.Events.REFRESH_TAG_TREE.toString(), true, false);
    }
    private TagTreeRefreshEvent(String eventName, Object oldValue, Object newValue) {
        super(eventName, oldValue, newValue);
    }
    
}
