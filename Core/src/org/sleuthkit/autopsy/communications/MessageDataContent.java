/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.beans.PropertyChangeEvent;
import org.sleuthkit.autopsy.contentviewers.MessageContentViewer;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;

/**
 * Extend MessageContentViewer so that it implements DataContent and can be set
 * as the only ContentViewer for a DataResultPanel
 */
public class MessageDataContent extends MessageContentViewer implements DataContent {

    private static final long serialVersionUID = 1L;

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
