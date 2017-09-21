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
     * no separators, or only '-', or only ' '. Checks digit grouping for
     * 15,16,and 19 digit numbers. All other length numbers must be contiguous
     * or begin with a group of 4 digits.
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
        String[] splitCCN;
        if (separator != null) {
            //there is a seperator, strip if for canoncial form of CCN
            cannonicalCCN = CharMatcher.anyOf(separator.toString()).removeFrom(rawCCN);
            splitCCN = rawCCN.split(separator.toString());
        } else {
            //else use 'defualt'values
            cannonicalCCN = rawCCN;
            splitCCN = new String[]{cannonicalCCN};
        }

        // validate digit grouping for 15, 16, and 19 digit cards
        switch (cannonicalCCN.length()) {
            case 15:
                if (false == isValid15DigitGrouping(splitCCN)) {
                    return false;
                }
                break;
            case 16:
                if (false == isValid16DigitGrouping(splitCCN)) {
                    return false;
                }
                break;
            case 19:
                if (false == isValid19DigitGrouping(splitCCN)) {
                    return false;
                }
                break;
            default:
                if (false == isValidOtherDigitGrouping(splitCCN)) {
                    return false;
                }
        }

        return CREDIT_CARD_NUM_LUHN_CHECK.isValid(cannonicalCCN);
    }

    private boolean isValidOtherDigitGrouping(String[] splitCCN) {
        if (splitCCN.length == 1) {
            return true;
        } else {
            return splitCCN[0].length() == 4;
        }
    }

    private boolean isValid19DigitGrouping(String[] splitCCN) {
        switch (splitCCN.length) {
            case 1:
                return true;
            case 2:
                return splitCCN[0].length() == 6
                        && splitCCN[1].length() == 13;
            case 5:
                return splitCCN[0].length() == 4
                        && splitCCN[1].length() == 4
                        && splitCCN[2].length() == 4
                        && splitCCN[3].length() == 4
                        && splitCCN[4].length() == 3;
            default:
                return false;
        }
    }

    private boolean isValid16DigitGrouping(String[] splitCCN) {
        switch (splitCCN.length) {
            case 1:
                return true;
            case 4:
                return splitCCN[0].length() == 4
                        && splitCCN[1].length() == 4
                        && splitCCN[2].length() == 4
                        && splitCCN[3].length() == 4;
            default:
                return false;
        }
    }

    private boolean isValid15DigitGrouping(String[] splitCCN) {
        switch (splitCCN.length) {
            case 1:
                return true;
            case 3:
                return (splitCCN[0].length() == 4 && splitCCN[1].length() == 6 && splitCCN[2].length() == 5);
//                   UATP     || ((splitCCN[0].length() == 4 && splitCCN[1].length() == 5 && splitCCN[2].length() == 6));
            default:
                return false;
        }
    }
}
