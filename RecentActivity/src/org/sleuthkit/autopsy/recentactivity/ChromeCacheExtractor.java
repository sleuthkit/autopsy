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
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * Extracts and parses Chrome Cache files.
 */
final class ChromeCacheExtractor {
    
    private final static long UINT32_MASK = 0xFFFFFFFFl;
    
    private final static int INDEXFILE_HDR_SIZE = 92*4;
    private final static int DATAFILE_HDR_SIZE = 8192;
    
    private final static Logger logger = Logger.getLogger(ChromeCacheExtractor.class.getName());
    
    private static final String VERSION_NUMBER = "1.0.0";
    private final String moduleName;
    
    private String absOutputFolderName;
    private String relOutputFolderName;
     
    private final Content dataSource;
    private final IngestJobContext context;
    private final IngestServices services = IngestServices.getInstance();
    private Case currentCase;
    private FileManager fileManager;
 
    
    private final Map<String, CacheFileCopy> filesTable = new HashMap<>();
    
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

    ChromeCacheExtractor(Content dataSource, IngestJobContext context ) {
        moduleName = NbBundle.getMessage(ChromeCacheExtractor.class, "ChromeCacheExtractor.moduleName");           
        this.dataSource = dataSource;
        this.context = context;
    }
    
    
    /**
     * Initializes Chrome cache extractor module
     * 
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    void init() throws IngestModuleException {
        
        try {
            currentCase = Case.getCurrentCaseThrows();
            fileManager = currentCase.getServices().getFileManager();
             
            // Create an output folder to save any derived files
            absOutputFolderName = RAImageIngestModule.getRAOutputPath(currentCase, moduleName);
            relOutputFolderName = RAImageIngestModule.getRelModuleOutputPath() + File.separator + moduleName;
            File dir = new File(absOutputFolderName);
            if (dir.exists() == false) {
                dir.mkdirs();
            }
        } catch (NoCurrentCaseException ex) {
            String msg = "Failed to get current case.";
            throw new IngestModuleException(msg, ex);
        } 
    }
    
    /**
     * Cleans up after the module is done
     * 
     */
    void cleanup () {
        
        for (Entry<String, CacheFileCopy> entry : this.filesTable.entrySet()) {
            String tempFilePathname = RAImageIngestModule.getRATempPath(currentCase, moduleName) + File.separator + entry.getKey(); 
            try {
                entry.getValue().getFileCopy().getChannel().close();
                entry.getValue().getFileCopy().close();
               
                File tmpFile = new File(tempFilePathname);
                if (!tmpFile.delete()) {
                    tmpFile.deleteOnExit();
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Failed to delete cache file copy %s", tempFilePathname), ex);
            }
        }
    }
    
    /**
     * Returns the location of output folder for this module
     * 
     * @return 
     */
    private String getAbsOutputFolderName() {
        return absOutputFolderName;
    }
    
     /**
     * Returns the relative location of output folder for this module
     * 
     * @return 
     */
    private String getRelOutputFolderName() {
        return relOutputFolderName;
    }
    
    /**
     * Extracts the data from Chrome cache
     */
    void getCache() {
        
         try {
           init();
        } catch (IngestModuleException ex) {
           
            // TBD: show the error to Autospy error console??
            String msg = "Failed to initialize ChromeCacheExtractor.";
            logger.log(Level.SEVERE, msg, ex);
            return;
        }
        
        Optional<CacheFileCopy> indexFile;
        try {
            // find the index file
            indexFile = findAndCopyCacheFile("index");
            if (!indexFile.isPresent()) {
                return;
            }
            
            for (int i = 0; i < 4; i ++)  {
                Optional<CacheFileCopy> dataFile = findAndCopyCacheFile(String.format("data_%1d",i));
                if (!dataFile.isPresent()) {
                    return;
                }
            }
            
        } catch (TskCoreException | IngestModuleException ex) {
            String msg = "Failed to find cache files";
            logger.log(Level.SEVERE, msg, ex);
            return;
        } 

        logger.log(Level.INFO, "{0}- Now reading Cache index file", new Object[]{moduleName}); //NON-NLS
            
        List<AbstractFile> derivedFiles = new ArrayList<>();
        
        ByteBuffer indexFileROBuffer = indexFile.get().getByteBuffer();
        IndexFileHeader indexHdr = new IndexFileHeader(indexFileROBuffer);
 
        // seek past the header
        indexFileROBuffer.position(INDEXFILE_HDR_SIZE);

        // Process each address in the table
        for (int i = 0; i <  indexHdr.getTableLen(); i++) {
            CacheAddress addr = new CacheAddress(indexFileROBuffer.getInt() & UINT32_MASK);
            
            if (addr.isInitialized()) {
                String fileName = addr.getFilename(); 
                try {
                    Optional<CacheFileCopy> cacheFileCopy = this.getCacheFileCopy(fileName);
                    if (!cacheFileCopy.isPresent()) {
                        logger.log(Level.SEVERE, String.format("Failed to get cache entry at address %s", addr)); //NON-NLS
                    }
                    
                    // Get the cache entry at this address
                    CacheEntry cacheEntry = new CacheEntry(addr, cacheFileCopy.get() );
                    
                    // Get the data segments - each entry can have up to 4 data segments
                    List<CacheData> dataEntries = cacheEntry.getData();
                    for (int j = 0; j < dataEntries.size(); j++) {
                        CacheData data = dataEntries.get(j);
                        
                        // Todo: extract the data if we are going to do something with it in the future
                        
                        //data.extract();
                        String dataFilename = data.getAddress().getFilename();
                        Optional<AbstractFile> dataFile = this.findCacheFile(dataFilename);
                        
                        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                                    bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                                        moduleName,
                                        ((cacheEntry.getKey() != null) ? cacheEntry.getKey() : ""))); //NON-NLS
                        
                        if (dataFile.isPresent()) {
                            if (data.isInExternalFile() )  {
                                try {
                                    BlackboardArtifact bbart = dataFile.get().newArtifact(ARTIFACT_TYPE.TSK_SOURCE_ARTIFACT);
                                    if (bbart != null) {
                                        bbart.addAttributes(bbattributes);
                                    }
                                } catch (TskException ex) {
                                    logger.log(Level.SEVERE, "Error while trying to add an artifact", ex); //NON-NLS
                                }
                            } else {
                            
                            // extract data segment and save it as derived file
                            data.extract();
                            
                            if (data.hasHTTPHeaders()) {
                                String encoding = data.getHTTPHeader("content-encoding");   
                                
                            }
                            
                            String filename = data.save();
                            String relPathname = getRelOutputFolderName() + File.separator + filename; 
                            
                            // TBD: check if data segment is compressed? With Brotli?
                            DerivedFile derivedFile = fileManager.addDerivedFile(filename, relPathname,
                                data.getDataLength(), 
                                cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), cacheEntry.getCreationTime(), // TBD 
                                true, 
                                dataFile.get(), 
                                "",
                                moduleName, 
                                VERSION_NUMBER, 
                                "", 
                                TskData.EncodingType.NONE);
                            
                                derivedFiles.add(derivedFile);
                                try {
                                    BlackboardArtifact bbart = derivedFile.newArtifact(ARTIFACT_TYPE.TSK_SOURCE_ARTIFACT);
                                    if (bbart != null) {
                                        bbart.addAttributes(bbattributes);
                                    }
                                } catch (TskException ex) {
                                    logger.log(Level.SEVERE, "Error while trying to add an artifact", ex); //NON-NLS
                                }
                            }
                        }
                    }
            
                } catch (TskCoreException | IngestModuleException ex) {
                   logger.log(Level.SEVERE, String.format("Failed to get cache entry at address %s", addr)); //NON-NLS
                } 
            }  
        }
        
        if (derivedFiles.isEmpty() == false) {
            for (AbstractFile derived : derivedFiles) {
                services.fireModuleContentEvent(new ModuleContentEvent(derived));
            }
        }
         
        context.addFilesToJob(derivedFiles);
        services.fireModuleDataEvent(new ModuleDataEvent(moduleName, BlackboardArtifact.ARTIFACT_TYPE.TSK_SOURCE_ARTIFACT));
       
        cleanup();
    }
    
    /**
     * Finds abstract file for cache file with a specified name
     * 
     * @param cacheFileName
     * @return Opt
     * @throws TskCoreException 
     */
    Optional<AbstractFile> findCacheFile(String cacheFileName) throws TskCoreException {
        
        List<AbstractFile> cacheFiles = fileManager.findFiles(dataSource, cacheFileName, "default/cache"); //NON-NLS
        if (!cacheFiles.isEmpty()) {
            if (cacheFiles.size() > 1 ) {
                logger.log(Level.WARNING, String.format("Found multiple matches for filename = %s", cacheFileName));
            }
            return Optional.of(cacheFiles.get(0));
        }
        
        return Optional.empty(); 
    }
    
    /**
     * Returns CacheFileCopy for the specified file from the file table.
     * Find the file and creates a copy if it isnt already in the table.
     * 
     * @param cacheFileName
     * @return CacheFileCopy
     * @throws TskCoreException 
     */
    Optional<CacheFileCopy> getCacheFileCopy(String cacheFileName) throws TskCoreException, IngestModuleException {
        
        // Check if the file is already in the table
        if (filesTable.containsKey(cacheFileName)) {
            return Optional.of(filesTable.get(cacheFileName));
        }
        
        return findAndCopyCacheFile(cacheFileName);
    }
 
    /**
     * Finds the specified cache file and makes a temporary copy.
     * 
     * @param cacheFileName
     * @return Cache file copy
     * @throws TskCoreException 
     */ 
    Optional<CacheFileCopy> findAndCopyCacheFile(String cacheFileName) throws TskCoreException, IngestModuleException  {
        
        Optional<AbstractFile> cacheFileOptional = findCacheFile(cacheFileName);
        if (!cacheFileOptional.isPresent()) {
            return Optional.empty(); 
        }
        
        AbstractFile cacheFile = cacheFileOptional.get();
        String tempFilePathname = RAImageIngestModule.getRATempPath(currentCase, moduleName) + File.separator + cacheFile.getName(); //NON-NLS
        try {
            File newFile = new File(tempFilePathname);
            ContentUtils.writeToFile(cacheFile, newFile, context::dataSourceIngestIsCancelled);
            
            RandomAccessFile randomAccessFile = new RandomAccessFile(tempFilePathname, "r");
            FileChannel roChannel = randomAccessFile.getChannel();
            ByteBuffer cacheFileROBuf = roChannel.map(FileChannel.MapMode.READ_ONLY, 0,
                                                        (int) roChannel.size());

            cacheFileROBuf.order(ByteOrder.nativeOrder());
            CacheFileCopy cacheFileCopy = new CacheFileCopy(cacheFile, randomAccessFile, cacheFileROBuf );
            
            if (!cacheFileName.startsWith("f_")) {
                filesTable.put(cacheFileName, cacheFileCopy);
            }
            
            return Optional.of(cacheFileCopy);
        }
        catch (ReadContentInputStream.ReadContentInputStreamException ex) {
            String msg = String.format("Error reading Chrome cache file '%s' (id=%d).",
                    cacheFile.getName(), cacheFile.getId());
            throw new IngestModuleException(msg, ex);
        } catch (IOException ex) {
            String msg = String.format("Error writing temp Chrome cache file '%s' (id=%d).",
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
        
        
        CacheAddress(long uint32) {
            
            uint32CacheAddr = uint32;
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
        
        String getFilename() {
            return fileName;
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
        
        String getHTTPRespone() {
            return httpResponse;
        }
        
        /**
         * Extracts the data segment from the file 
         * 
         * @throws TskCoreException 
         */
        void extract() throws TskCoreException, IngestModuleException {

            // do nothing if already extracted, 
            if (data != null) {
                return;
            }
            
            cacheFileCopy = getCacheFileCopy(address.getFilename()).get();
            if (!address.isInExternalFile() ) {
                
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
                        
                            // hhtp headers are terminated by 0x00 0x00 
                            if (data[i+1] == 0) {
                                done = true;
                            }
                        
                            int len = (i - start);
                            String headerLine = new String(data, start, len, charset);
         
                            // first line is the http response
                            if (hdrNum == 1) { 
                                httpResponse = headerLine;
                            } else {
                                int nPos = headerLine.indexOf(":");
                                String key = headerLine.substring(0, nPos);
                                String val= headerLine.substring(nPos+1);
                
                                httpHeaders.put(key.toLowerCase(), val);
                            }
                            
                            i++;
                            hdrNum++;
                        }
                    }
                }
                
            } 
            //else {
                // Handle external f_* files
                
                // External files may or may not be compressed
                // They may be compresswed with GZIP, which our other ingest modules recognize and decpress
                // Alternatively thay may be compressed with Brotli, in that case we may want to decopmress them
                // content-encoding header in the data segment with HTTP header can tell us if data is compressed.
                
                // TBD: In future if we want to do anything with contents of file.
//                this.data = new byte [length];
//                
//                ByteBuffer buf = cacheFileCopy.getByteBuffer();
//                buf.position(0);
//                buf.get(data, 0, length);
//                
                
           //}
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
        
        
        String save() throws TskCoreException, IngestModuleException {
            String fileName;
            
            if (address.isInExternalFile()) {
                fileName = address.getFilename();
            } else {
                fileName = String.format("%s__%08x", address.getFilename(), address.getUint32CacheAddr());
            }
            
            String filePathName = getAbsOutputFolderName() + File.separator + fileName;
            save(filePathName);
            
            return  fileName;
        }
        
       
        void save(String filePathName) throws TskCoreException, IngestModuleException {
            
            // Save the data to specified file 
            if (data == null) {
                extract();
            }
            
            if (!this.isInExternalFile()) {
                
                // write the
                try (FileOutputStream stream = new FileOutputStream(filePathName)) {
                    stream.write(data);
                } catch (IOException ex) {
                    throw new TskCoreException(String.format("Failed to write output file %s", filePathName), ex);
                }
            }
//            else {
//                if (mimeType.toLowerCase().contains("gzip")) {
//                //if (mimeType.equalsIgnoreCase("application/gzip")) {
//                    try {
//                        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(data);
//                        GZIPInputStream in = new GZIPInputStream(byteInputStream);
//                        FileOutputStream out = new FileOutputStream(filePathName);
//                        byte[] buffer = new byte[2048];
//                        int len;
//                        while((len = in.read(buffer)) != -1){
//                            out.write(buffer, 0, len);
//                        }
//                        out.close();
//   
//                    } catch (IOException ex) {
//                        throw new TskCoreException(String.format("Failed to write output file %s", filePathName), ex);
//                    }  
//                }
//                else {
//                    // TBD: how to uncompress Brotli ??
//                     System.out.println("TBD Dont know how to uncompress Brotli yet" );
//                }
//            }
        }
        
        @Override
        public String toString() {
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(String.format("\t\tData type = : %s, Data Len = %d\n", 
                                    this.type.toString(), this.length ))
                    .append(String.format("\n\t\tData = : %s ", new String(data) ));
            
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
        
        private final long flags;
        private final int pad[] = new int[4];
        
        private final long selfHash;  // hash of the entry itself so far.
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
            nextAddress = (uint32 != 0) ?  new CacheAddress(uint32) : null;  
           
            uint32 = fileROBuf.getInt() & UINT32_MASK;
            rankingsNodeAddress = (uint32 != 0) ?  new CacheAddress(uint32) : null;  
            
            reuseCount = fileROBuf.getInt();
            refetchCount = fileROBuf.getInt();
            
            state = EntryStateEnum.values()[fileROBuf.getInt()];
            creationTime = (fileROBuf.getLong() / 1000000) - Long.valueOf("11644473600");
            
            keyLen = fileROBuf.getInt();
            
            uint32 = fileROBuf.getInt() & UINT32_MASK;
            longKeyAddresses = (uint32 != 0) ?  new CacheAddress(uint32) : null;  
            
            dataSizes= new int[4];
            for (int i = 0; i < 4; i++)  {
                dataSizes[i] = fileROBuf.getInt();
            }
            dataAddresses = new CacheAddress[4];
            for (int i = 0; i < 4; i++)  {
                dataAddresses[i] =  new CacheAddress(fileROBuf.getInt() & UINT32_MASK);
            }
        
            flags = fileROBuf.getInt() & UINT32_MASK;
            for (int i = 0; i < 4; i++)  {
                pad[i] = fileROBuf.getInt();
            }
            
            selfHash = fileROBuf.getInt() & UINT32_MASK;
        
            // get the key
            if (longKeyAddresses != null) {
                // Key is stored outside of the entry
                try {
                    CacheData data = new CacheData(longKeyAddresses, this.keyLen, true);
                    key = data.getDataString();
                } catch (TskCoreException | IngestModuleException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to get external key from address %s", longKeyAddresses)); //NON-NLS 
                } 
            }
            else {  // key stored within entry 
                StringBuilder strBuilder = new StringBuilder(MAX_KEY_LEN);
                int i = 0;
                while (fileROBuf.remaining() > 0  && i < MAX_KEY_LEN)  {
                    char c = (char)fileROBuf.get();
                    if (c == '\0') { 
                        break;
                    }
                    strBuilder.append(c);
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
        
        public List<CacheData> getData() {
            ArrayList<CacheData> list = new ArrayList<>();
             for (int i = 0; i < 4; i++)  {
                 if (dataSizes[i] > 0) {
                     CacheData cacheData = new CacheData(dataAddresses[i], dataSizes[i], true );
                     list.add(cacheData);
                 }
            }
            return list; 
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
                    sb.append(String.format("\n\tData %d: %8d bytes at cache address = %s", 
                                         i, dataSizes[i], dataAddresses[i] ));
                }
            }
            
            return sb.toString(); 
        }
    }
}
