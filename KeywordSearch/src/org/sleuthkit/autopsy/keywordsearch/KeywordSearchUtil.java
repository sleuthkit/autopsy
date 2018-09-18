/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.io.File;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JOptionPane;
import org.openide.windows.WindowManager;

class KeywordSearchUtil {

    public enum DIALOG_MESSAGE_TYPE {

        ERROR, WARN, INFO
    };
    private static final Logger logger = Logger.getLogger(KeywordSearchUtil.class.getName());

    /**
     * Return a quoted version of the query if the original query is not quoted
     *
     * @param query the query to check if it is quoted
     *
     * @return quoted query
     */
    public static String quoteQuery(String query) {
        //ensure a single pair of quotes around the query
        final int length = query.length();
        if (length > 1 && query.charAt(0) == '"'
                && query.charAt(length - 1) == '"') {
            return query;
        }

        return "\""+query+"\"";
    }

    /**
     * Perform standard escaping of Solr chars such as /+-&|!(){}[]^"~*?:\
     * before sending over net does not escape the outter enclosing double
     * quotes, if present
     *
     * @param query to be encoded
     *
     * @return encoded query
     */
    public static String escapeLuceneQuery(String query) {
        String queryEscaped = null;
        String inputString = query.trim();

        if (inputString.length() == 0) {
            return inputString;
        }

        final String ESCAPE_CHARS = "/+-&|!(){}[]^\"~*?:\\";
        StringBuilder sb = new StringBuilder();
        final int length = inputString.length();

        //see if the quoery is quoted
        boolean quotedQuery = false;
        if (length > 1 && inputString.charAt(0) == '"' && inputString.charAt(length - 1) == '"') {
            quotedQuery = true;
        }

        for (int i = 0; i < length; ++i) {
            final char c = inputString.charAt(i);

            if (ESCAPE_CHARS.contains(Character.toString(c))) {
                //escape if not outter quotes
                if (quotedQuery == false || (i > 0 && i < length - 1)) {
                    sb.append("\\");
                }
            }
            sb.append(c);
        }
        queryEscaped = inputString = sb.toString();

        return queryEscaped;
    }

    public static void displayDialog(final String title, final String message, final DIALOG_MESSAGE_TYPE type) {
        int messageType;
        if (type == DIALOG_MESSAGE_TYPE.ERROR) {
            messageType = JOptionPane.ERROR_MESSAGE;
        } else if (type == DIALOG_MESSAGE_TYPE.WARN) {
            messageType = JOptionPane.WARNING_MESSAGE;
        } else {
            messageType = JOptionPane.INFORMATION_MESSAGE;
        }

        final Component parentComponent = WindowManager.getDefault().getMainWindow();
        JOptionPane.showMessageDialog(
                parentComponent,
                message,
                title,
                messageType);
    }

    public static boolean displayConfirmDialog(final String title, final String message, final DIALOG_MESSAGE_TYPE type) {
        int messageType;
        if (type == DIALOG_MESSAGE_TYPE.ERROR) {
            messageType = JOptionPane.ERROR_MESSAGE;
        } else if (type == DIALOG_MESSAGE_TYPE.WARN) {
            messageType = JOptionPane.WARNING_MESSAGE;
        } else {
            messageType = JOptionPane.INFORMATION_MESSAGE;
        }
        if (JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), message, title, JOptionPane.YES_NO_OPTION, messageType) == JOptionPane.YES_OPTION) {
            return true;
        } else {
            return false;
        }
    }
    
    static KeywordSearchQuery getQueryForKeyword(Keyword keyword, KeywordList keywordList) {
        KeywordSearchQuery query = null;
        if (keyword.searchTermIsLiteral() && keyword.searchTermIsWholeWord()) {
            // literal, exact match
            query = new LuceneQuery(keywordList, keyword);
            query.escape();
        } // regexp and literal substring match
        else {
            query = new RegexQuery(keywordList, keyword);
            if (keyword.searchTermIsLiteral()) {
                query.escape();
            }
        }
        return query;
    }

    /**
     * Is the Keyword Search list at absPath an XML list?
     *
     * @param absPath
     *
     * @return yes or no
     */
    static boolean isXMLList(String absPath) {
        //TODO: make this more robust, if necessary
        return new File(absPath).getName().endsWith(".xml"); //NON-NLS
    }
}
