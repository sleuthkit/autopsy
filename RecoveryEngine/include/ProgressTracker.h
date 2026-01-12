/**
 * @file ProgressTracker.h
 * @brief Progress tracking and callback management
 */

#ifndef PROGRESS_TRACKER_H
#define PROGRESS_TRACKER_H

#include "RecoveryTypes.h"
#include <atomic>
#include <mutex>
#include <chrono>
#include <functional>

namespace RecoveryEngine {

/**
 * @class ProgressTracker
 * @brief Tracks operation progress and manages callbacks
 * 
 * Thread-safe progress tracking with support for pause/resume,
 * time estimation, and rate-limited callback invocation.
 */
class ProgressTracker {
public:
    ProgressTracker();
    ~ProgressTracker();
    
    // Non-copyable but movable
    ProgressTracker(const ProgressTracker&) = delete;
    ProgressTracker& operator=(const ProgressTracker&) = delete;
    
    /**
     * @brief Reset tracker for new operation
     * @param totalBytes Total bytes to process
     * @param totalItems Total items to process
     * @param scanType Type of scan operation
     */
    void reset(uint64_t totalBytes, uint64_t totalItems, ScanType scanType);
    
    /**
     * @brief Update bytes processed
     * @param bytes Bytes processed since last update
     */
    void updateBytes(uint64_t bytes);
    
    /**
     * @brief Update items processed
     * @param items Items processed since last update
     */
    void updateItems(uint64_t items);
    
    /**
     * @brief Increment files found counter
     * @param deleted True if file is deleted
     * @param carved True if file was carved
     */
    void incrementFilesFound(bool deleted = false, bool carved = false);
    
    /**
     * @brief Set current operation description
     * @param operation Description of current operation
     */
    void setCurrentOperation(const std::string& operation);
    
    /**
     * @brief Set current path being processed
     * @param path Current file/folder path
     */
    void setCurrentPath(const std::string& path);
    
    /**
     * @brief Get current scan progress
     * @return Current progress snapshot
     */
    ScanProgress getProgress() const;
    
    /**
     * @brief Set progress callback
     * @param callback Callback function
     * @param minIntervalMs Minimum milliseconds between callbacks
     */
    void setCallback(ProgressCallback callback, uint32_t minIntervalMs = 100);
    
    /**
     * @brief Trigger callback if interval elapsed
     */
    void notifyIfNeeded();
    
    /**
     * @brief Force callback notification
     */
    void notifyNow();
    
    /**
     * @brief Set operation status
     * @param status New status
     */
    void setStatus(OperationStatus status);
    
    /**
     * @brief Get current status
     */
    OperationStatus getStatus() const;
    
    /**
     * @brief Request pause
     */
    void requestPause();
    
    /**
     * @brief Request resume
     */
    void requestResume();
    
    /**
     * @brief Request cancellation
     */
    void requestCancel();
    
    /**
     * @brief Check if pause requested
     */
    bool isPauseRequested() const;
    
    /**
     * @brief Check if cancel requested
     */
    bool isCancelRequested() const;
    
    /**
     * @brief Wait while paused
     * 
     * Blocks until either resumed or cancelled.
     * 
     * @return True if resumed, false if cancelled
     */
    bool waitWhilePaused();
    
    /**
     * @brief Get elapsed time
     */
    std::chrono::seconds getElapsedTime() const;
    
    /**
     * @brief Get estimated time remaining
     */
    std::chrono::seconds getEstimatedTimeRemaining() const;
    
    /**
     * @brief Get processing rate in bytes/second
     */
    double getBytesPerSecond() const;
    
private:
    mutable std::mutex m_mutex;
    std::condition_variable m_pauseCondition;
    
    std::atomic<OperationStatus> m_status{OperationStatus::NotStarted};
    std::atomic<bool> m_pauseRequested{false};
    std::atomic<bool> m_cancelRequested{false};
    
    std::atomic<uint64_t> m_totalBytes{0};
    std::atomic<uint64_t> m_scannedBytes{0};
    std::atomic<uint64_t> m_totalItems{0};
    std::atomic<uint64_t> m_processedItems{0};
    std::atomic<uint64_t> m_filesFound{0};
    std::atomic<uint64_t> m_deletedFilesFound{0};
    std::atomic<uint64_t> m_carvedFilesFound{0};
    
    ScanType m_scanType{ScanType::QuickScan};
    std::string m_currentOperation;
    std::string m_currentPath;
    
    ProgressCallback m_callback;
    uint32_t m_minIntervalMs{100};
    std::chrono::steady_clock::time_point m_lastCallbackTime;
    std::chrono::steady_clock::time_point m_startTime;
    
    // Rate calculation
    static constexpr size_t RATE_SAMPLES = 10;
    uint64_t m_byteSamples[RATE_SAMPLES] = {0};
    std::chrono::steady_clock::time_point m_sampleTimes[RATE_SAMPLES];
    size_t m_sampleIndex{0};
    
    void updateRateSample();
};

/**
 * @class RecoveryProgressTracker
 * @brief Tracks file recovery progress specifically
 */
class RecoveryProgressTracker {
public:
    RecoveryProgressTracker();
    ~RecoveryProgressTracker();
    
    /**
     * @brief Reset for new recovery operation
     * @param totalFiles Total files to recover
     * @param totalBytes Total bytes to recover
     */
    void reset(uint64_t totalFiles, uint64_t totalBytes);
    
    /**
     * @brief Mark file as recovered
     * @param fileSize Size of recovered file
     */
    void markFileRecovered(uint64_t fileSize);
    
    /**
     * @brief Mark file as failed
     * @param error Error that occurred
     * @param message Error message
     */
    void markFileFailed(RecoveryError error, const std::string& message);
    
    /**
     * @brief Mark file as skipped
     */
    void markFileSkipped();
    
    /**
     * @brief Update bytes recovered (for streaming)
     * @param bytes Bytes recovered
     */
    void updateBytesRecovered(uint64_t bytes);
    
    /**
     * @brief Set current file being recovered
     * @param fileName File name
     */
    void setCurrentFile(const std::string& fileName);
    
    /**
     * @brief Get current progress
     */
    RecoveryProgress getProgress() const;
    
    /**
     * @brief Set callback
     */
    void setCallback(RecoveryProgressCallback callback, uint32_t minIntervalMs = 100);
    
    /**
     * @brief Notify callback if interval elapsed
     */
    void notifyIfNeeded();
    
    /**
     * @brief Set status
     */
    void setStatus(OperationStatus status);
    
    /**
     * @brief Get status
     */
    OperationStatus getStatus() const;
    
    /**
     * @brief Request pause
     */
    void requestPause();
    
    /**
     * @brief Request resume
     */
    void requestResume();
    
    /**
     * @brief Request cancel
     */
    void requestCancel();
    
    /**
     * @brief Check if cancelled
     */
    bool isCancelled() const;
    
    /**
     * @brief Wait while paused
     */
    bool waitWhilePaused();
    
private:
    mutable std::mutex m_mutex;
    std::condition_variable m_pauseCondition;
    
    std::atomic<OperationStatus> m_status{OperationStatus::NotStarted};
    std::atomic<bool> m_cancelRequested{false};
    std::atomic<bool> m_pauseRequested{false};
    
    std::atomic<uint64_t> m_totalFiles{0};
    std::atomic<uint64_t> m_recoveredFiles{0};
    std::atomic<uint64_t> m_failedFiles{0};
    std::atomic<uint64_t> m_skippedFiles{0};
    std::atomic<uint64_t> m_totalBytes{0};
    std::atomic<uint64_t> m_recoveredBytes{0};
    
    std::string m_currentFile;
    RecoveryError m_lastError{RecoveryError::None};
    std::string m_lastErrorMessage;
    
    RecoveryProgressCallback m_callback;
    uint32_t m_minIntervalMs{100};
    std::chrono::steady_clock::time_point m_lastCallbackTime;
    std::chrono::steady_clock::time_point m_startTime;
};

} // namespace RecoveryEngine

#endif // PROGRESS_TRACKER_H
