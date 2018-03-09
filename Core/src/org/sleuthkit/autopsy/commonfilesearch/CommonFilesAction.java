/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.commonfilesearch;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.directorytree.FileSearchProvider;

/**
 *
 * @author bsweeney
 */
final class CommonFilesAction extends CallableSystemAction implements FileSearchProvider {

    private static CommonFilesAction instance = null;
    
    CommonFilesAction(){
        super();
        this.setEnabled(Case.isCaseOpen());
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                setEnabled(evt.getNewValue() != null);
            }
        });
    }
    
    public static CommonFilesAction getDefault(){
        if(instance == null){
            instance = new CommonFilesAction();
        }
        return instance;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        new CommonFilesDialog().setVisible(true);
    }
    
    @Override
    public void performAction() {
        new CommonFilesDialog().setVisible(true);
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "CommonFilesAction.getName.text");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void showDialog() {
        performAction();
    }
    
}
