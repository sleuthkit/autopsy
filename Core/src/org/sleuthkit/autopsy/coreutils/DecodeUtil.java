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
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Decoding utilities.
 */
public class DecodeUtil {
    private static final Logger logger = Logger.getLogger(DecodeUtil.class.getName());
    
    /**
     * Decode the given url in UTF-8. 
     * @param url the url to be decoded
     * @return the decoded URL
     * @throws UnsupportedEncodingException if the format UTF-8 doesn't exist.
     */
    public static String decodeURL(String url) throws UnsupportedEncodingException {
        return URLDecoder.decode(url, "UTF-8");
    }
    
}
