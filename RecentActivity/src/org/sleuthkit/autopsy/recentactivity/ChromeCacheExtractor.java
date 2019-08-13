/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 *
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * Extracts and parses Chrome Cache files.
 * 
 * Cache may hold images, scripts, CSS, JSON files, 
 * and the URL they were downloaded from.
 * 
 * Cache entries may or may not be compressed, 
 * and the entries may reside in container files or external files.
 * 
 * We extract cache entries, create derived files if needed, 
 * and record the URL.
 */
final class ChromeCacheExtractor {
    
    private final static String DEFAULT_CACHE_PATH_STR = "default/cache"; //NON-NLS
    private final static String BROTLI_MIMETYPE ="application/x-brotli"; //NON-NLS
    
    private final static long UINT32_MASK = 0xFFFFFFFFl;
    
    private final static int INDEXFILE_HDR_SIZE = 92*4;
    private final static int DATAFILE_HDR_SIZE = 8192;
    
    private final static Logger logger = Logger.getLogger(ChromeCacheExtractor.class.getName());
    
    private static final String VERSION_NUMBER = "1.0.0"; //NON-NLS
    private final String moduleName;
    
    private String absOutputFolderName;
    private String relOutputFolderName;
     
    private final Content dataSource;
    private final IngestJobContext context;
    private final DataSourceIngestModuleProgress progressBar;
    private final IngestServices services = IngestServices.getInstance();
    private Case currentCase;
    private FileManager fileManager;

    // A file table to cache copies of index and data_n files.
    private final Map<String, CacheFileCopy> fileCopyCache = new HashMap<>();
    
    // A file table to cache the f_* files.
    private final Map<String, AbstractFile> externalFilesTable = new HashMap<>();
    
    /**
     * Encapsulates  abstract file for a cache file as well as a temp file copy
     * that can be accessed as a random access file.
     */
    final class CacheFileCopy {
        
        private final AbstractFile abstractFile;
        private final RandomAccessFile fileCopy;
        private final ByteBuffer byteBuffer;

        CacheFileCopy (AbstractFile abstractFile, RandomAccessFile fileCopy, ByteBuffer buffer ) {
            this.abstractFile = abstractFile;
            this.fileCopy = fileCopy;
            this.byteBuffer = buffer;
        }
        
        public RandomAccessFile getFileCopy() {
            return fileCopy;
        }
        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }
        AbstractFile getAbstractFile() {
            return abstractFile;
        } 
    }

    @NbBundle.Messages({
        "ChromeCacheExtractor.moduleName=ChromeCacheExtractor",
        "# {0} - module name",
        "# {1} - row number",
        "# {2} - table length",
        "# {3} - cache path",
        "ChromeCacheExtractor.progressMsg={0}: Extracting cache entry {1} of {2} entries from {3}"
    })
    ChromeCacheExtractor(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar ) { 
        moduleName = Bundle.ChromeCacheExtractor_moduleName();
        this.dataSource = dataSource;
        this.context = context;
        this.progressBar = progressBar;
    }
    
    
    /**
     * Initializes Chrome cache extractor module.
     * 
     * @throws IngestModuleException 
     */
    private void moduleInit() throws IngestModuleException {
        
        try {
            currentCase = Case.getCurrentCaseThrows();
            fileManager = currentCase.getServices().getFileManager();
             
            // Create an output folder to save any derived files
            absOutputFolderName = RAImageIngestModule.getRAOutputPath(currentCase, moduleName);
            relOutputFolderName = Paths.get( RAImageIngestModule.getRelModuleOutputPath(), moduleName).normalize().toString();
            
            File dir = new File(absOutputFolderName);
            if (dir.exists() == false) {
                dir.mkdirs();
            }
        } catch (NoCurrentCaseException ex) {
            String msg = "Failed to get current case."; //NON-NLS
            throw new IngestModuleException(msg, ex);
        } 
    }
    
    /**
     * Initializes the module to extract cache from a specific folder.
     * 
     * @param cachePath - path where cache files are found 
     * 
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    private void resetForNewFolder(String cachePath) throws IngestModuleException {
        
        fileCopyCache.clear();
        externalFilesTable.clear();
        
        String cacheAbsOutputFolderName = this.getAbsOutputFolderName() + cachePath;
        File outDir = new File(cacheAbsOutputFolderName);
        if (outDir.exists() == false) {
            outDir.mkdirs();
        }
        
        String cacheTempPath = RAImageIngestModule.getRATempPath(currentCase, moduleName) + cachePath;
        File tempDir = new File(cacheTempPath);
        if (tempDir.exists() == false) {
            tempDir.mkdirs();
        }
    }
    
    /**
     * Cleans up after the module is done.
     * 
     * Removes any temp copies of cache files created during extraction.
     * 
     */
    private void cleanup () {
        
        for (Entry<String, CacheFileCopy> entry : this.fileCopyCache.entrySet()) {
            Path tempFilePath = Paths.get(RAImageIngestModule.getRATempPath(currentCase, moduleName), entry.getKey() ); 
            try {
                entry.getValue().getFileCopy().getChannel().close();
                entry.getValue().getFileCopy().close();
               
                File tmpFile = tempFilePath.toFile();
                if (!tmpFile.delete()) {
                    tmpFile.deleteOnExit();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Failed to delete cache file copy %s", tempFilePath.toString()), ex); //NON-NLS
            }
        }
    }
    
    /**
     * Returns the location of output folder for this module
     * 
     * @return absolute location of output folder
     */
    private String getAbsOutputFolderName() {
        return absOutputFolderName;
    }
    
     /**
     * Returns the relative location of output folder for this module
     * 
     * @return relative location of output folder
     */
    private String getRelOutputFolderName() {
        return relOutputFolderName;
    }
    
    /**
     * Extracts the data from Chrome caches .  Main entry point for the analysis
     * 
     * A data source may have multiple Chrome user profiles and caches.
     * 
     */
    void getCaches() {
        
         try {
           moduleInit();
        } catch (IngestModuleException ex) {
            String msg = "Failed to initialize ChromeCacheExtractor."; //NON-NLS
            logger.log(Level.SEVERE, msg, ex);
            return;
        }
        
         // Find and process the cache folders.  There could be one per user
        List<AbstractFile> indexFiles;
        try {
            indexFiles = findCacheIndexFiles(); 
            
            // Process each of the caches
            for (AbstractFile indexFile: indexFiles) {  
                
                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }
                
                processCacheIndexFile(indexFile);
            }
        
        } catch (TskCoreException ex) {
                String msg = "Failed to find cache index files"; //NON-NLS
                logger.log(Level.WARNING, msg, ex);
        } 
    }
    
    /**
     * Processes a user's cache and creates corresponding artifacts and derived files. 
     * 
     * @param cacheIndexFile Cache index file for a given user
     */
    private void processCacheIndexFile(AbstractFile indexAbstractFile) {
        
        String cachePath = indexAbstractFile.getParentPath();
        Optional<CacheFileCopy> indexFileCopy;
        try {
            resetForNewFolder(cachePath);
             
            // @@@ This is little ineffecient because we later in this call search for the AbstractFile that we currently have
            indexFileCopy = this.getCacheFileCopy(indexAbstractFile.getName(), cachePath);
            if (!indexFileCopy.isPresent()) {
                String msg = String.format("Failed to find copy cache index file %s", indexAbstractFile.getUniquePath());
                logger.log(Level.WARNING, msg);
                return;
            }

            
            // load the data files.  We do this now to load them into the cache
            for (int i = 0; i < 4; i ++)  {
                Optional<CacheFileCopy> dataFile = findAndCopyCacheFile(String.format("data_%1d",i), cachePath );
                if (!dataFile.isPresent()) {
                    return;
                }
            }
            
            // find all f_* files in a single query and load them into the cache
            findExternalFiles(cachePath);

        } catch (TskCoreException | IngestModuleException ex) {
            String msg = "Failed to find cache files in path " + cachePath; //NON-NLS
            logger.log(Level.WARNING, msg, ex);
            return;
        } 

        
        // parse the index file
        logger.log(Level.INFO, "{0}- Now reading Cache index file from path {1}", new Object[]{moduleName, cachePath }); //NON-NLS

        List<AbstractFile> derivedFiles = new ArrayList<>();
        Collection<BlackboardArtifact> sourceArtifacts = new ArrayList<>();
        Collection<BlackboardArtifact> webCacheArtifacts = new ArrayList<>();

        ByteBuffer indexFileROBuffer = indexFileCopy.get().getByteBuffer();
        IndexFileHeader indexHdr = new IndexFileHeader(indexFileROBuffer);

        // seek past the header
        indexFileROBuffer.position(INDEXFILE_HDR_SIZE);

        // Process each address in the table
        for (int i = 0; i <  indexHdr.getTableLen(); i++) {
            
            if (context.dataSourceIngestIsCancelled()) {
                cleanup();
                return;
            }
            
            CacheAddress addr = new CacheAddress(indexFileROBuffer.getInt() & UINT32_MASK, cachePath);
            if (addr.isInitialized()) {
                progressBar.progress( NbBundle.getMessage(this.getClass(),
                                        "ChromeCacheExtractor.progressMsg",
                                        moduleName, i, indexHdr.getTableLen(), cachePath)  );
                try {
                    List<DerivedFile> addedFiles = this.processCacheEntry(addr, sourceArtifacts, webCacheArtifacts);
                    derivedFiles.addAll(addedFiles);
                }
                catch (TskCoreException | IngestModuleException ex) {
                   logger.log(Level.WARNING, String.format("Failed to get cache entry at address %s", addr), ex); //NON-NLS
                } 
            }  
        }
        
        if (context.dataSourceIngestIsCancelled()) {
            cleanup();
            return;
        }

        derivedFiles.forEach((derived) -> {
            services.fireModuleContentEvent(new ModuleContentEvent(derived));
         });

        context.addFilesToJob(derivedFiles);

        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();
        
        try {
            blackboard.postArtifacts(sourceArtifacts, moduleName);
            blackboard.postArtifacts(webCacheArtifacts, moduleName);
        } catch (Blackboard.BlackboardException ex) {
           logger.log(Level.WARNING, String.format("Failed to post cacheIndex artifacts "), ex); //NON-NLS
        }
       
        cleanup();
    }
    
    /**
     * Gets the cache entry at the specified address.
     * 
     * Extracts the files if needed and adds as derived files, creates artifacts
     * 
     * @param cacheEntryAddress cache entry address
     * @param sourceArtifacts any source artifacts created are added to this collection
     * @param webCacheArtifacts any web cache artifacts created are added to this collection
     * 
     * @return Optional derived file, is a derived file is added for the given entry
     */
    private List<DerivedFile> processCacheEntry(CacheAddress cacheEntryAddress, Collection<BlackboardArtifact> sourceArtifacts, Collection<BlackboardArtifact> webCacheArtifacts ) throws TskCoreException, IngestModuleException {
         
        List<DerivedFile> derivedFiles = new ArrayList<>();
        
        // get the path to the corresponding data_X file
        String dataFileName = cacheEntryAddress.getFilename(); 
        String cachePath = cacheEntryAddress.getCachePath();
            
        
        Optional<CacheFileCopy> cacheEntryFile = this.getCacheFileCopy(dataFileName, cachePath);
        if (!cacheEntryFile.isPresent()) {
            String msg = String.format("Failed to get cache entry at address %s", cacheEntryAddress); //NON-NLS
            throw new IngestModuleException(msg);
        }

        
        // Get the cache entry and its data segments
        CacheEntry cacheEntry = new CacheEntry(cacheEntryAddress, cacheEntryFile.get() );
        
        List<CacheData> dataEntries = cacheEntry.getData();
        // Only process the first payload data segment in each entry
        //  first data segement has the HTTP headers, 2nd is the payload
        if (dataEntries.size() < 2) {
            return derivedFiles;
        }
        CacheData dataSegment = dataEntries.get(1);


        // name of the file that was downloaded and cached (or data_X if it was saved into there)
        String cachedFileName = dataSegment.getAddress().getFilename();
        Optional<AbstractFile> cachedFileAbstractFile = this.findCacheFile(cachedFileName, cachePath);
        if (!cachedFileAbstractFile.isPresent()) {
            logger.log(Level.WARNING, "Error finding file: " + cachePath + "/" + cachedFileName); //NON-NLS
            return derivedFiles;
        }        
        
        boolean isBrotliCompressed = false;
        if (dataSegment.getType() != CacheDataTypeEnum.HTTP_HEADER && cacheEntry.isBrotliCompressed() ) {
            isBrotliCompressed = true;
        }

        // setup some attributes for later use
        BlackboardAttribute urlAttr = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                                                    moduleName,
                                                    ((cacheEntry.getKey() != null) ? cacheEntry.getKey() : ""));
        BlackboardAttribute createTimeAttr = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                                                    moduleName,
                                                    cacheEntry.getCreationTime());
        BlackboardAttribute httpHeaderAttr = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HEADERS,
                                                    moduleName,
                                                    cacheEntry.getHTTPHeaders());
        
        Collection<BlackboardAttribute> sourceArtifactAttributes = new ArrayList<>();
        sourceArtifactAttributes.add(urlAttr);
        sourceArtifactAttributes.add(createTimeAttr);

        Collection<BlackboardAttribute> webCacheAttributes = new ArrayList<>();
        webCacheAttributes.add(urlAttr);
        webCacheAttributes.add(createTimeAttr);
        webCacheAttributes.add(httpHeaderAttr);

        
        // add artifacts to the f_XXX file
        if (dataSegment.isInExternalFile() )  {
            try {
                BlackboardArtifact sourceArtifact = cachedFileAbstractFile.get().newArtifact(ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE);
                if (sourceArtifact != null) {
                    sourceArtifact.addAttributes(sourceArtifactAttributes);
                    sourceArtifacts.add(sourceArtifact);
                }

                BlackboardArtifact webCacheArtifact = cacheEntryFile.get().getAbstractFile().newArtifact(ARTIFACT_TYPE.TSK_WEB_CACHE);
                if (webCacheArtifact != null) {
                    webCacheArtifact.addAttributes(webCacheAttributes);

                     // Add path of f_* file as attribute
                    webCacheArtifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH,
                        moduleName, 
                        cachedFileAbstractFile.get().getUniquePath()));

                    webCacheArtifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID,
                                moduleName, cachedFileAbstractFile.get().getId()));

                    webCacheArtifacts.add(webCacheArtifact);
                }

                if (isBrotliCompressed) {
                    cachedFileAbstractFile.get().setMIMEType(BROTLI_MIMETYPE);
                    cachedFileAbstractFile.get().save();
                }
            } catch (TskException ex) {
                logger.log(Level.SEVERE, "Error while trying to add an artifact", ex); //NON-NLS
            }
        } 
        // extract the embedded data to a derived file and create artifacts
        else {

            // Data segments in "data_x" files are saved in individual files and added as derived files
            String filename = dataSegment.save();
            String relPathname = getRelOutputFolderName() + dataSegment.getAddress().getCachePath() + filename; 
            try {
                DerivedFile derivedFile = fileManager.addDerivedFile(filename, relPathname,
                                                    dataSegment.getDataLength(), 
                                                    cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), // TBD 
                                                    true, 
                                                    cachedFileAbstractFile.get(), 
                                                    "",
                                                    moduleName, 
                                                    VERSION_NUMBER, 
                                                    "", 
                                                    TskData.EncodingType.NONE);

                BlackboardArtifact sourceArtifact = derivedFile.newArtifact(ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE);
                if (sourceArtifact != null) {
                    sourceArtifact.addAttributes(sourceArtifactAttributes);
                    sourceArtifacts.add(sourceArtifact);
                }    

                BlackboardArtifact webCacheArtifact =  cacheEntryFile.get().getAbstractFile().newArtifact(ARTIFACT_TYPE.TSK_WEB_CACHE); 
                if (webCacheArtifact != null) {
                    webCacheArtifact.addAttributes(webCacheAttributes);

                    // Add path of derived file as attribute
                    webCacheArtifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH,
                        moduleName, 
                        derivedFile.getUniquePath()));

                    webCacheArtifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID,
                            moduleName, derivedFile.getId()));

                    webCacheArtifacts.add(webCacheArtifact);
                }

                if (isBrotliCompressed) {
                    derivedFile.setMIMEType(BROTLI_MIMETYPE);
                    derivedFile.save();
                }

                derivedFiles.add(derivedFile);
            } catch (TskException ex) {
                logger.log(Level.SEVERE, "Error while trying to add an artifact", ex); //NON-NLS
            }
        }
        
        return derivedFiles;
     }
    
    /**
     * Finds all the f_* files in the specified path, and fills them in the 
     * effFilesTable, so that subsequent searches are fast.
     * 
     * @param cachePath path under which to look for.
     * 
     * @throws TskCoreException 
     */
    private void findExternalFiles(String cachePath) throws TskCoreException {
        
        List<AbstractFile> effFiles = fileManager.findFiles(dataSource, "f_%", cachePath); //NON-NLS 
        for (AbstractFile abstractFile : effFiles ) {
            this.externalFilesTable.put(cachePath + abstractFile.getName(), abstractFile);
        }
    }
    /**
     * Finds abstract file for cache file with a specified name.
     * First checks in the file tables.
     * 
     * @param cacheFileName
     * @return Optional abstract file 
     * @throws TskCoreException 
     */
    private Optional<AbstractFile> findCacheFile(String cacheFileName, String cachePath) throws TskCoreException {
       
        // see if it is cached
        String fileTableKey = cachePath + cacheFileName;
        if (cacheFileName.startsWith("f_") && externalFilesTable.containsKey(fileTableKey)) {
            return Optional.of(externalFilesTable.get(fileTableKey));
        }
        if (fileCopyCache.containsKey(fileTableKey)) {
            return Optional.of(fileCopyCache.get(fileTableKey).getAbstractFile());
        }
        
        
        List<AbstractFile> cacheFiles = fileManager.findFiles(dataSource, cacheFileName, cachePath); //NON-NLS
        if (!cacheFiles.isEmpty()) {
            for (AbstractFile abstractFile: cacheFiles ) {
                if (abstractFile.getUniquePath().trim().endsWith(DEFAULT_CACHE_PATH_STR)) {
                    return Optional.of(abstractFile);
                }
            }
            return Optional.of(cacheFiles.get(0));
        }
        
        return Optional.empty(); 
    }
    
     /**
     * Finds abstract file(s) for a cache file with the specified name.
     * 
     * @return list of abstract files matching the specified file name
     * @throws TskCoreException 
     */
    private List<AbstractFile> findCacheIndexFiles() throws TskCoreException {
        return fileManager.findFiles(dataSource, "index", DEFAULT_CACHE_PATH_STR); //NON-NLS 
    }
    
    
    /**
     * Returns CacheFileCopy for the specified file from the file table.
     * Find the file and creates a copy if it isn't already in the table.
     *
     * @param cacheFileName Name of file 
     * @param cachePath Parent path of file
     * @return CacheFileCopy
     * @throws TskCoreException 
     */
    private Optional<CacheFileCopy> getCacheFileCopy(String cacheFileName, String cachePath) throws TskCoreException, IngestModuleException {
        
        // Check if the file is already in the cache
        String fileTableKey = cachePath + cacheFileName;
        if (fileCopyCache.containsKey(fileTableKey)) {
            return Optional.of(fileCopyCache.get(fileTableKey));
        }
        
        return findAndCopyCacheFile(cacheFileName, cachePath);
    }
     
    /**
     * Finds the specified cache file under the specified path, and makes a temporary copy.
     * 
     * @param cacheFileName
     * @return Cache file copy
     * @throws TskCoreException 
     */ 
    private Optional<CacheFileCopy> findAndCopyCacheFile(String cacheFileName, String cachePath) throws TskCoreException, IngestModuleException  {
        
        Optional<AbstractFile> cacheFileOptional = findCacheFile(cacheFileName, cachePath);
        if (!cacheFileOptional.isPresent()) {
            return Optional.empty(); 
        }
        
        
        // write the file to disk so that we can have a memory-mapped ByteBuffer
        // @@@ NOTE: I"m not sure this is needed. These files are small enough and we could probably just load them into
        //    a byte[] for ByteBuffer.
        AbstractFile cacheFile = cacheFileOptional.get();
        RandomAccessFile randomAccessFile = null;
        String tempFilePathname = RAImageIngestModule.getRATempPath(currentCase, moduleName) + cachePath + cacheFile.getName(); //NON-NLS
        try {
            File newFile = new File(tempFilePathname);
            ContentUtils.writeToFile(cacheFile, newFile, context::dataSourceIngestIsCancelled);
            
            randomAccessFile = new RandomAccessFile(tempFilePathname, "r");
            FileChannel roChannel = randomAccessFile.getChannel();
            ByteBuffer cacheFileROBuf = roChannel.map(FileChannel.MapMode.READ_ONLY, 0,
                                                        (int) roChannel.size());

            cacheFileROBuf.order(ByteOrder.nativeOrder());
            CacheFileCopy cacheFileCopy = new CacheFileCopy(cacheFile, randomAccessFile, cacheFileROBuf );
            
            if (!cacheFileName.startsWith("f_")) {
                fileCopyCache.put(cachePath + cacheFileName, cacheFileCopy);
            }
            
            return Optional.of(cacheFileCopy);
        }
        catch (IOException ex) {
           
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            }
            catch (IOException ex2) {
                logger.log(Level.SEVERE, "Error while trying to close temp file after exception.", ex2); //NON-NLS
            }
            String msg = String.format("Error reading/copying Chrome cache file '%s' (id=%d).", //NON-NLS
                                            cacheFile.getName(), cacheFile.getId()); 
            throw new IngestModuleException(msg, ex);
        } 
    }
    
    /**
     * Encapsulates the header found in the index file
     */
    final class IndexFileHeader {
    
        private final long magic;
        private final int version;
        private final int numEntries;
        private final int numBytes;
        private final int lastFile;
        private final int tableLen;
            
        IndexFileHeader(ByteBuffer indexFileROBuf) {
        
            magic = indexFileROBuf.getInt() & UINT32_MASK; 
          
            indexFileROBuf.position(indexFileROBuf.position()+2);
             
            version = indexFileROBuf.getShort();
            numEntries = indexFileROBuf.getInt();
            numBytes = indexFileROBuf.getInt();
            lastFile = indexFileROBuf.getInt();
            
            indexFileROBuf.position(indexFileROBuf.position()+4); // this_id
            indexFileROBuf.position(indexFileROBuf.position()+4); // stats cache address
            
            tableLen = indexFileROBuf.getInt();
        }
        
        public long getMagic() {
            return magic;
        }

        public int getVersion() {
            return version;
        }

        public int getNumEntries() {
            return numEntries;
        }

        public int getNumBytes() {
            return numBytes;
        }

        public int getLastFile() {
            return lastFile;
        }

        public int getTableLen() {
            return tableLen;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            
            sb.append(String.format("Index Header:"))
                .append(String.format("\tMagic = %x" , getMagic()) )
                .append(String.format("\tVersion = %x" , getVersion()) )
                .append(String.format("\tNumEntries = %x" , getNumEntries()) )
                .append(String.format("\tNumBytes = %x" , getNumBytes()) )
                .append(String.format("\tLastFile = %x" , getLastFile()) )
                .append(String.format("\tTableLen = %x" , getTableLen()) );
        
            return sb.toString();
        }
    }
    
    /**
     * Cache file type enum - as encoded the address
     */
    enum CacheFileTypeEnum {
	  EXTERNAL,     
	  RANKINGS,
	  BLOCK_256,
	  BLOCK_1K,
	  BLOCK_4K,
	  BLOCK_FILES,
	  BLOCK_ENTRIES,
	  BLOCK_EVICTED
    }
    
    
    
    /**
     * Encapsulates Cache address.  
     * 
     * CacheAddress is a unsigned 32 bit number
     *
     * Header:
     *   1000 0000 0000 0000 0000 0000 0000 0000 : initialized bit
     *   0111 0000 0000 0000 0000 0000 0000 0000 : file type
     *
     * If separate file:
     *   0000 1111 1111 1111 1111 1111 1111 1111 : file#  0 - 268,435,456 (2^28)
     *
     * If block file:
     *   0000 1100 0000 0000 0000 0000 0000 0000 : reserved bits
     *   0000 0011 0000 0000 0000 0000 0000 0000 : number of contiguous blocks 1-4
     *   0000 0000 1111 1111 0000 0000 0000 0000 : file selector 0 - 255
     *   0000 0000 0000 0000 1111 1111 1111 1111 : block#  0 - 65,535 (2^16)
     * 
     */
    final class CacheAddress {
        // sundry constants to parse the bit fields in address
        private static final long ADDR_INITIALIZED_MASK    = 0x80000000l;
	private static final long FILE_TYPE_MASK     = 0x70000000;
        private static final long FILE_TYPE_OFFSET   = 28;
	private static final long NUM_BLOCKS_MASK     = 0x03000000;
	private static final long NUM_BLOCKS_OFFSET   = 24;
	private static final long FILE_SELECTOR_MASK   = 0x00ff0000;
        private static final long FILE_SELECTOR_OFFSET = 16;
	private static final long START_BLOCK_MASK     = 0x0000FFFF;
	private static final long EXTERNAL_FILE_NAME_MASK = 0x0FFFFFFF;
        
        private final long uint32CacheAddr;
        private final CacheFileTypeEnum fileType;
        private final int numBlocks;
        private final int startBlock;
        private final String fileName;
        private final int fileNumber;
        
        private final String cachePath;
        
        
        CacheAddress(long uint32, String cachePath) {
            
            uint32CacheAddr = uint32;
            this.cachePath = cachePath;
            
            int fileTypeEnc = (int)(uint32CacheAddr &  FILE_TYPE_MASK) >> FILE_TYPE_OFFSET;
            fileType = CacheFileTypeEnum.values()[fileTypeEnc];
            
            if (isInitialized()) {
                if (isInExternalFile()) {
                    fileNumber = (int)(uint32CacheAddr & EXTERNAL_FILE_NAME_MASK);
                    fileName =  String.format("f_%06x", getFileNumber() );
                    numBlocks = 0;
                    startBlock = 0;
                } else {
                    fileNumber = (int)((uint32CacheAddr & FILE_SELECTOR_MASK) >> FILE_SELECTOR_OFFSET);
                    fileName = String.format("data_%d", getFileNumber() );
                    numBlocks = (int)(uint32CacheAddr &  NUM_BLOCKS_MASK >> NUM_BLOCKS_OFFSET);
                    startBlock = (int)(uint32CacheAddr &  START_BLOCK_MASK);
                }
            }
            else {
                fileName = null;
                fileNumber = 0;
                numBlocks = 0;
                startBlock = 0;
            }
        }

        boolean isInitialized() {
            return ((uint32CacheAddr & ADDR_INITIALIZED_MASK) != 0);
        }
        
        CacheFileTypeEnum getFileType() {
            return fileType;
        }
        
        /**
         * Name where cached file is stored.  Either a data or f_ file. 
         * @return 
         */
        String getFilename() {
            return fileName;
        }
        
        String getCachePath() {
            return cachePath;
        }
        
        boolean isInExternalFile() {
            return (fileType == CacheFileTypeEnum.EXTERNAL);
        }
        
        int getFileNumber() {
            return fileNumber;
        }
        
        int getStartBlock() {
            return startBlock;
        }
        
        int getNumBlocks() {  
            return numBlocks;
        }
        
        int getBlockSize() {
            switch (fileType) {
                case RANKINGS:
                    return 36;
                case BLOCK_256:
                    return 256;
                case BLOCK_1K:
                    return 1024;
                case BLOCK_4K:
                    return 4096;
                case BLOCK_FILES:
                    return 8;
                case BLOCK_ENTRIES:
                    return 104;
                case BLOCK_EVICTED:
                    return 48;
                default:
                    return 0;
	    }
        }
       
        public long getUint32CacheAddr() {
            return uint32CacheAddr;
        }
         
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("CacheAddr %08x : %s : filename %s", 
                                    uint32CacheAddr,
                                    isInitialized() ? "Initialized" : "UnInitialized",
                                    getFilename()));
            
            if ((fileType == CacheFileTypeEnum.BLOCK_256) || 
                (fileType == CacheFileTypeEnum.BLOCK_1K) || 
                (fileType == CacheFileTypeEnum.BLOCK_4K) ) {
                sb.append(String.format(" (%d blocks starting at %08X)", 
                                    this.getNumBlocks(),
                                    this.getStartBlock()
                                    ));
            }
                                    
            return sb.toString();
        }
        
    }
    
    /**
     * Enum for data type in a data segment.
     */
    enum CacheDataTypeEnum {
        HTTP_HEADER,
        UNKNOWN,    
    };
        
    /**
     * Encapsulates a cached data segment.
     * 
     * A data segment may have HTTP headers, scripts, image files (png, gif), or JSON files
     * 
     * A data segment may be stored in one of the data_x files or an external file - f_xxxxxx
     * 
     * A data segment may be compressed - GZIP and BRotli are the two commonly used methods.
     */
    final class CacheData {
        
        private int length;
        private final CacheAddress address;
        private CacheDataTypeEnum type;
       
        private boolean isHTTPHeaderHint;
         
        private CacheFileCopy cacheFileCopy = null;
        private byte[] data = null;
        
        private String httpResponse;
        private final Map<String, String> httpHeaders = new HashMap<>();
                
        CacheData(CacheAddress cacheAdress, int len) {
            this(cacheAdress, len, false);
        }
        
        CacheData(CacheAddress cacheAdress, int len, boolean isHTTPHeader ) {
            this.type = CacheDataTypeEnum.UNKNOWN;
            this.length = len;
            this.address = cacheAdress;
            this.isHTTPHeaderHint = isHTTPHeader;
        }
        
        boolean isInExternalFile() {
            return address.isInExternalFile();
        }
        
        boolean hasHTTPHeaders() {
            return this.type == CacheDataTypeEnum.HTTP_HEADER;
        }
        
        String getHTTPHeader(String key) {
            return this.httpHeaders.get(key);
        }
        
        /**
         * Returns all HTTP headers as a single '\n' separated string
         * 
         * @return 
         */
        String getHTTPHeaders() {
            if (!hasHTTPHeaders()) {
                return "";
            }
            
            StringBuilder sb = new StringBuilder();
            httpHeaders.entrySet().forEach((entry) -> {
                if (sb.length() > 0) {
                    sb.append(" \n");
                }
                sb.append(String.format("%s : %s",
                        entry.getKey(), entry.getValue()));
            });
                                    
            return sb.toString();
        }
        
        String getHTTPRespone() {
            return httpResponse;
        }
        
        /**
         * Extracts the data segment from the cache file 
         * 
         * @throws TskCoreException 
         */
        void extract() throws TskCoreException, IngestModuleException {

            // do nothing if already extracted, 
            if (data != null) {
                return;
            }
            
            // Don't extract data from external files.
            if (!address.isInExternalFile() ) {
                
                cacheFileCopy = getCacheFileCopy(address.getFilename(), address.getCachePath()).get();

                this.data = new byte [length];
                ByteBuffer buf = cacheFileCopy.getByteBuffer();
                int dataOffset = DATAFILE_HDR_SIZE + address.getStartBlock() * address.getBlockSize();
                buf.position(dataOffset);
                buf.get(data, 0, length);
                
                // if this might be a HTPP header, lets try to parse it as such
                if ((isHTTPHeaderHint)) {
                    String strData = new String(data);
                    if (strData.contains("HTTP")) {
                        
                        // Http headers if present, are usually in frst data segment in an entry
                        // General Parsing algo:
                        //   - Find start of HTTP header by searching for string "HTTP"
                        //   - Skip to the first 0x00 to get to the end of the HTTP response line, this makrs start of headers section
                        //   - Find the end of the header by searching for 0x00 0x00 bytes
                        //   - Extract the headers section
                        //   - Parse the headers section - each null terminated string is a header
                        //   - Each header is of the format "name: value" e.g. 
                        
                        type = CacheDataTypeEnum.HTTP_HEADER;

                        int startOff = strData.indexOf("HTTP");
                        Charset charset = Charset.forName("UTF-8");
                        boolean done = false;
                        int i = startOff;
                        int hdrNum = 1;
                        
                        while (!done) {
                            // each header is null terminated
                            int start = i;
                            while (i < data.length && data[i] != 0)  {
                                i++;
                            }
                        
                            // http headers are terminated by 0x00 0x00 
                            if (i == data.length || data[i+1] == 0) {
                                done = true;
                            }
                        
                            int len = (i - start);
                            String headerLine = new String(data, start, len, charset);
         
                            // first line is the http response
                            if (hdrNum == 1) { 
                                httpResponse = headerLine;
                            } else {
                                int nPos = headerLine.indexOf(':');
                                if (nPos > 0 ) {
                                    String key = headerLine.substring(0, nPos);
                                    String val= headerLine.substring(nPos+1);
                                    httpHeaders.put(key.toLowerCase(), val);
                                }
                            }
                            
                            i++;
                            hdrNum++;
                        }
                    }
                }
            } 
        }
        
        String getDataString() throws TskCoreException, IngestModuleException {
            if (data == null) {
                extract();
            }
            return new String(data);
        }
        
        byte[] getDataBytes() throws TskCoreException, IngestModuleException {
            if (data == null) {
                extract();
            }
            return data.clone();
        }
        
        int getDataLength() {
            return this.length;
        }
        
        CacheDataTypeEnum getType() {
            return type;
        }

        CacheAddress getAddress() {
            return address;
        }
        
        
        /**
         * Saves the data segment to a file in the local disk.
         * 
         * @return file name the data is saved in 
         * 
         * @throws TskCoreException
         * @throws IngestModuleException 
         */
        String save() throws TskCoreException, IngestModuleException {
            String fileName;
            
            if (address.isInExternalFile()) {
                fileName = address.getFilename();
            } else {
                fileName = String.format("%s__%08x", address.getFilename(), address.getUint32CacheAddr());
            }
            
            String filePathName = getAbsOutputFolderName() + address.getCachePath() + fileName;
            save(filePathName);
            
            return  fileName;
        }
        
        /**
         * Saves the data in he specified file name
         * 
         * @param filePathName - file name to save the data in
         * 
         * @throws TskCoreException
         * @throws IngestModuleException 
         */
       
        void save(String filePathName) throws TskCoreException, IngestModuleException {
            
            // Save the data to specified file 
            if (data == null) {
                extract();
            }
            
            // Data in external files is not saved in local files
            if (!this.isInExternalFile()) {
                // write the
                try (FileOutputStream stream = new FileOutputStream(filePathName)) {
                    stream.write(data);
                } catch (IOException ex) {
                    throw new TskCoreException(String.format("Failed to write output file %s", filePathName), ex);
                }
            }
        }
        
        @Override
        public String toString() {
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(String.format("\t\tData type = : %s, Data Len = %d ", 
                                    this.type.toString(), this.length ));
            
            if (hasHTTPHeaders()) {
                String str = getHTTPHeader("content-encoding");
                if (str != null) {
                    strBuilder.append(String.format("\t%s=%s", "content-encoding", str ));
                }
            }
            
            return strBuilder.toString(); 
        }
        
    }
    
    
    /**
     *  State of cache entry
     */
    enum EntryStateEnum {
        ENTRY_NORMAL,
        ENTRY_EVICTED,    
        ENTRY_DOOMED
    };

    
// Main structure for an entry on the backing storage. 
// 
// Each entry has a key, identifying the URL the cache entry pertains to.
// If the key is longer than
// what can be stored on this structure, it will be extended on consecutive
// blocks (adding 256 bytes each time), up to 4 blocks (1024 - 32 - 1 chars).
// After that point, the whole key will be stored as a data block or external
// file.
// 
// Each entry can have upto 4 data segments
//
//	struct EntryStore {
//	  uint32      hash;               // Full hash of the key.
//	  CacheAddr   next;               // Next entry with the same hash or bucket.
//	  CacheAddr   rankings_node;      // Rankings node for this entry.
//	  int32       reuse_count;        // How often is this entry used.
//	  int32       refetch_count;      // How often is this fetched from the net.
//	  int32       state;              // Current state.
//	  uint64      creation_time;
//	  int32       key_len;
//	  CacheAddr   long_key;           // Optional address of a long key.
//	  int32       data_size[4];       // We can store up to 4 data streams for each
//	  CacheAddr   data_addr[4];       // entry.
//	  uint32      flags;              // Any combination of EntryFlags.
//	  int32       pad[4];
//	  uint32      self_hash;          // The hash of EntryStore up to this point.
//	  char        key[256 - 24 * 4];  // null terminated
//	};

    /** 
     * Encapsulates a Cache Entry
     */
    final class CacheEntry {
    
        // each entry is 256 bytes.  The last section of the entry, after all the other fields is a null terminated key
        private static final int MAX_KEY_LEN = 256-24*4; 
        
        private final CacheAddress selfAddress; 
        private final CacheFileCopy cacheFileCopy;
        
        private final long hash;
        private final CacheAddress nextAddress;
        private final CacheAddress rankingsNodeAddress;
        
        private final int reuseCount;
        private final int refetchCount;
        private final EntryStateEnum state;
        
        private final long creationTime;
        private final int keyLen;
        
        private final CacheAddress longKeyAddresses; // address of the key, if the key is external to the entry
        
        private final int dataSizes[];
        private final CacheAddress dataAddresses[];
        private List<CacheData> dataList;
                
        private final long flags;
       
        private String key;     // Key may be found within the entry or may be external
        
        CacheEntry(CacheAddress cacheAdress, CacheFileCopy cacheFileCopy ) {
            this.selfAddress = cacheAdress;
            this.cacheFileCopy = cacheFileCopy;
            
            ByteBuffer fileROBuf = cacheFileCopy.getByteBuffer();
            
            int entryOffset = DATAFILE_HDR_SIZE + cacheAdress.getStartBlock() * cacheAdress.getBlockSize();
            
            // reposition the buffer to the the correct offset
            fileROBuf.position(entryOffset);
            
            hash = fileROBuf.getInt() & UINT32_MASK;
            
            long uint32 = fileROBuf.getInt() & UINT32_MASK;
            nextAddress = (uint32 != 0) ?  new CacheAddress(uint32, selfAddress.getCachePath()) : null;  
           
            uint32 = fileROBuf.getInt() & UINT32_MASK;
            rankingsNodeAddress = (uint32 != 0) ?  new CacheAddress(uint32, selfAddress.getCachePath()) : null;  
            
            reuseCount = fileROBuf.getInt();
            refetchCount = fileROBuf.getInt();
            
            state = EntryStateEnum.values()[fileROBuf.getInt()];
            creationTime = (fileROBuf.getLong() / 1000000) - Long.valueOf("11644473600");
            
            keyLen = fileROBuf.getInt();
            
            uint32 = fileROBuf.getInt() & UINT32_MASK;
            longKeyAddresses = (uint32 != 0) ?  new CacheAddress(uint32, selfAddress.getCachePath()) : null;  
            
            dataList = null;
            dataSizes= new int[4];
            for (int i = 0; i < 4; i++)  {
                dataSizes[i] = fileROBuf.getInt();
            }
            dataAddresses = new CacheAddress[4];
            for (int i = 0; i < 4; i++)  {
                dataAddresses[i] =  new CacheAddress(fileROBuf.getInt() & UINT32_MASK, selfAddress.getCachePath());
            }
        
            flags = fileROBuf.getInt() & UINT32_MASK;
            // skip over pad 
            for (int i = 0; i < 4; i++)  {
                fileROBuf.getInt();
            }
            
            // skip over self hash
            fileROBuf.getInt();
        
            // get the key
            if (longKeyAddresses != null) {
                // Key is stored outside of the entry
                try {
                    CacheData data = new CacheData(longKeyAddresses, this.keyLen, true);
                    key = data.getDataString();
                } catch (TskCoreException | IngestModuleException ex) {
                    logger.log(Level.WARNING, String.format("Failed to get external key from address %s", longKeyAddresses)); //NON-NLS 
                } 
            }
            else {  // key stored within entry 
                StringBuilder strBuilder = new StringBuilder(MAX_KEY_LEN);
                int keyLen = 0;
                while (fileROBuf.remaining() > 0  && keyLen < MAX_KEY_LEN)  {
                    char keyChar = (char)fileROBuf.get();
                    if (keyChar == '\0') { 
                        break;
                    }
                    strBuilder.append(keyChar);
                    keyLen++;
                }

                key = strBuilder.toString();
            }
        }

        public CacheAddress getAddress() {
            return selfAddress;
        }

        public long getHash() {
            return hash;
        }

        public CacheAddress getNextAddress() {
            return nextAddress;
        }

        public int getReuseCount() {
            return reuseCount;
        }

        public int getRefetchCount() {
            return refetchCount;
        }

        public EntryStateEnum getState() {
            return state;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public long getFlags() {
            return flags;
        }

        public String getKey() {
            return key;
        }
        
        /**
         * Returns the data segments in the cache entry.
         * 
         * @return list of data segments in the entry.
         * 
         * @throws TskCoreException
         * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
         */
        public List<CacheData> getData() throws TskCoreException, IngestModuleException {
            
            if (dataList == null) { 
                dataList = new ArrayList<>();
                 for (int i = 0; i < 4; i++)  {
                     if (dataSizes[i] > 0) {
                         CacheData cacheData = new CacheData(dataAddresses[i], dataSizes[i], true );

                         cacheData.extract();
                         dataList.add(cacheData);
                     }
                }
            }
            return dataList; 
        }
        
        /**
         * Returns if the Entry has HTTP headers.
         * 
         * If present, the HTTP headers are in the first data segment
         * 
         * @return true if the entry has HTTP headers
         */
        boolean hasHTTPHeaders() {
            if ((dataList == null) || dataList.isEmpty()) {
                return false;
            }
            return dataList.get(0).hasHTTPHeaders();
        }
        
        /**
         * Returns the specified http header , if present
         * 
         * @param key name of header to return
         * @return header value, null if not found
         */
        String getHTTPHeader(String key) {
            if ((dataList == null) || dataList.isEmpty()) {
                return null;
            }
            // First data segment has the HTTP headers, if any
            return dataList.get(0).getHTTPHeader(key);
        }
        
        /**
         * Returns the all the HTTP headers as a single string
         * 
         * @return header value, null if not found
         */
        String getHTTPHeaders() {
            if ((dataList == null) || dataList.isEmpty()) {
                return null;
            }
            // First data segment has the HTTP headers, if any
            return dataList.get(0).getHTTPHeaders();
        }
        
        /**
         * Returns if the entry is compressed with Brotli
         * 
         * An entry is considered to be Brotli compressed if it has a 
         * HTTP header "content-encoding: br"
         * 
         * @return true if the entry id compressed with Brotli, false otherwise.
         */
        boolean isBrotliCompressed() {
            
            if (hasHTTPHeaders() ) {
                String encodingHeader = getHTTPHeader("content-encoding");
                if (encodingHeader!= null) {
                    return encodingHeader.trim().equalsIgnoreCase("br");
                }
            } 
            
            return false;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Entry = Hash: %08x,  State: %s, ReuseCount: %d, RefetchCount: %d", 
                                    this.hash, this.state.toString(), this.reuseCount, this.refetchCount ))
                .append(String.format("\n\tKey: %s, Keylen: %d", 
                                    this.key, this.keyLen, this.reuseCount, this.refetchCount ))
                .append(String.format("\n\tCreationTime: %s", 
                                    TimeUtilities.epochToTime(this.creationTime) ))
                .append(String.format("\n\tNext Address: %s", 
                                    (nextAddress != null) ? nextAddress.toString() : "None"));
            
            for (int i = 0; i < 4; i++) {
                if (dataSizes[i] > 0) {
                    sb.append(String.format("\n\tData %d: cache address = %s, Data = %s", 
                                         i, dataAddresses[i].toString(), 
                                         (dataList != null)
                                                 ? dataList.get(i).toString() 
                                                 : "Data not retrived yet."));
                }
            }
            
            return sb.toString(); 
        }
    }
}
