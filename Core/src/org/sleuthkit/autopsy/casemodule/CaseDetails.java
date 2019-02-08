/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.Serializable;

/**
 * Wrapper to contain the modifiable details of a case, such as case display
 * name, case number, and examiner related fields.
 */
public final class CaseDetails implements Serializable {

    private final String caseDisplayName;
    private final String caseNumber;
    private final String examinerName;
    private final String examinerPhone;
    private final String examinerEmail;
    private final String caseNotes;
    private final String originalCaseCreationDate;

    /**
     * Create a case details object with all optional fields set to default
     * values.
     *
     * @param displayName the display name of the case
     */
    public CaseDetails(String displayName) {
        caseDisplayName = displayName;
        caseNumber = "";
        examinerName = "";
        examinerPhone = "";
        examinerEmail = "";
        caseNotes = "";
        originalCaseCreationDate = "";
    }

    /**
     * Create a case details object with the specified values.
     *
     * @param displayName the display name of the case
     * @param number      the case number
     * @param exName      the examiner name
     * @param exPhone     the examiner phone number
     * @param exEmail     the examiner email address
     * @param notes       the case notes
     */
    public CaseDetails(String displayName, String number, String exName, String exPhone, String exEmail, String notes) {
        this(displayName, number, exName, exPhone, exEmail, notes, "");
    }
    
        
    /**
     * Create a case details object with the specified values.
     * Only set originalCaseCreationDate for portable cases.
     *
     * @param displayName    the display name of the case
     * @param number         the case number
     * @param exName         the examiner name
     * @param exPhone        the examiner phone number
     * @param exEmail        the examiner email address
     * @param notes          the case notes
     * @param originalCaseCreationDate  timestamp when the original case was created (for portable cases)
     */
    CaseDetails(String displayName, String number, String exName, String exPhone, String exEmail, String notes,
            String originalCaseCreationDate) {        
        caseDisplayName = displayName;
        caseNumber = number;
        examinerName = exName;
        examinerPhone = exPhone;
        examinerEmail = exEmail;
        caseNotes = notes;
        this.originalCaseCreationDate = originalCaseCreationDate;
    }    

    /**
     * Get the case display name
     *
     * @return caseDisplayName - the display name of the case
     */
    public String getCaseDisplayName() {
        return caseDisplayName;
    }

    /**
     * Get the case number
     *
     * @return caseNumber - the optional number assiciated with the case
     */
    public String getCaseNumber() {
        return caseNumber;
    }

    /**
     * Get the examiner name
     *
     * @return name - the name associated with the examiner
     */
    public String getExaminerName() {
        return examinerName;
    }

    /**
     * Get the examiner phone number
     *
     * @return phone - the phone number associated with the examiner
     */
    public String getExaminerPhone() {
        return examinerPhone;
    }

    /**
     * Get the examiner email address
     *
     * @return email - the email address associated with the examiner
     */
    public String getExaminerEmail() {
        return examinerEmail;
    }

    /**
     * Get the case notes
     *
     * @return notes - the note associated with the case
     */
    public String getCaseNotes() {
        return caseNotes;
    }

    /**
     * Get the creation timestamp of the case that the portable case was created from
     * 
     * @return originalCaseCreationDate - the timestamp when the case was created. Returns empty string
                                    for non-portable cases.
     */
    public String getOriginalCaseCreationDate() {
        return originalCaseCreationDate;
    }
}
