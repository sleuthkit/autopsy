/**
 * @file RecoveryEngine.h
 * @brief Main recovery engine interface
 * 
 * This is the primary API header for the Data Recovery Engine.
 * It provides a clean, consumer-oriented interface for disk and file recovery
 * operations using The Sleuth Kit core functionality.
 */

#ifndef RECOVERY_ENGINE_H
#define RECOVERY_ENGINE_H

#include "RecoveryTypes.h"
#include <memory>
#include <vector>
#include <string>
#include <functional>

namespace RecoveryEngine {

// Forward declarations
class DeviceScanner;
class VolumeScanner;
class FileSystemParser;
class DeepScanner;
class FileCarver;
class RecoverySession;
class SessionDatabase;
class ProgressTracker;

/**
 * @class DataRecoveryEngine
 * @brief Main entry point for all recovery operations
 * 
 * This class provides the primary API for consumers to scan devices,
 * list recoverable files, and perform file recovery operations.
 * 
 * Thread Safety: Most operations are thread-safe. Long-running operations
 * support cancellation and pause/resume through the session interface.
 * 
 * Example usage:
 * @code
 *     auto engine = DataRecoveryEngine::create();
 *     
 *     // Scan for connected devices
 *     auto devices = engine->scanDevices();
 *     
 *     // Create a scan session
 *     auto session = engine->createSession("My Recovery");
 *     
 *     // Scan a device
 *     ScanOptions options;
 *     options.scanType = ScanType::QuickScan;
 *     session->scanVolume(devices[0].devicePath, options, progressCallback);
 *     
 *     // Get recoverable files
 *     auto files = session->listFiles();
 *     
 *     // Recover specific files
 *     RecoveryOptions recOpts;
 *     recOpts.destinationPath = "D:\\Recovered";
 *     session->recoverFiles(fileIds, recOpts, recoveryCallback);
 * @endcode
 */
class DataRecoveryEngine {
public:
    virtual ~DataRecoveryEngine() = default;
    
    /**
     * @brief Create a new DataRecoveryEngine instance
     * @param databasePath Optional path for session database. Default uses temp directory.
     * @return Shared pointer to the engine instance
     */
    static std::shared_ptr<DataRecoveryEngine> create(
        const std::string& databasePath = ""
    );
    
    /**
     * @brief Get engine version information
     * @return Version string (e.g., "1.0.0")
     */
    virtual std::string getVersion() const = 0;
    
    /**
     * @brief Get TSK (The Sleuth Kit) version
     * @return TSK version string
     */
    virtual std::string getTSKVersion() const = 0;
    
    // =========================================================================
    // Device Discovery
    // =========================================================================
    
    /**
     * @brief Scan for all connected storage devices
     * 
     * Enumerates physical disks, partitions, removable drives, and mounted volumes.
     * Requires administrator/elevated privileges on Windows.
     * 
     * @return List of discovered devices
     */
    virtual Result<std::vector<DeviceInfo>> scanDevices() = 0;
    
    /**
     * @brief Get detailed information about a specific device
     * @param devicePath Path to the device (e.g., "\\\\.\\PhysicalDrive0")
     * @return Device information or error
     */
    virtual Result<DeviceInfo> getDeviceInfo(const std::string& devicePath) = 0;
    
    /**
     * @brief Scan for volumes/partitions on a device
     * @param devicePath Path to the physical device
     * @return List of volumes found on the device
     */
    virtual Result<std::vector<VolumeInfo>> scanVolumes(const std::string& devicePath) = 0;
    
    /**
     * @brief Detect lost or deleted partitions on a device
     * @param devicePath Path to the physical device
     * @param callback Progress callback
     * @return List of detected partitions (including lost ones)
     */
    virtual Result<std::vector<VolumeInfo>> detectLostPartitions(
        const std::string& devicePath,
        ProgressCallback callback = nullptr
    ) = 0;
    
    // =========================================================================
    // Session Management
    // =========================================================================
    
    /**
     * @brief Create a new recovery session
     * 
     * Sessions track scan state, found files, and allow pause/resume operations.
     * 
     * @param sessionName Human-readable name for the session
     * @return New session instance
     */
    virtual std::shared_ptr<RecoverySession> createSession(
        const std::string& sessionName = ""
    ) = 0;
    
    /**
     * @brief Load an existing session from the database
     * @param sessionId Unique session identifier
     * @return Loaded session or error
     */
    virtual Result<std::shared_ptr<RecoverySession>> loadSession(
        const std::string& sessionId
    ) = 0;
    
    /**
     * @brief List all saved sessions
     * @return List of session states
     */
    virtual Result<std::vector<SessionState>> listSessions() = 0;
    
    /**
     * @brief Delete a saved session
     * @param sessionId Session to delete
     * @return Success or error
     */
    virtual Result<void> deleteSession(const std::string& sessionId) = 0;
    
    // =========================================================================
    // Quick Access Methods (Stateless Operations)
    // =========================================================================
    
    /**
     * @brief Quick scan a volume without creating a persistent session
     * 
     * This is a convenience method for simple recovery scenarios.
     * For complex operations, use createSession() instead.
     * 
     * @param volumePath Path to the volume or device
     * @param options Scan options
     * @param progressCallback Progress updates
     * @param fileCallback Called for each file found
     * @return List of recoverable files
     */
    virtual Result<std::vector<FileMetadata>> quickScan(
        const std::string& volumePath,
        const ScanOptions& options = ScanOptions{},
        ProgressCallback progressCallback = nullptr,
        FileFoundCallback fileCallback = nullptr
    ) = 0;
    
    /**
     * @brief Deep scan (sector-by-sector with carving) without persistent session
     * @param volumePath Path to volume or device
     * @param options Scan options
     * @param progressCallback Progress updates
     * @param fileCallback Called for each file found
     * @return List of recoverable files
     */
    virtual Result<std::vector<FileMetadata>> deepScan(
        const std::string& volumePath,
        const ScanOptions& options = ScanOptions{},
        ProgressCallback progressCallback = nullptr,
        FileFoundCallback fileCallback = nullptr
    ) = 0;
    
    /**
     * @brief Preview file content without recovery
     * 
     * Reads and returns a portion of the file for preview purposes.
     * Useful for displaying thumbnails or text previews in UI.
     * 
     * @param volumePath Path to the source volume
     * @param fileId Internal file ID from scan results
     * @param maxBytes Maximum bytes to read (default 1MB)
     * @return File data buffer
     */
    virtual Result<std::vector<uint8_t>> previewFile(
        const std::string& volumePath,
        uint64_t fileId,
        size_t maxBytes = 1024 * 1024
    ) = 0;
    
    /**
     * @brief Recover a single file to destination
     * @param volumePath Source volume path
     * @param fileId Internal file ID
     * @param destinationPath Full path for recovered file
     * @param callback Progress callback
     * @return Success or error
     */
    virtual Result<void> recoverFile(
        const std::string& volumePath,
        uint64_t fileId,
        const std::string& destinationPath,
        RecoveryProgressCallback callback = nullptr
    ) = 0;
    
    /**
     * @brief Recover multiple files to a destination folder
     * @param volumePath Source volume path
     * @param fileIds List of file IDs to recover
     * @param options Recovery options including destination
     * @param callback Progress callback
     * @return Recovery results
     */
    virtual Result<RecoveryProgress> recoverFiles(
        const std::string& volumePath,
        const std::vector<uint64_t>& fileIds,
        const RecoveryOptions& options,
        RecoveryProgressCallback callback = nullptr
    ) = 0;
    
    /**
     * @brief Recover all files from a scan
     * @param volumePath Source volume path
     * @param options Recovery options including destination and filters
     * @param callback Progress callback
     * @return Recovery results
     */
    virtual Result<RecoveryProgress> recoverAll(
        const std::string& volumePath,
        const RecoveryOptions& options,
        RecoveryProgressCallback callback = nullptr
    ) = 0;
    
    // =========================================================================
    // Metadata and Analysis
    // =========================================================================
    
    /**
     * @brief Get detailed metadata for a specific file
     * @param volumePath Source volume path
     * @param fileId Internal file ID
     * @return File metadata
     */
    virtual Result<FileMetadata> getMetadata(
        const std::string& volumePath,
        uint64_t fileId
    ) = 0;
    
    /**
     * @brief Calculate recovery score for a file
     * 
     * Analyzes file integrity, fragmentation, and overwrite status
     * to determine recovery probability.
     * 
     * @param volumePath Source volume path
     * @param fileId Internal file ID
     * @return Recovery score and confidence
     */
    virtual Result<std::pair<RecoveryScore, float>> getRecoveryScore(
        const std::string& volumePath,
        uint64_t fileId
    ) = 0;
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /**
     * @brief Check if a path points to a valid recoverable source
     * @param path Path to check
     * @return True if valid source
     */
    virtual bool isValidSource(const std::string& path) = 0;
    
    /**
     * @brief Get filesystem type from a volume
     * @param volumePath Path to volume
     * @return Detected filesystem type
     */
    virtual Result<FilesystemType> detectFilesystem(const std::string& volumePath) = 0;
    
    /**
     * @brief Cancel any running operation
     * 
     * This is a global cancel that affects all active operations.
     * For session-specific cancel, use the session's cancel method.
     */
    virtual void cancelAll() = 0;
    
    /**
     * @brief Set global error callback
     * @param callback Error handler
     */
    virtual void setErrorCallback(ErrorCallback callback) = 0;
    
protected:
    DataRecoveryEngine() = default;
};


/**
 * @class RecoverySession
 * @brief Manages a single recovery session with state persistence
 * 
 * A session represents a single recovery workflow. It maintains state
 * for found files, scan progress, and supports pause/resume operations.
 */
class RecoverySession {
public:
    virtual ~RecoverySession() = default;
    
    /**
     * @brief Get unique session identifier
     */
    virtual std::string getSessionId() const = 0;
    
    /**
     * @brief Get session name
     */
    virtual std::string getSessionName() const = 0;
    
    /**
     * @brief Get current session state
     */
    virtual SessionState getState() const = 0;
    
    // =========================================================================
    // Scanning Operations
    // =========================================================================
    
    /**
     * @brief Start scanning a volume
     * @param volumePath Path to volume or device
     * @param options Scan options
     * @param progressCallback Progress updates
     * @param fileCallback Called for each file found (streaming results)
     * @return Success or error
     */
    virtual Result<void> scanVolume(
        const std::string& volumePath,
        const ScanOptions& options = ScanOptions{},
        ProgressCallback progressCallback = nullptr,
        FileFoundCallback fileCallback = nullptr
    ) = 0;
    
    /**
     * @brief Pause the current scan operation
     * @return Success or error
     */
    virtual Result<void> pauseScan() = 0;
    
    /**
     * @brief Resume a paused scan
     * @return Success or error
     */
    virtual Result<void> resumeScan() = 0;
    
    /**
     * @brief Cancel the current scan
     * @return Success or error
     */
    virtual Result<void> cancelScan() = 0;
    
    /**
     * @brief Get current scan progress
     */
    virtual ScanProgress getScanProgress() const = 0;
    
    // =========================================================================
    // Results Access
    // =========================================================================
    
    /**
     * @brief List all found files
     * @param offset Pagination offset
     * @param limit Maximum results (0 = all)
     * @return List of file metadata
     */
    virtual Result<std::vector<FileMetadata>> listFiles(
        size_t offset = 0,
        size_t limit = 0
    ) const = 0;
    
    /**
     * @brief List files by category
     * @param category File category filter
     * @param offset Pagination offset
     * @param limit Maximum results
     * @return Filtered file list
     */
    virtual Result<std::vector<FileMetadata>> listFilesByCategory(
        FileCategory category,
        size_t offset = 0,
        size_t limit = 0
    ) const = 0;
    
    /**
     * @brief List files by extension
     * @param extension File extension (without dot)
     * @param offset Pagination offset
     * @param limit Maximum results
     * @return Filtered file list
     */
    virtual Result<std::vector<FileMetadata>> listFilesByExtension(
        const std::string& extension,
        size_t offset = 0,
        size_t limit = 0
    ) const = 0;
    
    /**
     * @brief Search files by name pattern
     * @param pattern Search pattern (supports * and ? wildcards)
     * @param offset Pagination offset
     * @param limit Maximum results
     * @return Matching files
     */
    virtual Result<std::vector<FileMetadata>> searchFiles(
        const std::string& pattern,
        size_t offset = 0,
        size_t limit = 0
    ) const = 0;
    
    /**
     * @brief Get file counts by category
     * @return Map of category to count
     */
    virtual Result<std::vector<std::pair<FileCategory, size_t>>> getFileCounts() const = 0;
    
    /**
     * @brief Get total count of found files
     */
    virtual size_t getTotalFileCount() const = 0;
    
    /**
     * @brief Get count of deleted files found
     */
    virtual size_t getDeletedFileCount() const = 0;
    
    // =========================================================================
    // Recovery Operations
    // =========================================================================
    
    /**
     * @brief Recover selected files
     * @param fileIds List of file IDs to recover
     * @param options Recovery options
     * @param callback Progress callback
     * @return Recovery results
     */
    virtual Result<RecoveryProgress> recoverFiles(
        const std::vector<uint64_t>& fileIds,
        const RecoveryOptions& options,
        RecoveryProgressCallback callback = nullptr
    ) = 0;
    
    /**
     * @brief Recover all files in session
     * @param options Recovery options
     * @param callback Progress callback
     * @return Recovery results
     */
    virtual Result<RecoveryProgress> recoverAll(
        const RecoveryOptions& options,
        RecoveryProgressCallback callback = nullptr
    ) = 0;
    
    /**
     * @brief Pause recovery operation
     */
    virtual Result<void> pauseRecovery() = 0;
    
    /**
     * @brief Resume recovery operation
     */
    virtual Result<void> resumeRecovery() = 0;
    
    /**
     * @brief Cancel recovery operation
     */
    virtual Result<void> cancelRecovery() = 0;
    
    /**
     * @brief Get current recovery progress
     */
    virtual RecoveryProgress getRecoveryProgress() const = 0;
    
    // =========================================================================
    // Session Persistence
    // =========================================================================
    
    /**
     * @brief Save session state to database
     * @return Success or error
     */
    virtual Result<void> save() = 0;
    
    /**
     * @brief Export session results to file
     * @param path Export file path
     * @param format Export format ("json", "csv", "xml")
     * @return Success or error
     */
    virtual Result<void> exportResults(
        const std::string& path,
        const std::string& format = "json"
    ) const = 0;
    
protected:
    RecoverySession() = default;
};

} // namespace RecoveryEngine

#endif // RECOVERY_ENGINE_H
