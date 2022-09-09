/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
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
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.OsAccount;
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
 * 
 * CACHE BASICS (https://www.chromium.org/developers/design-documents/network-stack/disk-cache)
 * - A cached item is broken up into segments (header, payload, etc.). The segments are not stored together.
 * - Each user has a cache folder in AppData\Local\Google\Chrome\User Data\Default\Cache
 * - Each folder has three kinds of files
 * -- index: This is the main file.  It has one entry for every cached item. You can start from here and work you way to the various data_x and f_XXX files that contain segments. 
 * -- data_X: These files are containers for small segments and other supporting data (such as the cache entry)
 * -- f_XXXX: If the cached data cannot fit into a slot in data_X, it will be saved to its
 *    own f_XXXX file. These could be compressed if the data being sent was compressed.
 *    These are referred to as "External Files" in the below code.
 * - A CacheAddress embeds information about which file something is stored in.  This address is used in several structures to make it easy to abstract out where data is stored.
 * - General Flow: index file -> process Cache Entry in data_X file -> process segment in data_X or f_XXX. 
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
    private final Map<String, FileWrapper> fileCopyCache = new HashMap<>();
    
    // A file table to cache the f_* files.
    private final Map<String, AbstractFile> externalFilesTable = new HashMap<>();
    
    /**
     * Allows methods to use data in an AbstractFile in a variety of
     * ways. As a ByteBuffer, AbstractFile, etc.  A local copy of the file
     * backs the ByteBuffer. 
     */
    final class FileWrapper {       
        private final AbstractFile abstractFile;
        private final RandomAccessFile fileCopy;
        private final ByteBuffer byteBuffer;

        FileWrapper (AbstractFile abstractFile, RandomAccessFile fileCopy, ByteBuffer buffer ) {
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
        "# {0} - module name",
        "# {1} - row number",
        "# {2} - table length",
        "# {3} - cache path",
        "ChromeCacheExtractor.progressMsg={0}: Extracting cache entry {1} of {2} entries from {3}"
    })
    ChromeCacheExtractor(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) { 
        moduleName = NbBundle.getMessage(Chromium.class, "Chrome.moduleName");
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
             
        } catch (NoCurrentCaseException ex) {
            String msg = "Failed to get current case."; //NON-NLS
            throw new IngestModuleException(msg, ex);
        } 
    }
    
    /**
     * Resets the internal caches and temp folders in between processing each user cache folder
     * 
     * @param cachePath - path (in data source) of the cache being processed 
     * 
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    private void resetForNewCacheFolder(String cachePath) throws IngestModuleException {
        
        fileCopyCache.clear();
        externalFilesTable.clear();
        
        String cacheAbsOutputFolderName = this.getAbsOutputFolderName() + cachePath;
        File outDir = new File(cacheAbsOutputFolderName);
        if (outDir.exists() == false) {
            outDir.mkdirs();
        }
        
        String cacheTempPath = RAImageIngestModule.getRATempPath(currentCase, moduleName, context.getJobId()) + cachePath;
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
        
        for (Entry<String, FileWrapper> entry : this.fileCopyCache.entrySet()) {
            Path tempFilePath = Paths.get(RAImageIngestModule.getRATempPath(currentCase, moduleName, context.getJobId()), entry.getKey() ); 
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
    void processCaches() {
        
         try {
           moduleInit();
        } catch (IngestModuleException ex) {
            String msg = "Failed to initialize ChromeCacheExtractor."; //NON-NLS
            logger.log(Level.SEVERE, msg, ex);
            return;
        }
        
         // Find and process the cache folders.  There could be one per user
        try {
            // Identify each cache folder by searching for the index files in each
            List<AbstractFile> indexFiles = findIndexFiles(); 
            
            if (indexFiles.size() > 0) {
                // Create an output folder to save any derived files
                absOutputFolderName = RAImageIngestModule.getRAOutputPath(currentCase, moduleName, context.getJobId());
                relOutputFolderName = Paths.get(RAImageIngestModule.getRelModuleOutputPath(currentCase, moduleName, context.getJobId())).normalize().toString();
            
                File dir = new File(absOutputFolderName);
                if (dir.exists() == false) {
                    dir.mkdirs();
                }
            }

            // Process each of the cache folders
            for (AbstractFile indexFile: indexFiles) {  
                
                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }
                
                if (indexFile.getSize() > 0) {
                    processCacheFolder(indexFile);
                }
            }
        
        } catch (TskCoreException ex) {
                String msg = "Failed to find cache index files"; //NON-NLS
                logger.log(Level.WARNING, msg, ex);
        } 
    }
    
    @Messages({
        "ChromeCacheExtract_adding_extracted_files_msg=Chrome Cache: Adding %d extracted files for analysis.",
        "ChromeCacheExtract_adding_artifacts_msg=Chrome Cache: Adding %d artifacts for analysis.",
        "ChromeCacheExtract_loading_files_msg=Chrome Cache: Loading files from %s."
    })
    
    /**
     * Processes a user's cache and creates corresponding artifacts and derived files. 
     * Will ultimately process the f_XXXX and data_X files in the folder.
     * 
     * @param indexFile Index file that is located in a user's cache folder
     */
    private void processCacheFolder(AbstractFile indexFile) {
        
        String cacheFolderName = indexFile.getParentPath();
        Optional<FileWrapper> indexFileWrapper;
        
        /*
         * The first part of this method is all about finding the needed files in the cache
         * folder and making internal copies/caches of them so that we can later process them
         * and effeciently look them up. 
        */
        try {
            progressBar.progress(String.format(Bundle.ChromeCacheExtract_loading_files_msg(), cacheFolderName));
            resetForNewCacheFolder(cacheFolderName);
             
            // @@@ This is little ineffecient because we later in this call search for the AbstractFile that we currently have
            // Load the index file into the caches
            indexFileWrapper = findDataOrIndexFile(indexFile.getName(), cacheFolderName);
            if (!indexFileWrapper.isPresent()) {
                String msg = String.format("Failed to find copy cache index file %s", indexFile.getUniquePath());
                logger.log(Level.WARNING, msg);
                return;
            }

            
            // load the data files into the internal cache.  We do this because we often
            // jump in between the various data_X files resolving segments
            for (int i = 0; i < 4; i ++)  {
                Optional<FileWrapper> dataFile = findDataOrIndexFile(String.format("data_%1d",i), cacheFolderName );
                if (!dataFile.isPresent()) {
                    return;
                }
            }
            
            // find all f_* files in a single query and load them into the cache
            // we do this here so that it is a single query instead of hundreds of individual ones
            findExternalFiles(cacheFolderName);

        } catch (TskCoreException | IngestModuleException ex) {
            String msg = "Failed to find cache files in path " + cacheFolderName; //NON-NLS
            logger.log(Level.WARNING, msg, ex);
            return;
        } 

        /*
         * Now the analysis begins.  We parse the index file and that drives parsing entries
         * from data_X or f_XXXX files. 
        */
        logger.log(Level.INFO, "{0}- Now reading Cache index file from path {1}", new Object[]{moduleName, cacheFolderName }); //NON-NLS

        List<AbstractFile> derivedFiles = new ArrayList<>();
        Collection<BlackboardArtifact> artifactsAdded = new ArrayList<>();
        
        ByteBuffer indexFileROBuffer = indexFileWrapper.get().getByteBuffer();
        IndexFileHeader indexHdr = new IndexFileHeader(indexFileROBuffer);

        // seek past the header
        indexFileROBuffer.position(INDEXFILE_HDR_SIZE);

        try {
            /* Cycle through index and get the CacheAddress for each CacheEntry.  Process each entry
             * to extract data, add artifacts, etc. from the f_XXXX and data_x files */
            for (int i = 0; i <  indexHdr.getTableLen(); i++) {

                if (context.dataSourceIngestIsCancelled()) {
                    cleanup();
                    return;
                }

                CacheAddress addr = new CacheAddress(indexFileROBuffer.getInt() & UINT32_MASK, cacheFolderName);
                if (addr.isInitialized()) {
                    progressBar.progress(NbBundle.getMessage(this.getClass(),
                                            "ChromeCacheExtractor.progressMsg",
                                            moduleName, i, indexHdr.getTableLen(), cacheFolderName)  );
                    try {
                        List<DerivedFile> addedFiles = processCacheEntry(addr, artifactsAdded);
                        derivedFiles.addAll(addedFiles);
                    }
                    catch (TskCoreException | IngestModuleException ex) {
                       logger.log(Level.WARNING, String.format("Failed to get cache entry at address %s for file with object ID %d (%s)", addr, indexFile.getId(), ex.getLocalizedMessage())); //NON-NLS
                    } 
                }  
            }
        } catch (java.nio.BufferUnderflowException ex) {
            logger.log(Level.WARNING, String.format("Ran out of data unexpectedly reading file %s (ObjID: %d)", indexFile.getName(), indexFile.getId()));
        }
        
        if (context.dataSourceIngestIsCancelled()) {
            cleanup();
            return;
        }
        

        // notify listeners of new files and schedule for analysis
        progressBar.progress(String.format(Bundle.ChromeCacheExtract_adding_extracted_files_msg(), derivedFiles.size()));
        derivedFiles.forEach((derived) -> {
            services.fireModuleContentEvent(new ModuleContentEvent(derived));
         });
        context.addFilesToJob(derivedFiles);

        // notify listeners about new artifacts
        progressBar.progress(String.format(Bundle.ChromeCacheExtract_adding_artifacts_msg(), artifactsAdded.size()));
        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();
        try {
            blackboard.postArtifacts(artifactsAdded, moduleName, context.getJobId());
        } catch (Blackboard.BlackboardException ex) {
           logger.log(Level.WARNING, String.format("Failed to post cacheIndex artifacts "), ex); //NON-NLS
        }
       
        cleanup();
    }
    
    /**
     * Processes the cache entry that is stored at the given address.   A CacheEntry is 
     * located in a data_X file and stores information about where the various segments
     * for a given cached entry are located. 
     * 
     * Extracts the files if needed and adds as derived files, creates artifacts
     * 
     * @param cacheAddress Address where CacheEntry is located (from index file)
     * @param artifactsAdded any  artifact that was added
     * 
     * @return Optional derived file, is a derived file is added for the given entry
     */
    private List<DerivedFile> processCacheEntry(CacheAddress cacheAddress, Collection<BlackboardArtifact> artifactsAdded ) throws TskCoreException, IngestModuleException {
         
        List<DerivedFile> derivedFiles = new ArrayList<>();
        
        // get the path to the corresponding data_X file for the cache entry
        String cacheEntryFileName = cacheAddress.getFilename(); 
        String cachePath = cacheAddress.getCachePath();
           
        Optional<FileWrapper> cacheEntryFileOptional = findDataOrIndexFile(cacheEntryFileName, cachePath);
        if (!cacheEntryFileOptional.isPresent()) {
            String msg = String.format("Failed to find data file %s", cacheEntryFileName); //NON-NLS
            throw new IngestModuleException(msg);
        }

        // Load the entry to get its metadata, segments, etc. 
        CacheEntry cacheEntry = new CacheEntry(cacheAddress, cacheEntryFileOptional.get() );
        List<CacheDataSegment> dataSegments = cacheEntry.getDataSegments();
        
        // Only process the first payload data segment in each entry
        //  first data segement has the HTTP headers, 2nd is the payload
        if (dataSegments.size() < 2) {
            return derivedFiles;
        }
        CacheDataSegment dataSegment = dataSegments.get(1);

        // Name where segment is located (could be diffrent from where entry was located) 
        String segmentFileName = dataSegment.getCacheAddress().getFilename();
        Optional<AbstractFile> segmentFileAbstractFile = findAbstractFile(segmentFileName, cachePath);
        if (!segmentFileAbstractFile.isPresent()) {
            logger.log(Level.WARNING, "Error finding segment file: " + cachePath + "/" + segmentFileName); //NON-NLS
            return derivedFiles;
        }        
        
        boolean isBrotliCompressed = false;
        if (dataSegment.getType() != CacheDataTypeEnum.HTTP_HEADER && cacheEntry.isBrotliCompressed() ) {
            isBrotliCompressed = true;
        }


        // Make artifacts around the cached item and extract data from data_X file
        try {
            AbstractFile cachedItemFile; // 
            /* If the cached data is in a f_XXXX file, we only need to make artifacts. */
            if (dataSegment.isInExternalFile() )  {
                cachedItemFile = segmentFileAbstractFile.get();
            } 
            /* If the data is in a data_X file, we need to extract it out and then make the similar artifacts */
            else {

                // Data segments in "data_x" files are saved in individual files and added as derived files
                String filename = dataSegment.save();
                String relPathname = getRelOutputFolderName() + dataSegment.getCacheAddress().getCachePath() + filename; 
            
                // @@@ We should batch these up and do them in one big insert / transaction
                DerivedFile derivedFile = fileManager.addDerivedFile(filename, relPathname,
                                                    dataSegment.getDataLength(), 
                                                    cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), // TBD 
                                                    true, 
                                                    segmentFileAbstractFile.get(), 
                                                    "",
                                                    moduleName, 
                                                    VERSION_NUMBER, 
                                                    "", 
                                                    TskData.EncodingType.NONE);

                derivedFiles.add(derivedFile);
                cachedItemFile = derivedFile;
            }
                
            addArtifacts(cacheEntry, cacheEntryFileOptional.get().getAbstractFile(), cachedItemFile, artifactsAdded);

            // Tika doesn't detect these types.  So, make sure they have the correct MIME type */
            if (isBrotliCompressed) {
                cachedItemFile.setMIMEType(BROTLI_MIMETYPE);
                cachedItemFile.save();
            }
        
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Error while trying to add an artifact", ex); //NON-NLS
        }
        
        return derivedFiles;
     }
    
    /**
     * Add artifacts for a given cached item
     * 
     * @param cacheEntry Entry item came from
     * @param cacheEntryFile File that stored the cache entry
     * @param cachedItemFile File that stores the cached data (Either a derived file or f_XXXX file)
     * @param artifactsAdded List of artifacts that were added by this call
     * @throws TskCoreException 
     */
    private void addArtifacts(CacheEntry cacheEntry, AbstractFile cacheEntryFile, AbstractFile cachedItemFile, Collection<BlackboardArtifact> artifactsAdded) throws TskCoreException {
  
        // Create a TSK_WEB_CACHE entry with the parent as data_X file that had the cache entry
        Collection<BlackboardAttribute> webAttr = new ArrayList<>();
        String url = cacheEntry.getKey() != null ? cacheEntry.getKey() : "";
        webAttr.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                moduleName, url));
        webAttr.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                moduleName, NetworkUtils.extractDomain(url)));
        webAttr.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED,
                moduleName, cacheEntry.getCreationTime()));
        webAttr.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HEADERS,
                moduleName, cacheEntry.getHTTPHeaders()));  
        webAttr.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH,
                moduleName, cachedItemFile.getUniquePath()));
        webAttr.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID,
                moduleName, cachedItemFile.getId()));

        BlackboardArtifact webCacheArtifact = cacheEntryFile.newDataArtifact(new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_CACHE), webAttr);
        artifactsAdded.add(webCacheArtifact);

        // Create a TSK_ASSOCIATED_OBJECT on the f_XXX or derived file file back to the CACHE entry
        BlackboardArtifact associatedObjectArtifact = cachedItemFile.newDataArtifact(
                new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT), 
                Arrays.asList(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, 
                        moduleName, webCacheArtifact.getArtifactID())));
        
        artifactsAdded.add(associatedObjectArtifact);
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
            String cacheKey = cachePath + abstractFile.getName();
            if (cachePath.equals(abstractFile.getParentPath()) && abstractFile.isFile()) {
                // Don't overwrite an allocated version with an unallocated version
                if (abstractFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)
                        || !externalFilesTable.containsKey(cacheKey)) {
                    this.externalFilesTable.put(cacheKey, abstractFile);
                }
            }
        }
    }
    /**
     * Finds a file with a given name in a given cache folder
     * First checks in the file tables.
     * 
     * @param cacheFileName
     * @return Optional abstract file 
     * @throws TskCoreException 
     */
    private Optional<AbstractFile> findAbstractFile(String cacheFileName, String cacheFolderName) throws TskCoreException {
       
        // see if it is cached
        String fileTableKey = cacheFolderName + cacheFileName;

        if (cacheFileName != null) {
            if (cacheFileName.startsWith("f_") && externalFilesTable.containsKey(fileTableKey)) {
                return Optional.of(externalFilesTable.get(fileTableKey));
            }
        } else {
            return Optional.empty();
        }
        
        if (fileCopyCache.containsKey(fileTableKey)) {
            return Optional.of(fileCopyCache.get(fileTableKey).getAbstractFile());
        }

        List<AbstractFile> cacheFiles = currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource, 
                cacheFileName, cacheFolderName);
        if (!cacheFiles.isEmpty()) {
            // Sort the list for consistency. Preference is:
            // - In correct subfolder and allocated
            // - In correct subfolder and unallocated
            // - In incorrect subfolder and allocated
            Collections.sort(cacheFiles, new Comparator<AbstractFile>() {
                @Override
                public int compare(AbstractFile file1, AbstractFile file2) {
                    try {
                        if (file1.getUniquePath().trim().endsWith(DEFAULT_CACHE_PATH_STR)
                                && ! file2.getUniquePath().trim().endsWith(DEFAULT_CACHE_PATH_STR)) {
                            return -1;
                        }
                        
                        if (file2.getUniquePath().trim().endsWith(DEFAULT_CACHE_PATH_STR)
                                && ! file1.getUniquePath().trim().endsWith(DEFAULT_CACHE_PATH_STR)) {
                            return 1;
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Error getting unique path for file with ID " + file1.getId() + " or " + file2.getId(), ex);
                    }
                        
                    if (file1.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)
                            && ! file2.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                        return -1;
                    }
                    if (file2.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)
                            && ! file1.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                        return 1;
                    }

                    return Long.compare(file1.getId(), file2.getId());
                }
            });
            
            // The best match will be the first element
            return Optional.of(cacheFiles.get(0));
        }
        
        return Optional.empty(); 
    }
   
     /**
     * Finds the "index" file that exists in each user's cache.  This is used to 
     * enumerate all of the caches on the system. 
     * 
     * @return list of index files in Chrome cache folders
     * @throws TskCoreException 
     */
    private List<AbstractFile> findIndexFiles() throws TskCoreException {
        return fileManager.findFiles(dataSource, "index", DEFAULT_CACHE_PATH_STR); //NON-NLS 
    }
    
    

    /**
     * Finds the specified data or index cache file under the specified path.
     * The FileWrapper is easier to parse than a raw AbstractFile. 
     * Will save the file to an internal cache. For the f_XXXX files, use
     * findAbstractFile().
     * 
     * @param cacheFileName Name file file
     * @param cacheFolderName Name of user's cache folder
     * @return Cache file copy
     * @throws TskCoreException 
     */ 
    private Optional<FileWrapper> findDataOrIndexFile(String cacheFileName, String cacheFolderName) throws TskCoreException, IngestModuleException  {
        
        // Check if the file is already in the cache
        String fileTableKey = cacheFolderName + cacheFileName;
        if (fileCopyCache.containsKey(fileTableKey)) {
            return Optional.of(fileCopyCache.get(fileTableKey));
        }
        
        // Use Autopsy to get the AbstractFile
        Optional<AbstractFile> abstractFileOptional = findAbstractFile(cacheFileName, cacheFolderName);
        if (!abstractFileOptional.isPresent()) {
            return Optional.empty(); 
        }
                
        // Wrap the file so that we can get the ByteBuffer later.
        // @@@ BC: I think this should nearly all go into FileWrapper and be done lazily and perhaps based on size. 
        //     Many of the files are small enough to keep in memory for the ByteBuffer
        
        // write the file to disk so that we can have a memory-mapped ByteBuffer
        AbstractFile cacheFile = abstractFileOptional.get();
        RandomAccessFile randomAccessFile = null;
        String tempFilePathname = RAImageIngestModule.getRATempPath(currentCase, moduleName, context.getJobId()) + cacheFolderName + cacheFile.getName(); //NON-NLS
        try {
            File newFile = new File(tempFilePathname);
            ContentUtils.writeToFile(cacheFile, newFile, context::dataSourceIngestIsCancelled);
            
            randomAccessFile = new RandomAccessFile(tempFilePathname, "r");
            FileChannel roChannel = randomAccessFile.getChannel();
            ByteBuffer cacheFileROBuf = roChannel.map(FileChannel.MapMode.READ_ONLY, 0,
                                                        (int) roChannel.size());

            cacheFileROBuf.order(ByteOrder.nativeOrder());
            FileWrapper cacheFileWrapper = new FileWrapper(cacheFile, randomAccessFile, cacheFileROBuf );
            
            if (!cacheFileName.startsWith("f_")) {
                fileCopyCache.put(cacheFolderName + cacheFileName, cacheFileWrapper);
            }
            
            return Optional.of(cacheFileWrapper);
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
            indexFileROBuf.position(indexFileROBuf.position()+4); // stats cache cacheAddress
            
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
     * Cache file type enum - as encoded the cacheAddress
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
     * Google defines the notion of a CacheAddress that spans the various
     * files in the cache.  The 32-bit number embeds which file and offset
     * the address is in. 
 
     * The below defines what each bit means.  A 1 means the bit is used
     * for that value. 
     *
     * Header:
     * 1000 0000 0000 0000 0000 0000 0000 0000 : initialized bit
     * 0111 0000 0000 0000 0000 0000 0000 0000 : file type
     * 
     * If external file: (i.e. f_XXXX)
     * 0000 1111 1111 1111 1111 1111 1111 1111 : file#  0 - 268,435,456 (2^28)
     * 
     * If block file: (i.e. data_X)
     * 0000 1100 0000 0000 0000 0000 0000 0000 : reserved bits
     * 0000 0011 0000 0000 0000 0000 0000 0000 : number of contiguous blocks 1-4
     * 0000 0000 1111 1111 0000 0000 0000 0000 : file selector 0 - 255
     * 0000 0000 0000 0000 1111 1111 1111 1111 : block#  0 - 65,535 (2^16)
     * 
     */
    final class CacheAddress {
        // sundry constants to parse the bit fields 
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
        
        
        /**
         * 
         * @param uint32 Encoded address
         * @param cachePath Folder that index file was located in
         */
        CacheAddress(long uint32, String cachePath) {
            
            uint32CacheAddr = uint32;
            this.cachePath = cachePath;
            
            
            // analyze the 
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
    final class CacheDataSegment {
        
        private int length;
        private final CacheAddress cacheAddress;
        private CacheDataTypeEnum type;
       
        private boolean isHTTPHeaderHint;
         
        private FileWrapper cacheFileCopy = null;
        private byte[] data = null;
        
        private String httpResponse;
        private final Map<String, String> httpHeaders = new HashMap<>();
                
        CacheDataSegment(CacheAddress cacheAddress, int len) {
            this(cacheAddress, len, false);
        }
        
        CacheDataSegment(CacheAddress cacheAddress, int len, boolean isHTTPHeader ) {
            this.type = CacheDataTypeEnum.UNKNOWN;
            this.length = len;
            this.cacheAddress = cacheAddress;
            this.isHTTPHeaderHint = isHTTPHeader;
        }
        
        boolean isInExternalFile() {
            return cacheAddress.isInExternalFile();
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
            if (!cacheAddress.isInExternalFile()) {
                
                if (cacheAddress.getFilename() == null) {
                    throw new TskCoreException("Cache address has no file name");
                }
                
                cacheFileCopy = findDataOrIndexFile(cacheAddress.getFilename(), cacheAddress.getCachePath()).get();

                this.data = new byte [length];
                ByteBuffer buf = cacheFileCopy.getByteBuffer();
                int dataOffset = DATAFILE_HDR_SIZE + cacheAddress.getStartBlock() * cacheAddress.getBlockSize();
                if (dataOffset > buf.capacity()) {
                    return;
                }
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

        CacheAddress getCacheAddress() {
            return cacheAddress;
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
            
            if (cacheAddress.isInExternalFile()) {
                fileName = cacheAddress.getFilename();
            } else {
                fileName = String.format("%s__%08x", cacheAddress.getFilename(), cacheAddress.getUint32CacheAddr());
            }
            
            String filePathName = getAbsOutputFolderName() + cacheAddress.getCachePath() + fileName;
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
//	  CacheAddr   long_key;           // Optional cacheAddress of a long key.
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
        private final FileWrapper cacheFileCopy;
        
        private final long hash;
        private final CacheAddress nextAddress;
        private final CacheAddress rankingsNodeAddress;
        
        private final int reuseCount;
        private final int refetchCount;
        private final EntryStateEnum state;
        
        private final long creationTime;
        private final int keyLen;
        
        private final CacheAddress longKeyAddresses; // cacheAddress of the key, if the key is external to the entry
        
        private final int[] dataSegmentSizes;
        private final CacheAddress[] dataSegmentIndexFileEntries;
        private List<CacheDataSegment> dataSegments;
                
        private final long flags;
       
        private String key;     // Key may be found within the entry or may be external
        
        CacheEntry(CacheAddress cacheAdress, FileWrapper cacheFileCopy ) throws TskCoreException, IngestModuleException {
            this.selfAddress = cacheAdress;
            this.cacheFileCopy = cacheFileCopy;
            
            ByteBuffer fileROBuf = cacheFileCopy.getByteBuffer();
            
            int entryOffset = DATAFILE_HDR_SIZE + cacheAdress.getStartBlock() * cacheAdress.getBlockSize();
            
            // reposition the buffer to the the correct offset
            if (entryOffset < fileROBuf.capacity()) {
                fileROBuf.position(entryOffset);
            } else {
                throw new IngestModuleException("Position seeked in Buffer to big"); // NON-NLS
            }
            
            hash = fileROBuf.getInt() & UINT32_MASK;
            
            long uint32 = fileROBuf.getInt() & UINT32_MASK;
            nextAddress = (uint32 != 0) ?  new CacheAddress(uint32, selfAddress.getCachePath()) : null;  
           
            uint32 = fileROBuf.getInt() & UINT32_MASK;
            rankingsNodeAddress = (uint32 != 0) ?  new CacheAddress(uint32, selfAddress.getCachePath()) : null;  
            
            reuseCount = fileROBuf.getInt();
            refetchCount = fileROBuf.getInt();
            
            int stateVal = fileROBuf.getInt();
            if ((stateVal >= 0) && (stateVal < EntryStateEnum.values().length)) {
                state = EntryStateEnum.values()[stateVal];
            } else {
                throw new TskCoreException("Invalid EntryStateEnum value"); // NON-NLS
            }
            creationTime = (fileROBuf.getLong() / 1000000) - Long.valueOf("11644473600");
            
            keyLen = fileROBuf.getInt();
            
            uint32 = fileROBuf.getInt() & UINT32_MASK;
            longKeyAddresses = (uint32 != 0) ?  new CacheAddress(uint32, selfAddress.getCachePath()) : null;  
            
            dataSegments = null;
            dataSegmentSizes= new int[4];
            for (int i = 0; i < 4; i++)  {
                dataSegmentSizes[i] = fileROBuf.getInt();
            }
            dataSegmentIndexFileEntries = new CacheAddress[4];
            for (int i = 0; i < 4; i++)  {
                dataSegmentIndexFileEntries[i] =  new CacheAddress(fileROBuf.getInt() & UINT32_MASK, selfAddress.getCachePath());
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
                    if (longKeyAddresses.getFilename() != null) {
                        CacheDataSegment data = new CacheDataSegment(longKeyAddresses, this.keyLen, true);
                        key = data.getDataString();
                    }
                } catch (TskCoreException | IngestModuleException ex) {
                    throw new TskCoreException(String.format("Failed to get external key from address %s", longKeyAddresses)); //NON-NLS 
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

        public CacheAddress getCacheAddress() {
            return selfAddress;
        }

        public long getHash() {
            return hash;
        }

        public CacheAddress getNextCacheAddress() {
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
        public List<CacheDataSegment> getDataSegments() throws TskCoreException, IngestModuleException {
            
            if (dataSegments == null) { 
                dataSegments = new ArrayList<>();
                 for (int i = 0; i < 4; i++)  {
                     if (dataSegmentSizes[i] > 0) {
                         CacheDataSegment cacheData = new CacheDataSegment(dataSegmentIndexFileEntries[i], dataSegmentSizes[i], true );

                         cacheData.extract();
                         dataSegments.add(cacheData);
                     }
                }
            }
            return dataSegments; 
        }
        
        /**
         * Returns if the Entry has HTTP headers.
         * 
         * If present, the HTTP headers are in the first data segment
         * 
         * @return true if the entry has HTTP headers
         */
        boolean hasHTTPHeaders() {
            if ((dataSegments == null) || dataSegments.isEmpty()) {
                return false;
            }
            return dataSegments.get(0).hasHTTPHeaders();
        }
        
        /**
         * Returns the specified http header , if present
         * 
         * @param key name of header to return
         * @return header value, null if not found
         */
        String getHTTPHeader(String key) {
            if ((dataSegments == null) || dataSegments.isEmpty()) {
                return null;
            }
            // First data segment has the HTTP headers, if any
            return dataSegments.get(0).getHTTPHeader(key);
        }
        
        /**
         * Returns the all the HTTP headers as a single string
         * 
         * @return header value, null if not found
         */
        String getHTTPHeaders() {
            if ((dataSegments == null) || dataSegments.isEmpty()) {
                return null;
            }
            // First data segment has the HTTP headers, if any
            return dataSegments.get(0).getHTTPHeaders();
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
                if (dataSegmentSizes[i] > 0) {
                    sb.append(String.format("\n\tData %d: cache address = %s, Data = %s", 
                                         i, dataSegmentIndexFileEntries[i].toString(), 
                                         (dataSegments != null)
                                                 ? dataSegments.get(i).toString() 
                                                 : "Data not retrived yet."));
                }
            }
            
            return sb.toString(); 
        }
    }
}
