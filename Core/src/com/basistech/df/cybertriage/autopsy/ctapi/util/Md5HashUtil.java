/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.ctapi.util;
import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.StringUtils;
/**
 *
 * @author jayaram
 */
public class Md5HashUtil {
     /**
     * Returns MD5 hash value for the lower case value of the string provided.
     * @param inp
     * @return 
     */
    public static String getMD5MessageDigest(String inp) {
        if (StringUtils.isNotBlank(inp)) {
            HashFunction hf = Hashing.md5(); // Using despite its deprecation as md5 is good enough for our uses.  
            HashCode hc = hf.newHasher()
                    .putString(inp.toLowerCase(), Charsets.UTF_8)
                    .hash();
            return hc.toString();
        }
        return "";
    }
}
