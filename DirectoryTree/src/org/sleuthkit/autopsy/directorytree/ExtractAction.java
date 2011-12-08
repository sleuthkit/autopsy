// TODO: ContentNode fix - reimplement ExtractAction


///*
// * Autopsy Forensic Browser
// *
// * Copyright 2011 Basis Technology Corp.
// * Contact: carrier <at> sleuthkit <dot> org
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.sleuthkit.autopsy.directorytree;
//
//import org.sleuthkit.autopsy.datamodel.FileNode;
//import java.io.*;
//import java.awt.event.ActionEvent;
//import javax.swing.JFileChooser;
//import java.io.File;
//import java.awt.Component;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import javax.swing.AbstractAction;
//import javax.swing.JPanel;
//import javax.swing.filechooser.FileFilter;
//import org.openide.nodes.Node;
//import org.sleuthkit.autopsy.casemodule.GeneralFilter;
//import org.sleuthkit.autopsy.datamodel.DirectoryNode;
//import org.sleuthkit.autopsy.logging.Log;
//import org.sleuthkit.datamodel.Content;
//import org.sleuthkit.datamodel.TskException;
//
///**
// * This is an action class to extract and save the bytes given as a file.
// *
// * @author jantonius
// */
//public final class ExtractAction extends AbstractAction {
//
//    private JFileChooser fc = new JFileChooser();
//    private byte[] source;
//    private Content content;
//    private String fileName;
//    private String extension;
//    // for error handling
//    private JPanel caller;
//    private String className = this.getClass().toString();
//
//    /** the constructor */
//    public ExtractAction(String title, Node contentNode) {
//        super(title);
//        
//        String fullFileName = contentNode.getDisplayName();
//        
//        
//        if (fullFileName.equals(".")) {
//            // . folders are not associated with their children in the database,
//            // so get original
//            Node parentNode = contentNode.getParentNode();            
//            contentNode = parentNode;
//            fullFileName = parentNode.getDisplayName();
//        }
//        
//        
//        this.content = contentNode.getLookup().lookup(Content.class);
//        
//        long size = content.getSize();
//        
//
//
//        /**
//         * Checks first if the the selected it file or directory. If it's a file,
//         * check if the file size is bigger than 0. If it's a directory, check
//         * if it's not referring to the parent directory. Disables the menu otherwise.
//         */
//        if ((contentNode instanceof FileNode && size > 0) || (contentNode instanceof DirectoryNode && !fullFileName.equals(".."))) {
//            if (contentNode instanceof FileNode && fullFileName.contains(".")) {
//                String tempFileName = fullFileName.substring(0, fullFileName.indexOf("."));
//                String tempExtension = fullFileName.substring(fullFileName.indexOf("."));
//                this.fileName = tempFileName;
//                this.extension = tempExtension;
//            } else {
//                this.fileName = fullFileName;
//                this.extension = "";
//            }
//        } else {
//            this.fileName = fullFileName;
//            this.extension = "";
//            this.setEnabled(false); // can't extract zero-sized file or ".." directory
//        }
//
//    }
//
//    /**
//     * Converts and saves the bytes into the file.
//     *
//     * @param e  the action event
//     */
//    @Override
//    public void actionPerformed(ActionEvent e) {
//        Log.noteAction(this.getClass());
//
//        // set the filter for File
//        if (content instanceof org.sleuthkit.datamodel.File && !extension.equals("")) {
//            //FileFilter filter = new ExtensionFileFilter(extension.substring(1).toUpperCase() + " File (*" + extension + ")", new String[]{extension.substring(1)});
//            String[] fileExt = {extension};
//            FileFilter filter = new GeneralFilter(fileExt, extension.substring(1).toUpperCase() + " File (*" + extension + ")", false);
//            fc.setFileFilter(filter);
//        }
//
//        fc.setSelectedFile(new File(this.fileName));
//
//        int returnValue = fc.showSaveDialog((Component) e.getSource());
//        if (returnValue == JFileChooser.APPROVE_OPTION) {
//            String path = fc.getSelectedFile().getPath() + extension;
//            
//            
//            // TODO: convert this to a Content Visitor
//            try {
//                // file extraction
//                if (content instanceof org.sleuthkit.datamodel.File) {
//                    extractFile(path, (org.sleuthkit.datamodel.File) content);
//                }
//
//                // directory extraction
//                if (content instanceof org.sleuthkit.datamodel.File) {
//                    extractDirectory(path, (org.sleuthkit.datamodel.Directory) content);
//                }
//            } catch (Exception ex) {
//                Logger.getLogger(this.className).log(Level.WARNING, "Error: Couldn't extract file/directory.", ex);
//            }
//
//        }
//
//    }
//
//    /**
//     * Extracts the content of the given fileNode into the given path.
//     *
//     * @param givenPath  the path to extract the file
//     * @param file   the file node that contain the file
//     */
//    private void extractFile(String givenPath, org.sleuthkit.datamodel.File file) throws Exception {
//        try {
//            if (file.getSize() > 0) {
//                try {
//                    this.source = file.read(0, file.getSize());
//                } catch (TskException ex) {
//                    throw new Exception("Error: can't read the content of the file.", ex);
//                }
//            } else {
//                this.source = new byte[0];
//            }
//
//            String path = givenPath;
//
//            File outputFile = new File(path);
//            if (outputFile.exists()) {
//                outputFile.delete();
//            }
//            outputFile.createNewFile();
//            // convert char to byte
//            byte[] dataSource = new byte[source.length];
//            for (int i = 0; i < source.length; i++) {
//                dataSource[i] = (byte) source[i];
//            }
//            FileOutputStream fos = new FileOutputStream(outputFile);
//            //fos.write(dataSource);
//            fos.write(dataSource);
//            fos.close();
//        } catch (IOException ex) {
//            throw new Exception("Error while trying to extract the file.", ex);
//        }
//    }
//
//    /**
//     * Extracts the content of the given directoryNode into the given path.
//     *
//     * @param givenPath  the path to extract the directory
//     * @param dirNode    the directory node that contain the directory
//     */
//    private void extractDirectory(String givenPath, DirectoryNode dirNode) throws Exception {
//        String path = givenPath;
//        File dir = new File(path);
//        if (!dir.exists()) {
//            dir.mkdir();
//        }
//
//        int totalChildren = dirNode.getChildren().getNodesCount();
//        for (int i = 0; i < totalChildren; i++) {
//            Node childNode = dirNode.getChildren().getNodeAt(i);
//
//            if (childNode instanceof FileNode) {
//                FileNode fileNode = (FileNode) childNode;
//                String tempPath = path + File.separator + ((Node)fileNode).getDisplayName();
//                try {
//                    extractFile(tempPath, fileNode);
//                } catch (Exception ex) {
//                    throw ex;
//                }
//            }
//
//            if (childNode instanceof DirectoryNode) {
//                DirectoryNode dirNode2 = (DirectoryNode) childNode;
//                String dirNode2Name = ((Node)dirNode2).getDisplayName();
//                
//                if (!dirNode2Name.trim().equals(".") && !dirNode2Name.trim().equals("..")) {
//                    String tempPath = path + File.separator + dirNode2Name;
//                    extractDirectory(tempPath, dirNode2);
//                }
//            }
//        }
//
//
//    }
//}
