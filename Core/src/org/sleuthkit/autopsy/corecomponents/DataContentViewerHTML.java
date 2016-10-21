/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.logging.Level;
import com.sun.javafx.application.PlatformImpl;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javax.swing.JButton;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.datamodel.DataConversion;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * HTML view of file contents
 */
@ServiceProvider(service = DataContentViewer.class, position = 6)
public class DataContentViewerHTML extends javax.swing.JPanel implements DataContentViewer {

    private Content dataSource;
    private Stage stage;
    private WebView browser;
    private JFXPanel jfxPanel;
    private JButton swingButton;
    private WebEngine webEngine;
    
    private static final Logger logger = Logger.getLogger(DataContentViewerHTML.class.getName());
    
    public DataContentViewerHTML() {
        initComponents();
        customizeComponents();
        resetComponent();
        logger.log(Level.INFO, "Created HTMLView instance: " + this); //NON-NLS
    }
    
    private void initComponents() {
       jfxPanel = new JFXPanel();
       createScene();
       
       setLayout(new BorderLayout());
       add(jfxPanel, BorderLayout.CENTER);
    }
    
    private void createScene() {
        PlatformImpl.startup(new Runnable() {
            @Override
            public void run() {
                stage = new Stage();
                
                stage.setResizable(true);
                
                Group root = new Group();
                Scene scene = new Scene(root, 80, 20);
                stage.setScene(scene);
                
                browser = new WebView();
                webEngine = browser.getEngine();
                //webEngine.load("https://www.google.com");
                
                ObservableList<javafx.scene.Node> children = root.getChildren();
                children.add(browser);
                
                jfxPanel.setScene(scene);
            }
        });
    }
    
    private void customizeComponents() {
        
    }
    
    @Override
    public void setNode(Node selectedNode) {

        if ((selectedNode == null) || (!isSupported(selectedNode))) {
            resetComponent();
            return;
        }

        Content content = selectedNode.getLookup().lookup(Content.class);
        if (content == null) {
            resetComponent();
            return;
        }
        
        dataSource = content;
        
        try {  
            
            long size = content.getSize();
            byte[] dataBytes = new byte[(int)size];
            dataSource.read(dataBytes, 0, (int)size);
            String str = new String(dataBytes);
            PlatformImpl.startup(new Runnable() {
                @Override
                public void run() {
                    webEngine.loadContent(str);
                }
            });
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerHTML.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerHTML.toolTip");
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerHTML();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }
        Content content = node.getLookup().lookup(Content.class);
        if (content != null && content.getSize() > 0) {
            
            //look at content to make sure it is HTML
            
            return true;
        }

        return false;
    }

    @Override
    public int isPreferred(Node node) {
        if(isSupported(node))
            return 10;
        else 
            return 0;
    }
    
}
