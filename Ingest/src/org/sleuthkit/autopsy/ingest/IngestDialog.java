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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.sleuthkit.datamodel.Image;

/**
 * IngestDialog shown on Case.CASE_ADD_IMAGE property change
 */
public class IngestDialog extends JDialog {
    
    private static final String TITLE = "Ingest Modules";
    private static Dimension DIMENSIONS = new Dimension(300, 300);
    private IngestDialogPanel panel = null;
    
    private static Logger logger = Logger.getLogger(IngestDialog.class.getName());

    public IngestDialog(JFrame frame, String title, boolean modal) {
        super(frame, title, modal);
        panel = IngestDialogPanel.getDefault();
    }
    
    public IngestDialog(){
        this(new JFrame(TITLE), TITLE, true);
    }



    /**
     * Shows the Ingest dialog.
     */
    public void display() {
        setLayout(new BorderLayout());
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();

        // set the popUp window / JFrame
        setSize(DIMENSIONS);
        int w = this.getSize().width;
        int h = this.getSize().height;

        // set the location of the popUp Window on the center of the screen
        setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);

        add(panel, BorderLayout.PAGE_START);
        JButton startButton = new JButton("Start");
        JButton closeButton = new JButton("Close");
        startButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panel.start();
                close();
            }
        });
        closeButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        add(startButton, BorderLayout.LINE_START);
        add(closeButton, BorderLayout.LINE_END);
        
        pack();
        setResizable(false);
        setVisible(true);
    }
    
    public void setImage(Image image) {
        panel.setImage(image);
    }
    
    
    /**
     * Closes the Ingest dialog
     */
    public void close() {
        setVisible(false);
        dispose();
    }

   
}
