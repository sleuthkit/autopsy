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

/**
 * Wrapper to contain the details associated with an Examiner 
 */
class Examiner {
    private final String name;
    private final String phone;
    private final String email;
    private final String notes;
    
    Examiner(String exName, String exPhone, String exEmail, String exNotes){
        name = exName;
        phone = exPhone;
        email = exEmail;
        notes = exNotes;
    }

    /**
     * Get the examiner name
     * 
     * @return name - the name associated with the examiner
     */
    String getName() {
        return name;
    }

    /**
     *  Get the examiner phone number
     * 
     * @return phone - the phone number associated with the examiner
     */
    String getPhone() {
        return phone;
    }

    /**
     * Get the examiner email address
     * 
     * @return email - the email address associated with the examiner
     */
    String getEmail() {
        return email;
    }

    /**
     * Get the examiner notes
     * 
     * @return notes - the note asssociated with the examiner
     */
    String getNotes() {
        return notes;
    }  
}
