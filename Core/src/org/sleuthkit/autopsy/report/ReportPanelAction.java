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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.coreutils.Logger;


public class ReportPanelAction {

    private static final String ACTION_NAME = "Report Preview";
    private StringBuilder viewReport = new StringBuilder();
    private int cc = 1;
    private HashMap<ReportModule,String> reports = new HashMap<ReportModule,String>();
    public ReportPanelAction() {
    }

    public void reportGenerate(final ReportConfiguration reportconfig, final ArrayList<String> classList, final ArrayList<String> preview, final ReportFilter rr) {
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
                }
            });
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    rr.progBarCount(((classList.size())*2)+1);
                }
            });
            // Advance the bar a bit so the user knows something is happening
             SwingUtilities.invokeLater(new Runnable() {

                            @Override
                            public void run() {
                                rr.progBarSet(1);
                            }
                        });
            //Turn our results into the appropriate xml/html reports
            //TODO: add a way for users to select what they will run when
            Thread reportThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    reports.clear();
                    for (String s : classList) {
                        try {
                            rr.progBarSet(cc);
                            final Class reportclass = Class.forName(s);
                            rr.setUpdateLabel("Running " + reportclass.getSimpleName() + " report...");
                            Object reportObject = reportclass.newInstance();
                            Class[] argTypes = new Class[]{ReportConfiguration.class};
                            Method generatereport = reportclass.getDeclaredMethod("generateReport", argTypes);
                            Object invoke = generatereport.invoke(reportObject, reportconfig);
                            rr.progBarSet(cc);
                            String path = invoke.toString();
                            Class[] argTypes2 = new Class[]{String.class};
                            Method getpreview = reportclass.getMethod("getPreview", argTypes2);
                            reports.put((ReportModule) reportObject, path);
                            for (String prev : preview) {
                                if (s == null ? preview == null : s.equals(prev)) {
                                    getpreview.invoke(reportObject, path);
                                }
                            }

                        } catch (Exception e) {
                            Logger.getLogger(ReportFilterAction.class.getName()).log(Level.WARNING, "Error generating " + s + "! Reason: ", e);
                        }
                    }
                }
            });


            // start our threads
            reportThread.start();

            // display the window

            // create the popUp window for it
            if (ReportFilter.cancel == false) {

                final JFrame frame = new JFrame(ACTION_NAME);
                final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal


                // initialize panel with loaded settings   

                //Set the temporary label to let the user know its done and is waiting on the report
               
                final ReportPanel panel = new ReportPanel(this);


                panel.setCloseButtonActionListener(new ActionListener() {

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
                rr.progBarText();
                rr.progBarDone();
                panel.setFinishedReportText();
                popUpWindow.setVisible(true);




            }
        } catch (Exception ex) {
            Logger.getLogger(ReportFilterAction.class.getName()).log(Level.WARNING, "Error displaying " + ACTION_NAME + " window.", ex);
        }
    }
    
    public HashMap<ReportModule,String> getReports(){       
        return reports;
    }
}
