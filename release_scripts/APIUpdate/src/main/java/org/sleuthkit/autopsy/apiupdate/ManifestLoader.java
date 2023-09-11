/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads manifest.mf files.
 */
public class ManifestLoader {

    private static final String JAR_MANIFEST_REL_PATH = "META-INF/MANIFEST.MF";

    /**
     * Loads the manifest attributes from an input stream.
     * @param is The input stream.
     * @return The manifest attributes.
     * @throws IOException 
     */
    public static Attributes loadManifestAttributes(InputStream is) throws IOException {
        Manifest manifest = loadManifest(is);
        return manifest.getMainAttributes();
    }

    /**
     * Loads the manifest from an input stream.
     * @param is The input stream.
     * @return The manifest.
     * @throws IOException 
     */
    public static Manifest loadManifest(InputStream is) throws IOException {
        return new Manifest(is);
    }

    /**
     * Loads manifest attributes from a jar file.
     * @param jarFile The jar file.
     * @return The manifest attributes.
     * @throws IOException 
     */
    public static Attributes loadFromJar(File jarFile) throws IOException {
        ZipFile zipFile = new ZipFile(jarFile);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (JAR_MANIFEST_REL_PATH.equalsIgnoreCase(entry.getName())) {
                return loadManifestAttributes(zipFile.getInputStream(entry));
            }
        }

        throw new FileNotFoundException("Could not find MANIFEST.MF in " + jarFile.getAbsolutePath());
    }
}
