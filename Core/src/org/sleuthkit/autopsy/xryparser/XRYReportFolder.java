/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.xryparser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *
 */
public class XRYReportFolder {

    //Depth to the first potential XRY file. Root children are at 1. We expect 
    //XRY files to be contained in a subfolder of the root, so 2.
    private static final int XRY_FILES_DEPTH = 2;

    public XRYReportFolder(Path reportFolder) {

    }

    public List<Path> getXRYReportFiles() {
        throw new UnsupportedOperationException();
    }

    public List<Path> getOtherFiles() {
        throw new UnsupportedOperationException();
    }

    /**
     * Searches all immediate subdirectories looking for XRY files. If any file
     * matches, the folder is assumed to be an XRY Report folder.
     *
     * @param folder Folder to test. Assumes that caller has read access to the
     * folder and all of its immediate sub folders.
     * @return Flag indicating a valid XRY report folder.
     *
     * @throws IOException Error occurred during File I/O.
     * @throws SecurityException If the security manager denies access to the
     * starting folder.
     */
    public static boolean isXRYReportFolder(Path folder) throws IOException {
        BasicFileAttributes folderAttributes = Files.readAttributes(folder, BasicFileAttributes.class);
        if (!folderAttributes.isDirectory()) {
            return false;
        }

        try (Stream<Path> folderChildren = Files.walk(folder, XRY_FILES_DEPTH)) {
            Optional<Path> xryFile = folderChildren
                    //Filter out all directories.
                    .filter(path -> {
                        try {
                            BasicFileAttributes fileAttributes = 
                                    Files.readAttributes(path, BasicFileAttributes.class);
                            return !fileAttributes.isDirectory();
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    })
                    //Filter out all files that are not in the right depth.
                    .filter(path -> folder.relativize(path).getNameCount() == XRY_FILES_DEPTH)
                    //Filter out all files that don't pass the report test.
                    .filter(filePath -> XRYReportFile.isXRYReportFile(filePath))
                    //For debugging.
                    .peek(path -> System.out.println(path))
                    .findAny();
            return xryFile.isPresent();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
