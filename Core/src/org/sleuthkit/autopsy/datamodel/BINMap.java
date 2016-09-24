package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

public class BINMap {

    private static final Logger LOGGER = Logger.getLogger(BINMap.class.getName());
    /**
     * Range Map from a (ranges of) B/IINs to data model object with details of
     * the B/IIN, ie, bank name, phone, url, visa/amex/mastercard/...,
     */
    @GuardedBy("Accounts.class")
    private final static RangeMap<Integer, Accounts.IINRange> iinRanges = TreeRangeMap.create();

    /**
     * Flag for if we have loaded the IINs from the file already.
     */
    @GuardedBy("Accounts.class")
    private static boolean iinsLoaded = false;

    /**
     * Load the IIN range information from disk. If the map has already been
     * initialized, don't load again.
     */
    synchronized private static void loadIINRanges() {
        if (iinsLoaded == false) {
            try {
                InputStreamReader in = new InputStreamReader(Accounts.class.getResourceAsStream("ranges.csv")); //NON-NLS
                CSVParser rangesParser = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);

                //parse each row and add to range map
                for (CSVRecord record : rangesParser) {

                    /**
                     * Because ranges.csv allows both 6 and (the newer) 8 digit
                     * IINs, but we need a consistent length for the range map,
                     * we pad all the numbers out to 8 digits
                     */
                    String start = StringUtils.rightPad(record.get("iin_start"), 8, "0"); //pad start with 0's //NON-NLS

                    //if there is no end listed, use start, since ranges will be closed.
                    String end = StringUtils.defaultIfBlank(record.get("iin_end"), start); //NON-NLS
                    end = StringUtils.rightPad(end, 8, "99"); //pad end with 9's //NON-NLS

                    final String numberLength = record.get("number_length"); //NON-NLS

                    try {
                        Accounts.IINRange iinRange = new Accounts.IINRange(Integer.parseInt(start),
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

                        iinRanges.put(Range.closed(iinRange.getIINstart(), iinRange.getIINend()), iinRange);

                    } catch (NumberFormatException numberFormatException) {
                        LOGGER.log(Level.WARNING, "Failed to parse IIN range: " + record.toString(), numberFormatException); //NON-NLS
                    }
                    iinsLoaded = true;
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Failed to load IIN ranges form ranges.csv", ex); //NON-NLS
                MessageNotifyUtil.Notify.warn("Credit Card Number Discovery", "There was an error loading Bank Identification Number information.  Accounts will not have their BINs identified.");
            }
        }
    }

    /**
     * Get an IINInfo object with details about the given IIN
     *
     * @param iin the IIN to get details of.
     *
     * @return
     */
    synchronized static public Accounts.IINInfo getIINInfo(int iin) {
        loadIINRanges();
        return iinRanges.get(iin);
    }

}
