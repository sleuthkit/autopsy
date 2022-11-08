/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException;

/**
 * Class for wrapping a map which stores FilesSets as values with a String key.
 */
class FileSetsDefinitions implements Serializable {

    private static final long serialVersionUID = 1L;
    //By wrapping the map in this class we avoid warnings for unchecked casting when serializing
    private final Map<String, FilesSet> filesSets;

    FileSetsDefinitions(Map<String, FilesSet> filesSets) {
        this.filesSets = filesSets;
    }

    /**
     * @return the filesSets
     */
    Map<String, FilesSet> getFilesSets() {
        return filesSets;
    }

    /**
     * Writes FilesSet definitions to disk as an XML file, logging any errors.
     *
     * @param basePath The base output directory.
     * @param fileName Name of the set definitions file as a string.
     *
     * @returns True if the definitions are written to disk, false otherwise.
     */
    static boolean writeDefinitionsFile(String basePath, String fileName, Map<String, FilesSet> interestingFilesSets) throws FilesSetsManager.FilesSetsManagerException {
        File outputPath = Paths.get(basePath, fileName).toFile();
        outputPath.getParentFile().mkdirs();
        try (final NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(outputPath))) {
            out.writeObject(new FileSetsDefinitions(interestingFilesSets));
        } catch (IOException ex) {
            throw new FilesSetsManager.FilesSetsManagerException(String.format("Failed to write settings to %s", fileName), ex);
        }
        return true;
    }

    /**
     * Reads the definitions from the serialization file
     *
     * @param basePath       The base output directory.
     * @param serialFileName Name of the set definitions file as a string.
     *
     * @return the map representing settings saved to serialization file, empty
     *         set if the file does not exist.
     *
     * @throws FilesSetsManagerException if file could not be read
     */
    static Map<String, FilesSet> readSerializedDefinitions(String basePath, String serialFileName) throws FilesSetsManager.FilesSetsManagerException {
        Path filePath = Paths.get(basePath, serialFileName);
        File fileSetFile = filePath.toFile();
        String filePathStr = filePath.toString();
        if (fileSetFile.exists()) {
            try {
                try (final NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePathStr))) {
                    FileSetsDefinitions filesSetsSettings = (FileSetsDefinitions) in.readObject();
                    return filesSetsSettings.getFilesSets();
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new FilesSetsManager.FilesSetsManagerException(String.format("Failed to read settings from %s", filePathStr), ex);
            }
        } else {
            return new HashMap<>();
        }
    }

}