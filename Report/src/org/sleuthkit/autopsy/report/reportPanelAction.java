/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 * @author Alex
 */
public class reportPanelAction {
     private static final String ACTION_NAME = "Report Preview";
     
     public reportPanelAction(ArrayList<Integer> reportlist){
         try {
            // Generate the reports and create the hashmap
         HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> Results = new HashMap();
         report bbreport = new report();
         //see what reports we need to run and run them
             if(reportlist.contains(1)){Results.putAll(bbreport.getGenInfo());}
             if(reportlist.contains(2)){Results.putAll(bbreport.getWebBookmark());}
             if(reportlist.contains(3)){Results.putAll(bbreport.getWebCookie());}
             if(reportlist.contains(4)){Results.putAll(bbreport.getWebHistory());}
             if(reportlist.contains(5)){Results.putAll(bbreport.getWebDownload());}
             if(reportlist.contains(6)){Results.putAll(bbreport.getRecentObject());}
            // if(reportlist.contains(7)){Results.putAll(bbreport.getGenInfo());}
            // if(reportlist.contains(7)){Results.putAll(bbreport.getGenInfo());}
             if(reportlist.contains(9)){Results.putAll(bbreport.getKeywordHit());}
             if(reportlist.contains(10)){Results.putAll(bbreport.getHashHit());}
         
         //Turn our results into the appropriate xml/html reports
         //TODO: add a way for users to select what they will run when
         reportXML xmlReport = new reportXML(Results);
         reportHTML htmlReport = new reportHTML(Results);
            // create the popUp window for it
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal

            // initialize panel with loaded settings
            final reportPanel panel = new reportPanel(htmlReport.formatted_Report);
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
}
