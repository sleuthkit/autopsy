/**
 * @file FileSystemParser.cpp
 * @brief Filesystem parsing using The Sleuth Kit
 */

#include "FileSystemParser.h"
#include "FileTypeClassifier.h"
#include "RecoveryScore.h"

#include <tsk/libtsk.h>
#include <mutex>
#include <atomic>
#include <algorithm>
#include <cstring>

namespace RecoveryEngine {

// Context structure for TSK callbacks
struct TSKCallbackContext {
    FileSystemParser::Impl* parser;
    FileFoundCallback callback;
    const ScanOptions* options;
    std::atomic<bool>* cancelled;
    size_t fileCount;
};

class FileSystemParser::Impl {
public:
    explicit Impl(const std::string& volumePath)
        : m_volumePath(volumePath)
        , m_imgInfo(nullptr)
        , m_fsInfo(nullptr)
    {
    }
    
    ~Impl() {
        close();
    }
    
    Result<void> open() {
        if (m_fsInfo) {
            return Result<void>::success(); // Already open
        }
        
        // Open the image/volume
        const char* images[1] = { m_volumePath.c_str() };
        m_imgInfo = tsk_img_open(1, images, TSK_IMG_TYPE_DETECT, 0);
        
        if (!m_imgInfo) {
            return Result<void>::failure(
                RecoveryError::DeviceAccessDenied,
                "Cannot open device: " + std::string(tsk_error_get())
            );
        }
        
        // Try to open filesystem at offset 0
        m_fsInfo = tsk_fs_open_img(m_imgInfo, 0, TSK_FS_TYPE_DETECT);
        
        if (!m_fsInfo) {
            // Try to find filesystem via volume system
            TSK_VS_INFO* vsInfo = tsk_vs_open(m_imgInfo, 0, TSK_VS_TYPE_DETECT);
            
            if (vsInfo) {
                // Use first partition with a filesystem
                const TSK_VS_PART_INFO* part = vsInfo->part_list;
                while (part) {
                    if ((part->flags & TSK_VS_PART_FLAG_ALLOC) &&
                        !(part->flags & TSK_VS_PART_FLAG_META)) {
                        TSK_OFF_T offset = part->start * vsInfo->block_size;
                        m_fsInfo = tsk_fs_open_img(m_imgInfo, offset, TSK_FS_TYPE_DETECT);
                        if (m_fsInfo) {
                            break;
                        }
                    }
                    part = part->next;
                }
                tsk_vs_close(vsInfo);
            }
        }
        
        if (!m_fsInfo) {
            tsk_img_close(m_imgInfo);
            m_imgInfo = nullptr;
            return Result<void>::failure(
                RecoveryError::UnsupportedFilesystem,
                "Cannot detect filesystem: " + std::string(tsk_error_get())
            );
        }
        
        // Determine filesystem type
        m_fsType = tskFsTypeToEnum(m_fsInfo->ftype);
        
        return Result<void>::success();
    }
    
    void close() {
        if (m_fsInfo) {
            tsk_fs_close(m_fsInfo);
            m_fsInfo = nullptr;
        }
        if (m_imgInfo) {
            tsk_img_close(m_imgInfo);
            m_imgInfo = nullptr;
        }
    }
    
    bool isOpen() const {
        return m_fsInfo != nullptr;
    }
    
    FilesystemType getFilesystemType() const {
        return m_fsType;
    }
    
    Result<VolumeInfo> getVolumeInfo() const {
        if (!m_fsInfo) {
            return Result<VolumeInfo>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        VolumeInfo info;
        info.volumePath = m_volumePath;
        info.filesystem = m_fsType;
        info.totalSize = m_imgInfo->size;
        info.clusterSize = m_fsInfo->block_size;
        
        // Calculate used/free space
        TSK_FS_BLOCK_FLAG_ENUM allocFlag = TSK_FS_BLOCK_FLAG_ALLOC;
        uint64_t allocatedBlocks = 0;
        
        // Count allocated blocks (simplified)
        // In real implementation, would walk block bitmap
        info.usedSpace = 0; // Would need to calculate
        info.freeSpace = info.totalSize - info.usedSpace;
        
        return Result<VolumeInfo>::success(std::move(info));
    }
    
    Result<size_t> enumerateFiles(
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback
    ) {
        if (!m_fsInfo) {
            return Result<size_t>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSKCallbackContext context;
        context.parser = this;
        context.callback = callback;
        context.options = &options;
        context.cancelled = &m_cancelled;
        context.fileCount = 0;
        
        m_cancelled = false;
        
        // Walk the filesystem directory tree
        uint8_t flags = TSK_FS_DIR_WALK_FLAG_ALLOC | TSK_FS_DIR_WALK_FLAG_RECURSE;
        
        if (!options.scanDeletedOnly) {
            flags |= TSK_FS_DIR_WALK_FLAG_UNALLOC;
        }
        
        int result = tsk_fs_dir_walk(
            m_fsInfo,
            m_fsInfo->root_inum,
            static_cast<TSK_FS_DIR_WALK_FLAG_ENUM>(flags),
            tskDirWalkCallback,
            &context
        );
        
        if (result != 0 && !m_cancelled) {
            return Result<size_t>::failure(
                RecoveryError::TSKError,
                "Directory walk failed: " + std::string(tsk_error_get())
            );
        }
        
        return Result<size_t>::success(context.fileCount);
    }
    
    Result<size_t> enumerateDeletedFiles(
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback
    ) {
        ScanOptions deletedOptions = options;
        deletedOptions.scanDeletedOnly = true;
        return enumerateFiles(deletedOptions, callback, progressCallback);
    }
    
    Result<FileMetadata> getFileByPath(const std::string& path) {
        if (!m_fsInfo) {
            return Result<FileMetadata>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSK_FS_FILE* fsFile = tsk_fs_file_open(m_fsInfo, nullptr, path.c_str());
        if (!fsFile) {
            return Result<FileMetadata>::failure(
                RecoveryError::FileNotFound,
                "File not found: " + path
            );
        }
        
        FileMetadata metadata = tskFileToMetadata(fsFile, path);
        tsk_fs_file_close(fsFile);
        
        return Result<FileMetadata>::success(std::move(metadata));
    }
    
    Result<FileMetadata> getFileByInode(uint64_t inode) {
        if (!m_fsInfo) {
            return Result<FileMetadata>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSK_FS_FILE* fsFile = tsk_fs_file_open_meta(m_fsInfo, nullptr, inode);
        if (!fsFile) {
            return Result<FileMetadata>::failure(
                RecoveryError::FileNotFound,
                "File not found with inode: " + std::to_string(inode)
            );
        }
        
        FileMetadata metadata = tskFileToMetadata(fsFile, "");
        tsk_fs_file_close(fsFile);
        
        return Result<FileMetadata>::success(std::move(metadata));
    }
    
    Result<std::vector<uint8_t>> readFile(
        uint64_t fileId,
        uint64_t offset,
        size_t length
    ) {
        if (!m_fsInfo) {
            return Result<std::vector<uint8_t>>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSK_FS_FILE* fsFile = tsk_fs_file_open_meta(m_fsInfo, nullptr, fileId);
        if (!fsFile) {
            return Result<std::vector<uint8_t>>::failure(
                RecoveryError::FileNotFound,
                "File not found"
            );
        }
        
        // Get file size
        uint64_t fileSize = 0;
        if (fsFile->meta && fsFile->meta->size > 0) {
            fileSize = fsFile->meta->size;
        }
        
        if (offset >= fileSize) {
            tsk_fs_file_close(fsFile);
            return Result<std::vector<uint8_t>>::success(std::vector<uint8_t>());
        }
        
        // Adjust length
        size_t toRead = length;
        if (toRead == SIZE_MAX || offset + toRead > fileSize) {
            toRead = static_cast<size_t>(fileSize - offset);
        }
        
        std::vector<uint8_t> buffer(toRead);
        
        ssize_t bytesRead = tsk_fs_file_read(
            fsFile,
            offset,
            reinterpret_cast<char*>(buffer.data()),
            toRead,
            TSK_FS_FILE_READ_FLAG_NONE
        );
        
        tsk_fs_file_close(fsFile);
        
        if (bytesRead < 0) {
            return Result<std::vector<uint8_t>>::failure(
                RecoveryError::ReadError,
                "Error reading file: " + std::string(tsk_error_get())
            );
        }
        
        buffer.resize(bytesRead);
        return Result<std::vector<uint8_t>>::success(std::move(buffer));
    }
    
    Result<size_t> readFileToBuffer(
        uint64_t fileId,
        uint8_t* buffer,
        size_t bufferSize,
        uint64_t offset
    ) {
        if (!buffer || bufferSize == 0) {
            return Result<size_t>::failure(
                RecoveryError::InvalidParameter,
                "Invalid buffer"
            );
        }
        
        auto result = readFile(fileId, offset, bufferSize);
        if (result.isError()) {
            return Result<size_t>::failure(result.error, result.errorMessage);
        }
        
        size_t copySize = std::min(result.value.size(), bufferSize);
        std::memcpy(buffer, result.value.data(), copySize);
        
        return Result<size_t>::success(copySize);
    }
    
    Result<std::vector<FileMetadata>> listDirectory(const std::string& path) {
        if (!m_fsInfo) {
            return Result<std::vector<FileMetadata>>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        std::vector<FileMetadata> entries;
        
        TSK_FS_DIR* dir = tsk_fs_dir_open(m_fsInfo, path.c_str());
        if (!dir) {
            return Result<std::vector<FileMetadata>>::failure(
                RecoveryError::FileNotFound,
                "Directory not found: " + path
            );
        }
        
        for (size_t i = 0; i < tsk_fs_dir_getsize(dir); ++i) {
            TSK_FS_FILE* fsFile = tsk_fs_dir_get(dir, i);
            if (fsFile) {
                std::string entryPath = path;
                if (!entryPath.empty() && entryPath.back() != '/') {
                    entryPath += '/';
                }
                if (fsFile->name && fsFile->name->name) {
                    entryPath += fsFile->name->name;
                }
                
                entries.push_back(tskFileToMetadata(fsFile, entryPath));
                tsk_fs_file_close(fsFile);
            }
        }
        
        tsk_fs_dir_close(dir);
        
        return Result<std::vector<FileMetadata>>::success(std::move(entries));
    }
    
    Result<std::pair<RecoveryScore, float>> analyzeRecoveryPotential(uint64_t fileId) {
        if (!m_fsInfo) {
            return Result<std::pair<RecoveryScore, float>>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSK_FS_FILE* fsFile = tsk_fs_file_open_meta(m_fsInfo, nullptr, fileId);
        if (!fsFile) {
            return Result<std::pair<RecoveryScore, float>>::failure(
                RecoveryError::FileNotFound,
                "File not found"
            );
        }
        
        RecoveryScore score = RecoveryScore::Excellent;
        float confidence = 1.0f;
        
        // Check if file is deleted
        bool isDeleted = false;
        if (fsFile->meta) {
            isDeleted = (fsFile->meta->flags & TSK_FS_META_FLAG_UNALLOC) != 0;
        }
        
        if (isDeleted) {
            // For deleted files, check cluster allocation
            auto fragmentsResult = getFileFragmentsInternal(fsFile);
            
            if (fragmentsResult.isSuccess()) {
                const auto& fragments = fragmentsResult.value;
                
                if (fragments.empty()) {
                    // No cluster information - resident data or corrupted
                    if (fsFile->meta && fsFile->meta->size < m_fsInfo->block_size) {
                        // Small file, might be resident
                        score = RecoveryScore::Good;
                        confidence = 0.7f;
                    } else {
                        score = RecoveryScore::Poor;
                        confidence = 0.2f;
                    }
                } else {
                    // Check if clusters are still free
                    bool clustersOverwritten = false;
                    size_t fragmentCount = fragments.size();
                    
                    // Check each cluster run
                    for (const auto& frag : fragments) {
                        TSK_FS_BLOCK_FLAG_ENUM flags;
                        for (uint64_t i = 0; i < frag.second; ++i) {
                            flags = tsk_fs_block_getflags(m_fsInfo, frag.first + i);
                            if (flags & TSK_FS_BLOCK_FLAG_ALLOC) {
                                clustersOverwritten = true;
                                break;
                            }
                        }
                        if (clustersOverwritten) break;
                    }
                    
                    if (clustersOverwritten) {
                        score = RecoveryScore::Partial;
                        confidence = 0.4f;
                    } else if (fragmentCount > 1) {
                        score = RecoveryScore::Good;
                        confidence = 0.8f;
                    } else {
                        score = RecoveryScore::Excellent;
                        confidence = 0.95f;
                    }
                }
            } else {
                score = RecoveryScore::Poor;
                confidence = 0.3f;
            }
        }
        
        tsk_fs_file_close(fsFile);
        
        return Result<std::pair<RecoveryScore, float>>::success(
            std::make_pair(score, confidence)
        );
    }
    
    Result<bool> areClusterOverwritten(uint64_t fileId) {
        if (!m_fsInfo) {
            return Result<bool>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSK_FS_FILE* fsFile = tsk_fs_file_open_meta(m_fsInfo, nullptr, fileId);
        if (!fsFile) {
            return Result<bool>::failure(
                RecoveryError::FileNotFound,
                "File not found"
            );
        }
        
        auto fragmentsResult = getFileFragmentsInternal(fsFile);
        tsk_fs_file_close(fsFile);
        
        if (fragmentsResult.isError()) {
            return Result<bool>::failure(
                fragmentsResult.error, fragmentsResult.errorMessage
            );
        }
        
        for (const auto& frag : fragmentsResult.value) {
            for (uint64_t i = 0; i < frag.second; ++i) {
                TSK_FS_BLOCK_FLAG_ENUM flags = tsk_fs_block_getflags(
                    m_fsInfo, frag.first + i
                );
                if (flags & TSK_FS_BLOCK_FLAG_ALLOC) {
                    return Result<bool>::success(true);
                }
            }
        }
        
        return Result<bool>::success(false);
    }
    
    Result<std::vector<std::pair<uint64_t, uint64_t>>> getFileFragments(uint64_t fileId) {
        if (!m_fsInfo) {
            return Result<std::vector<std::pair<uint64_t, uint64_t>>>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        TSK_FS_FILE* fsFile = tsk_fs_file_open_meta(m_fsInfo, nullptr, fileId);
        if (!fsFile) {
            return Result<std::vector<std::pair<uint64_t, uint64_t>>>::failure(
                RecoveryError::FileNotFound,
                "File not found"
            );
        }
        
        auto result = getFileFragmentsInternal(fsFile);
        tsk_fs_file_close(fsFile);
        
        return result;
    }
    
    Result<size_t> parseMFT(
        FileFoundCallback callback,
        ProgressCallback progressCallback
    ) {
        if (!m_fsInfo) {
            return Result<size_t>::failure(
                RecoveryError::DeviceNotFound,
                "Filesystem not open"
            );
        }
        
        if (m_fsType != FilesystemType::NTFS) {
            return Result<size_t>::failure(
                RecoveryError::UnsupportedFilesystem,
                "MFT parsing only available for NTFS"
            );
        }
        
        // Walk all inodes
        TSKCallbackContext context;
        context.parser = this;
        context.callback = callback;
        context.options = nullptr;
        context.cancelled = &m_cancelled;
        context.fileCount = 0;
        
        m_cancelled = false;
        
        int result = tsk_fs_meta_walk(
            m_fsInfo,
            m_fsInfo->first_inum,
            m_fsInfo->last_inum,
            TSK_FS_META_FLAG_ALLOC | TSK_FS_META_FLAG_UNALLOC,
            tskMetaWalkCallback,
            &context
        );
        
        if (result != 0 && !m_cancelled) {
            return Result<size_t>::failure(
                RecoveryError::TSKError,
                "MFT walk failed: " + std::string(tsk_error_get())
            );
        }
        
        return Result<size_t>::success(context.fileCount);
    }
    
    Result<FileMetadata> getMFTRecord(uint64_t recordNumber) {
        return getFileByInode(recordNumber);
    }
    
    void cancel() {
        m_cancelled = true;
    }
    
    bool isCancelled() const {
        return m_cancelled;
    }
    
    // TSK callback for directory walk
    static TSK_WALK_RET_ENUM tskDirWalkCallback(
        TSK_FS_FILE* fsFile,
        const char* path,
        void* ptr
    ) {
        auto* context = static_cast<TSKCallbackContext*>(ptr);
        
        if (*context->cancelled) {
            return TSK_WALK_STOP;
        }
        
        // Skip . and ..
        if (fsFile->name && fsFile->name->name) {
            const char* name = fsFile->name->name;
            if ((name[0] == '.' && name[1] == '\0') ||
                (name[0] == '.' && name[1] == '.' && name[2] == '\0')) {
                return TSK_WALK_CONT;
            }
        }
        
        // Apply filters
        if (context->options) {
            // Skip directories if not requested
            if (fsFile->meta && fsFile->meta->type == TSK_FS_META_TYPE_DIR) {
                return TSK_WALK_CONT;
            }
            
            // Skip system files if not requested
            if (!context->options->includeSystemFiles && fsFile->name) {
                if (fsFile->name->flags & TSK_FS_NAME_FLAG_ALLOC) {
                    // Check for system file attributes
                }
            }
        }
        
        std::string fullPath = path ? path : "";
        if (fsFile->name && fsFile->name->name) {
            if (!fullPath.empty() && fullPath.back() != '/') {
                fullPath += '/';
            }
            fullPath += fsFile->name->name;
        }
        
        FileMetadata metadata = context->parser->tskFileToMetadata(fsFile, fullPath);
        
        // Apply size filters
        if (context->options) {
            if (metadata.fileSize < context->options->minFileSize ||
                metadata.fileSize > context->options->maxFileSize) {
                return TSK_WALK_CONT;
            }
        }
        
        context->fileCount++;
        
        if (context->callback) {
            context->callback(metadata);
        }
        
        return TSK_WALK_CONT;
    }
    
    // TSK callback for meta walk (MFT parsing)
    static TSK_WALK_RET_ENUM tskMetaWalkCallback(
        TSK_FS_FILE* fsFile,
        void* ptr
    ) {
        auto* context = static_cast<TSKCallbackContext*>(ptr);
        
        if (*context->cancelled) {
            return TSK_WALK_STOP;
        }
        
        if (!fsFile->meta) {
            return TSK_WALK_CONT;
        }
        
        // Only process regular files
        if (fsFile->meta->type != TSK_FS_META_TYPE_REG) {
            return TSK_WALK_CONT;
        }
        
        FileMetadata metadata = context->parser->tskFileToMetadata(fsFile, "");
        context->fileCount++;
        
        if (context->callback) {
            context->callback(metadata);
        }
        
        return TSK_WALK_CONT;
    }
    
    FileMetadata tskFileToMetadata(TSK_FS_FILE* fsFile, const std::string& path) {
        FileMetadata metadata;
        
        // File ID (inode/MFT record number)
        if (fsFile->meta) {
            metadata.fileId = fsFile->meta->addr;
            metadata.mftRecordNumber = fsFile->meta->addr;
        }
        
        // Name and path
        if (fsFile->name && fsFile->name->name) {
            metadata.fileName = fsFile->name->name;
        }
        metadata.filePath = path;
        
        // Extract extension
        size_t dotPos = metadata.fileName.rfind('.');
        if (dotPos != std::string::npos && dotPos < metadata.fileName.length() - 1) {
            metadata.extension = metadata.fileName.substr(dotPos + 1);
            std::transform(metadata.extension.begin(), metadata.extension.end(),
                          metadata.extension.begin(), ::tolower);
        }
        
        // Categorize by extension
        metadata.category = FileTypeClassifier::categorizeByExtension(metadata.extension);
        
        // Size
        if (fsFile->meta) {
            metadata.fileSize = fsFile->meta->size;
        }
        
        // Timestamps
        if (fsFile->meta) {
            metadata.createdTime = fsFile->meta->crtime;
            metadata.modifiedTime = fsFile->meta->mtime;
            metadata.accessedTime = fsFile->meta->atime;
        }
        
        // Deleted status
        if (fsFile->name) {
            metadata.isDeleted = (fsFile->name->flags & TSK_FS_NAME_FLAG_UNALLOC) != 0;
        }
        if (fsFile->meta) {
            metadata.isDeleted = metadata.isDeleted ||
                (fsFile->meta->flags & TSK_FS_META_FLAG_UNALLOC) != 0;
        }
        
        // NTFS resident data check
        if (m_fsType == FilesystemType::NTFS && fsFile->meta) {
            // Check default data attribute
            const TSK_FS_ATTR* attr = tsk_fs_file_attr_get(fsFile);
            if (attr && (attr->flags & TSK_FS_ATTR_RES)) {
                metadata.hasResidentData = true;
            }
        }
        
        // Calculate recovery score
        if (metadata.isDeleted) {
            metadata.score = RecoveryScore::Good; // Default for deleted
            metadata.recoveryConfidence = 0.7f;
        } else {
            metadata.score = RecoveryScore::Excellent;
            metadata.recoveryConfidence = 1.0f;
        }
        
        return metadata;
    }
    
    FilesystemType tskFsTypeToEnum(TSK_FS_TYPE_ENUM tskType) {
        switch (tskType) {
            case TSK_FS_TYPE_NTFS:
            case TSK_FS_TYPE_NTFS_DETECT:
                return FilesystemType::NTFS;
            case TSK_FS_TYPE_FAT12:
                return FilesystemType::FAT12;
            case TSK_FS_TYPE_FAT16:
                return FilesystemType::FAT16;
            case TSK_FS_TYPE_FAT32:
                return FilesystemType::FAT32;
            case TSK_FS_TYPE_EXFAT:
                return FilesystemType::ExFAT;
            case TSK_FS_TYPE_EXT2:
                return FilesystemType::Ext2;
            case TSK_FS_TYPE_EXT3:
                return FilesystemType::Ext3;
            case TSK_FS_TYPE_EXT4:
                return FilesystemType::Ext4;
            case TSK_FS_TYPE_HFS:
                return FilesystemType::HFS;
            case TSK_FS_TYPE_ISO9660:
                return FilesystemType::ISO9660;
            case TSK_FS_TYPE_RAW:
                return FilesystemType::RAW;
            default:
                return FilesystemType::Unknown;
        }
    }
    
private:
    std::string m_volumePath;
    TSK_IMG_INFO* m_imgInfo;
    TSK_FS_INFO* m_fsInfo;
    FilesystemType m_fsType = FilesystemType::Unknown;
    std::atomic<bool> m_cancelled{false};
    
    Result<std::vector<std::pair<uint64_t, uint64_t>>> getFileFragmentsInternal(
        TSK_FS_FILE* fsFile
    ) {
        std::vector<std::pair<uint64_t, uint64_t>> fragments;
        
        if (!fsFile->meta) {
            return Result<std::vector<std::pair<uint64_t, uint64_t>>>::success(
                std::move(fragments)
            );
        }
        
        // Get default data attribute
        const TSK_FS_ATTR* attr = tsk_fs_file_attr_get(fsFile);
        if (!attr) {
            return Result<std::vector<std::pair<uint64_t, uint64_t>>>::success(
                std::move(fragments)
            );
        }
        
        // Walk the run list
        for (int i = 0; i < attr->nrd.run_end; ++i) {
            TSK_FS_ATTR_RUN* run = &attr->nrd.run[i];
            if (run->flags & TSK_FS_ATTR_RUN_FLAG_NONE) {
                // Valid run
                fragments.emplace_back(run->addr, run->len);
            }
        }
        
        return Result<std::vector<std::pair<uint64_t, uint64_t>>>::success(
            std::move(fragments)
        );
    }
};

// FileSystemParser public interface implementation

FileSystemParser::FileSystemParser(const std::string& volumePath)
    : pImpl(std::make_unique<Impl>(volumePath))
{
}

FileSystemParser::~FileSystemParser() = default;

Result<void> FileSystemParser::open() {
    return pImpl->open();
}

void FileSystemParser::close() {
    pImpl->close();
}

bool FileSystemParser::isOpen() const {
    return pImpl->isOpen();
}

FilesystemType FileSystemParser::getFilesystemType() const {
    return pImpl->getFilesystemType();
}

Result<VolumeInfo> FileSystemParser::getVolumeInfo() const {
    return pImpl->getVolumeInfo();
}

Result<size_t> FileSystemParser::enumerateFiles(
    const ScanOptions& options,
    FileFoundCallback callback,
    ProgressCallback progressCallback
) {
    return pImpl->enumerateFiles(options, callback, progressCallback);
}

Result<size_t> FileSystemParser::enumerateDeletedFiles(
    const ScanOptions& options,
    FileFoundCallback callback,
    ProgressCallback progressCallback
) {
    return pImpl->enumerateDeletedFiles(options, callback, progressCallback);
}

Result<FileMetadata> FileSystemParser::getFileByPath(const std::string& path) {
    return pImpl->getFileByPath(path);
}

Result<FileMetadata> FileSystemParser::getFileByInode(uint64_t inode) {
    return pImpl->getFileByInode(inode);
}

Result<std::vector<uint8_t>> FileSystemParser::readFile(
    uint64_t fileId,
    uint64_t offset,
    size_t length
) {
    return pImpl->readFile(fileId, offset, length);
}

Result<size_t> FileSystemParser::readFileToBuffer(
    uint64_t fileId,
    uint8_t* buffer,
    size_t bufferSize,
    uint64_t offset
) {
    return pImpl->readFileToBuffer(fileId, buffer, bufferSize, offset);
}

Result<std::vector<FileMetadata>> FileSystemParser::listDirectory(const std::string& path) {
    return pImpl->listDirectory(path);
}

Result<std::pair<RecoveryScore, float>> FileSystemParser::analyzeRecoveryPotential(
    uint64_t fileId
) {
    return pImpl->analyzeRecoveryPotential(fileId);
}

Result<bool> FileSystemParser::areClusterOverwritten(uint64_t fileId) {
    return pImpl->areClusterOverwritten(fileId);
}

Result<std::vector<std::pair<uint64_t, uint64_t>>> FileSystemParser::getFileFragments(
    uint64_t fileId
) {
    return pImpl->getFileFragments(fileId);
}

Result<size_t> FileSystemParser::parseMFT(
    FileFoundCallback callback,
    ProgressCallback progressCallback
) {
    return pImpl->parseMFT(callback, progressCallback);
}

Result<FileMetadata> FileSystemParser::getMFTRecord(uint64_t recordNumber) {
    return pImpl->getMFTRecord(recordNumber);
}

void FileSystemParser::cancel() {
    pImpl->cancel();
}

bool FileSystemParser::isCancelled() const {
    return pImpl->isCancelled();
}

} // namespace RecoveryEngine
