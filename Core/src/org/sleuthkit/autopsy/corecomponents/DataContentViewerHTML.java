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

import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.logging.Level;
import com.sun.javafx.application.PlatformImpl;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebErrorEvent;
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
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;
import javafx.scene.layout.StackPane;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML view of file contents
 */
@ServiceProvider(service = DataContentViewer.class, position = 6)
public class DataContentViewerHTML extends javax.swing.JPanel implements DataContentViewer {

    private AbstractFile dataSource;
    private FileTypeDetector fileTypeDetector;
    private Stage stage;
    private WebView browser;
    private JFXPanel jfxPanel;
    private WebEngine webEngine;
    private Scene scene;
    
    private static final Logger logger = Logger.getLogger(DataContentViewerHTML.class.getName());
    
  
    public DataContentViewerHTML() {
        
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetectorInitException e) {
            e.printStackTrace();
        }
        
        initComponents();
        customizeComponents();
        resetComponent();
        logger.log(Level.INFO, "Created HTMLView instance: " + this); //NON-NLS
    }
    
    private void initComponents() {     
        
       jfxPanel = new JFXPanel();
       setLayout(new BorderLayout());
       add(jfxPanel, BorderLayout.CENTER);
       createScene();     
    }
    
    private void createScene() {
        PlatformImpl.startup(new Runnable() {
            @Override
            public void run() {
                stage = new Stage();
                
                stage.setResizable(true);
                
                StackPane root = new StackPane();
                Scene scene = new Scene(root);
                stage.setScene(scene);
                
                browser = new WebView();
                webEngine = browser.getEngine();
                
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

        AbstractFile file = selectedNode.getLookup().lookup(AbstractFile.class);
        if (file == null) {
            resetComponent();
            return;
        }
        
        dataSource = file;
        
        try {  
            
            long size = file.getSize();
            byte[] dataBytes = new byte[(int)size];
            dataSource.read(dataBytes, 0, (int)size);
            String HTML = new String(dataBytes);
            PlatformImpl.startup(() -> {
                webEngine.loadContent(HTML);
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
        PlatformImpl.startup(() -> {
            webEngine.loadContent("");
        });
    }

    @Override
    public boolean isSupported(Node node) {
        
        if (node == null) {
            return false;
        }
        
        if(node.getLookup().lookupAll(BlackboardArtifact.class).size() > 0) {
            return false;
        }
        
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        
        if (file != null && file.getSize() > 0 && file.isFile()) {
            
            String acceptedMIMEType = NbBundle.getMessage(this.getClass(), 
                        "DataContentViewerHTML.acceptedMIMEType");
            
            if(file.getMIMEType() == null) {
                
                if(fileTypeDetector == null)
                    return false;
                
                try {
                    return fileTypeDetector.detect(file).equals(acceptedMIMEType);
                } catch (TskCoreException ex) {
                     Exceptions.printStackTrace(ex);
                    return false;
                }
            }
            else 
                return file.getMIMEType().equals(acceptedMIMEType);     
        }

        return false;
    }

    @Override
    public int isPreferred(Node node) {
        if(isSupported(node))
            //A preference of 8 is returned, because the viewer might want to see
            //the string version of the file.
            return 8;
        else 
            return 0;
    }
    
}
