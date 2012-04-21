 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> autopsy <dot> org
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
package org.sleuthkit.autopsy.report;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionID;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Log;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;

@ActionID(category = "Tools",
id = "org.sleuthkit.autopsy.report.reportAction")
@ActionRegistration(displayName = "#CTL_reportAction")
@ActionReferences({
    @ActionReference(path = "Menu/Tools", position = 80)
})
@Messages("CTL_reportAction=Run Report")
public final class reportAction extends CallableSystemAction implements Presenter.Toolbar{
    
    private JButton toolbarButton = new JButton();
    private static final String ACTION_NAME = "Generate Report";
     Logger logger = Logger.getLogger(reportAction.class.getName());
    
    public reportAction() {
        setEnabled(false);
        Case.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(evt.getPropertyName().equals(Case.CASE_CURRENT_CASE)){
                    setEnabled(evt.getNewValue() != null);
                }
            }
            
        });
        //attempt to create a report folder if a case is active
        Case.addPropertyChangeListener(new PropertyChangeListener () {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String changed = evt.getPropertyName();

            //case has been changed
            if (changed.equals(Case.CASE_CURRENT_CASE)) {
            Case newCase = (Case)evt.getNewValue();

                if (newCase != null) {
                    boolean exists = (new File(newCase.getCaseDirectory() + "\\Reports")).exists();
                    if (exists) {
                        // report directory exists -- don't need to do anything
                        
                    } else {
                        // report directory does not exist -- create it
                        boolean reportCreate = (new File(newCase.getCaseDirectory() + "\\Reports")).mkdirs();
                        if(!reportCreate){
                            logger.log(Level.WARNING, "Could not create Reports directory for case. It does not exist.");
                        }
                    }
                } 
            }
        }

});
        
        // set action of the toolbar button
        toolbarButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                reportAction.this.actionPerformed(e);
            }
        });

    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            
            // create the popUp window for it
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal

            // initialize panel with loaded settings
            final reportFilter panel = new reportFilter();   
             panel.setjButton2ActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                  popUpWindow.dispose();
                            }
                        });
             
            // add the panel to the popup window
            popUpWindow.add(panel);
            popUpWindow.pack();
            popUpWindow.setResizable(false);

            // set the location of the popUp Window on the center of the screen
            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            double w = popUpWindow.getSize().getWidth();
            double h = popUpWindow.getSize().getHeight();
            popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));

            // display the window
            popUpWindow.setVisible(true);
            // add the command to close the window to the button on the Case Properties form / panel
           
            
        } catch (Exception ex) {
            Log.get(reportFilterAction.class).log(Level.WARNING, "Error displaying " + ACTION_NAME + " window.", ex);
        }
    }
    
    @Override
    public void performAction() {
        
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    /**
     * Returns the toolbar component of this action
     *
     * @return component  the toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_generate_report.png"));
        toolbarButton.setIcon(icon);
        toolbarButton.setText("Generate Report");
        return toolbarButton;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value  whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value){
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }
}