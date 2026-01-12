/**
 * @file RecoveryTypes.h
 * @brief Core type definitions for the Data Recovery Engine
 * 
 * This header defines all fundamental types, enums, and structures used
 * throughout the recovery engine. These types are designed for consumer
 * data recovery workflows without forensic case management overhead.
 */

#ifndef RECOVERY_TYPES_H
#define RECOVERY_TYPES_H

#include <cstdint>
#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <chrono>
#include <optional>

namespace RecoveryEngine {

// Forward declarations
class RecoverySession;
class ProgressTracker;

/**
 * @brief Recovery probability scores for files
 */
enum class RecoveryScore : uint8_t {
    Excellent = 0,   ///< File fully intact, high recovery confidence
    Good = 1,        ///< Most data recoverable, minor issues possible
    Partial = 2,     ///< Some data recoverable, fragmentation or overwrite detected
    Poor = 3,        ///< Limited recovery possible, significant damage
    Unrecoverable = 4 ///< File data overwritten or corrupted beyond recovery
};

/**
 * @brief File type categories for consumer-friendly organization
 */
enum class FileCategory : uint8_t {
    Photo = 0,       ///< Images: JPEG, PNG, GIF, BMP, TIFF, RAW, HEIC, WebP
    Video = 1,       ///< Videos: MP4, AVI, MOV, MKV, WMV, FLV
    Audio = 2,       ///< Audio: MP3, WAV, FLAC, AAC, OGG, WMA
    Document = 3,    ///< Documents: PDF, DOC, DOCX, XLS, XLSX, PPT, TXT, RTF
    Archive = 4,     ///< Archives: ZIP, RAR, 7Z, TAR, GZ
    Email = 5,       ///< Email: PST, OST, EML, MBOX
    Database = 6,    ///< Databases: SQLite, MDB, ACCDB
    Executable = 7,  ///< Executables: EXE, DLL, MSI
    System = 8,      ///< System files: SYS, DRV, INF
    Other = 9        ///< Other/unknown file types
};

/**
 * @brief Filesystem types supported for recovery
 */
enum class FilesystemType : uint8_t {
    Unknown = 0,
    NTFS = 1,
    FAT12 = 2,
    FAT16 = 3,
    FAT32 = 4,
    ExFAT = 5,
    Ext2 = 6,
    Ext3 = 7,
    Ext4 = 8,
    HFS = 9,
    HFSPlus = 10,
    APFS = 11,
    RAW = 12,         ///< No filesystem detected, raw sector scan
    ISO9660 = 13,
    UDF = 14
};

/**
 * @brief Device types that can be scanned
 */
enum class DeviceType : uint8_t {
    Unknown = 0,
    PhysicalDisk = 1,    ///< Physical hard drive or SSD
    Partition = 2,       ///< Logical partition
    RemovableDrive = 3,  ///< USB drive, SD card, etc.
    OpticalDrive = 4,    ///< CD/DVD/Blu-ray
    NetworkDrive = 5,    ///< Mapped network location
    ImageFile = 6,       ///< Disk image file (raw, E01, etc.)
    VSSSnapshot = 7      ///< Volume Shadow Copy snapshot
};

/**
 * @brief Scan operation types
 */
enum class ScanType : uint8_t {
    QuickScan = 0,      ///< Scan filesystem metadata only
    DeepScan = 1,       ///< Full sector-by-sector scan with carving
    PartitionScan = 2,  ///< Scan for lost partitions
    CustomScan = 3      ///< User-defined scan parameters
};

/**
 * @brief Operation status codes
 */
enum class OperationStatus : uint8_t {
    NotStarted = 0,
    Running = 1,
    Paused = 2,
    Completed = 3,
    Cancelled = 4,
    Failed = 5
};

/**
 * @brief Error codes for recovery operations
 */
enum class RecoveryError : uint32_t {
    None = 0,
    DeviceNotFound = 1,
    DeviceAccessDenied = 2,
    DeviceBusy = 3,
    InvalidPath = 4,
    FileNotFound = 5,
    InsufficientSpace = 6,
    WriteError = 7,
    ReadError = 8,
    CorruptedData = 9,
    UnsupportedFilesystem = 10,
    SessionNotFound = 11,
    SessionCorrupted = 12,
    InvalidParameter = 13,
    OutOfMemory = 14,
    Cancelled = 15,
    TSKError = 16,
    DatabaseError = 17,
    UnknownError = 255
};

/**
 * @brief Device information structure
 */
struct DeviceInfo {
    std::string devicePath;          ///< Device path (e.g., \\.\PhysicalDrive0)
    std::string displayName;         ///< Human-readable name
    std::string serialNumber;        ///< Device serial number if available
    std::string model;               ///< Device model
    DeviceType type = DeviceType::Unknown;
    uint64_t totalSize = 0;          ///< Total size in bytes
    uint64_t sectorSize = 512;       ///< Sector size in bytes
    bool isRemovable = false;
    bool isWritable = true;
    bool isOnline = true;
};

/**
 * @brief Volume/partition information structure
 */
struct VolumeInfo {
    std::string volumePath;          ///< Volume path
    std::string label;               ///< Volume label
    std::string mountPoint;          ///< Mount point (drive letter on Windows)
    FilesystemType filesystem = FilesystemType::Unknown;
    uint64_t startOffset = 0;        ///< Start offset in bytes on parent device
    uint64_t totalSize = 0;          ///< Total size in bytes
    uint64_t usedSpace = 0;          ///< Used space in bytes
    uint64_t freeSpace = 0;          ///< Free space in bytes
    uint32_t clusterSize = 0;        ///< Cluster size in bytes
    bool isMounted = false;
    bool isBootable = false;
    int partitionIndex = -1;         ///< Partition index on parent device
};

/**
 * @brief File metadata for recovered/recoverable files
 */
struct FileMetadata {
    uint64_t fileId = 0;             ///< Internal file ID
    std::string fileName;            ///< File name
    std::string filePath;            ///< Full path within volume
    std::string extension;           ///< File extension (lowercase)
    FileCategory category = FileCategory::Other;
    uint64_t fileSize = 0;           ///< File size in bytes
    uint64_t allocatedSize = 0;      ///< Allocated size (may differ from fileSize)
    uint64_t startCluster = 0;       ///< Starting cluster/sector
    
    // Timestamps (Unix epoch, seconds)
    int64_t createdTime = 0;
    int64_t modifiedTime = 0;
    int64_t accessedTime = 0;
    
    // Recovery metadata
    RecoveryScore score = RecoveryScore::Excellent;
    float recoveryConfidence = 1.0f; ///< 0.0 to 1.0 confidence value
    bool isDeleted = false;          ///< File was deleted
    bool isFragmented = false;       ///< File is fragmented
    bool isPartiallyOverwritten = false;
    uint32_t fragmentCount = 1;      ///< Number of fragments
    
    // NTFS-specific
    uint64_t mftRecordNumber = 0;
    bool hasResidentData = false;    ///< Data stored in MFT record
    
    // Metadata
    std::string mimeType;
    std::string detectedType;        ///< Type detected by magic number
    uint32_t attributes = 0;         ///< File attributes (system, hidden, etc.)
};

/**
 * @brief Scan progress information
 */
struct ScanProgress {
    OperationStatus status = OperationStatus::NotStarted;
    ScanType scanType = ScanType::QuickScan;
    
    uint64_t totalBytes = 0;          ///< Total bytes to scan
    uint64_t scannedBytes = 0;        ///< Bytes scanned so far
    uint64_t totalItems = 0;          ///< Total items (files/sectors) to process
    uint64_t processedItems = 0;      ///< Items processed so far
    
    float percentComplete = 0.0f;     ///< 0.0 to 100.0
    
    uint64_t filesFound = 0;          ///< Total files found
    uint64_t deletedFilesFound = 0;   ///< Deleted files found
    uint64_t carvedFilesFound = 0;    ///< Files recovered via carving
    
    std::chrono::seconds elapsedTime{0};
    std::chrono::seconds estimatedTimeRemaining{0};
    
    std::string currentOperation;     ///< Description of current operation
    std::string currentPath;          ///< Current file/folder being processed
};

/**
 * @brief Recovery operation progress
 */
struct RecoveryProgress {
    OperationStatus status = OperationStatus::NotStarted;
    
    uint64_t totalFiles = 0;
    uint64_t recoveredFiles = 0;
    uint64_t failedFiles = 0;
    uint64_t skippedFiles = 0;
    
    uint64_t totalBytes = 0;
    uint64_t recoveredBytes = 0;
    
    float percentComplete = 0.0f;
    
    std::chrono::seconds elapsedTime{0};
    std::chrono::seconds estimatedTimeRemaining{0};
    
    std::string currentFile;          ///< File currently being recovered
    RecoveryError lastError = RecoveryError::None;
    std::string lastErrorMessage;
};

/**
 * @brief Session save/load state
 */
struct SessionState {
    std::string sessionId;
    std::string sessionName;
    std::string devicePath;
    std::chrono::system_clock::time_point createdAt;
    std::chrono::system_clock::time_point lastModified;
    ScanProgress scanProgress;
    bool isComplete = false;
};

/**
 * @brief Scan options configuration
 */
struct ScanOptions {
    ScanType scanType = ScanType::QuickScan;
    
    // File type filters
    std::vector<FileCategory> includeCategories;  ///< Empty = all
    std::vector<std::string> includeExtensions;   ///< Empty = all
    std::vector<std::string> excludeExtensions;
    
    // Size filters
    uint64_t minFileSize = 0;
    uint64_t maxFileSize = UINT64_MAX;
    
    // Time filters (Unix timestamps)
    int64_t modifiedAfter = 0;
    int64_t modifiedBefore = INT64_MAX;
    
    // Scan behavior
    bool scanDeletedOnly = false;
    bool includeSystemFiles = false;
    bool includeHiddenFiles = true;
    bool followSymlinks = false;
    bool enableCarving = true;         ///< Enable signature-based carving
    bool scanSlackSpace = false;       ///< Scan cluster slack space
    bool scanUnallocated = true;       ///< Scan unallocated space
    
    // Performance
    uint32_t maxThreads = 0;           ///< 0 = auto-detect
    uint64_t maxMemoryMB = 0;          ///< 0 = auto (default ~512MB)
    uint32_t readBufferSizeKB = 64;    ///< Read buffer size
};

/**
 * @brief Recovery options configuration
 */
struct RecoveryOptions {
    std::string destinationPath;       ///< Where to save recovered files
    
    bool preserveFolder structure = true;  ///< Maintain original folder paths
    bool preserveTimestamps = true;    ///< Restore original timestamps
    bool preserveAttributes = true;    ///< Restore file attributes
    
    // Conflict handling
    enum class ConflictAction {
        Skip,
        Overwrite,
        Rename,
        Ask
    } onConflict = ConflictAction::Rename;
    
    // Partial recovery
    bool recoverPartialFiles = true;   ///< Recover even if incomplete
    float minRecoveryConfidence = 0.0f;///< Skip files below this confidence
    
    // Performance
    bool verifyAfterRecovery = false;  ///< Verify recovered file integrity
    uint32_t maxConcurrentFiles = 4;   ///< Parallel file recovery
};

/**
 * @brief Callback function types
 */
using ProgressCallback = std::function<void(const ScanProgress&)>;
using RecoveryProgressCallback = std::function<void(const RecoveryProgress&)>;
using FileFoundCallback = std::function<void(const FileMetadata&)>;
using ErrorCallback = std::function<void(RecoveryError, const std::string&)>;

/**
 * @brief Result wrapper with error handling
 */
template<typename T>
struct Result {
    T value;
    RecoveryError error = RecoveryError::None;
    std::string errorMessage;
    
    bool isSuccess() const { return error == RecoveryError::None; }
    bool isError() const { return error != RecoveryError::None; }
    
    static Result<T> success(T val) {
        return {std::move(val), RecoveryError::None, ""};
    }
    
    static Result<T> failure(RecoveryError err, const std::string& msg = "") {
        return {T{}, err, msg};
    }
};

// Specialization for void results
template<>
struct Result<void> {
    RecoveryError error = RecoveryError::None;
    std::string errorMessage;
    
    bool isSuccess() const { return error == RecoveryError::None; }
    bool isError() const { return error != RecoveryError::None; }
    
    static Result<void> success() {
        return {RecoveryError::None, ""};
    }
    
    static Result<void> failure(RecoveryError err, const std::string& msg = "") {
        return {err, msg};
    }
};

/**
 * @brief Get string representation of RecoveryScore
 */
inline const char* recoveryScoreToString(RecoveryScore score) {
    switch (score) {
        case RecoveryScore::Excellent: return "Excellent";
        case RecoveryScore::Good: return "Good";
        case RecoveryScore::Partial: return "Partial";
        case RecoveryScore::Poor: return "Poor";
        case RecoveryScore::Unrecoverable: return "Unrecoverable";
        default: return "Unknown";
    }
}

/**
 * @brief Get string representation of FileCategory
 */
inline const char* fileCategoryToString(FileCategory category) {
    switch (category) {
        case FileCategory::Photo: return "Photo";
        case FileCategory::Video: return "Video";
        case FileCategory::Audio: return "Audio";
        case FileCategory::Document: return "Document";
        case FileCategory::Archive: return "Archive";
        case FileCategory::Email: return "Email";
        case FileCategory::Database: return "Database";
        case FileCategory::Executable: return "Executable";
        case FileCategory::System: return "System";
        case FileCategory::Other: return "Other";
        default: return "Unknown";
    }
}

/**
 * @brief Get string representation of FilesystemType
 */
inline const char* filesystemTypeToString(FilesystemType fs) {
    switch (fs) {
        case FilesystemType::NTFS: return "NTFS";
        case FilesystemType::FAT12: return "FAT12";
        case FilesystemType::FAT16: return "FAT16";
        case FilesystemType::FAT32: return "FAT32";
        case FilesystemType::ExFAT: return "exFAT";
        case FilesystemType::Ext2: return "ext2";
        case FilesystemType::Ext3: return "ext3";
        case FilesystemType::Ext4: return "ext4";
        case FilesystemType::HFS: return "HFS";
        case FilesystemType::HFSPlus: return "HFS+";
        case FilesystemType::APFS: return "APFS";
        case FilesystemType::RAW: return "RAW";
        case FilesystemType::ISO9660: return "ISO9660";
        case FilesystemType::UDF: return "UDF";
        default: return "Unknown";
    }
}

/**
 * @brief Get string representation of RecoveryError
 */
inline const char* recoveryErrorToString(RecoveryError err) {
    switch (err) {
        case RecoveryError::None: return "No error";
        case RecoveryError::DeviceNotFound: return "Device not found";
        case RecoveryError::DeviceAccessDenied: return "Device access denied";
        case RecoveryError::DeviceBusy: return "Device is busy";
        case RecoveryError::InvalidPath: return "Invalid path";
        case RecoveryError::FileNotFound: return "File not found";
        case RecoveryError::InsufficientSpace: return "Insufficient disk space";
        case RecoveryError::WriteError: return "Write error";
        case RecoveryError::ReadError: return "Read error";
        case RecoveryError::CorruptedData: return "Corrupted data";
        case RecoveryError::UnsupportedFilesystem: return "Unsupported filesystem";
        case RecoveryError::SessionNotFound: return "Session not found";
        case RecoveryError::SessionCorrupted: return "Session data corrupted";
        case RecoveryError::InvalidParameter: return "Invalid parameter";
        case RecoveryError::OutOfMemory: return "Out of memory";
        case RecoveryError::Cancelled: return "Operation cancelled";
        case RecoveryError::TSKError: return "Sleuth Kit error";
        case RecoveryError::DatabaseError: return "Database error";
        case RecoveryError::UnknownError: return "Unknown error";
        default: return "Unknown error";
    }
}

} // namespace RecoveryEngine

#endif // RECOVERY_TYPES_H
