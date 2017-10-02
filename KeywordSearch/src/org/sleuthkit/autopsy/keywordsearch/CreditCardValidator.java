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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;

/**
 * Utility class to validate Credit Card Numbers. Validation entails checking
 * that numbers are compatible but not necessarily 'real'. Validation checks the
 * following properties:
 * <ul>
 * <li> A number can have obly one of dashes, spaces, or none as a seperator
 * character. </li>
 * <li> If a number has seperator character, the digits must be grouped into a
 * valid pattern for the number length</li>
 * <li> A number must pass the luhn check.</li>
 * </ul>
 *
 */
final class CreditCardValidator {

    private CreditCardValidator() {
    }

    private static final LuhnCheckDigit CREDIT_CARD_NUM_LUHN_CHECK = new LuhnCheckDigit();

    /**
     * map from ccn IIN to allowed lengths
     */
    static private final RangeMap<Integer, Set<Integer>> allowedLengths = TreeRangeMap.create();
    private static final ImmutableSet<Integer> Set12to19 = ImmutableSet.of(12, 13, 14, 15, 16, 17, 18, 19);
    private static final ImmutableSet<Integer> Set14to19 = ImmutableSet.of(14, 15, 16, 17, 18, 19);
    private static final ImmutableSet<Integer> Set16to19 = ImmutableSet.of(16, 17, 18, 29);

    static {
        //amex
        allowedLengths.put(Range.closedOpen(34000000, 35000000), ImmutableSet.of(15));
        allowedLengths.put(Range.closedOpen(37000000, 38000000), ImmutableSet.of(15));

        //visa
        allowedLengths.put(Range.closedOpen(40000000, 50000000), Set12to19);

        //visa electron
        allowedLengths.put(Range.closedOpen(40260000, 40270000), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(41750000, 41750100), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(44050000, 44060000), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(45080000, 45090000), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(48440000, 48450000), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(49130000, 49140000), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(49170000, 49180000), ImmutableSet.of(16));

        //China UnionPay
        allowedLengths.put(Range.closedOpen(62000000, 63000000), Set16to19);

        //MasterCard
        allowedLengths.put(Range.closedOpen(51000000, 56000000), ImmutableSet.of(16));
        allowedLengths.put(Range.closedOpen(22210000, 27210000), ImmutableSet.of(16));

        //Verve, these over lap with discover
        allowedLengths.put(Range.closedOpen(50609900, 50619900), ImmutableSet.of(16, 19));
        allowedLengths.put(Range.closedOpen(65000200, 65002700), ImmutableSet.of(16, 19));

        //Maestro
        allowedLengths.put(Range.closedOpen(50000000, 50100000), Set12to19);
        allowedLengths.put(Range.closedOpen(56000000, 59000000), Set12to19);
        allowedLengths.put(Range.closedOpen(60000000, 70000000), Set12to19);
        allowedLengths.put(Range.closedOpen(63900000, 63910000), Set12to19);
        allowedLengths.put(Range.closedOpen(67000000, 68000000), Set12to19);

        //Diners Club International (processed by discover
        allowedLengths.put(Range.closedOpen(30000000, 30600000), Set16to19);
        allowedLengths.put(Range.closedOpen(30950000, 30960000), Set16to19);
        allowedLengths.put(Range.closedOpen(36000000, 37000000), Set14to19);
        allowedLengths.put(Range.closedOpen(38000000, 40000000), Set16to19);

        //Diners Club USA & Canada (MasterCard co brand)
        allowedLengths.put(Range.closedOpen(54000000, 56000000), Set14to19);

        //Discover
        allowedLengths.put(Range.closedOpen(60110000, 60120000), Set16to19);
        allowedLengths.put(Range.closedOpen(62212600, 62292600), Set16to19);
        allowedLengths.put(Range.closedOpen(64400000, 66000000), Set16to19);

        //JCB //process by discover
        allowedLengths.put(Range.closedOpen(35280000, 35900000), Set16to19);

        //Dankort
        allowedLengths.put(Range.closedOpen(50190000, 50200000), Set16to19);

        //InterPayment
        allowedLengths.put(Range.closedOpen(63600000, 63700000), Set16to19);
    }

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
    static public boolean isValidCCN(String rawCCN) {
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

        if (false == lengthMatchesBin(cannonicalCCN)) {
            return false;
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

    static private boolean lengthMatchesBin(String cannonicalCCN) {
        String BIN = cannonicalCCN.substring(0, 8);
        final Set<Integer> lengthsForBIN = allowedLengths.get(Integer.valueOf(BIN));
        return null == lengthsForBIN ||  lengthsForBIN.contains(cannonicalCCN.length());
    }

    static private boolean isValidOtherDigitGrouping(String[] splitCCN) {
        if (splitCCN.length == 1) {
            return true;
        } else {
            return splitCCN[0].length() == 4;
        }
    }

    static private boolean isValid19DigitGrouping(String[] splitCCN) {
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

    static private boolean isValid16DigitGrouping(String[] splitCCN) {
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

    static private boolean isValid15DigitGrouping(String[] splitCCN) {
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
