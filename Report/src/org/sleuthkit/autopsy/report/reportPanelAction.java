 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.coreutils.Log;

/**
 *
 * @author Alex
 */
public class reportPanelAction {
     private static final String ACTION_NAME = "Report Preview";
     private StringBuilder viewReport = new StringBuilder();
     public reportPanelAction(){
        
     }
         
     public void reportGenerate(final ReportConfiguration reportconfig, final reportFilter rr){
         try {
             //Clear any old reports in the string
             viewReport.setLength(0);

             
            // Generate the reports and create the hashmap
       final ReportGen report = new ReportGen();
         //see what reports we need to run and run them
         //Set progress bar to move while doing this
             SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                 rr.progBarStartText();
                 }});
              report.populateReport(reportconfig);
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                 rr.progBarCount(2*report.Results.size());
                 }});
         //Turn our results into the appropriate xml/html reports
         //TODO: add a way for users to select what they will run when
             Thread reportThread = new Thread(new Runnable()
                {
                @Override
                   public void run()
                   {  
                    StopWatch a = new StopWatch();
                    a.start();
                    reportHTML htmlReport = new reportHTML();
                    try{
                    htmlReport.generateReport(reportconfig, rr);
                    BrowserControl.openUrl(reportHTML.htmlPath);
                    }
                    catch(ReportModuleException e){
                        Logger.getLogger(reportHTML.class.getName()).log(Level.WARNING, "Exception occurred in generating the htmlReport", e);
                    }
                    a.stop();
                    System.out.println("html in milliseconds: " + a.getElapsedTime());
                    
                    StopWatch s = new StopWatch();
                    s.start();
                    reportXLS xlsReport = new reportXLS();
                    try{
                    xlsReport.generateReport(reportconfig,rr);
                    }
                    catch(ReportModuleException e){
                        Logger.getLogger(reportHTML.class.getName()).log(Level.WARNING, "Exception occurred in generating the XLS Report", e);
                    }
                    s.stop();
                    System.out.println("xls in milliseconds: " + s.getElapsedTime());
                    
                    StopWatch S = new StopWatch();
                    S.start();
                    reportXML xmlReport = new reportXML(); 
                    try{
                    xmlReport.generateReport(reportconfig,rr);
                    }
                    catch(ReportModuleException e){
                        Logger.getLogger(reportHTML.class.getName()).log(Level.WARNING, "Exception occurred in generating the XML Report", e);
                    }
                    S.stop();
                    System.out.println("xml in milliseconds: " + S.getElapsedTime());
                   }
                });
             

        // start our threads
        reportThread.start();
    
            // display the window
            
            // create the popUp window for it
         if(reportFilter.cancel == false){
                         
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal
            
            
            // initialize panel with loaded settings   
            
            //Set the temporary label to let the user know its done and is waiting on the report
            rr.progBarText();
           final reportPanel panel = new reportPanel();
            
           
             panel.setjButton1ActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                popUpWindow.dispose();
                            }
                        });
            // add the panel to the popup window
            popUpWindow.add(panel);
            
            popUpWindow.setResizable(true);
            popUpWindow.pack();
            // set the location of the popUp Window on the center of the screen
            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            double w = popUpWindow.getSize().getWidth();
            double h = popUpWindow.getSize().getHeight();
            popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));
          
            reportThread.join();  
            rr.progBarDone();
            panel.setFinishedReportText();
            popUpWindow.setVisible(true);
            
     
            
           
         }
        } catch (Exception ex) {
            Log.get(reportFilterAction.class).log(Level.WARNING, "Error displaying " + ACTION_NAME + " window.", ex);
        }
     }
}
