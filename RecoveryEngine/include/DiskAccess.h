/**
 * @file DiskAccess.h
 * @brief Windows raw disk access utilities
 */

#ifndef DISK_ACCESS_H
#define DISK_ACCESS_H

#include "RecoveryTypes.h"
#include <string>
#include <vector>
#include <memory>
#include <cstdint>

#ifdef _WIN32
#include <windows.h>
#endif

namespace RecoveryEngine {

/**
 * @class DiskAccess
 * @brief Provides raw disk access on Windows
 * 
 * Handles opening physical drives, reading sectors, and managing
 * disk I/O with proper alignment and buffering.
 */
class DiskAccess {
public:
    DiskAccess();
    ~DiskAccess();
    
    // Non-copyable
    DiskAccess(const DiskAccess&) = delete;
    DiskAccess& operator=(const DiskAccess&) = delete;
    
    /**
     * @brief Open a disk or volume for reading
     * @param path Device path (e.g., "\\\\.\\PhysicalDrive0" or "\\\\.\\C:")
     * @param writeAccess Request write access (requires admin)
     * @return Success or error
     */
    Result<void> open(const std::string& path, bool writeAccess = false);
    
    /**
     * @brief Close the disk
     */
    void close();
    
    /**
     * @brief Check if disk is open
     */
    bool isOpen() const;
    
    /**
     * @brief Get device path
     */
    std::string getPath() const;
    
    /**
     * @brief Get disk geometry information
     * @return Device info structure
     */
    Result<DeviceInfo> getGeometry() const;
    
    /**
     * @brief Get sector size
     */
    uint32_t getSectorSize() const;
    
    /**
     * @brief Get total size in bytes
     */
    uint64_t getTotalSize() const;
    
    /**
     * @brief Get total sector count
     */
    uint64_t getSectorCount() const;
    
    /**
     * @brief Read sectors from disk
     * @param sectorOffset First sector to read
     * @param sectorCount Number of sectors to read
     * @param buffer Output buffer (must be aligned and sized for sector reads)
     * @return Bytes read or error
     */
    Result<size_t> readSectors(
        uint64_t sectorOffset,
        uint32_t sectorCount,
        uint8_t* buffer
    );
    
    /**
     * @brief Read bytes from disk (handles alignment)
     * @param byteOffset Byte offset
     * @param length Bytes to read
     * @param buffer Output buffer
     * @return Bytes read or error
     */
    Result<size_t> read(
        uint64_t byteOffset,
        size_t length,
        uint8_t* buffer
    );
    
    /**
     * @brief Read into vector (convenience method)
     * @param byteOffset Byte offset
     * @param length Bytes to read
     * @return Data buffer
     */
    Result<std::vector<uint8_t>> read(uint64_t byteOffset, size_t length);
    
    /**
     * @brief Write sectors to disk
     * @param sectorOffset First sector to write
     * @param sectorCount Number of sectors
     * @param buffer Data to write
     * @return Bytes written or error
     */
    Result<size_t> writeSectors(
        uint64_t sectorOffset,
        uint32_t sectorCount,
        const uint8_t* buffer
    );
    
    /**
     * @brief Seek to position
     * @param byteOffset Byte offset
     * @return Success or error
     */
    Result<void> seek(uint64_t byteOffset);
    
    /**
     * @brief Get current position
     */
    uint64_t getPosition() const;
    
    /**
     * @brief Lock the volume (prevent Windows from mounting/modifying)
     * @return Success or error
     */
    Result<void> lock();
    
    /**
     * @brief Unlock the volume
     */
    void unlock();
    
    /**
     * @brief Dismount the volume (for recovery operations)
     * @return Success or error
     */
    Result<void> dismount();
    
    /**
     * @brief Check if volume is mounted
     */
    bool isMounted() const;
    
    // Static utility methods
    
    /**
     * @brief Convert drive letter to device path
     * @param driveLetter Drive letter (e.g., 'C')
     * @return Device path (e.g., "\\\\.\\C:")
     */
    static std::string driveLetterToPath(char driveLetter);
    
    /**
     * @brief Convert volume path to physical disk path
     * @param volumePath Volume path
     * @return Physical disk paths that contain this volume
     */
    static Result<std::vector<std::string>> getPhysicalDisksForVolume(
        const std::string& volumePath
    );
    
    /**
     * @brief Allocate sector-aligned buffer
     * @param size Size in bytes
     * @param sectorSize Sector size for alignment
     * @return Aligned buffer (use alignedFree to release)
     */
    static uint8_t* alignedAlloc(size_t size, size_t sectorSize = 512);
    
    /**
     * @brief Free aligned buffer
     * @param ptr Buffer pointer
     */
    static void alignedFree(uint8_t* ptr);
    
    /**
     * @brief Check if running as administrator
     */
    static bool isAdministrator();
    
    /**
     * @brief Get last Windows error as string
     */
    static std::string getLastErrorString();
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

/**
 * @class AlignedBuffer
 * @brief RAII wrapper for sector-aligned buffer
 */
class AlignedBuffer {
public:
    /**
     * @brief Allocate aligned buffer
     * @param size Size in bytes
     * @param alignment Alignment (default 512 for sector alignment)
     */
    explicit AlignedBuffer(size_t size, size_t alignment = 512);
    ~AlignedBuffer();
    
    // Non-copyable but movable
    AlignedBuffer(const AlignedBuffer&) = delete;
    AlignedBuffer& operator=(const AlignedBuffer&) = delete;
    AlignedBuffer(AlignedBuffer&& other) noexcept;
    AlignedBuffer& operator=(AlignedBuffer&& other) noexcept;
    
    /**
     * @brief Get buffer pointer
     */
    uint8_t* data() { return m_buffer; }
    const uint8_t* data() const { return m_buffer; }
    
    /**
     * @brief Get buffer size
     */
    size_t size() const { return m_size; }
    
    /**
     * @brief Check if valid
     */
    bool isValid() const { return m_buffer != nullptr; }
    
    /**
     * @brief Array access
     */
    uint8_t& operator[](size_t index) { return m_buffer[index]; }
    const uint8_t& operator[](size_t index) const { return m_buffer[index]; }
    
private:
    uint8_t* m_buffer{nullptr};
    size_t m_size{0};
};

} // namespace RecoveryEngine

#endif // DISK_ACCESS_H
