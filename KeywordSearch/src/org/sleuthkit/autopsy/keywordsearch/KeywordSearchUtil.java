/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

public class KeywordSearchUtil {

    public enum DIALOG_MESSAGE_TYPE {

        ERROR, WARN, INFO
    };
    private static final Logger logger = Logger.getLogger(KeywordSearchUtil.class.getName());

    public static String buildDirName(FsContent f) {

        String dirName = null;
        StringBuilder dirNameB = new StringBuilder();
        try {

            Directory pd = f.getParentDirectory();

            while (pd != null && pd.isRoot() == false) {
                dirNameB.insert(0, "/");
                dirNameB.insert(0, pd.getName());
                pd = pd.getParentDirectory();
            }
            dirNameB.insert(0, "/");

        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting path for fscontent id: " + Long.toString(f.getId()), ex);
        } finally {
            dirName = dirNameB.toString();
        }
        return dirName;
    }

    /**
     * Perform standard escaping / encoding into UTF-8 before sending over net
     * @param query to be encoded
     * @param escapeLuceneChars if true perform first escaping of Lucene specific special chars
     * such as /+-&|!(){}[]^"~*?:\ and treat the whole query as literal word
     * @return encoded query
     */
    public static String escapeLuceneQuery(String query, boolean escapeLuceneChars, boolean encode) {
        String queryEscaped = null;
        String inputString = query;

        if (escapeLuceneChars == true) {
            final String ESCAPE_CHARS = "/+-&|!(){}[]^\"~*?:\\";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputString.length(); ++i) {
                char c = inputString.charAt(i);
                if (ESCAPE_CHARS.contains(Character.toString(c))) {
                    sb.append("\\");
                }
                sb.append(c);
            }
            queryEscaped = inputString = sb.toString();
        }

        if (encode) {
            try {
                queryEscaped = URLEncoder.encode(inputString, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                logger.log(Level.SEVERE, "Error escaping URL query, should not happen.", ex);
                queryEscaped = query;
            }
        }
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

        final Component parentComponent = null; // Use default window frame.
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
        if (JOptionPane.showConfirmDialog(null, message, title, JOptionPane.YES_NO_OPTION, messageType) == JOptionPane.YES_OPTION) {
            return true;
        } else {
            return false;
        }
    }
    
    //TODO escaping should be handled by blackboard
    public static String escapeForBlackBoard(String text) {
        try {
            text = text.replaceAll("\\\\", URLEncoder.encode("\\", "UTF-8"));
            text = text.replaceAll("'", URLEncoder.encode("'", "UTF-8"));
            text = text.replaceAll("=", URLEncoder.encode("=", "UTF-8"));
            text = text.replaceAll("\\?", URLEncoder.encode("?", "UTF-8"));
            text = text.replaceAll(">", URLEncoder.encode(">", "UTF-8"));
            text = text.replaceAll("<", URLEncoder.encode("<", "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
        }
        return text;
    }
}
