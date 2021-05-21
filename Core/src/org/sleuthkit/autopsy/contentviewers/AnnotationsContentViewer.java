/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;

import static org.openide.util.NbBundle.Messages;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.contentviewers.application.Annotations;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.jsoup.nodes.Document;

/**
 * Annotations view of file contents.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 9)
@Messages({
    "AnnotationsContentViewer.title=Annotations",
    "AnnotationsContentViewer.toolTip=Displays tags and comments associated with the selected content.",
    "AnnotationsContentViewer.onEmpty=No annotations were found for this particular item."
})
public class AnnotationsContentViewer extends javax.swing.JPanel implements DataContentViewer {

    private static final int DEFAULT_FONT_SIZE = new JLabel().getFont().getSize();

    // how big the subheader should be
    private static final int SUBHEADER_FONT_SIZE = DEFAULT_FONT_SIZE * 12 / 11;

    // how big the header should be
    private static final int HEADER_FONT_SIZE = DEFAULT_FONT_SIZE * 14 / 11;

    // the subsection indent
    private static final int DEFAULT_SUBSECTION_LEFT_PAD = DEFAULT_FONT_SIZE;

    // spacing occurring after an item
    private static final int DEFAULT_SECTION_SPACING = DEFAULT_FONT_SIZE * 2;
    private static final int DEFAULT_SUBSECTION_SPACING = DEFAULT_FONT_SIZE / 2;
    private static final int CELL_SPACING = DEFAULT_FONT_SIZE / 2;

    // additional styling for components
    private static final String STYLE_SHEET_RULE
            = String.format(" .%s { font-size: %dpx;font-style:italic; margin: 0px; padding: 0px; } ", Annotations.MESSAGE_CLASSNAME, DEFAULT_FONT_SIZE)
            + String.format(" .%s {font-size:%dpx;font-weight:bold; margin: 0px; margin-top: %dpx; padding: 0px; } ",
                    Annotations.SUBHEADER_CLASSNAME, SUBHEADER_FONT_SIZE, DEFAULT_SUBSECTION_SPACING)
            + String.format(" .%s { font-size:%dpx;font-weight:bold; margin: 0px; padding: 0px; } ", Annotations.HEADER_CLASSNAME, HEADER_FONT_SIZE)
            + String.format(" td { vertical-align: top; font-size:%dpx; text-align: left; margin: 0px; padding: 0px %dpx 0px 0px;} ", DEFAULT_FONT_SIZE, CELL_SPACING)
            + String.format(" th { vertical-align: top; text-align: left; margin: 0px; padding: 0px %dpx 0px 0px} ", DEFAULT_FONT_SIZE, CELL_SPACING)
            + String.format(" .%s { margin: %dpx 0px; padding-left: %dpx; } ", Annotations.SUBSECTION_CLASSNAME, DEFAULT_SUBSECTION_SPACING, DEFAULT_SUBSECTION_LEFT_PAD)
            + String.format(" .%s { margin-bottom: %dpx; } ", Annotations.SECTION_CLASSNAME, DEFAULT_SECTION_SPACING);
    
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AnnotationsContentViewer.class.getName());
    
    private AnnotationWorker worker;
    
    /**
     * Creates an instance of AnnotationsContentViewer.
     */
    public AnnotationsContentViewer() {
        initComponents();
        Utilities.configureTextPaneAsHtml(textPanel);
        // get html editor kit and apply additional style rules
        EditorKit editorKit = textPanel.getEditorKit();
        if (editorKit instanceof HTMLEditorKit) {
            HTMLEditorKit htmlKit = (HTMLEditorKit) editorKit;
            htmlKit.getStyleSheet().addRule(STYLE_SHEET_RULE);
        }
    }

    @Override
    public void setNode(Node node) {
        resetComponent();
        
        if(worker != null) {
            worker.cancel(true);
            worker = null;
        }
        
        if(node == null) {
            return;
        }

        worker = new AnnotationWorker(node);
        worker.execute();
    }


    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        textPanel = new javax.swing.JTextPane();

        setPreferredSize(new java.awt.Dimension(100, 58));

        textPanel.setEditable(false);
        textPanel.setName(""); // NOI18N
        textPanel.setPreferredSize(new java.awt.Dimension(600, 52));
        scrollPane.setViewportView(textPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 907, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane textPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public String getTitle() {
        return Bundle.AnnotationsContentViewer_title();
    }

    @Override
    public String getToolTip() {
        return Bundle.AnnotationsContentViewer_toolTip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new AnnotationsContentViewer();
    }

    @Override
    public boolean isSupported(Node node) {
        BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);

        try {
            if (artifact != null) {
                if (artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID()) != null) {
                    return true;
                }
            } else {
                if (node.getLookup().lookup(AbstractFile.class) != null) {
                    return true;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format(
                    "Exception while trying to retrieve a Content instance from the BlackboardArtifact '%s' (id=%d).",
                    artifact.getDisplayName(), artifact.getArtifactID()), ex);
        }

        return false;
    }

    @Override
    public int isPreferred(Node node) {
        return 1;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        textPanel.setText("");
    }
    
    /**
     * A SwingWorker that will fetch the annotation information for the given
     * node.
     */
    private class AnnotationWorker extends SwingWorker<String, Void> {
        private final Node node;
        
        AnnotationWorker(Node node) {
            this.node = node;
        }
        
        @Override
        protected String doInBackground() throws Exception {
           Document doc = Annotations.buildDocument(node);
           
           if(isCancelled()) {
               return null;
           }
           
           if(doc != null) {
               return doc.html();
           } else {
               return Bundle.AnnotationsContentViewer_onEmpty();
           }
        }
        
        @Override
        public void done() {
            if (isCancelled()) {
                return;
            }
            
            try {
                String text = get();
                textPanel.setText(text);
                textPanel.setCaretPosition(0);
            } catch (InterruptedException | ExecutionException ex) {
               logger.log(Level.SEVERE, "Failed to get annotation information for node", ex);
            } 
        }
    
    }
}
