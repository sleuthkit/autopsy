/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.xryparser;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class XRYReportTest {

    private final Path reportDirectory = Paths.get("C:", "Users", "dsmyda", "Downloads", "2019-10-23-XRYSamples", "files");

    public XRYReportTest() {
    }
    
    @Test
    public void testParseType() {
        List<Path> reportTestFiles = new ArrayList<Path>() {
            {
                add(reportDirectory.resolve("Calls.txt"));
                add(reportDirectory.resolve("Messages-SMS.txt"));
                add(reportDirectory.resolve("Contacts-Contacts.txt"));
                add(reportDirectory.resolve("Web-Bookmarks.txt"));
                add(reportDirectory.resolve("Device-General Information.txt"));
            }
        };

        List<String> expectedTypes = new ArrayList<String>() {
            {
                add("Calls");
                add("Messages/SMS");
                add("Contacts/Contacts");
                add("Web/Bookmarks");
                add("Device/General Information");
            }
        };

        List<String> actualTypes = new ArrayList<>();

        reportTestFiles.forEach((Path reportFile) -> {
            try {
                XRYReport reportObj = new MockXRYReport(reportFile);
                actualTypes.add(reportObj.getType());
            } catch (IOException ex) {
                fail(ex.getMessage());
            }
        });

        assertArrayEquals("Types did not match.", expectedTypes.toArray(), actualTypes.toArray());
    }

    /**
     * Mock the valid XRY Report encoding to UTF-8 so that the test files can be
     * run unmodified (they are currently UTF-8).
     */
    private class MockXRYReport extends XRYReport {

        public MockXRYReport(Path reportPath) throws IOException {
            super(reportPath);
        }

        @Override
        public Charset getEncoding() {
            return StandardCharsets.UTF_8;
        }
    }
}
