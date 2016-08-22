/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;

/**
 *
 */
public class IINValidator {

    RangeMap<Integer, IINRange> iinRanges = TreeRangeMap.create();

    IINValidator() throws FileNotFoundException, IOException {

        InputStreamReader in = new InputStreamReader(getClass().getResourceAsStream("ranges.csv"));
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
        for (CSVRecord record : records) {
            String start = record.get("iin_start");
            String end = StringUtils.defaultIfBlank(record.get("iin_end"), start);
            start = StringUtils.rightPad(start, 8, "0");
            end = StringUtils.rightPad(end, 8, "99");
            final String numberLength = record.get("number_length");

            //iin_start,iin_end,number_length,scheme,brand,type,prepaid,country,bank_name,bank_logo,bank_url,bank_phone,bank_city
            IINRange iinRange = new IINRange(Integer.parseInt(start),
                    Integer.parseInt(end),
                    StringUtils.isBlank(numberLength) ? null : Integer.parseInt(numberLength),
                    IINRange.PaymentCardScheme.valueOf(record.get("scheme")),
                    record.get("brand"),
                    IINRange.PaymentCardType.valueOf(record.get("type")),
                    record.get("country"),
                    record.get("bank_name"),
                    record.get("bank_url"),
                    record.get("bank_phone"),
                    record.get("bank_city"));

            iinRanges.put(Range.closed(iinRange.getIINstart(), iinRange.getIINend()), iinRange);
        }
    }

    boolean contains(int iin) {
        return iinRanges.get(iin) != null;
    }

    IINRange getIINRange(int iin) {
        return iinRanges.get(iin);
    }
}
