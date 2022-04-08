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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.accounts.BINRange;

public class CreditCards {

    //Interface for objects that provide details about one or more BINs.
    static public interface BankIdentificationNumber {

        /**
         * Get the city of the issuer.
         *
         * @return the city of the issuer.
         */
        Optional<String> getBankCity();

        /**
         * Get the name of the issuer.
         *
         * @return the name of the issuer.
         */
        Optional<String> getBankName();

        /**
         * Get the phone number of the issuer.
         *
         * @return the phone number of the issuer.
         */
        Optional<String> getBankPhoneNumber();

        /**
         * Get the URL of the issuer.
         *
         * @return the URL of the issuer.
         */
        Optional<String> getBankURL();

        /**
         * Get the brand of this BIN range.
         *
         * @return the brand of this BIN range.
         */
        Optional<String> getBrand();

        /**
         * Get the type of card (credit vs debit) for this BIN range.
         *
         * @return the type of cards in this BIN range.
         */
        Optional<String> getCardType();

        /**
         * Get the country of the issuer.
         *
         * @return the country of the issuer.
         */
        Optional<String> getCountry();

        /**
         * Get the length of account numbers in this BIN range.
         *
         * NOTE: the length is currently unused, and not in the data file for
         * any ranges. It could be quite helpfull for validation...
         *
         * @return the length of account numbers in this BIN range. Or an empty
         *         Optional if the length is unknown.
         *
         */
        Optional<Integer> getNumberLength();

        /**
         * Get the scheme this BIN range uses to amex,visa,mastercard, etc
         *
         * @return the scheme this BIN range uses.
         */
        Optional<String> getScheme();
    }

    private static final Logger logger = Logger.getLogger(CreditCards.class.getName());

  
    /**
     * Range Map from a (ranges of) BINs to data model object with details of
     * the BIN, ie, bank name, phone, url, visa/amex/mastercard/...,
     */
    @GuardedBy("CreditCards.class")
    private final static RangeMap<Integer, BINRange> binRanges = TreeRangeMap.create();

    /**
     * Flag for if we have loaded the BINs from the file already.
     */
    @GuardedBy("CreditCards.class")
    private static boolean binsLoaded = false;

    /**
     * Load the BIN range information from disk. If the map has already been
     * initialized, don't load again.
     */
    synchronized private static void loadBINRanges() {
        if (binsLoaded == false) {
            try {
                InputStreamReader in = new InputStreamReader(CreditCards.class.getResourceAsStream("ranges.csv")); //NON-NLS
                
                CSVParser rangesParser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(in);

                //parse each row and add to range map
                for (CSVRecord record : rangesParser) {

                    /**
                     * Because ranges.csv allows both 6 and (the newer) 8 digit
                     * BINs, but we need a consistent length for the range map,
                     * we pad all the numbers out to 8 digits
                     */
                    String start = StringUtils.rightPad(record.get("iin_start"), 8, "0"); //pad start with 0's //NON-NLS

                    //if there is no end listed, use start, since ranges will be closed.
                    String end = StringUtils.defaultIfBlank(record.get("iin_end"), start); //NON-NLS
                    end = StringUtils.rightPad(end, 8, "99"); //pad end with 9's //NON-NLS

                    final String numberLength = record.get("number_length"); //NON-NLS

                    try {
                        BINRange binRange = new BINRange(Integer.parseInt(start),
                                Integer.parseInt(end),
                                StringUtils.isBlank(numberLength) ? null : Integer.valueOf(numberLength),
                                record.get("scheme"), //NON-NLS
                                record.get("brand"), //NON-NLS
                                record.get("type"), //NON-NLS
                                record.get("country"), //NON-NLS
                                record.get("bank_name"), //NON-NLS
                                record.get("bank_url"), //NON-NLS
                                record.get("bank_phone"), //NON-NLS
                                record.get("bank_city")); //NON-NLS

                        binRanges.put(Range.closed(binRange.getBINstart(), binRange.getBINend()), binRange);

                    } catch (NumberFormatException numberFormatException) {
                        logger.log(Level.WARNING, "Failed to parse BIN range: " + record.toString(), numberFormatException); //NON-NLS
                    }
                    binsLoaded = true;
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to load BIN ranges form ranges.csv", ex); //NON-NLS
                MessageNotifyUtil.Notify.warn("Credit Card Number Discovery", "There was an error loading Bank Identification Number information.  Accounts will not have their BINs identified.");
            }
        }
    }

    /**
     * Get an BINInfo object with details about the given BIN
     *
     * @param bin the BIN to get details of.
     *
     * @return
     */
    synchronized static public BankIdentificationNumber getBINInfo(int bin) {
        loadBINRanges();
        return binRanges.get(bin);
    }
}
