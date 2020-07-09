/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import org.openide.util.NbBundle.Messages;

/**
 *
 * A JTabbed pane with one tab that says "Filters".  When the user clicks on that
 * table the content of the tab will be hidden.
 * 
 * The content pane provides support for scrolling. 
 */
public final class HidingPane extends JTabbedPane {
    
    private static final long serialVersionUID = 1L;
    
    private final JScrollPane scrollPane;
    private final JPanel panel;
    private final JLabel tabLabel;
    
    private boolean panelVisible = true;
    
    /**
     * Constructs a new HidingFilterPane
     */
    @Messages({
        "HidingPane_default_title=Filters"
    })
    public HidingPane() {
        super();
        
        scrollPane = new JScrollPane();
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        tabLabel = new JLabel(Bundle.HidingPane_default_title());
        tabLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/funnel.png")));
        tabLabel.setUI(new VerticalLabelUI(true));
        tabLabel.setOpaque(false);
        tabLabel.setFont(tabLabel.getFont().deriveFont(Font.BOLD, tabLabel.getFont().getSize()+7));
        
        addTab(null, panel);
        setTabComponentAt(0, tabLabel);
        
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                handleMouseClick(evt.getPoint());
            }
        });
        
        this.setTabPlacement(JTabbedPane.RIGHT);
    }
    
    /**
     * Change the title of the tab.
     * 
     * @param title 
     */
    void setTitle(String title) {
        tabLabel.setText(title);
    }
    
    /**
     * Set the icon that appears on the tab.
     * 
     * @param icon 
     */
    void setIcon(Icon icon) {
        tabLabel.setIcon(icon);
    }
    
    /**
     * Set the content for this panel.
     * 
     * @param panel A panel to display in the tabbed pane.
     */
    void setPanel(JPanel panel) {
        scrollPane.setViewportView(panel);
    }
    
    /**
     * Handle the mouse click.
     * 
     * @param point 
     */
    private void handleMouseClick(Point point) {
        int index = indexAtLocation(point.x, point.y);
        
        if(index == -1) {
            return;
        }
        
        if(panelVisible) {
            panel.removeAll();
            panel.revalidate();
            panelVisible = false;
        } else {
            panel.add(scrollPane, BorderLayout.CENTER);
            panel.revalidate();
            panelVisible = true;
        }
    }
    
}
