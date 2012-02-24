/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionID;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(category = "Form",
id = "org.sleuthkit.autopsy.ingest.IngestMessagesAction")
@ActionRegistration(iconBase = "org/sleuthkit/autopsy/ingest/eye-icon.png",
displayName = "#CTL_IngestMessagesAction")
@ActionReferences({
    @ActionReference(path = "Toolbars/File", position = 575)
})
@Messages("CTL_IngestMessagesAction=Messages")
public final class IngestMessagesAction implements ActionListener {

    Logger logger = Logger.getLogger(IngestMessagesAction.class.getName());
    
    @Override
    public void actionPerformed(ActionEvent e) {
        IngestMessageTopComponent tc = IngestMessageTopComponent.findInstance();
       
        Mode mode = WindowManager.getDefault().findMode("floatingLeftBottom");
        if (mode != null) {
            //TopComponent[] tcs = mode.getTopComponents();
            mode.dockInto(tc);
            tc.open();
            //tc.requestActive();   
        }
    }
}
