/**
 * @file DeviceScanner.h
 * @brief Device enumeration and information retrieval
 */

#ifndef DEVICE_SCANNER_H
#define DEVICE_SCANNER_H

#include "RecoveryTypes.h"
#include <vector>
#include <string>
#include <memory>

namespace RecoveryEngine {

/**
 * @class DeviceScanner
 * @brief Enumerates storage devices on the system
 * 
 * Provides platform-specific device enumeration for Windows.
 * Discovers physical disks, partitions, removable media, and optical drives.
 */
class DeviceScanner {
public:
    DeviceScanner();
    ~DeviceScanner();
    
    // Non-copyable
    DeviceScanner(const DeviceScanner&) = delete;
    DeviceScanner& operator=(const DeviceScanner&) = delete;
    
    /**
     * @brief Scan for all connected storage devices
     * @return List of discovered devices
     */
    Result<std::vector<DeviceInfo>> scanDevices();
    
    /**
     * @brief Get information about a specific device
     * @param devicePath Device path (e.g., "\\\\.\\PhysicalDrive0")
     * @return Device information
     */
    Result<DeviceInfo> getDeviceInfo(const std::string& devicePath);
    
    /**
     * @brief Get list of physical disk paths
     * @return List of physical disk device paths
     */
    Result<std::vector<std::string>> getPhysicalDrives();
    
    /**
     * @brief Get list of logical volumes
     * @return List of volume device paths
     */
    Result<std::vector<std::string>> getLogicalVolumes();
    
    /**
     * @brief Check if path is a valid device
     * @param path Path to check
     * @return True if valid device path
     */
    bool isValidDevice(const std::string& path);
    
    /**
     * @brief Check if running with administrator privileges
     * @return True if elevated
     */
    static bool hasAdminPrivileges();
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

} // namespace RecoveryEngine

#endif // DEVICE_SCANNER_H
