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

import java.io.IOException;
import java.nio.file.Path;
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

/**
 * RJCTODO
 */
@Immutable
@ServiceProvider(service = ManifestFileParser.class)
public final class AutopsyManifestFileParser implements ManifestFileParser {

    private static final String MANIFEST_FILE_NAME_SIGNATURE = "_Manifest.xml";
    private static final String ROOT_ELEM_TAG_NAME = "Manifest";
    private static final String CASE_NAME_XPATH = "/Manifest/Collection/Name/text()";
    private static final String DEVICE_ID_XPATH = "/Manifest/Collection/Image/ID/text()";
    private static final String DATA_SOURCE_NAME_XPATH = "/Manifest/Collection/Image/Name/text()";
    

    /**
     * RJCTODO
     *
     * @param filePath
     *
     * @return
     */
    @Override
    public boolean fileIsManifest(Path filePath) {
        boolean fileIsManifest = false;
        try {
            Path fileName = filePath.getFileName();
            if (fileName.toString().endsWith(MANIFEST_FILE_NAME_SIGNATURE)) {
                Document doc = this.createManifestDOM(filePath);
                Element docElement = doc.getDocumentElement();
                fileIsManifest = docElement.getTagName().equals(ROOT_ELEM_TAG_NAME);
            }
        } catch (Exception unused) {
            fileIsManifest = false;
        }
        return fileIsManifest;
    }

    /**
     * RJCTODO
     *
     * @param filePath
     *
     * @return
     *
     * @throws org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException
     */
    @Override
    public Manifest parse(Path filePath) throws ManifestFileParserException {
        if (!fileIsManifest(filePath)) {
            throw new ManifestFileParserException(String.format("%s not recognized as a manifest", filePath));
        }

        try {
            Document doc = this.createManifestDOM(filePath);
            XPath xpath = XPathFactory.newInstance().newXPath();            
            XPathExpression expr = xpath.compile(CASE_NAME_XPATH);
            String caseName = (String) expr.evaluate(doc, XPathConstants.STRING);            
            expr = xpath.compile(DEVICE_ID_XPATH);
            String deviceId = (String) expr.evaluate(doc, XPathConstants.STRING);
            expr = xpath.compile(DATA_SOURCE_NAME_XPATH);
            String dataSourceName = (String) expr.evaluate(doc, XPathConstants.STRING);            
            Path dataSourcePath = filePath.getParent().resolve(dataSourceName);
            return new Manifest(filePath, caseName, deviceId, dataSourcePath,  new HashMap<>());
        } catch (Exception ex) {
            throw new ManifestFileParserException(String.format("Error parsing manifest %s", filePath), ex);
        }
    }

    /**
     * RJCTODO
     *
     * @param manifestFilePath
     *
     * @return
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

}
