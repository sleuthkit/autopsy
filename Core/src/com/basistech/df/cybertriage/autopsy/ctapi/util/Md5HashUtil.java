/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2018 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
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
