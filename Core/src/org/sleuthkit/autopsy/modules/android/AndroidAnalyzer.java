/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.android;

import java.io.File;
import java.sql.Connection;
import org.sleuthkit.datamodel.AbstractFile;

public interface AndroidAnalyzer {

    /**
     * Implement this method if the android analyzer analyzes database artifact
     *
     * @param connection   jdbc:sqlite connection to the database.
     * @param abstractFile file used to create the artifacts and add attributes.
     *                     Must correspond to the database file.
     */
    public void findInDB(Connection connection, AbstractFile abstractFile);

    /**
     * Implement this method if the android analyzer analyzes file (non
     * database) artifact.
     *
     * @param file         File instance of the file to be analyzed.
     * @param abstractFile file used to create the artifacts and add attributes.
     *                     Must correspond to the File instance.
     */
    public void findInFile(File file, AbstractFile abstractFile);

    /**
     * Returns an array of names of files analyzed by the analyzer.
     *
     * @return array of names of the files (database or non-database) to be
     *         analyzed.
     */
    public String[] getDatabaseNames();

    /**
     * Checks if the android analyzer parses a database.
     *
     * @return true if the analyzer parses a database. Else returns false.
     */
    public boolean parsesDB();

}
