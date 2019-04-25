/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.translation;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 * A JPanel used by TranslatedContentViewer to display machine translation of
 * text.
 */
public class TranslationContentPanel extends javax.swing.JPanel {

    private static final ImmutableBiMap<String, String> LANGUAGE_NAMES;
    private static final long serialVersionUID = 1L;
    private int lastSelectedIndex = 0;

    /**
     * Creates new form TranslatedContentPanel
     */
    public TranslationContentPanel() {
        initComponents();
        reset();
    }

    final void removeDisplayTextOptions(String option) {
        displayTextComboBox.removeItem(option);
    }

    public void display(String text, ComponentOrientation direction, int font) {
        displayTextArea.setComponentOrientation(direction);
        displayTextArea.setText(text);
        displayTextArea.setFont(new Font(displayTextArea.getFont().getName(),
                font, displayTextArea.getFont().getSize()));
        displayTextArea.setCaretPosition(0);
    }

    void addDisplayTextActionListener(ActionListener listener) {
        displayTextComboBox.addActionListener(listener);
    }

    final void addOcrDropDownActionListener(ActionListener listener) {
        ocrDropdown.addActionListener(listener);
    }

    String getDisplayDropDownSelection() {
        return (String) displayTextComboBox.getSelectedItem();
    }

    String getSelectedOcrLanguagePack() {
        String selection = (String) ocrDropdown.getSelectedItem();
        return Strings.nullToEmpty(LANGUAGE_NAMES.inverse().get(selection));
    }

    /**
     * Remove all action listeners from a JComboBox.
     *
     * @param dropDown Src ComboBox to remove listeners from
     */
    private void removeAllActionListeners(JComboBox<String> dropDown) {
        for (ActionListener listener : dropDown.getActionListeners()) {
            dropDown.removeActionListener(listener);
        }
    }

    @NbBundle.Messages({"TranslationContentPanel.autoDetectOCR=Autodetect language"})
    final void reset() {
        display("", ComponentOrientation.LEFT_TO_RIGHT, Font.PLAIN);

        removeAllActionListeners(displayTextComboBox);
        displayTextComboBox.removeAllItems();
        displayTextComboBox.addItem(DisplayDropdownOptions.TRANSLATED_TEXT.toString());
        displayTextComboBox.addItem(DisplayDropdownOptions.ORIGINAL_TEXT.toString());

        enableOCRSelection(false);
        removeAllActionListeners(ocrDropdown);
        ocrDropdown.removeAllItems();
    }

    void enableOCRSelection(boolean enabled) {
        ocrLabel.setEnabled(enabled);
        ocrDropdown.setEnabled(enabled);
    }

    /**
     * Populates the OCR drop-down menu with installed language packs.
     *
     * @param languagePackAbbreviations
     */
    void addLanguagePackNames(List<String> languagePackAbbreviations) {
        //Put the default at the top of the list.
        ocrDropdown.addItem(Bundle.TranslationContentPanel_autoDetectOCR());

        //Create a shallow copy of the input list for sorting
        List<String> localCopy = new ArrayList<>(languagePackAbbreviations);

        localCopy.stream().sorted().forEach((abbrev) -> {
            if (LANGUAGE_NAMES.get(abbrev) != null) {
                ocrDropdown.addItem(LANGUAGE_NAMES.get(abbrev));
            }
        });

        ocrDropdown.setSelectedIndex(lastSelectedIndex);
        addOcrDropDownActionListener((ActionEvent e) -> {
            lastSelectedIndex = ocrDropdown.getSelectedIndex();
        });
    }

    private static ImmutableBiMap<String, String> createLanguageBiMap() {
        return ImmutableBiMap.<String, String>builder()
                .put("afr", "Afrikaans")
                .put("amh", "Amharic")
                .put("ara", "Arabic")
                .put("asm", "Assamese")
                .put("aze", "Azerbaijani")
                .put("aze_cyrl", "Azerbaijani - Cyrillic")
                .put("bel", "Belarusian")
                .put("ben", "Bengali")
                .put("bod", "Tibetan")
                .put("bos", "Bosnian")
                .put("bul", "Bulgarian")
                .put("cat", "Catalan; Valencian")
                .put("ceb", "Cebuano")
                .put("ces", "Czech")
                .put("chi_sim", "Chinese - Simplified")
                .put("chi_tra", "Chinese - Traditional")
                .put("chr", "Cherokee")
                .put("cym", "Welsh")
                .put("dan", "Danish")
                .put("deu", "German")
                .put("dzo", "Dzongkha")
                .put("ell", "Greek, Modern (1453-)")
                .put("eng", "English")
                .put("enm", "English, Middle (1100-1500)")
                .put("epo", "Esperanto")
                .put("est", "Estonian")
                .put("eus", "Basque")
                .put("fas", "Persian")
                .put("fin", "Finnish")
                .put("fra", "French")
                .put("frk", "Frankish")
                .put("frm", "French, Middle (ca. 1400-1600)")
                .put("gle", "Irish")
                .put("glg", "Galician")
                .put("grc", "Greek, Ancient (-1453)")
                .put("guj", "Gujarati")
                .put("hat", "Haitian; Haitian Creole")
                .put("heb", "Hebrew")
                .put("hin", "Hindi")
                .put("hrv", "Croatian")
                .put("hun", "Hungarian")
                .put("iku", "Inuktitut")
                .put("ind", "Indonesian")
                .put("isl", "Icelandic")
                .put("ita", "Italian")
                .put("ita_old", "Italian - Old")
                .put("jav", "Javanese")
                .put("jpn", "Japanese")
                .put("kan", "Kannada")
                .put("kat", "Georgian")
                .put("kat_old", "Georgian - Old")
                .put("kaz", "Kazakh")
                .put("khm", "Central Khmer")
                .put("kir", "Kirghiz; Kyrgyz")
                .put("kor", "Korean")
                .put("kur", "Kurdish")
                .put("lao", "Lao")
                .put("lat", "Latin")
                .put("lav", "Latvian")
                .put("lit", "Lithuanian")
                .put("mal", "Malayalam")
                .put("mar", "Marathi")
                .put("mkd", "Macedonian")
                .put("mlt", "Maltese")
                .put("msa", "Malay")
                .put("mya", "Burmese")
                .put("nep", "Nepali")
                .put("nld", "Dutch; Flemish")
                .put("nor", "Norwegian")
                .put("ori", "Oriya")
                .put("pan", "Panjabi; Punjabi")
                .put("pol", "Polish")
                .put("por", "Portuguese")
                .put("pus", "Pushto; Pashto")
                .put("ron", "Romanian; Moldavian; Moldovan")
                .put("rus", "Russian")
                .put("san", "Sanskrit")
                .put("sin", "Sinhala; Sinhalese")
                .put("slk", "Slovak")
                .put("slv", "Slovenian")
                .put("spa", "Spanish; Castilian")
                .put("spa_old", "Spanish; Castilian - Old")
                .put("sqi", "Albanian")
                .put("srp", "Serbian")
                .put("srp_latn", "Serbian - Latin")
                .put("swa", "Swahili")
                .put("swe", "Swedish")
                .put("syr", "Syriac")
                .put("tam", "Tamil")
                .put("tel", "Telugu")
                .put("tgk", "Tajik")
                .put("tgl", "Tagalog")
                .put("tha", "Thai")
                .put("tir", "Tigrinya")
                .put("tur", "Turkish")
                .put("uig", "Uighur; Uyghur")
                .put("ukr", "Ukrainian")
                .put("urd", "Urdu")
                .put("uzb", "Uzbek")
                .put("uzb_cyrl", "Uzbek - Cyrillic")
                .put("vie", "Vietnamese")
                .put("yid", "Yiddish")
                .build();
    }

    static {
        //https://github.com/tesseract-ocr/tesseract/wiki/Data-Files
        LANGUAGE_NAMES = createLanguageBiMap();
    }

    /**
     * Selection choices to be displayed in the combobox dropdown.
     */
    @Messages({"TranslatedContentPanel.comboBoxOption.originalText=Original Text",
        "TranslatedContentPanel.comboBoxOption.translatedText=Translated Text"})
    static enum DisplayDropdownOptions {
        ORIGINAL_TEXT(Bundle.TranslatedContentPanel_comboBoxOption_originalText()),
        TRANSLATED_TEXT(Bundle.TranslatedContentPanel_comboBoxOption_translatedText());

        private final String displayString;

        DisplayDropdownOptions(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
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

        jPanel1 = new javax.swing.JPanel();
        ShowLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jSeparator2 = new javax.swing.JSeparator();
        displayTextComboBox = new javax.swing.JComboBox<>();
        ocrDropdown = new javax.swing.JComboBox<>();
        ocrLabel = new javax.swing.JLabel();
        warningLabel2MB = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        displayTextArea = new javax.swing.JTextArea();

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanel1.setPreferredSize(new java.awt.Dimension(600, 81));

        org.openide.awt.Mnemonics.setLocalizedText(ShowLabel, org.openide.util.NbBundle.getMessage(TranslationContentPanel.class, "TranslationContentPanel.ShowLabel.text")); // NOI18N

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

        displayTextComboBox.setMinimumSize(new java.awt.Dimension(150, 20));
        displayTextComboBox.setPreferredSize(new java.awt.Dimension(150, 20));

        ocrDropdown.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "N/A" }));
        ocrDropdown.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(ocrLabel, org.openide.util.NbBundle.getMessage(TranslationContentPanel.class, "TranslationContentPanel.ocrLabel.text")); // NOI18N
        ocrLabel.setEnabled(false);

        warningLabel2MB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(warningLabel2MB, org.openide.util.NbBundle.getMessage(TranslationContentPanel.class, "TranslationContentPanel.warningLabel2MB.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(warningLabel2MB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(ShowLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(displayTextComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ocrLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ocrDropdown, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addGap(279, 279, 279)
                    .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(204, 204, 204)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(242, Short.MAX_VALUE)))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(displayTextComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE)
                        .addComponent(ShowLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ocrLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(warningLabel2MB))
                    .addComponent(ocrDropdown))
                .addGap(7, 7, 7))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addContainerGap(35, Short.MAX_VALUE)))
        );

        displayTextArea.setEditable(false);
        displayTextArea.setColumns(20);
        displayTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        displayTextArea.setLineWrap(true);
        displayTextArea.setRows(5);
        displayTextArea.setMaximumSize(new java.awt.Dimension(2000, 2000));
        jScrollPane1.setViewportView(displayTextArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 628, Short.MAX_VALUE)
            .addComponent(jScrollPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 66, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel ShowLabel;
    private javax.swing.JTextArea displayTextArea;
    private javax.swing.JComboBox<String> displayTextComboBox;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JComboBox<String> ocrDropdown;
    private javax.swing.JLabel ocrLabel;
    private javax.swing.JLabel warningLabel2MB;
    // End of variables declaration//GEN-END:variables
}
