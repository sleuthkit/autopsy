/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import com.google.common.eventbus.EventBus;

/**
 *
 */
public class CVTEvents {

private final static EventBus cvtEventBus = new EventBus();

    public static EventBus getCVTEventBus() {
        return cvtEventBus;
    }
    
}
