/**
 * @file VolumeScanner.h
 * @brief Volume and partition scanning functionality
 */

#ifndef VOLUME_SCANNER_H
#define VOLUME_SCANNER_H

#include "RecoveryTypes.h"
#include <vector>
#include <string>
#include <memory>

// Forward declarations for TSK types
struct TSK_VS_INFO;
struct TSK_VS_PART_INFO;
struct TSK_IMG_INFO;

namespace RecoveryEngine {

/**
 * @class VolumeScanner
 * @brief Scans devices for volumes and partitions
 * 
 * Uses The Sleuth Kit to analyze partition tables and detect
 * volumes, including lost/deleted partitions.
 */
class VolumeScanner {
public:
    VolumeScanner();
    ~VolumeScanner();
    
    // Non-copyable
    VolumeScanner(const VolumeScanner&) = delete;
    VolumeScanner& operator=(const VolumeScanner&) = delete;
    
    /**
     * @brief Scan a device for volumes/partitions
     * @param devicePath Path to device
     * @return List of detected volumes
     */
    Result<std::vector<VolumeInfo>> scanVolumes(const std::string& devicePath);
    
    /**
     * @brief Detect lost or deleted partitions
     * 
     * Performs a deeper analysis to find partitions that were
     * deleted or not in the current partition table.
     * 
     * @param devicePath Path to device
     * @param callback Progress callback
     * @return List of all detected partitions
     */
    Result<std::vector<VolumeInfo>> detectLostPartitions(
        const std::string& devicePath,
        ProgressCallback callback = nullptr
    );
    
    /**
     * @brief Get information about a specific volume
     * @param volumePath Path to volume
     * @return Volume information
     */
    Result<VolumeInfo> getVolumeInfo(const std::string& volumePath);
    
    /**
     * @brief Detect filesystem type on a volume
     * @param volumePath Path to volume or offset on device
     * @return Detected filesystem type
     */
    Result<FilesystemType> detectFilesystem(const std::string& volumePath);
    
    /**
     * @brief Detect filesystem at specific offset
     * @param devicePath Device path
     * @param offset Byte offset
     * @return Detected filesystem type
     */
    Result<FilesystemType> detectFilesystemAtOffset(
        const std::string& devicePath,
        uint64_t offset
    );
    
    /**
     * @brief Open a disk image for scanning
     * @param imagePath Path to image file
     * @return Volume information for the image
     */
    Result<VolumeInfo> openDiskImage(const std::string& imagePath);
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
    
    // TSK helper methods
    FilesystemType tskFsTypeToEnum(int tskFsType);
    VolumeInfo partitionToVolumeInfo(TSK_VS_PART_INFO* part, TSK_IMG_INFO* img);
};

} // namespace RecoveryEngine

#endif // VOLUME_SCANNER_H
