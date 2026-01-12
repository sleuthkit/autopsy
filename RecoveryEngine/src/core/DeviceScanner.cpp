/**
 * @file DeviceScanner.cpp
 * @brief Windows device enumeration implementation
 */

#include "DeviceScanner.h"
#include "DiskAccess.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <setupapi.h>
#include <devguid.h>
#include <cfgmgr32.h>
#include <winioctl.h>
#include <ntddscsi.h>
#pragma comment(lib, "setupapi.lib")
#endif

#include <vector>
#include <string>
#include <sstream>
#include <algorithm>

namespace RecoveryEngine {

class DeviceScanner::Impl {
public:
    Impl() = default;
    
#ifdef _WIN32
    Result<std::vector<DeviceInfo>> scanDevices() {
        std::vector<DeviceInfo> devices;
        
        // Enumerate physical drives
        auto physicalResult = enumeratePhysicalDrives();
        if (physicalResult.isSuccess()) {
            for (auto& dev : physicalResult.value) {
                devices.push_back(std::move(dev));
            }
        }
        
        // Enumerate logical volumes
        auto volumeResult = enumerateLogicalVolumes();
        if (volumeResult.isSuccess()) {
            for (auto& vol : volumeResult.value) {
                devices.push_back(std::move(vol));
            }
        }
        
        // Enumerate removable drives
        auto removableResult = enumerateRemovableDrives();
        if (removableResult.isSuccess()) {
            for (auto& dev : removableResult.value) {
                // Check if not already in list
                bool found = false;
                for (const auto& existing : devices) {
                    if (existing.devicePath == dev.devicePath) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    devices.push_back(std::move(dev));
                }
            }
        }
        
        return Result<std::vector<DeviceInfo>>::success(std::move(devices));
    }
    
    Result<DeviceInfo> getDeviceInfo(const std::string& devicePath) {
        DeviceInfo info;
        info.devicePath = devicePath;
        
        HANDLE hDevice = CreateFileA(
            devicePath.c_str(),
            GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            nullptr,
            OPEN_EXISTING,
            0,
            nullptr
        );
        
        if (hDevice == INVALID_HANDLE_VALUE) {
            // Try without read access (for locked volumes)
            hDevice = CreateFileA(
                devicePath.c_str(),
                0,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                nullptr,
                OPEN_EXISTING,
                0,
                nullptr
            );
            
            if (hDevice == INVALID_HANDLE_VALUE) {
                return Result<DeviceInfo>::failure(
                    RecoveryError::DeviceAccessDenied,
                    "Cannot open device: " + DiskAccess::getLastErrorString()
                );
            }
        }
        
        // Get disk geometry
        DISK_GEOMETRY_EX geometry = {};
        DWORD bytesReturned = 0;
        
        if (DeviceIoControl(
            hDevice,
            IOCTL_DISK_GET_DRIVE_GEOMETRY_EX,
            nullptr, 0,
            &geometry, sizeof(geometry),
            &bytesReturned, nullptr
        )) {
            info.totalSize = geometry.DiskSize.QuadPart;
            info.sectorSize = geometry.Geometry.BytesPerSector;
            
            switch (geometry.Geometry.MediaType) {
                case RemovableMedia:
                    info.type = DeviceType::RemovableDrive;
                    info.isRemovable = true;
                    break;
                case FixedMedia:
                    info.type = DeviceType::PhysicalDisk;
                    break;
                default:
                    info.type = DeviceType::Unknown;
            }
        }
        
        // Get storage device descriptor for model/serial
        STORAGE_PROPERTY_QUERY query = {};
        query.PropertyId = StorageDeviceProperty;
        query.QueryType = PropertyStandardQuery;
        
        uint8_t buffer[1024] = {};
        
        if (DeviceIoControl(
            hDevice,
            IOCTL_STORAGE_QUERY_PROPERTY,
            &query, sizeof(query),
            buffer, sizeof(buffer),
            &bytesReturned, nullptr
        )) {
            auto* descriptor = reinterpret_cast<STORAGE_DEVICE_DESCRIPTOR*>(buffer);
            
            if (descriptor->VendorIdOffset) {
                info.model = reinterpret_cast<char*>(buffer + descriptor->VendorIdOffset);
            }
            if (descriptor->ProductIdOffset) {
                if (!info.model.empty()) info.model += " ";
                info.model += reinterpret_cast<char*>(buffer + descriptor->ProductIdOffset);
            }
            if (descriptor->SerialNumberOffset) {
                info.serialNumber = reinterpret_cast<char*>(buffer + descriptor->SerialNumberOffset);
            }
            
            // Trim whitespace
            auto trim = [](std::string& s) {
                s.erase(0, s.find_first_not_of(" \t\n\r"));
                s.erase(s.find_last_not_of(" \t\n\r") + 1);
            };
            trim(info.model);
            trim(info.serialNumber);
            
            info.isRemovable = descriptor->RemovableMedia != 0;
        }
        
        // Generate display name
        if (!info.model.empty()) {
            info.displayName = info.model;
        } else {
            info.displayName = devicePath;
        }
        
        std::stringstream ss;
        ss << info.displayName << " (" << (info.totalSize / (1024 * 1024 * 1024)) << " GB)";
        info.displayName = ss.str();
        
        CloseHandle(hDevice);
        
        return Result<DeviceInfo>::success(std::move(info));
    }
    
    Result<std::vector<std::string>> getPhysicalDrives() {
        std::vector<std::string> drives;
        
        // Try PhysicalDrive0 through PhysicalDrive15
        for (int i = 0; i < 16; ++i) {
            std::string path = "\\\\.\\PhysicalDrive" + std::to_string(i);
            
            HANDLE hDevice = CreateFileA(
                path.c_str(),
                0,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                nullptr,
                OPEN_EXISTING,
                0,
                nullptr
            );
            
            if (hDevice != INVALID_HANDLE_VALUE) {
                drives.push_back(path);
                CloseHandle(hDevice);
            }
        }
        
        return Result<std::vector<std::string>>::success(std::move(drives));
    }
    
    Result<std::vector<std::string>> getLogicalVolumes() {
        std::vector<std::string> volumes;
        
        DWORD drives = GetLogicalDrives();
        
        for (char letter = 'A'; letter <= 'Z'; ++letter) {
            if (drives & (1 << (letter - 'A'))) {
                std::string path = std::string("\\\\.\\") + letter + ":";
                volumes.push_back(path);
            }
        }
        
        return Result<std::vector<std::string>>::success(std::move(volumes));
    }
    
    bool isValidDevice(const std::string& path) {
        HANDLE hDevice = CreateFileA(
            path.c_str(),
            0,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            nullptr,
            OPEN_EXISTING,
            0,
            nullptr
        );
        
        bool valid = (hDevice != INVALID_HANDLE_VALUE);
        if (valid) {
            CloseHandle(hDevice);
        }
        
        return valid;
    }
    
    static bool hasAdminPrivileges() {
        BOOL isAdmin = FALSE;
        PSID adminGroup = nullptr;
        
        SID_IDENTIFIER_AUTHORITY ntAuthority = SECURITY_NT_AUTHORITY;
        
        if (AllocateAndInitializeSid(
            &ntAuthority, 2,
            SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_ADMINS,
            0, 0, 0, 0, 0, 0,
            &adminGroup
        )) {
            CheckTokenMembership(nullptr, adminGroup, &isAdmin);
            FreeSid(adminGroup);
        }
        
        return isAdmin != FALSE;
    }
    
private:
    Result<std::vector<DeviceInfo>> enumeratePhysicalDrives() {
        std::vector<DeviceInfo> devices;
        
        auto drivesResult = getPhysicalDrives();
        if (drivesResult.isError()) {
            return Result<std::vector<DeviceInfo>>::failure(
                drivesResult.error, drivesResult.errorMessage
            );
        }
        
        for (const auto& path : drivesResult.value) {
            auto infoResult = getDeviceInfo(path);
            if (infoResult.isSuccess()) {
                infoResult.value.type = DeviceType::PhysicalDisk;
                devices.push_back(std::move(infoResult.value));
            }
        }
        
        return Result<std::vector<DeviceInfo>>::success(std::move(devices));
    }
    
    Result<std::vector<DeviceInfo>> enumerateLogicalVolumes() {
        std::vector<DeviceInfo> devices;
        
        DWORD drivesMask = GetLogicalDrives();
        
        for (char letter = 'A'; letter <= 'Z'; ++letter) {
            if (!(drivesMask & (1 << (letter - 'A')))) {
                continue;
            }
            
            std::string rootPath = std::string(1, letter) + ":\\";
            UINT driveType = GetDriveTypeA(rootPath.c_str());
            
            // Skip network and CD-ROM drives for now
            if (driveType == DRIVE_REMOTE || driveType == DRIVE_CDROM) {
                continue;
            }
            
            std::string devicePath = std::string("\\\\.\\") + letter + ":";
            auto infoResult = getDeviceInfo(devicePath);
            
            if (infoResult.isSuccess()) {
                DeviceInfo& info = infoResult.value;
                
                // Get volume label
                char volumeName[MAX_PATH + 1] = {};
                char fileSystem[MAX_PATH + 1] = {};
                DWORD serialNumber = 0;
                DWORD maxComponentLen = 0;
                DWORD fsFlags = 0;
                
                if (GetVolumeInformationA(
                    rootPath.c_str(),
                    volumeName, sizeof(volumeName),
                    &serialNumber,
                    &maxComponentLen,
                    &fsFlags,
                    fileSystem, sizeof(fileSystem)
                )) {
                    if (volumeName[0]) {
                        info.displayName = std::string(volumeName) + " (" + letter + ":)";
                    } else {
                        info.displayName = std::string("Local Disk (") + letter + ":)";
                    }
                }
                
                switch (driveType) {
                    case DRIVE_REMOVABLE:
                        info.type = DeviceType::RemovableDrive;
                        info.isRemovable = true;
                        break;
                    case DRIVE_FIXED:
                        info.type = DeviceType::Partition;
                        break;
                    default:
                        info.type = DeviceType::Unknown;
                }
                
                devices.push_back(std::move(info));
            }
        }
        
        return Result<std::vector<DeviceInfo>>::success(std::move(devices));
    }
    
    Result<std::vector<DeviceInfo>> enumerateRemovableDrives() {
        std::vector<DeviceInfo> devices;
        
        HDEVINFO deviceInfoSet = SetupDiGetClassDevs(
            &GUID_DEVCLASS_DISKDRIVE,
            nullptr,
            nullptr,
            DIGCF_PRESENT
        );
        
        if (deviceInfoSet == INVALID_HANDLE_VALUE) {
            return Result<std::vector<DeviceInfo>>::success(std::move(devices));
        }
        
        SP_DEVINFO_DATA deviceInfoData = {};
        deviceInfoData.cbSize = sizeof(SP_DEVINFO_DATA);
        
        for (DWORD i = 0; SetupDiEnumDeviceInfo(deviceInfoSet, i, &deviceInfoData); ++i) {
            char buffer[1024] = {};
            DWORD dataType = 0;
            DWORD actualSize = 0;
            
            // Get device description
            if (SetupDiGetDeviceRegistryPropertyA(
                deviceInfoSet,
                &deviceInfoData,
                SPDRP_DEVICEDESC,
                &dataType,
                reinterpret_cast<PBYTE>(buffer),
                sizeof(buffer),
                &actualSize
            )) {
                DeviceInfo info;
                info.displayName = buffer;
                info.type = DeviceType::RemovableDrive;
                info.isRemovable = true;
                
                // Try to get device path
                // This would need more complex logic to map to PhysicalDrive
                
                devices.push_back(std::move(info));
            }
        }
        
        SetupDiDestroyDeviceInfoList(deviceInfoSet);
        
        return Result<std::vector<DeviceInfo>>::success(std::move(devices));
    }
#else
    // Linux/Mac stubs - not implemented for this Windows-focused build
    Result<std::vector<DeviceInfo>> scanDevices() {
        return Result<std::vector<DeviceInfo>>::failure(
            RecoveryError::UnsupportedFilesystem,
            "Only Windows is supported in this build"
        );
    }
    
    Result<DeviceInfo> getDeviceInfo(const std::string&) {
        return Result<DeviceInfo>::failure(
            RecoveryError::UnsupportedFilesystem,
            "Only Windows is supported in this build"
        );
    }
    
    Result<std::vector<std::string>> getPhysicalDrives() {
        return Result<std::vector<std::string>>::failure(
            RecoveryError::UnsupportedFilesystem,
            "Only Windows is supported in this build"
        );
    }
    
    Result<std::vector<std::string>> getLogicalVolumes() {
        return Result<std::vector<std::string>>::failure(
            RecoveryError::UnsupportedFilesystem,
            "Only Windows is supported in this build"
        );
    }
    
    bool isValidDevice(const std::string&) { return false; }
    static bool hasAdminPrivileges() { return false; }
#endif
};

// DeviceScanner public interface implementation

DeviceScanner::DeviceScanner()
    : pImpl(std::make_unique<Impl>())
{
}

DeviceScanner::~DeviceScanner() = default;

Result<std::vector<DeviceInfo>> DeviceScanner::scanDevices() {
    return pImpl->scanDevices();
}

Result<DeviceInfo> DeviceScanner::getDeviceInfo(const std::string& devicePath) {
    return pImpl->getDeviceInfo(devicePath);
}

Result<std::vector<std::string>> DeviceScanner::getPhysicalDrives() {
    return pImpl->getPhysicalDrives();
}

Result<std::vector<std::string>> DeviceScanner::getLogicalVolumes() {
    return pImpl->getLogicalVolumes();
}

bool DeviceScanner::isValidDevice(const std::string& path) {
    return pImpl->isValidDevice(path);
}

bool DeviceScanner::hasAdminPrivileges() {
    return Impl::hasAdminPrivileges();
}

} // namespace RecoveryEngine
