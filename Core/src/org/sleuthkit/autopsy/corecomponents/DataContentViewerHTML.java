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
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * HTML view of file contents. This viewer uses a JavaFX WebView to display HTML data.
 */
@ServiceProvider(service = DataContentViewer.class, position = 6)
public final class DataContentViewerHTML extends javax.swing.JPanel implements DataContentViewer {

    private AbstractFile dataSource;
    private FileTypeDetector fileTypeDetector;
    private Stage stage;
    private WebView browser;
    private JFXPanel jfxPanel;
    private WebEngine webEngine;
    private Scene scene;
    private Button javaScriptButton;
    
    private final String enableJavaScriptTxt;
    private final String disableJavaScriptTxt;
    private final String acceptedMIMEType = "text/html";
    private final String[] validExtensions = new String[] {".html", ".htm"};
    
    private static final Logger logger = Logger.getLogger(DataContentViewerHTML.class.getName());
    
  
    public DataContentViewerHTML() {
        
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetectorInitException ex) {
            logger.log(Level.WARNING, "An error occured while initializing the file type "
                    + "detector for the HTML content viewer.", ex);
        }
        
        enableJavaScriptTxt = NbBundle.getMessage(this.getClass(), 
                "DataContentViewerHTML.enableJavaScriptBtnTxt");
        disableJavaScriptTxt = NbBundle.getMessage(this.getClass(), 
                "DataContentViewerHTML.disableJavaScriptBtnTxt");
        
        jfxPanel = new JFXPanel();
        setLayout(new BorderLayout());
        add(jfxPanel, BorderLayout.CENTER);
        
        Platform.runLater(() -> {
            
            stage = new Stage();
            stage.setResizable(true);
            
            BorderPane root = new BorderPane();
            scene = new Scene(root);
            stage.setScene(scene);
            
            //Button bar components
            HBox btnBox = new HBox();
            btnBox.setPadding(new Insets(10, 10, 10, 10));
            btnBox.setSpacing(10);
            
            javaScriptButton = new Button(enableJavaScriptTxt);
            javaScriptButton.setOnAction((ActionEvent event) -> {
                if(webEngine.isJavaScriptEnabled()) {
                    webEngine.setJavaScriptEnabled(false);
                    javaScriptButton.setText(enableJavaScriptTxt);
                } else {
                    webEngine.setJavaScriptEnabled(true);
                    javaScriptButton.setText(disableJavaScriptTxt);
                }
                webEngine.reload();                
            });
            
            btnBox.getChildren().add(javaScriptButton);
            
            //Webview components
            browser = new WebView();
            webEngine = browser.getEngine();
            
            //Javascript disabled by default
            webEngine.setJavaScriptEnabled(false);
            
            root.setTop(btnBox);
            root.setCenter(browser);
            jfxPanel.setScene(scene);
        }); 
        
        resetComponents();    
        
        logger.log(Level.INFO, "Created HTMLView instance: " + this); //NON-NLS
    }
    
    private void resetComponents() {
        
        //Disable JavaScript 
        Platform.runLater(()-> {
            webEngine.setJavaScriptEnabled(false);
            javaScriptButton.setText(enableJavaScriptTxt);
        });
        
        //Load an empty "page"
        loadWebEngineContent("");
    }
    
    /**
     * Loads HTML content into the webEngine as long as the engine is not null and the
     * content is not null. 
     * @param content Not null. The content to load into the web engine.
     */
    private void loadWebEngineContent(String content) {
        if(content != null && webEngine != null) {
            Platform.runLater(() -> {
                webEngine.loadContent(content);
            });
        }
    }
    
    /**
     * Loads an HTML file into the webEngine as long as the engine is not null and the
     * content is not null.
     * @param localFilePath Not null. The  local file path to the HTML file to be loaded.
     */
    private void loadWebEngineFileURL(String localFilePath) {
        
        if(localFilePath != null && webEngine != null) {
            Platform.runLater(() -> {
                webEngine.load("file://" + localFilePath);
            });
        }
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
        
        resetComponent();

        //Local files with .html extensions are loaded with a file URL
        if(dataSource.getType() == TSK_DB_FILES_TYPE_ENUM.LOCAL &&
                hasValidPathExtension(dataSource.getLocalAbsPath())) {
            loadWebEngineFileURL(dataSource.getLocalAbsPath());
        }
        else { //Non-local files, or local files without a .html extension are loaded as a string
            try {            

                long size = file.getSize();
                byte[] dataBytes = new byte[(int)size];
                dataSource.read(dataBytes, 0, (int)size);
                String HTML = new String(dataBytes);
                loadWebEngineContent(HTML);

            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "An error occurred while reading data from a "
                        + "file in the HTML content viewer.", ex);
            }
        }
    }
    
    private boolean hasValidPathExtension(String localPath) {
        for(String extension : validExtensions) {
            if(localPath.endsWith(extension))
                return true;
        }
        return false;
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
        resetComponents();
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
            
            
            
            //If the MIMEType has not already been detected, detect it.
            if(file.getMIMEType() == null) {
                
                if(fileTypeDetector == null)
                    return false;
                
                try {
                    return fileTypeDetector.detect(file).equals(acceptedMIMEType);
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "An error occurred while detecting a file"
                            + " type in the HTML content viewer.", ex);
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
            //A preference of 8 is returned, because the viewer might be interested
            //in seeing other views of the file first. Example, the viewer might
            //be more interested in the strings view of the HTML content.
            return 8;
        else 
            return 0;
    }
    
}
