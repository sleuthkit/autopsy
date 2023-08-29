/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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
 *
 * @author gregd
 */
public class ManifestLoader {
    private static final String JAR_MANIFEST_REL_PATH = "META-INF/MANIFEST.MF";

    public static Attributes loadInputStream(InputStream is) throws IOException {
        Manifest manifest = new Manifest(is);
        return manifest.getMainAttributes();
    }

    public static Attributes loadFromJar(File jarFile) throws IOException {
        ZipFile zipFile = new ZipFile(jarFile);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (JAR_MANIFEST_REL_PATH.equalsIgnoreCase(entry.getName())) {
                return loadInputStream(zipFile.getInputStream(entry));
            }
        }

        throw new FileNotFoundException("Could not find MANIFEST.MF in " + jarFile.getAbsolutePath());
    }
}
