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
 * This is a panel for translation with a subcomponent that allows for translation.
 */
public class TranslatablePanel extends JPanel {
    
    /**
     * This is an exception that can occur during the normal operation of the translatable 
     * panel. For instance, this exception can be thrown if it is not possible to set the child
     * content to the provided content string.
     */
    public class TranslatablePanelException extends Exception {
        public static final long serialVersionUID = 1L;

        TranslatablePanelException(String message) {
            super(message);
        }

        TranslatablePanelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    

    /**
     * This describes a child component to be placed as a child of this panel.  The child received
     * from {@link #getRootComponent() getRootComponent() } will listen for content updates from setContent().
     */
    public interface ContentComponent {
        /**
         * This method gets root component of the translation panel.
         * @return      the root component to insert into the translatable panel
         */
        Component getRootComponent();

        /**
         * This method sets the content of the component to the provided content.
         * @param content       the content to be displayed
         * @param orientation   how it should be displayed
         * @throws Exception    if there is an error in rendering the content
         */
        void setContent(String content, ComponentOrientation orientation) throws TranslatablePanelException;
    }
    
    
    /**
     * This is an option in drop down of whether or not to translate.
     */
    private static class TranslateOption {

        private final String text;
        private final boolean translate;

        TranslateOption(String text, boolean translate) {
            this.text = text;
            this.translate = translate;
        }

        String getText() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }

        boolean shouldTranslate() {
            return translate;
        }
    }

    /**
     * This represents the cached result of translating the current content.
     */
    private static class TranslatedText {

        private final String text;
        private final ComponentOrientation orientation;

        TranslatedText(String text, ComponentOrientation orientation) {
            this.text = text;
            this.orientation = orientation;
        }

        String getText() {
            return text;
        }

        ComponentOrientation getOrientation() {
            return orientation;
        }
    }

    /**
     * This connects the swing worker specified by 
     * {@link org.sleuthkit.autopsy.texttranslation.ui.TranslateTextTask TranslateTextTask} to this component.
     */
    private class OnTranslation extends TranslateTextTask {

        OnTranslation() {
            super(true, contentDescriptor == null ? "" : contentDescriptor);
        }

        @Override
        protected String translate(String input) throws NoServiceProviderException, TranslationException {
            // This defers to the outer class method so that it can be overridden for items like html, rtf, etc.
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
            // On successful acquire, this caches the result and set the text.
            setCachedTranslated(new TranslatedText(text, orientation));
            setChildComponentContent(text, orientation);

            // This clears any status that may be present.
            clearStatus();
        }
    }

    private static final long serialVersionUID = 1L;
    private static final ComponentOrientation DEFAULT_ORIENTATION = ComponentOrientation.LEFT_TO_RIGHT;

    private final ImageIcon warningIcon = new ImageIcon(TranslatablePanel.class.getResource("/org/sleuthkit/autopsy/images/warning16.png"));

    private final ContentComponent contentComponent;
    private final TextTranslationService translationService;
    private final ThreadFactory translationThreadFactory = new ThreadFactoryBuilder().setNameFormat("translatable-panel-%d").build();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(translationThreadFactory);

    private final Object cachedTranslatedLock = new Object();
    private final Object backgroundTaskLock = new Object();

    private String content;
    private String contentDescriptor;
    private boolean prevTranslateSelection;

    private volatile TranslatedText cachedTranslated;
    private volatile OnTranslation backgroundTask = null;

    @Messages({"TranslatablePanel.comboBoxOption.originalText=Original Text",
        "TranslatablePanel.comboBoxOption.translatedText=Translated Text"})
    public TranslatablePanel(ContentComponent contentComponent) {
        this(
            contentComponent,
            Bundle.TranslatablePanel_comboBoxOption_originalText(),
            Bundle.TranslatablePanel_comboBoxOption_translatedText(),
            null,
            TextTranslationService.getInstance());
    }

    /**
     * This creates a new panel using @{link ContentPanel ContentPanel} as a child.
     */
    TranslatablePanel(ContentComponent contentComponent, String origOptionText, String translatedOptionText, String origContent,
            TextTranslationService translationService) {
        this.contentComponent = contentComponent;
        this.translationService = translationService;

        initComponents();
        additionalInit(contentComponent.getRootComponent(), origOptionText, translatedOptionText);
        reset();
    }
    


    /**
     * @return  the cached translated text or returns null
     */
    private TranslatedText getCachedTranslated() {
        synchronized (cachedTranslatedLock) {
            return cachedTranslated;
        }
    }

    /**
     * @param translated    the translated text to be cached
     */
    private void setCachedTranslated(TranslatedText translated) {
        synchronized (cachedTranslatedLock) {
            this.cachedTranslated = translated;
        }
    }

    /**
     * If a translation worker is running, this is called to cancel the worker.
     */
    private void cancelPendingTranslation() {
        synchronized (backgroundTaskLock) {
            if (backgroundTask != null && !backgroundTask.isDone()) {
                backgroundTask.cancel(true);
            }
            backgroundTask = null;
        }
    }

    /**
     * This runs a translation worker to translate the text.
     */
    private void runTranslationTask() {
        synchronized (backgroundTaskLock) {
            cancelPendingTranslation();
            backgroundTask = new OnTranslation();

            //Pass the background task to a single threaded pool to keep
            //the number of jobs running to one.
            executorService.execute(backgroundTask);
        }
    }

    /**
     * This resets the component to an empty state and sets the translation bar visibility
     * based on whether there is a provider.
     */
    public final void reset() {
        setContent(null, null);
    }

    /**
     * This method sets the content for the component; this also clears the status.
     * @param content               the content for the panel
     * @param contentDescriptor     the content descriptor to be used in error messages
     */
    public void setContent(String content, String contentDescriptor) {
        cancelPendingTranslation();
        setTranslationEnabled();
        this.translateComboBox.setSelectedIndex(0);
        this.prevTranslateSelection = false;
        this.content = content;
        this.contentDescriptor = contentDescriptor;
        clearStatus();
        setCachedTranslated(null);
        setChildComponentContent(content);
    }

    /**
     * This is where actual translation takes place allowed to be overridden for the
     * sake of varying translatable content (i.e. html, rtf, etc).
     *
     * @param input the input content
     * @return the result of translation
     * @throws TranslationException
     * @throws NoServiceProviderException
     */
    protected String retrieveTranslation(String input) throws TranslationException, NoServiceProviderException {
        return translationService.translate(input);
    }

    /**
     * This method clears the status bar.
     */
    private void clearStatus() {
        setStatus(null, false);
    }

    /**
     * This sets the status bar message.
     * @param msg               the status bar message to show
     * @param showWarningIcon   whether that status is a warning
     */
    private synchronized void setStatus(String msg, boolean showWarningIcon) {
        statusLabel.setText(msg);
        statusLabel.setIcon(showWarningIcon ? warningIcon : null);
    }

    /**
     * This method sets the translation bar visibility based on whether or not there is a provided.
     */
    private void setTranslationEnabled() {
        translateComboBox.setEnabled(this.translationService.hasProvider());
    }

    /**
     * The child component provided in the constructor will have its content set to the string provided.
     * @param content   the content to display in the child component
     */
    private void setChildComponentContent(String content) {
        setChildComponentContent(content, DEFAULT_ORIENTATION);
    }

    /**
     * The child component provided in the constructor will have its content set to the string provided.
     * @param content   the content to display in the child component
     * @param orientation the orientation for the text
     */
    @Messages({"# {0} - exception message", "TranslatablePanel.onSetContentError.text=There was an error displaying the text: {0}"})
    private synchronized void setChildComponentContent(String content, ComponentOrientation orientation) {
        SwingUtilities.invokeLater(() -> {
            try {
                contentComponent.setContent(content, orientation);
            } catch (TranslatablePanelException ex) {
                setStatus(Bundle.TranslatablePanel_onSetContentError_text(ex.getMessage()), true);
            }
        });
    }

    /**
     * This method is for items that are programmatically initialized.
     */
    private void additionalInit(Component rootComponent, String origOptionText, String translatedOptionText) {
        add(rootComponent, java.awt.BorderLayout.CENTER);
        translateComboBox.removeAllItems();
        translateComboBox.addItem(new TranslateOption(origOptionText, false));
        translateComboBox.addItem(new TranslateOption(translatedOptionText, true));
    }

    /**
     * When the combo box choice is selected, this method is fired.
     * @param translateOption   the current translate option
     */
    private void handleComboBoxChange(TranslateOption translateOption) {
        boolean curTranslateSelection = translateOption.shouldTranslate();
        if (curTranslateSelection != this.prevTranslateSelection) {
            this.prevTranslateSelection = curTranslateSelection;
            
            cancelPendingTranslation();
            clearStatus();

            if (curTranslateSelection) {
                TranslatedText translated = getCachedTranslated();
                if (translated != null) {
                    setChildComponentContent(translated.getText(), translated.getOrientation());
                } else {
                    runTranslationTask();
                }
            } else {
                setChildComponentContent(content);
            }    
        }
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
