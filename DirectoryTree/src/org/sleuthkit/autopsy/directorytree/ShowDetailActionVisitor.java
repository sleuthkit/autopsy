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


package org.sleuthkit.autopsy.directorytree;

import javax.swing.*;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;

/**
 * 
 *
 * @author jantonius
 */
class ShowDetailActionVisitor extends ContentVisitor.Default<List<? extends Action>> {
    
    private static ShowDetailActionVisitor instance = new ShowDetailActionVisitor();
   
    public static List<Action> getActions(Content c) {
        List<Action> actions = new ArrayList<Action>();
        
        actions.addAll(c.accept(instance));
        
        while (c.isOnto()) {
            try {
                List<? extends Content> children = c.getChildren();
                if (!children.isEmpty()) {
                    c = c.getChildren().get(0);
                } else {
                   return actions;
                }
            } catch (TskException ex) {
                Log.get(ShowDetailActionVisitor.class).log(Level.WARNING, "Error getting show detail actions.", ex);
                return actions;
            }
            actions.addAll(c.accept(instance));
        }
        return actions;
    }

    ShowDetailActionVisitor() {}

    @Override
    public List<? extends Action> visit(final Image img) {
        final String title = "Image Details";

        return Collections.singletonList(new AbstractAction(title) {

            @Override
            public void actionPerformed(ActionEvent e) {
                Log.noteAction(ShowDetailActionVisitor.class);

                final JFrame frame = new JFrame(title);
                final JDialog popUpWindow = new JDialog(frame, title, true); // to make the popUp Window to be modal
                // if we select the Image Details menu

                Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();

                // set the popUp window / JFrame
                popUpWindow.setSize(750, 400);

                int w = popUpWindow.getSize().width;
                int h = popUpWindow.getSize().height;

                // set the location of the popUp Window on the center of the screen
                popUpWindow.setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);

                ImageDetailsPanel imgDetailPanel = new ImageDetailsPanel();
                Boolean counter = false;

                imgDetailPanel.setImgNameValue(img.getName());
                imgDetailPanel.setImgTypeValue(Image.imageTypeToString(img.getType()));
                imgDetailPanel.setImgSectorSizeValue(Long.toString(img.getSsize()));
                counter = true;

                if (counter) {
                    // add the volume detail panel to the popUp window
                    popUpWindow.add(imgDetailPanel);
                } else {
                    // error handler if no volume matches
                    JLabel error = new JLabel("Error: No Volume Matches.");
                    error.setFont(new Font("Arial", Font.BOLD, 24));
                    popUpWindow.add(error);
                }

                // add the command to close the window to the button on the Volume Detail Panel
                imgDetailPanel.setOKButtonActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        popUpWindow.dispose();
                    }
                });


                popUpWindow.pack();
                popUpWindow.setResizable(false);
                popUpWindow.setVisible(true);
            }
        });
    }

    @Override
    public List<? extends Action> visit(final FileSystem fs) {
        final String title = "File System Details";

        return Collections.singletonList(new AbstractAction(title) {

            @Override
            public void actionPerformed(ActionEvent e) {
                Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
                
                Log.noteAction(ShowDetailActionVisitor.class);

                final JFrame frame = new JFrame(title);
                final JDialog popUpWindow = new JDialog(frame, title, true); // to make the popUp Window to be modal

                // set the popUp window / JFrame

                popUpWindow.setSize(1000, 500);

                int w = popUpWindow.getSize().width;
                int h = popUpWindow.getSize().height;

                // set the location of the popUp Window on the center of the screen
                popUpWindow.setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);

                String[] columnNames = new String[]{
                    "fs_id",
                    "img_offset",
                    "par_id",
                    "fs_type",
                    "block_size",
                    "block_count",
                    "root_inum",
                    "first_inum",
                    "last_inum"
                };




                Object[][] rowValues = new Object[1][9];

                Arrays.fill(rowValues, 0, 1, new Object[]{
                            fs.getId(),
                            fs.getImg_offset(),
                            fs.getParent().getId(),
                            fs.getFs_type(),
                            fs.getBlock_size(),
                            fs.getBlock_count(),
                            fs.getRoot_inum(),
                            fs.getFirst_inum(),
                            fs.getLast_inum()
                        });


                JTable table = new JTable(new DefaultTableModel(rowValues, columnNames));

                FileSystemDetailsPanel fsdPanel = new FileSystemDetailsPanel();

                // add the command to close the window to the button on the Volume Detail Panel
                fsdPanel.setOKButtonActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        popUpWindow.dispose();
                    }
                });

                try {
                    fsdPanel.setFileSystemTypeValue(table.getValueAt(0, 3).toString());
                    fsdPanel.setImageOffsetValue(table.getValueAt(0, 1).toString());
                    fsdPanel.setVolumeIDValue(table.getValueAt(0, 2).toString());  //TODO: fix this to parent id, not vol id
                    fsdPanel.setBlockSizeValue(table.getValueAt(0, 4).toString());
                    fsdPanel.setBlockCountValue(table.getValueAt(0, 5).toString());
                    fsdPanel.setRootInumValue(table.getValueAt(0, 6).toString());
                    fsdPanel.setFirstInumValue(table.getValueAt(0, 7).toString());
                    fsdPanel.setLastInumValue(table.getValueAt(0, 8).toString());

                    popUpWindow.add(fsdPanel);
                } catch (Exception ex) {
                    Log.get(ShowDetailActionVisitor.class).log(Level.WARNING, "Error setting up File System Details panel.", ex);
                }

                popUpWindow.pack();
                popUpWindow.setResizable(false);
                popUpWindow.setVisible(true);

            }
        });
    }

    @Override
    public List<? extends Action> visit(final Volume vol) {
        final String title = "Volume Details";

        return Collections.singletonList(new AbstractAction(title) {

            @Override
            public void actionPerformed(ActionEvent e) {
                Log.noteAction(ShowDetailActionVisitor.class);

                final JFrame frame = new JFrame(title);
                final JDialog popUpWindow = new JDialog(frame, title, true); // to make the popUp Window to be modal


                Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();

                // set the popUp window / JFrame
                popUpWindow.setSize(800, 400);

                int w = popUpWindow.getSize().width;
                int h = popUpWindow.getSize().height;

                // set the location of the popUp Window on the center of the screen
                popUpWindow.setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);

                VolumeDetailsPanel volumeDetailPanel = new VolumeDetailsPanel();
                Boolean counter = false;

                volumeDetailPanel.setVolumeIDValue(Long.toString(vol.getAddr()));
                volumeDetailPanel.setStartValue(Long.toString(vol.getStart()));
                volumeDetailPanel.setLengthValue(Long.toString(vol.getLength()));
                volumeDetailPanel.setDescValue(vol.getDescription());
                volumeDetailPanel.setFlagsValue(vol.getFlagsAsString());
                counter = true;

                if (counter) {
                    // add the volume detail panel to the popUp window
                    popUpWindow.add(volumeDetailPanel);
                } else {
                    // error handler if no volume matches
                    JLabel error = new JLabel("Error: No Volume Matches.");
                    error.setFont(new Font("Arial", Font.BOLD, 24));
                    popUpWindow.add(error);
                }

                // add the command to close the window to the button on the Volume Detail Panel
                volumeDetailPanel.setOKButtonActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        popUpWindow.dispose();
                    }
                });


                popUpWindow.pack();
                popUpWindow.setResizable(false);
                popUpWindow.setVisible(true);

            }
        });
    }

    @Override
    protected List<? extends Action> defaultVisit(Content di) {
        return Collections.EMPTY_LIST;
    }
}