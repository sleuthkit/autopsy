/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2020 Basis Technology Corp.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
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
    private static final String ROOT_ELEM_TAG_NAME = "AutopsyManifest";
    private static final String CASE_NAME_XPATH = "/AutopsyManifest/CaseName/text()";
    private static final String DEVICE_ID_XPATH = "/AutopsyManifest/DeviceId/text()";
    private static final String DATA_SOURCE_NAME_XPATH = "/AutopsyManifest/DataSource/text()";

    @Override
    public boolean fileIsManifest(Path filePath) {
        boolean fileIsManifest = false;
        try {
            Path fileName = filePath.getFileName();
            if (fileName.toString().toUpperCase().endsWith(MANIFEST_FILE_NAME_SIGNATURE)) {
                Document doc = this.createManifestDOM(filePath);
                Element docElement = doc.getDocumentElement();
                fileIsManifest = docElement.getTagName().equals(ROOT_ELEM_TAG_NAME);
            }
        } catch (Exception unused) {
            fileIsManifest = false;
        }
        return fileIsManifest;
    }

    @Override
    public Manifest parse(Path filePath) throws ManifestFileParserException {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            Date dateFileCreated = new Date(attrs.creationTime().toMillis());
            Document doc = this.createManifestDOM(filePath);
            XPath xpath = XPathFactory.newInstance().newXPath();
            
            XPathExpression expr = xpath.compile(CASE_NAME_XPATH);
            String caseName = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (caseName.isEmpty()) {
                throw new ManifestFileParserException("Case name not found, manifest is invalid");
            }
            
            expr = xpath.compile(DEVICE_ID_XPATH);
            String deviceId = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (deviceId.isEmpty()) {
                deviceId = UUID.randomUUID().toString();
            }
            
            expr = xpath.compile(DATA_SOURCE_NAME_XPATH);
            String dataSourceName = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (dataSourceName.isEmpty()) {
                throw new ManifestFileParserException("Data source path not found, manifest is invalid");                
            }
            Path dataSourcePath = filePath.getParent().resolve(dataSourceName);
            
            return new Manifest(filePath, dateFileCreated, caseName, deviceId, dataSourcePath, new HashMap<>());
        } catch (Exception ex) {
            throw new ManifestFileParserException(String.format("Error parsing manifest %s", filePath), ex);
        }
    }

    private Document createManifestDOM(Path manifestFilePath) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        return docBuilder.parse(manifestFilePath.toFile());
    }

}
