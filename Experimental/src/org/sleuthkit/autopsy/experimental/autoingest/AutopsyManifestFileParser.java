/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import javax.annotation.concurrent.Immutable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.openide.util.lookup.ServiceProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@Immutable
@ServiceProvider(service = ManifestFileParser.class)
public final class AutopsyManifestFileParser implements ManifestFileParser {

    private static final String MANIFEST_FILE_NAME_SIGNATURE = "_MANIFEST.XML";
    private static final String NMEC_MANIFEST_ELEM_TAG_NAME = "NMEC_Manifest";
    private static final String MANIFEST_ELEM_TAG_NAME = "Manifest";
    private static final String CASE_NAME_XPATH = "/Collection/Name/text()";
    private static final String DEVICE_ID_XPATH = "/Collection/Image/ID/text()";
    private static final String IMAGE_NAME_XPATH = "/Collection/Image/Name/text()";
    private static final String IMAGE_FULL_NAME_XPATH = "/Collection/Image/FullName/text()";
    private static final String IMAGE_RELATIVE_PATH_XPATH = "/Collection/Image/RelativePath/text()";
    
    private String actualRootElementTag = "";
    

    /**
     * Determine whether the given file is a supported manifest file.
     *
     * @param filePath
     *
     * @return true if this is a supported manifest file, otherwise false
     */
    @Override
    public boolean fileIsManifest(Path filePath) {
        boolean fileIsManifest = false;
        try {
            Path fileName = filePath.getFileName();
            if (fileName.toString().toUpperCase().endsWith(MANIFEST_FILE_NAME_SIGNATURE)) {
                Document doc = this.createManifestDOM(filePath);
                Element docElement = doc.getDocumentElement();
                actualRootElementTag = docElement.getTagName();
                fileIsManifest = actualRootElementTag.equals(MANIFEST_ELEM_TAG_NAME) ||
                        actualRootElementTag.equals(NMEC_MANIFEST_ELEM_TAG_NAME);
            }
        } catch (Exception unused) {
            fileIsManifest = false;
        }
        return fileIsManifest;
    }

    /**
     * Parse the given manifest file and create a Manifest object.
     *
     * @param filePath Fully qualified path to manifest file
     *
     * @return A Manifest object representing the parsed manifest file.
     *
     * @throws ManifestFileParserException
     */
    @Override
    public Manifest parse(Path filePath) throws ManifestFileParserException {
        try {
            Document doc = this.createManifestDOM(filePath);
            XPath xpath = XPathFactory.newInstance().newXPath();
            XPathExpression expr = xpath.compile(constructXPathExpression(CASE_NAME_XPATH));
            String caseName = (String) expr.evaluate(doc, XPathConstants.STRING);            
            expr = xpath.compile(constructXPathExpression(DEVICE_ID_XPATH));
            String deviceId = (String) expr.evaluate(doc, XPathConstants.STRING);
            Path dataSourcePath = determineDataSourcePath(filePath, doc);
            return new Manifest(filePath, caseName, deviceId, dataSourcePath,  new HashMap<>());
        } catch (Exception ex) {
            throw new ManifestFileParserException(String.format("Error parsing manifest %s", filePath), ex);
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
    private Document createManifestDOM(Path manifestFilePath) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        return docBuilder.parse(manifestFilePath.toFile());
    }

    /**
     * Creates an XPath expression string relative to the actual root
     * element of the manifest for the given path.
     * 
     * @param path
     * @return  XPath expression string.
     */
    private String constructXPathExpression(String path) {
        return "/" + actualRootElementTag + path;
    }

    /**
     * Attempt to find a valid (existing) data source for the manifest file.
     *
     * @param manifestFilePath Fully qualified path to manifest file.
     * @param doc DOM document object for the manifest file.
     * @return Path to an existing data source.
     * @throws ManifestFileParserException if an error occurred while parsing manifest file.
     */
    private Path determineDataSourcePath(Path manifestFilePath, Document doc) throws ManifestFileParserException {
        String dataSourcePath = "";
        try {
            for (String element : Arrays.asList(IMAGE_NAME_XPATH, IMAGE_FULL_NAME_XPATH, IMAGE_RELATIVE_PATH_XPATH)) {
                XPath xpath = XPathFactory.newInstance().newXPath();
                XPathExpression expr = xpath.compile(constructXPathExpression(element));
                String fileName = (String) expr.evaluate(doc, XPathConstants.STRING);
                if (fileName.contains("\\")) {
                    fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                }
                try {
                    dataSourcePath = manifestFilePath.getParent().resolve(fileName).toString();
                } catch (Exception ignore) {
                    // NOTE: exceptions can be thrown by resolve() method based on contents of the manifest file.
                    // For example if file name is "test .txt" and in one of the path fields they only enter "test "
                    // i.e. the file name without extension.
                    // We should continue on to the next XML path field
                    continue;
                }
                if (new File(dataSourcePath).exists()) {
                    // found the data source
                    return Paths.get(dataSourcePath);
                }
                // keep trying other XML fields
            }
            return Paths.get(dataSourcePath);
        } catch (Exception ex) {
            throw new ManifestFileParserException(String.format("Error parsing manifest %s", manifestFilePath), ex);
        }
    }
}
