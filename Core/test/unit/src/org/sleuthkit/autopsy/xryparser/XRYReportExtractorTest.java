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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.junit.Assert.*;

/**
 *
 */
public class XRYReportExtractorTest {

    private final Path reportDirectory = Paths.get("C:", "Users", "dsmyda", "Downloads", "2019-10-23-XRYSamples", "files");

    public XRYReportExtractorTest() {
    }

    @Test
    public void testCallLogsSample() throws IOException {
        Path reportPath = reportDirectory.resolve("Calls.txt");
        XRYReportExtractor extractor = new XRYReportExtractor(new XRYReport(reportPath));
        Set<String> expectation = new HashSet<String>() {
            {
                add("Calls #	1\n"
                        + "Call Type:	Missed\n"
                        + "Time:		1/2/2019 1:23:45 PM (Device)\n"
                        + "From\n"
                        + "Tel:		12345678\n");
                add("Calls #	2\n"
                        + "Call Type: 	Dialed\n"
                        + "Time:		1/2/2019 2:34:56 PM (Device)\n"
                        + "Duration:	00:00:05\n"
                        + "To\n"
                        + "Tel:		23456789\n");
                add("Calls #	3\n"
                        + "Call Type: 	Last Dialed\n"
                        + "Number: 	1234\n"
                        + "Storage:	SIM\n"
                        + "To\n");
                add("Calls #	4\n"
                        + "Call Type: 	Received\n"
                        + "Time:		1/2/2019 2:34:56 AM (Device)\n"
                        + "Duration:	00:00:20\n"
                        + "From\n"
                        + "Tel:		34567890\n");
            }
        };
        MockXRYRecordParser mockParser = new MockXRYRecordParser(expectation);
        extractor.extract(mockParser);
        assertEquals(expectation.size(), mockParser.getCount());
    }

    @Test
    public void testMessagesSample() throws IOException {
        Path reportPath = reportDirectory.resolve("Messages-SMS.txt");
        XRYReportExtractor extractor = new XRYReportExtractor(new XRYReport(reportPath));
        Set<String> expectation = new HashSet<String>() {
            {
                add("Messages-SMS #	1\n"
                        + "Text:	Hello, this is my message.  \n"
                        + "It has multiple lines.\n"
                        + "Time:	1/23/2019 1:23:45 PM UTC (Network)\n"
                        + "Type: 	Deliver\n"
                        + "Reference Number:	22\n"
                        + "Segment Number:		1\n"
                        + "Segments:		1\n"
                        + "From\n"
                        + "Tel:		12345678\n");
                add("Messages-SMS #	2\n"
                        + "Text:		Hello, this is another message.  one line.\n"
                        + "Time:		1/2/2019 1:33:44 PM (Device)\n"
                        + "Type: 		Submit\n"
                        + "To\n"
                        + "Tel:		1234\n");
                add("Messages-SMS #	3\n"
                        + "Text:		Text goes here\n"
                        + "Time:		1/3/2019 2:33:22 PM (Device)\n"
                        + "Type: 	Status Report\n"
                        + "Participant\n"
                        + "Tel:		12345\n");
            }
        };
        MockXRYRecordParser mockParser = new MockXRYRecordParser(expectation);
        extractor.extract(mockParser);
        assertEquals(expectation.size(), mockParser.getCount());
    }

    @Test
    public void testContactsSample() throws IOException {
        Path reportPath = reportDirectory.resolve("Contacts-Contacts.txt");
        XRYReportExtractor extractor = new XRYReportExtractor(new XRYReport(reportPath));
        Set<String> expectation = new HashSet<String>() {
            {
                add("Contacts-Contacts #	1\n"
                        + "Name:		Abc\n"
                        + "Tel:		+123456\n"
                        + "Storage:	Device\n");
                add("Contacts-Contacts #	2\n"
                        + "Name:		Xyz\n"
                        + "Tel:		+34567\n"
                        + "Storage:	SIM\n");
            }
        };
        MockXRYRecordParser mockParser = new MockXRYRecordParser(expectation);
        extractor.extract(mockParser);
        assertEquals(expectation.size(), mockParser.getCount());
    }

    @Test
    public void testWebBookmarksSample() throws IOException {
        Path reportPath = reportDirectory.resolve("Web-Bookmarks.txt");
        XRYReportExtractor extractor = new XRYReportExtractor(new XRYReport(reportPath));
        Set<String> expectation = new HashSet<String>() {
            {
                add("Web-Bookmarks #	1\n"
                        + "Web Address: 	http://www.google.com\n"
                        + "Domain:		Google Search\n");
            }
        };
        MockXRYRecordParser mockParser = new MockXRYRecordParser(expectation);
        extractor.extract(mockParser);
        assertEquals(expectation.size(), mockParser.getCount());
    }

    @Test
    public void testDeviceSample() throws IOException {
        Path reportPath = reportDirectory.resolve("Device-General Information.txt");
        XRYReportExtractor extractor = new XRYReportExtractor(new XRYReport(reportPath));
        Set<String> expectation = new HashSet<String>() {
            {
                add("Device-General Information #	1\n"
                        + "Data:	c:\\Path To Something I forget what though\\Maybe the Storage folder\n");
                add("Device-General Information #	2\n"
                        + "Data:	Nokia XYZ\n"
                        + "Attribute:	Device Name\n");
                add("Device-General Information #	3\n"
                        + "Data:	Phone\n"
                        + "Attribute:	Device Family\n");
                add("Device-General Information #	4\n"
                        + "Data:	XYZ\n"
                        + "Attribute:	Device Type\n");
                add("Device-General Information #	5\n"
                        + "Data:	123456\n"
                        + "Attribute:	Mobile Id (IMEI)\n");
                add("Device-General Information #	6\n"
                        + "Data:	12345\n"
                        + "Attribute:	Security Code\n");
                add("Device-General Information #	7\n"
                        + "Data:	SIM Card\n"
                        + "Attribute:	Device Name\n");
            }
        };
        MockXRYRecordParser mockParser = new MockXRYRecordParser(expectation);
        extractor.extract(mockParser);
        assertEquals(expectation.size(), mockParser.getCount());
    }

    /**
     * Mock XRYRecordParser. Rather than creating BlackboardArtifacts, we are
     * instead verifying that the XRY Records are being parsed out correctly.
     */
    private class MockXRYRecordParser implements XRYRecordParser {

        private final Set<String> allRecords;
        private int recordCount;

        public MockXRYRecordParser(Set<String> allRecords) {
            this.allRecords = allRecords;
            recordCount = 0;
        }

        @Override
        public BlackboardArtifact makeArtifact(String xryRecord) {
            assertNotNull(allRecords);
            assertNotNull(xryRecord);
            recordCount++;
            assertTrue("More records than expected: " + recordCount + ". Expected at most: "+allRecords.size(), recordCount <= allRecords.size());
            assertTrue("Did not find the following record: " + xryRecord, allRecords.contains(xryRecord));
            return null;
        }
        
        public int getCount() {
            return recordCount;
        }
    }
}