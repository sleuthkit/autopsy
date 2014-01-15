/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Methods common to ContentViewers.
 */
public class Utilities {

    /**
     * Check if is jpeg file based on header
     *
     * @param file
     *
     * @return true if jpeg file, false otherwise
     */
    @SuppressWarnings("cast")
    public static boolean isJpegFileHeader(AbstractFile file) {
        if (file.getSize() < 100) {
            return false;
        }

        byte[] fileHeaderBuffer = new byte[2];
        int bytesRead;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, 2);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a JPEG
            return false;
        }
        if (bytesRead != 2) {
            return false;
        }
        /*
         * Check for the JPEG header. Since Java bytes are signed, we cast them
         * to an int first.
         */
        if (((int) (fileHeaderBuffer[0] & 0xff) == 0xff) && ((int) (fileHeaderBuffer[1] & 0xff) == 0xd8)) {
            return true;
        }
        return false;
    }

    public static void configureTextPaneAsHtml(JTextPane pane) {
        pane.setContentType("text/html;charset=UTF-8");
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
        styleSheet.addRule("body {font-family:Arial;font-size:14pt;}");
        styleSheet.addRule("p {font-family:Arial;font-size:14pt;}");
        styleSheet.addRule("li {font-family:Arial;font-size:14pt;}");
        styleSheet.addRule("td {font-family:Arial;font-size:14pt;overflow:hidden;padding-right:5px;padding-left:5px;}");
        styleSheet.addRule("th {font-family:Arial;font-size:14pt;overflow:hidden;padding-right:5px;padding-left:5px;font-weight:bold;}");
        styleSheet.addRule("p {font-family:Arial;font-size:14pt;}");
    }
}
