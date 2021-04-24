/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.tidy.Tidy;
import org.xml.sax.SAXException;

/**
 * Responsible for parsing the manifest files that describe cases, devices, and
 * data sources. These are used by autoingest to create cases and add data
 * sources to the correct case.
 */
public interface ManifestFileParser {

    /**
     * Checks if a file is this type of manifest file
     *
     * @param filePath Path to potential manifest file
     *
     * @return True if the file is a manifest that this parser supports
     */
    boolean fileIsManifest(Path filePath);

    /**
     * Parses the given file. Will only be called if fileIsManifest() previously
     * returned true.
     *
     * @param filePath Path to manifest file
     *
     * @return Parsed results
     *
     * @throws
     * org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException
     */
    Manifest parse(Path filePath) throws ManifestFileParserException;

    /**
     * Creates a "tidy" version of the given XML file in same parent directory.
     *
     * @param filePath Path to original XML file.
     *
     * @return Path to the newly created tidy version of the file.
     *
     * @throws IOException
     */
    static Path makeTidyManifestFile(Path filePath) throws IOException {
        File tempFile = null;
        try{
            tempFile = File.createTempFile("mani", "tdy", new File(System.getProperty("java.io.tmpdir")));

            try (FileInputStream br = new FileInputStream(filePath.toFile()); FileOutputStream out = new FileOutputStream(tempFile);) {
                Tidy tidy = new Tidy();
                tidy.setXmlOut(true);
                tidy.setXmlTags(true);
                tidy.parseDOM(br, out);
            }

            return Paths.get(tempFile.toString());
        } catch(IOException ex) {
            // If there is an exception delete the temp file.
            if(tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            throw ex;
        }
    }
    
        /**
     * Create a new DOM document object for the given manifest file.
     *
     * @param manifestFilePath Fully qualified path to manifest file.
     *
     * @return DOM document object
     *
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    static Document createManifestDOM(Path manifestFilePath) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        return docBuilder.parse(manifestFilePath.toFile());
    }

    /**
     * Return the root node of the given Manifest XML file.
     * 
     * @param filePath XML filePath
     * @param isRootTester Predicate method for testing if a string is the root node.
     * 
     * @return The XML file root node or null if the node was not found or the 
     *          file is not an XML file.
     */
    static String getManifestRootNode(Path filePath, Predicate<String> isRootTester) {
        Document doc;
        Path tempPath = null;
        try {
            try {
                doc = ManifestFileParser.createManifestDOM(filePath);
            } catch (Exception unused) {
                // If the above call to createManifestDOM threw an exception
                // try to fix the given XML file.
                tempPath = ManifestFileParser.makeTidyManifestFile(filePath);
                doc = ManifestFileParser.createManifestDOM(tempPath);
            }
            Element docElement = doc.getDocumentElement();
            String rootElementTag = docElement.getTagName();
            if(isRootTester.test(rootElementTag)) {
                return rootElementTag;
            }                 
        } catch (Exception unused) {
            // Unused exception. If an exception is thrown the given XML file
            // cannot be parsed.
        } finally {
            if (tempPath != null) {
                tempPath.toFile().delete();
            }
        }
        return null;
    }
    
    public final static class ManifestFileParserException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw if a manifest file cannot be parsed.
         *
         * @param message The exception message.
         */
        public ManifestFileParserException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw if a manifest file cannot be parsed.
         *
         * @param message The exception message.
         * @param cause   The exception cause, if it was a Throwable.
         */
        public ManifestFileParserException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
