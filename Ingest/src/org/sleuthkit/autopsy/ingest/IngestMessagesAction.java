/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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


package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;


//@ActionID(category = "File",
//id = "org.sleuthkit.autopsy.ingest.IngestMessagesAction")
//@ActionRegistration(iconBase = "org/sleuthkit/autopsy/ingest/eye-icon.png",
//displayName = "#CTL_IngestMessagesAction")
//@ActionReferences({
//    @ActionReference(path = "Toolbars/File", position = 575)
//})
//@Messages("CTL_IngestMessagesAction=Messages")
public final class IngestMessagesAction implements ActionListener {
    
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
