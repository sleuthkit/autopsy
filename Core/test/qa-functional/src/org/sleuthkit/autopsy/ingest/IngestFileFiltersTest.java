/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import junit.framework.TestCase;
import org.netbeans.junit.NbModuleSuite;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import junit.framework.Test;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;

public class IngestFileFiltersTest extends TestCase {

    private static final Path caseDirectoryPath = Paths.get(System.getProperty("java.io.tmpdir"), "IngestFileFiltersTest");
    private static final File CASE_DIR = new File(caseDirectoryPath.toString());
    
    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestFileFiltersTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    @Override
    public void setUp() {
        try {
            Case.createAsCurrentCase(Case.CaseType.SINGLE_USER_CASE, caseDirectoryPath.toString(), new CaseDetails("IngestFiltersTest"));
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
        }        
        assertTrue(CASE_DIR.exists());
    }

    @Override
    public void tearDown() {
        try {
            Case.closeCurrentCase();
            FileUtils.deleteDirectory(CASE_DIR);

        } catch (CaseActionException | IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        assertFalse(CASE_DIR.exists());
    }

    public void testFilter() {
        System.out.println("testFilter");
    }
}
