/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Abstraction to facilitate access to files and directories.
 */
public class FileManager implements Closeable {
	
	private SleuthkitCase tskCase;

	public FileManager(SleuthkitCase tskCase) {
		this.tskCase = tskCase;
	}
	
	/**
	 * @param fileName the name of the file or directory to match
	 * @return a list of FsContent for files/directories whose name matches the
	 * given fileName
	 */
	public synchronized List<FsContent> findFiles(Image image, String fileName) throws TskCoreException {
            if (tskCase == null) {
                throw new TskCoreException("Attemtped to use FileManager after it was closed.");
            }
            return tskCase.findFiles(image, fileName);
	}
	
	/**
	 * @param fileName the name of the file or directory to match
	 * @param dirName the name of a parent directory of fileName
	 * @return a list of FsContent for files/directories whose name matches
	 * fileName and whose parent directory contains dirName.
	 */
	public synchronized List<FsContent> findFiles(Image image, String fileName, String dirName) throws TskCoreException {
            if (tskCase == null) {
                throw new TskCoreException("Attemtped to use FileManager after it was closed.");
            }
            return tskCase.findFiles(image, fileName, dirName);
	}
	
	/**
	 * @param fileName the name of the file or directory to match
	 * @param parentFsContent 
	 * @return a list of FsContent for files/directories whose name matches
	 * fileName and that were inside a directory described by parentFsContent.
	 */
	public synchronized List<FsContent> findFiles(Image image, String fileName, FsContent parentFsContent) throws TskCoreException {
            if (tskCase == null) {
                throw new TskCoreException("Attemtped to use FileManager after it was closed.");
            }
            return findFiles(image, fileName, parentFsContent.getName());
	}
	
	/**
	 * @param filePath The full path to the file(s) of interest. This can
	 * optionally include the image and volume names.
	 * @return a list of FsContent that have the given file path.
	 */
	public synchronized List<FsContent> openFiles(Image image, String filePath) throws TskCoreException {
            if (tskCase == null) {
                throw new TskCoreException("Attemtped to use FileManager after it was closed.");
            }
            return tskCase.openFiles(image, filePath);
	}

    @Override
    public synchronized void close() throws IOException {
        tskCase = null;
    }
	
}
