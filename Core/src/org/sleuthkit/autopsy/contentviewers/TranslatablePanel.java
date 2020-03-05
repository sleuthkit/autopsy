/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.autopsy.texttranslation.ui.TranslateTextTask;

/**
 * A panel for translation with a subcomponent that allows for translation
 */
final class TranslatablePanel extends JPanel {

    
    interface ContentSetter {
        void set(String content, ComponentOrientation orientation, int font) throws Exception;
    }

    /**
     * an option in drop down of whether or not to translate
     */
    private static class TranslateOption {
        private final String text;
        private final boolean translate;

        public TranslateOption(String text, boolean translate) {
            this.text = text;
            this.translate = translate;
        }

        public String getText() {
            return text;
        }
        
        @Override
        public String toString() {
            return text;
        }

        public boolean shouldTranslate() {
            return translate;
        }
    }
    
    private static class TranslatedText {
        private final String text;
        private final ComponentOrientation orientation;

        public TranslatedText(String text, ComponentOrientation orientation) {
            this.text = text;
            this.orientation = orientation;
        }

        public String getText() {
            return text;
        }

        public ComponentOrientation getOrientation() {
            return orientation;
        }
    }
    
    private class OnTranslation extends TranslateTextTask {
        public OnTranslation() {
            super(true, contentDescriptor == null ? "" : contentDescriptor);
        }
        
        @Override
        protected String translate(String input) throws NoServiceProviderException, TranslationException {
            // defer to outer class method so that it can be overridden for items like html, rtf, etc.
            return retrieveTranslation(input);
        }

        @Override
        protected void onProgressDisplay(String text, ComponentOrientation orientation, int font) {
            setStatus(text, false);
        }

        @Override
        protected void onErrorDisplay(String text, ComponentOrientation orientation, int font) {
            setStatus(text, true);
        }

        @Override
        protected String retrieveText() throws IOException, InterruptedException, IllegalStateException {
            return content == null ? "" : content;
        }

        @Override
        protected void onTextDisplay(String text, ComponentOrientation orientation, int font) {
            // on successful acquire cache the result and set the text
            setCachedTranslated(new TranslatedText(text, orientation));
            setSubcomponentContent(text, orientation, font);
        }        
    }
    
    
    
    private static final long serialVersionUID = 1L;
    private static final ComponentOrientation DEFAULT_ORIENTATION = ComponentOrientation.LEFT_TO_RIGHT;
    private static final int DEFAULT_FONT = Font.PLAIN;
    
    
    private final ImageIcon warningIcon = new ImageIcon(TranslatablePanel.class.getResource("/org/sleuthkit/autopsy/images/warning16.png"));
    
    private final ContentSetter onContent;
    private final Component subcomponent;
    private final String origOptionText;
    private final String translatedOptionText;
    private final TextTranslationService translationService;
    private final ThreadFactory translationThreadFactory = new ThreadFactoryBuilder().setNameFormat("translatable-panel-%d").build();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(translationThreadFactory);
    
    
    private String content;
    private String contentDescriptor;
    private volatile TranslatedText cachedTranslated;
    private volatile OnTranslation backgroundTask = null;
    
    

    @Messages({"TranslatablePanel.comboBoxOption.originalText=Original Text",
        "TranslatablePanel.comboBoxOption.translatedText=Translated Text"}) 
    TranslatablePanel(Component subcomponent, ContentSetter onContent) {
        this(
            subcomponent,
            onContent,
            Bundle.TranslatablePanel_comboBoxOption_originalText(), 
            Bundle.TranslatablePanel_comboBoxOption_translatedText(), 
            null,
            TextTranslationService.getInstance());
    }
        
    /**
     * Creates new form TranslatedContentPanel
     */
    TranslatablePanel(Component subcomponent, ContentSetter onContent, String origOptionText, String translatedOptionText, String origContent, 
            TextTranslationService translationService) {
        this.subcomponent = subcomponent;
        this.onContent = onContent;
        this.origOptionText = origOptionText;
        this.translatedOptionText = translatedOptionText;
        this.translationService = translationService;
        
        initComponents();
        additionalInit();
        setTranslationBarVisible();
        reset();
    }

    private TranslatedText getCachedTranslated() {
        synchronized(cachedTranslated) {
            return cachedTranslated;    
        }
    }

    private void setCachedTranslated(TranslatedText translated) {
        synchronized(cachedTranslated) {
            this.cachedTranslated = translated;
        }   
    }
    
    private synchronized void cancelPendingTranslation() {
        if (backgroundTask != null && !backgroundTask.isDone()) {
            backgroundTask.cancel(true);
        }
        backgroundTask = null;
    }
    
    private synchronized void runTranslationTask() {
        cancelPendingTranslation();
        backgroundTask = new OnTranslation(); 

        //Pass the background task to a single threaded pool to keep
        //the number of jobs running to one.
        executorService.execute(backgroundTask);
    }
 
    
    void reset() {
        setTranslationBarVisible();
        setContent(null, null);
    }
    
    void setContent(String content, String contentDescriptor) {
        cancelPendingTranslation();
        this.translateComboBox.setSelectedIndex(0);
        this.content = content;
        this.contentDescriptor = contentDescriptor;
        setStatus(null, false);
        setCachedTranslated(null);
        setSubcomponentContent(content);
    }
    
    
    /**
     * where actual translation takes place
     * allowed to be overridden for the sake of varying translatable content (i.e. html, rtf, etc)
     * @param input         the input content
     * @return              the result of translation
     * @throws TranslationException
     * @throws NoServiceProviderException 
     */
    protected String retrieveTranslation(String input) throws TranslationException, NoServiceProviderException  {
        return translationService.translate(input);
    }
    
    
    
    private void setStatus(String msg, boolean showWarningIcon) {
        statusLabel.setText(msg);
        statusLabel.setIcon(showWarningIcon ? warningIcon : null);
    }

    private void setTranslationBarVisible() {
        translationBar.setVisible(this.translationService.hasProvider());
    }
    
    private void setSubcomponentContent(String content) {
        setSubcomponentContent(content, DEFAULT_ORIENTATION, DEFAULT_FONT);
    }
    
    @Messages({"# {0} - exception message", "TranslatablePanel.onSetContentError.text=There was an error displaying the text: {0}"}) 
    private void setSubcomponentContent(String content, ComponentOrientation orientation, int font) {
        SwingUtilities.invokeLater(() -> {
            try {
                this.onContent.set(content, orientation, font);
            } catch (Exception ex) {
                setStatus(Bundle.TranslatablePanel_onSetContentError_text(ex.getMessage()), true);
            }
        });
    }

    private void additionalInit() {
        add(this.subcomponent, java.awt.BorderLayout.CENTER);
        setStatus(null, false);
        translateComboBox.removeAllItems();
        translateComboBox.addItem(new TranslateOption(this.origOptionText, false));
        translateComboBox.addItem(new TranslateOption(this.translatedOptionText, true));
    }
    
    private void handleComboBoxChange(TranslateOption translateOption) {
        cancelPendingTranslation();
        SwingUtilities.invokeLater(() -> {
            if (translateOption.shouldTranslate()) {
                TranslatedText translated = getCachedTranslated();
                if (translated != null) {
                    setSubcomponentContent(translated.getText(), translated.getOrientation(), DEFAULT_FONT);
                }
                else {
                    runTranslationTask();
                }
            }
            else {
                setSubcomponentContent(content);
            }
        });
    }
    

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        translationBar = new javax.swing.JPanel();
        translateComboBox = new javax.swing.JComboBox<>();
        statusLabel = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(2000, 2000));
        setMinimumSize(new java.awt.Dimension(2, 2));
        setName(""); // NOI18N
        setPreferredSize(new java.awt.Dimension(100, 58));
        setVerifyInputWhenFocusTarget(false);
        setLayout(new java.awt.BorderLayout());

        translationBar.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        translationBar.setMaximumSize(new java.awt.Dimension(182, 24));
        translationBar.setPreferredSize(new java.awt.Dimension(182, 24));
        translationBar.setLayout(new java.awt.BorderLayout());

        translateComboBox.setMaximumSize(new java.awt.Dimension(200, 20));
        translateComboBox.setMinimumSize(new java.awt.Dimension(200, 20));
        translateComboBox.setName(""); // NOI18N
        translateComboBox.setPreferredSize(new java.awt.Dimension(200, 20));
        translateComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                translateComboBoxActionPerformed(evt);
            }
        });
        translationBar.add(translateComboBox, java.awt.BorderLayout.LINE_END);

        statusLabel.setMaximumSize(new java.awt.Dimension(32767, 32767));
        translationBar.add(statusLabel, java.awt.BorderLayout.CENTER);

        add(translationBar, java.awt.BorderLayout.NORTH);
    }// </editor-fold>//GEN-END:initComponents

    private void translateComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_translateComboBoxActionPerformed
        handleComboBoxChange((TranslateOption) translateComboBox.getSelectedItem());
    }//GEN-LAST:event_translateComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel statusLabel;
    private javax.swing.JComboBox<TranslateOption> translateComboBox;
    private javax.swing.JPanel translationBar;
    // End of variables declaration//GEN-END:variables
}