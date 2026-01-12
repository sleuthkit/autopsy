/**
 * @file FileSystemParser.h
 * @brief Filesystem parsing and file enumeration
 */

#ifndef FILESYSTEM_PARSER_H
#define FILESYSTEM_PARSER_H

#include "RecoveryTypes.h"
#include <vector>
#include <string>
#include <memory>
#include <functional>

// Forward declarations for TSK types
struct TSK_FS_INFO;
struct TSK_FS_FILE;

namespace RecoveryEngine {

/**
 * @class FileSystemParser
 * @brief Parses filesystems to enumerate files and directories
 * 
 * Uses The Sleuth Kit to parse NTFS, FAT, exFAT and other filesystems.
 * Extracts file metadata, handles deleted files, and analyzes MFT records.
 */
class FileSystemParser {
public:
    explicit FileSystemParser(const std::string& volumePath);
    ~FileSystemParser();
    
    // Non-copyable
    FileSystemParser(const FileSystemParser&) = delete;
    FileSystemParser& operator=(const FileSystemParser&) = delete;
    
    /**
     * @brief Open the filesystem for parsing
     * @return Success or error
     */
    Result<void> open();
    
    /**
     * @brief Close the filesystem
     */
    void close();
    
    /**
     * @brief Check if filesystem is open
     */
    bool isOpen() const;
    
    /**
     * @brief Get the filesystem type
     */
    FilesystemType getFilesystemType() const;
    
    /**
     * @brief Get filesystem information
     */
    Result<VolumeInfo> getVolumeInfo() const;
    
    /**
     * @brief Enumerate all files and directories
     * @param options Scan options for filtering
     * @param callback Called for each file found
     * @param progressCallback Progress updates
     * @return Total files found
     */
    Result<size_t> enumerateFiles(
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback = nullptr
    );
    
    /**
     * @brief Enumerate deleted files only
     * @param options Scan options
     * @param callback Called for each deleted file
     * @param progressCallback Progress updates
     * @return Count of deleted files found
     */
    Result<size_t> enumerateDeletedFiles(
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback = nullptr
    );
    
    /**
     * @brief Get file metadata by path
     * @param path File path within volume
     * @return File metadata
     */
    Result<FileMetadata> getFileByPath(const std::string& path);
    
    /**
     * @brief Get file metadata by inode/MFT record number
     * @param inode Inode or MFT record number
     * @return File metadata
     */
    Result<FileMetadata> getFileByInode(uint64_t inode);
    
    /**
     * @brief Read file content
     * @param fileId Internal file ID
     * @param offset Start offset within file
     * @param length Bytes to read
     * @return File data buffer
     */
    Result<std::vector<uint8_t>> readFile(
        uint64_t fileId,
        uint64_t offset = 0,
        size_t length = SIZE_MAX
    );
    
    /**
     * @brief Read file content to a buffer
     * @param fileId Internal file ID
     * @param buffer Output buffer
     * @param bufferSize Buffer size
     * @param offset Start offset within file
     * @return Bytes read
     */
    Result<size_t> readFileToBuffer(
        uint64_t fileId,
        uint8_t* buffer,
        size_t bufferSize,
        uint64_t offset = 0
    );
    
    /**
     * @brief List directory contents
     * @param path Directory path
     * @return List of entries in directory
     */
    Result<std::vector<FileMetadata>> listDirectory(const std::string& path);
    
    /**
     * @brief Analyze file recovery potential
     * @param fileId Internal file ID
     * @return Recovery score and analysis
     */
    Result<std::pair<RecoveryScore, float>> analyzeRecoveryPotential(uint64_t fileId);
    
    /**
     * @brief Check if file clusters are overwritten
     * @param fileId Internal file ID
     * @return True if any clusters are overwritten
     */
    Result<bool> areClusterOverwritten(uint64_t fileId);
    
    /**
     * @brief Get file fragment information
     * @param fileId Internal file ID
     * @return List of cluster runs (start, length pairs)
     */
    Result<std::vector<std::pair<uint64_t, uint64_t>>> getFileFragments(uint64_t fileId);
    
    // NTFS-specific methods
    
    /**
     * @brief Parse MFT directly (NTFS only)
     * @param callback Called for each MFT record
     * @param progressCallback Progress updates
     * @return Count of records parsed
     */
    Result<size_t> parseMFT(
        FileFoundCallback callback,
        ProgressCallback progressCallback = nullptr
    );
    
    /**
     * @brief Get MFT record (NTFS only)
     * @param recordNumber MFT record number
     * @return File metadata from MFT
     */
    Result<FileMetadata> getMFTRecord(uint64_t recordNumber);
    
    /**
     * @brief Set cancellation flag
     */
    void cancel();
    
    /**
     * @brief Check if cancelled
     */
    bool isCancelled() const;
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
    
    // TSK callback adapter
    static int tskFileCallback(TSK_FS_FILE* fsFile, const char* path, void* context);
    
    // Convert TSK file to our metadata
    FileMetadata tskFileToMetadata(TSK_FS_FILE* fsFile, const std::string& path);
};

} // namespace RecoveryEngine

#endif // FILESYSTEM_PARSER_H
