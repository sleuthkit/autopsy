/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.events;

import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;

/**
 *
 * @author rcordovano
 */
public class HostsRemovedFromPersonEvent  extends PersonHostsEvent {
    
    private static final long serialVersionUID = 1L;
    
    HostsRemovedFromPersonEvent(Person person, List<Host> hosts) {
        super(Case.Events.HOSTS_REMOVED_FROM_PERSON.toString(), person, hosts);
    }
        
}