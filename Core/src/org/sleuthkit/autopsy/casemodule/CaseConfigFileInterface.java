/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
 * NOTE: NOTHING ACTUALLY USES THIS
 *
 * The interface for the classes that edit the case configuration file
 */
//TODO: check that this can just be deleted.
interface CaseConfigFileInterface {

    public void open(String conFilePath) throws Exception;  // opens the confiuation store (XML, DB...)

    public void writeFile() throws Exception;            // writes out the configuration to store

    public void close() throws Exception;                   // close and clear the document handler
//    public int[] getImageIDs() throws Exception;            // returns a list of image IDs and names
//    public int getNextImageID() throws Exception;           // returns the next free ID to be assigned to new image, and increments the internal counter

    // all the get and set methods
    public String getCaseName() throws Exception; // get the case name

    public void setCaseName(String caseName) throws Exception; // set the case name

    public String getCaseNumber() throws Exception; // get the case number

    public void setCaseNumber(String caseNumber) throws Exception; // set the case number

    public String getCaseExaminer() throws Exception; // get the examiner

    public void setCaseExaminer(String examiner) throws Exception; // set the examiner

    // public void getXXX(); // methods to get the case attributes
    // public void setXXX(); // methods to set the case attributes
}
