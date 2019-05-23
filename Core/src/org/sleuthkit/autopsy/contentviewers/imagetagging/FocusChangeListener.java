/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.contentviewers.imagetagging;

import java.util.EventListener;

/**
 *
 * @author dsmyda
 */
@FunctionalInterface
public interface FocusChangeListener extends EventListener{
    
    void focusChanged(FocusChangeEvent event);
}
