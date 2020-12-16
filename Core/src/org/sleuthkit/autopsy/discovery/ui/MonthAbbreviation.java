/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import org.openide.util.NbBundle;

/**
 * Utility for representing month abbreviations
 */
@NbBundle.Messages({
    "MonthAbbreviation.januraryAbbrev=Jan",
    "MonthAbbreviation.feburaryAbbrev=Feb",
    "MonthAbbreviation.marchAbbrev=Mar",
    "MonthAbbreviation.aprilAbbrev=Apr",
    "MonthAbbreviation.mayAbbrev=May",
    "MonthAbbreviation.juneAbbrev=Jun",
    "MonthAbbreviation.julyAbbrev=Jul",
    "MonthAbbreviation.augustAbbrev=Aug",
    "MonthAbbreviation.septemberAbbrev=Sep",
    "MonthAbbreviation.octoberAbbrev=Oct",
    "MonthAbbreviation.novemberAbbrev=Nov",
    "MonthAbbreviation.decemberAbbrev=Dec"
})
public enum MonthAbbreviation {
    JANURARY(Bundle.MonthAbbreviation_januraryAbbrev()),
    FEBURARY(Bundle.MonthAbbreviation_feburaryAbbrev()),
    MARCH(Bundle.MonthAbbreviation_marchAbbrev()),
    APRIL(Bundle.MonthAbbreviation_aprilAbbrev()),
    MAY(Bundle.MonthAbbreviation_mayAbbrev()),
    JUNE(Bundle.MonthAbbreviation_juneAbbrev()),
    JULY(Bundle.MonthAbbreviation_julyAbbrev()),
    AUGUST(Bundle.MonthAbbreviation_augustAbbrev()),
    SEPTEMBER(Bundle.MonthAbbreviation_septemberAbbrev()),
    OCTOBER(Bundle.MonthAbbreviation_octoberAbbrev()),
    NOVEMBER(Bundle.MonthAbbreviation_novemberAbbrev()),
    DECEMBER(Bundle.MonthAbbreviation_decemberAbbrev());
    
    private final String abbreviation;
    
    MonthAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }
    
    @Override
    public String toString() {
        return this.abbreviation;
    }
    
    /**
     * Converts a month value (1-12) to the appropriate abbreviation.
     * 
     * @param value Month value (1-12).
     * @return Abbreviation matching the month value, null if not found.
     */
    public static MonthAbbreviation fromMonthValue(int value) {
        MonthAbbreviation[] months = MonthAbbreviation.values();
        for(int i = 0; i < months.length; i++) {
            if (i + 1 == value) {
                return months[i];
            }
        }
        return null;
    }
}
