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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import org.sleuthkit.datamodel.Image;

/**
 * IngestDialog shown on Case.CASE_ADD_IMAGE property change
 */
public class IngestDialog extends JDialog {
    
    private static final String TITLE = "Ingest Modules";
    private static Dimension DIMENSIONS = new Dimension(500, 300);
    private Image image = null;
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

        panel.reload();
        add(panel, BorderLayout.PAGE_START);
        JButton startButton = new JButton("Start");
        JButton closeButton = new JButton("Close");
        startButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                
                panel.save();
                panel.setImage(image);
                panel.start();
                close();
            }
        });
        closeButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panel.save();
                close();
            }
        });
        this.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                panel.save();
                close();
            }
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10,10), new Dimension(10,10), new Dimension(10,10)));
        buttonPanel.add(startButton);
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10,10), new Dimension(10,10), new Dimension(10,10)));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.LINE_START);
        
        pack();
        setResizable(false);
        setVisible(true);
    }
    
    public void setImage(Image image) {
        this.image = image;
    }
    
    
    /**
     * Closes the Ingest dialog
     */
    public void close() {
        setVisible(false);
        dispose();
    }

   
}
