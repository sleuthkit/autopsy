/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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

import javax.swing.JTextPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.rtf.RTFEditorKit;

/**
 *
 * Methods common to ContentViewers.
 */
public class Utilities {

    public static void configureTextPaneAsHtml(JTextPane pane) {
        pane.setContentType("text/html;charset=UTF-8"); //NON-NLS
        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);
        StyleSheet styleSheet = kit.getStyleSheet();
        /*
         * I tried to play around with inheritence on font-size and it didn't
         * always work. Defined all of the basics just in case. @@@
         * IngestInboxViewer also defines styles similar to this. Consider a
         * method that sets consistent styles for all viewers and takes font
         * size as an argument.
         */
        styleSheet.addRule("body {font-family:Arial, 'ヒラギノ角ゴ Pro W3','Hiragino Kaku Gothic Pro','メイリオ',Meiryo,'ＭＳ Ｐゴシック','MS PGothic',sans-serif;font-size:14pt;}"); //NON-NLS
        styleSheet.addRule("p {font-family:Arial, 'ヒラギノ角ゴ Pro W3','Hiragino Kaku Gothic Pro','メイリオ',Meiryo,'ＭＳ Ｐゴシック','MS PGothic',sans-serif;font-size:14pt;}"); //NON-NLS
        styleSheet.addRule("li {font-family:Arial, 'ヒラギノ角ゴ Pro W3','Hiragino Kaku Gothic Pro','メイリオ',Meiryo,'ＭＳ Ｐゴシック','MS PGothic',sans-serif;font-size:14pt;}"); //NON-NLS
        styleSheet.addRule("td {font-family:Arial, 'ヒラギノ角ゴ Pro W3','Hiragino Kaku Gothic Pro','メイリオ',Meiryo,'ＭＳ Ｐゴシック','MS PGothic',sans-serif;font-size:14pt;overflow:hidden;padding-right:5px;padding-left:5px;}"); //NON-NLS
        styleSheet.addRule("th {font-family:Arial, 'ヒラギノ角ゴ Pro W3','Hiragino Kaku Gothic Pro','メイリオ',Meiryo,'ＭＳ Ｐゴシック','MS PGothic',sans-serif;font-size:14pt;overflow:hidden;padding-right:5px;padding-left:5px;font-weight:bold;}"); //NON-NLS
        styleSheet.addRule("p {font-family:Arial, 'ヒラギノ角ゴ Pro W3','Hiragino Kaku Gothic Pro','メイリオ',Meiryo,'ＭＳ Ｐゴシック','MS PGothic',sans-serif;font-size:14pt;}"); //NON-NLS
    }
    
     public static void configureTextPaneAsRtf(JTextPane pane) {
         
        pane.setContentType("text/html;charset=UTF-8"); //NON-NLS
        RTFEditorKit rtfkit = new RTFEditorKit();
        pane.setEditorKit(rtfkit);
    }
}
