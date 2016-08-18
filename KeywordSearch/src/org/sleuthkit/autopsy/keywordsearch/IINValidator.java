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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

/**
 *
 */
public class IINValidator {

    RangeMap<Integer, IINRange> iinRanges = TreeRangeMap.create();

    IINValidator() throws FileNotFoundException, IOException {
        Reader in = new FileReader("ranges.csv");
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
        for (CSVRecord record : records) {
            //iin_start,iin_end,number_length,scheme,brand,type,prepaid,country,bank_name,bank_logo,bank_url,bank_phone,bank_city
            IINRange iinRange = new IINRange(Integer.parseInt(record.get("iin_start")),
                    Integer.parseInt(record.get("iin_end")),
                    Integer.parseInt(record.get("number_length")),
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
