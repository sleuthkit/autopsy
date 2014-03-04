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
package org.sleuthkit.autopsy.filesearch;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.directorytree.FileSearchProvider;

 final class FileSearchAction extends CallableSystemAction implements FileSearchProvider{
    
    private static FileSearchAction instance = null;

    FileSearchAction() {
        super();
        setEnabled(Case.isCaseOpen()); //no guarantee listener executed, so check here
        
        Case.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())){
                    setEnabled(evt.getNewValue() != null);
                }
            }
            
        });
    }
    
    public static FileSearchAction getDefault() {
        if(instance == null){
            instance = new FileSearchAction();
        }
        return instance;
    }
    
    
    @Override
    public void actionPerformed(ActionEvent e) {
        new FileSearchDialog().setVisible(true);
    }

    @Override
    public void performAction() {
        new FileSearchDialog().setVisible(true);
    }

    @Override
    public String getName() {
        return "File Search by Attributes";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    public void showDialog() {
        performAction();
    }
}
