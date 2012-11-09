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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.*;
import javax.swing.border.Border;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.report.ReportAction")
@ActionRegistration(displayName = "#CTL_ReportAction")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 80)})
@Messages(value = "CTL_ReportAction=Run Report")
public final class ReportAction extends CallableSystemAction implements Presenter.Toolbar {

    private JButton toolbarButton = new JButton();
    private static final String ACTION_NAME = "Generate Report";
    static final Logger logger = Logger.getLogger(ReportAction.class.getName());
    private JPanel panel;
    public static ArrayList<JCheckBox> reportList = new ArrayList<JCheckBox>();
    public static ArrayList<String> preview;
    public static ReportConfiguration config;

    public ReportAction() {
        setEnabled(false);
        Case.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(Case.CASE_CURRENT_CASE)) {
                    Case newCase = (Case) evt.getNewValue();
                    setEnabled(newCase != null);

                    //attempt to create a report folder if a case is active
                    if (newCase != null) {
                        boolean exists = (new File(newCase.getCaseDirectory() + File.separator + "Reports")).exists();
                        if (exists) {
                            // report directory exists -- don't need to do anything
                        } else {
                            // report directory does not exist -- create it
                            boolean reportCreate = (new File(newCase.getCaseDirectory() + File.separator + "Reports")).mkdirs();
                            if (!reportCreate) {
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
                ReportAction.this.actionPerformed(e);
            }
        });

    }

    private class configListener implements ItemListener {

        @Override
        public void itemStateChanged(ItemEvent e) {
            Object source = e.getItem();
            JCheckBox comp = (JCheckBox) source;
            String name = comp.getName();
            BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromLabel(name);
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                try {
                    config.setGenArtifactType(type, Boolean.FALSE);
                } catch (ReportModuleException ex) {
                }
            }
            if (e.getStateChange() == ItemEvent.SELECTED) {
                try {
                    config.setGenArtifactType(type, Boolean.TRUE);
                } catch (ReportModuleException ex) {
                }
            }
        }
    };


    @Override
    public void actionPerformed(ActionEvent e) {
        try {

            // create the popUp window for it
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal
            popUpWindow.setLayout(new GridLayout(0, 1));
            // initialize panel with loaded settings
            final ReportFilter panel = new ReportFilter();
            panel.setjButton2ActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    popUpWindow.dispose();
                }
            });
            final configListener clistener = new configListener();
            preview = new ArrayList<String>();
            reportList.clear();
            config = new ReportConfiguration();
            int rows = Lookup.getDefault().lookupAll(ReportModule.class).size(); //One row for each report module
            final JPanel filterpanel = new JPanel(new GridLayout(rows, 2, 5, 5));
            final JPanel artpanel = new JPanel(new GridLayout(0, 3, 0, 0));
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {

                    Border border = BorderFactory.createTitledBorder("Reporting Modules");
                    filterpanel.setBorder(border);
                    filterpanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                    filterpanel.setAlignmentY(Component.TOP_ALIGNMENT);
                    filterpanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    filterpanel.setSize(300, 200);
                    int placement = 1;

                    for (ReportModule m : Lookup.getDefault().lookupAll(ReportModule.class)) {
                        String name = m.getName();
                        String desc = m.getReportTypeDescription();
                        JCheckBox ch = new JCheckBox();
                        ch.setAlignmentY(Component.TOP_ALIGNMENT);
                        ch.setText(name);
                        ch.setName(m.getClass().getName());
                        ch.setToolTipText(desc);
                        ch.setSelected(false);
                        if(m.getName().equals(ReportHTML.getDefault().getName())){
                            ch.setSelected(true);
                            reportList.add(0, ch);
                        }
                        else{
                             reportList.add(placement++, ch);
                        }
                    }
                    for(int i = reportList.size()-1; i > -1; i--){
                        filterpanel.add(reportList.get(i), 0);
                    }
                    Border artborder = BorderFactory.createTitledBorder("Report Data");
                    artpanel.setBorder(artborder);
                    artpanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                    artpanel.setAlignmentY(Component.TOP_ALIGNMENT);
                    artpanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    artpanel.setPreferredSize(new Dimension(300, 150));
                    for (BlackboardArtifact.ARTIFACT_TYPE a : ReportFilter.config.config.keySet()) {
                        JCheckBox ce = new JCheckBox();
                        ce.setText(a.getDisplayName());
                        ce.setToolTipText(a.getDisplayName());
                        ce.setName(a.getLabel());
                        ce.setPreferredSize(new Dimension(60, 30));
                        ce.setSelected(true);
                        ce.addItemListener(clistener);
                        artpanel.add(ce);
                    }

                }
            });
            popUpWindow.add(filterpanel, 0);
            popUpWindow.add(artpanel, 1);
            // add the panel to the popup window
            popUpWindow.add(panel, 2);

            popUpWindow.pack();
            popUpWindow.setResizable(false);
            // Modules need extra room for text to properly show
            popUpWindow.setSize(popUpWindow.getWidth(),
                    popUpWindow.getHeight() + 50);

            // set the location of the popUp Window on the center of the screen
            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            double w = popUpWindow.getSize().getWidth();
            double h = popUpWindow.getSize().getHeight();
            popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));

            // display the window
            popUpWindow.setVisible(true);
            // add the command to close the window to the button on the Case Properties form / panel


        } catch (Exception ex) {
            Logger.getLogger(ReportFilterAction.class.getName()).log(Level.WARNING, "Error displaying " + ACTION_NAME + " window.", ex);
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
     * @return component the toolbar button
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
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }
}