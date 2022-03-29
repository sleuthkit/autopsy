/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.logging.Level;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Text escaping utilities.
 */
public class EscapeUtil {

    private static final Logger logger = Logger.getLogger(EscapeUtil.class.getName());

    /**
     * Decode the given url in UTF-8.
     *
     * @param url the url to be decoded
     *
     * @return the decoded URL
     */
    public static String decodeURL(String url) {
        try {
            return URLDecoder.decode(url, "UTF-8"); //NON-NLS
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Could not decode URL " + url, ex); //NON-NLS
            //should not happen
            return "";
        }
    }

    /**
     * Encode the given url in UTF-8 to
     *
     * @param url the url to be decoded
     *
     * @return the encoded URL string
     */
    public static String encodeURL(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8"); //NON-NLS
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.SEVERE, "Could not encode URL " + url, ex); //NON-NLS
            //should not happen
            return "";
        }
    }

    /**
     * Escape html
     *
     * @param toEscape text (with html tags) to escape
     *
     * @return html-escaped string
     */
    public static String escapeHtml(String toEscape) {
        return StringEscapeUtils.escapeHtml4(toEscape);
    }

    /**
     * Unescape html
     *
     * @param toUnescape text (potentially escaped html) to unescape
     *
     * @return html unescaped string
     */
    public static String unEscapeHtml(String toUnescape) {
        return StringEscapeUtils.unescapeHtml4(toUnescape);
    }
}
