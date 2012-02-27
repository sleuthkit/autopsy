/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionID;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Log;

@ActionID(category = "Tools",
id = "org.sleuthkit.autopsy.report.reportAction")
@ActionRegistration(displayName = "#CTL_reportAction")
@ActionReferences({
    @ActionReference(path = "Menu/Tools", position = 80)
})
@Messages("CTL_reportAction=Run Report")
public final class reportAction implements ActionListener {
    private static final String ACTION_NAME = "Report Filter";
    public void actionPerformed(ActionEvent e) {
         try {
            
            // create the popUp window for it
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal

            // initialize panel with loaded settings
            final reportFilter panel = new reportFilter();
             panel.setjButton1ActionListener(new ActionListener() {

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
    
    public void closeme(JFrame frame){
       frame.dispose();
    }
}