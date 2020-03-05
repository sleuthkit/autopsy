/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2020 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

/**
 * provides the interface to be injected into the TranslatablePanel and displays text
 */
final class TextTranslatableComponent implements TranslatablePanel.TranslatableComponent {

    private static final Logger LOGGER = Logger.getLogger(TextTranslatableComponent.class.getName());
    private final Component parentComponent;
    private final JTextArea textComponent;
    private final TextTranslationService translationService;
    private boolean translate = false;
    private String translated = null;
    private String origContent = "";

    TextTranslatableComponent() {
        JTextArea textComponent = new JTextArea();
        textComponent.setEditable(false);
        textComponent.setLineWrap(true);
        textComponent.setRows(5);
        textComponent.setWrapStyleWord(true);
        
        JScrollPane parentComponent = new JScrollPane();
        parentComponent.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        parentComponent.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        parentComponent.setViewportView(textComponent);
        this.parentComponent = parentComponent;
        this.textComponent = textComponent;
        this.translationService = TextTranslationService.getInstance();
    }

    TextTranslatableComponent(Component parentComponent, JTextArea textComponent, TextTranslationService translationService) {
        this.parentComponent = parentComponent;
        this.textComponent = textComponent;
        this.translationService = translationService;
    }

    public Component getComponent() {
        return parentComponent;
    }

    public String getContent() {
        return origContent;
    }

    public boolean isTranslated() {
        return translate;
    }

    private boolean setPanelContent(String content) {
        try {
            textComponent.setText(content == null ? "" : content);
            textComponent.setCaretPosition(0);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "There was an error in setting up the text for MessageContentViewer text panel", e);
            return false;
        }
    }

    @NbBundle.Messages(value = "TextTranslatableComponent.setPanelContent.onSetContentError=Unable to display text at this time.")
    private String onErr(boolean success) {
        return (success) ? null : Bundle.TextTranslatableComponent_setPanelContent_onSetContentError();
    }

    public String setContent(String content) {
        this.origContent = content;
        this.translated = null;
        this.translate = false;
        return onErr(setPanelContent(content));
    }

    @NbBundle.Messages(value = "TextTranslatableComponent.setTranslated.onTranslateError=Unable to translate text at this time.")
    public String setTranslated(boolean translate) {
        this.translate = translate;
        if (this.translate) {
            if (this.translated == null) {
                final String originalContent = this.origContent;
                SwingUtilities.invokeLater(() -> {
                    try {
                        this.translated = this.translationService.translate(originalContent);
                    } catch (NoServiceProviderException | TranslationException ex) {
                        LOGGER.log(Level.WARNING, "Unable to translate text with translation service", ex);
                        //return Bundle.TextTranslatableComponent_setTranslated_onTranslateError();
                    }
                });
            }
            return onErr(setPanelContent(this.translated == null ? "" : this.translated));
        } else {
            return onErr(setPanelContent(this.origContent));
        }
    }
    
}
