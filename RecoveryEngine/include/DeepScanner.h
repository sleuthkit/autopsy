/**
 * @file DeepScanner.h
 * @brief Deep/sector-level scanning with file carving
 */

#ifndef DEEP_SCANNER_H
#define DEEP_SCANNER_H

#include "RecoveryTypes.h"
#include <vector>
#include <string>
#include <memory>

namespace RecoveryEngine {

class FileCarver;

/**
 * @class DeepScanner
 * @brief Performs sector-by-sector scanning for file recovery
 * 
 * Deep scanning reads every sector looking for file signatures,
 * enabling recovery of files from formatted drives or corrupted filesystems.
 */
class DeepScanner {
public:
    explicit DeepScanner(const std::string& devicePath);
    ~DeepScanner();
    
    // Non-copyable
    DeepScanner(const DeepScanner&) = delete;
    DeepScanner& operator=(const DeepScanner&) = delete;
    
    /**
     * @brief Start deep scan operation
     * @param options Scan options
     * @param callback Called for each file found
     * @param progressCallback Progress updates
     * @return Total files carved
     */
    Result<size_t> scan(
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback = nullptr
    );
    
    /**
     * @brief Scan specific sector range
     * @param startSector First sector
     * @param endSector Last sector (inclusive)
     * @param options Scan options
     * @param callback Called for each file found
     * @param progressCallback Progress updates
     * @return Files found in range
     */
    Result<size_t> scanSectorRange(
        uint64_t startSector,
        uint64_t endSector,
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback = nullptr
    );
    
    /**
     * @brief Scan only unallocated space
     * 
     * Requires filesystem information to determine unallocated regions.
     * 
     * @param options Scan options
     * @param callback Called for each file found
     * @param progressCallback Progress updates
     * @return Files carved from unallocated space
     */
    Result<size_t> scanUnallocatedSpace(
        const ScanOptions& options,
        FileFoundCallback callback,
        ProgressCallback progressCallback = nullptr
    );
    
    /**
     * @brief Pause scanning
     */
    Result<void> pause();
    
    /**
     * @brief Resume scanning
     */
    Result<void> resume();
    
    /**
     * @brief Cancel scanning
     */
    void cancel();
    
    /**
     * @brief Get current progress
     */
    ScanProgress getProgress() const;
    
    /**
     * @brief Check if scanning is running
     */
    bool isRunning() const;
    
    /**
     * @brief Set read buffer size
     * @param sizeKB Buffer size in KB
     */
    void setBufferSize(uint32_t sizeKB);
    
    /**
     * @brief Set maximum memory usage
     * @param sizeMB Memory limit in MB
     */
    void setMaxMemory(uint64_t sizeMB);
    
    /**
     * @brief Add file carver
     * @param carver Custom file carver
     */
    void addCarver(std::shared_ptr<FileCarver> carver);
    
    /**
     * @brief Get device sector size
     */
    uint32_t getSectorSize() const;
    
    /**
     * @brief Get total sectors
     */
    uint64_t getTotalSectors() const;
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

} // namespace RecoveryEngine

#endif // DEEP_SCANNER_H
