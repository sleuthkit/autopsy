/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Immutable
@ServiceProvider(service = ManifestFileParser.class)
public final class AutopsyManifestFileParser implements ManifestFileParser {

    private static final String MANIFEST_FILE_NAME_SIGNATURE = "_MANIFEST.XML";
    private static final String ROOT_ELEM_TAG_NAME = "AutopsyManifest";
    private static final String CASE_NAME_XPATH = "/AutopsyManifest/CaseName/text()";
    private static final String DEVICE_ID_XPATH = "/AutopsyManifest/DeviceId/text()";
    private static final String DATA_SOURCE_NAME_XPATH = "/AutopsyManifest/DataSource/text()";
    private static final Logger logger = Logger.getLogger(AutopsyManifestFileParser.class.getName());

    @Override
    public boolean fileIsManifest(Path filePath) {
        boolean fileIsManifest = false;

        Path fileName = filePath.getFileName();
        if (fileName.toString().toUpperCase().endsWith(MANIFEST_FILE_NAME_SIGNATURE)) {
            
            fileIsManifest = (ManifestFileParser.getManifestRootNode(filePath, (str) -> {
                return (str.compareToIgnoreCase(ROOT_ELEM_TAG_NAME) == 0);
            }) != null);
        }

        return fileIsManifest;
    }

    @Override
    public Manifest parse(Path filePath) throws ManifestFileParserException {
        Path tempPath = null;
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            Date dateFileCreated = new Date(attrs.creationTime().toMillis());
            Document doc;
            try {
                doc = ManifestFileParser.createManifestDOM(filePath);
            } catch (Exception ex) {
                // If the above call to createManifestDOM threw an exception
                // try to fix the given XML file.
                logger.log(Level.WARNING, String.format("Failed to create DOM for manifest at %s, attempting repair", filePath), ex);                
                tempPath = ManifestFileParser.makeTidyManifestFile(filePath);
                doc = ManifestFileParser.createManifestDOM(tempPath);
            }

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
        } finally {
            if (tempPath != null) {
                tempPath.toFile().delete();
            }
        }
    }

    /**
     * Check to see if the given file is an autopsy auto ingest manifest file by
     * if the root element is ROOT_ELEM_TAG_NAME.
     *
     * @param filePath Path to the manifest file.
     *
     * @return True if this a well formed autopsy auto ingest manifest file.
     */
    private boolean isAutopsyManifestFile(Path filePath) throws IOException {
        try {
            Document doc = ManifestFileParser.createManifestDOM(filePath);
            Element docElement = doc.getDocumentElement();
            return docElement.getTagName().equals(ROOT_ELEM_TAG_NAME);
        } catch (Exception unused) {
            // Double check that this isn't a manifest file that may have bad
            // characters that will be handled in the process method.
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains(ROOT_ELEM_TAG_NAME.toLowerCase())) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

}
