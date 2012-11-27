/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.services;

import java.util.List;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Abstraction to facilitate access to files and directories.
 */
public class FileManager {
	
	private SleuthkitCase tskCase;

	public FileManager(SleuthkitCase tskCase) {
		this.tskCase = tskCase;
	}
	
	/**
	 * @param fileName the name of the file or directory to match
	 * @return a list of FsContent for files/directories whose name matches the
	 * given fileName
	 */
	public List<FsContent> findFiles(String fileName) throws TskCoreException {
		return tskCase.findFiles(fileName);
	}
	
	/**
	 * @param fileName the name of the file or directory to match
	 * @param dirName the name of a parent directory of fileName
	 * @return a list of FsContent for files/directories whose name matches
	 * fileName and whose parent directory contains dirName.
	 */
	public List<FsContent> findFiles(String fileName, String dirName) throws TskCoreException {
		return tskCase.findFiles(fileName, dirName);
	}
	
	/**
	 * @param fileName the name of the file or directory to match
	 * @param parentFsContent 
	 * @return a list of FsContent for files/directories whose name matches
	 * fileName and that were inside a directory described by parentFsContent.
	 */
	public List<FsContent> findFiles(String fileName, FsContent parentFsContent) throws TskCoreException {
		return findFiles(fileName, parentFsContent.getName());
	}
	
	/**
	 * @param filePath The full path to the file(s) of interest. This can
	 * optionally include the image and volume names.
	 * @return a list of FsContent that have the given file path.
	 */
	public List<FsContent> openFiles(String filePath) throws TskCoreException {
		return tskCase.openFiles(filePath);
	}
	
}
