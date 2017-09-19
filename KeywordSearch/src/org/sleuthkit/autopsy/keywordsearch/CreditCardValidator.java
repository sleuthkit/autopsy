/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import com.google.common.base.CharMatcher;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;

/**
 *
 */
final class CreditCardValidator {

    CreditCardValidator() {
    }

    private static final LuhnCheckDigit CREDIT_CARD_NUM_LUHN_CHECK = new LuhnCheckDigit();

    /**
     * Does the given string represent a valid credit card number? It must have
     * no separators, or only '-', or only ' '. If it has separators and is 16
     * digits, they must be grouped in to four groups of four.
     *
     * @param rawCCN
     *
     * @return True if rawCCN represents a valid credit card number.
     */
    public boolean isValidCCN(String rawCCN) {
        //check for a valid separator
        boolean hasSpace = StringUtils.contains(rawCCN, ' ');
        boolean hasDash = StringUtils.contains(rawCCN, '-');
        if (hasSpace && hasDash) {
            return false;    //can only have dashes or spaces, not both.
        }

        Character separator = null;
        if (hasSpace) {
            separator = ' ';
        } else if (hasDash) {
            separator = '-';
        }

        final String cannonicalCCN;
        if (separator == null) {
            cannonicalCCN = rawCCN;
        } else {
            //if there is a seperator, strip if for canoncial form of CCN
            cannonicalCCN = CharMatcher.anyOf(" -").removeFrom(rawCCN);

            //and validate digit grouping
            if (cannonicalCCN.length() == 16) {
                String[] splitCCN = rawCCN.split(separator.toString());
                if (Arrays.stream(splitCCN).anyMatch(s -> s.length() != 4)
                        || splitCCN.length != 4) {
                    return false;
                }
            }
            if (cannonicalCCN.length() == 15
                    && (cannonicalCCN.startsWith("34") || cannonicalCCN.startsWith("37"))) {
                String[] splitCCN = rawCCN.split(separator.toString());

                if (splitCCN.length != 3) {
                    return false;
                } else if (false == (splitCCN[0].length() == 4 && splitCCN[1].length() == 6 && splitCCN[2].length() == 5)) {
                    return false;
                }
            }
        }
        return CREDIT_CARD_NUM_LUHN_CHECK.isValid(cannonicalCCN);
    }
}
