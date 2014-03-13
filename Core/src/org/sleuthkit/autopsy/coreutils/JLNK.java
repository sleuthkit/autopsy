/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LnkEnums.CommonNetworkRelativeLinkFlags;
import org.sleuthkit.autopsy.coreutils.LnkEnums.DriveType;
import org.sleuthkit.autopsy.coreutils.LnkEnums.FileAttributesFlags;
import org.sleuthkit.autopsy.coreutils.LnkEnums.LinkFlags;
import org.sleuthkit.autopsy.coreutils.LnkEnums.NetworkProviderType;

/**
 *
 * @author dick
 */
 public class JLNK {
    private int header;
    private byte[] linkClassIdentifier;
    private List<LinkFlags> linkFlags;
    private List<FileAttributesFlags> fileAttributesFlags;
    private long crtime;
    private long atime;
    private long mtime;
    private int fileSize;
    private int iconIndex;
    private int showCommand;
    private short hotKey;
    
    private List<String> linkTargetIdList;
    
    private boolean hasUnicodeLocalBaseAndCommonSuffixOffset;
    private String localBasePath;
    private String commonPathSuffix;
    private String localBasePathUnicode;
    private String commonPathSuffixUnicode;
    
    private String name;
    private String relativePath;
    private String workingDir;
    private String arguments;
    private String iconLocation;
    
    private int driveSerialNumber;
    private DriveType driveType;
    private String volumeLabel;
    
    private List<CommonNetworkRelativeLinkFlags> commonNetworkRelativeListFlags;
    private NetworkProviderType networkProviderType;
    private boolean unicodeNetAndDeviceName;
    private String netName;
    private String netNameUnicode;
    private String deviceName;
    private String deviceNameUnicode;

    public JLNK(int header, byte[] linkClassIdentifier, int linkFlags,
            int fileAttributesFlags, long crtime, long atime,
            long mtime, int fileSize, int iconIndex, int showCommand, short hotKey,
            List<String> linkTargetIdList,
            boolean hasUnicodeLocalBaseAndCommonSuffixOffset,
            String localBasePath, String commonPathSuffix, String localBasePathUnicode,
            String commonPathSuffixUnicode, String name, String relativePath,
            String workingDir, String arguments, String iconLocation, int driveSerialNumber,
            DriveType driveType, String volumeLabel,
            int commonNetworkRelativeListFlags,
            NetworkProviderType networkProviderType, boolean unicodeNetAndDeviceName,
            String netName, String netNameUnicode, String deviceName,
            String deviceNameUnicode) {
        this.header = header;
        this.linkClassIdentifier = linkClassIdentifier;
        this.linkFlags = new ArrayList<LinkFlags>();
        for(LnkEnums.LinkFlags enumVal : LnkEnums.LinkFlags.values()) {
            if((linkFlags & enumVal.getFlag()) == enumVal.getFlag()) {
                this.linkFlags.add(enumVal);
            }
        }
        this.fileAttributesFlags = new ArrayList<FileAttributesFlags>();
        for(LnkEnums.FileAttributesFlags enumVal : LnkEnums.FileAttributesFlags.values()) {
            if((fileAttributesFlags & enumVal.getFlag()) == enumVal.getFlag()) {
                this.fileAttributesFlags.add(enumVal);
            }
        }
        this.crtime = crtime;
        this.atime = atime;
        this.mtime = mtime;
        this.fileSize = fileSize;
        this.iconIndex = iconIndex;
        this.showCommand = showCommand;
        this.hotKey = hotKey;
        this.linkTargetIdList = linkTargetIdList;
        this.hasUnicodeLocalBaseAndCommonSuffixOffset = hasUnicodeLocalBaseAndCommonSuffixOffset;
        this.localBasePath = localBasePath;
        this.commonPathSuffix = commonPathSuffix;
        this.localBasePathUnicode = localBasePathUnicode;
        this.commonPathSuffixUnicode = commonPathSuffixUnicode;
        this.name = name;
        this.relativePath = relativePath;
        this.workingDir = workingDir;
        this.arguments = arguments;
        this.iconLocation = iconLocation;
        this.driveSerialNumber = driveSerialNumber;
        this.driveType = driveType;
        this.volumeLabel = volumeLabel;
        this.commonNetworkRelativeListFlags = new ArrayList<CommonNetworkRelativeLinkFlags>();
        for(LnkEnums.CommonNetworkRelativeLinkFlags enumVal : LnkEnums.CommonNetworkRelativeLinkFlags.values()) {
            if((commonNetworkRelativeListFlags & enumVal.getFlag()) == enumVal.getFlag()) {
                this.commonNetworkRelativeListFlags.add(enumVal);
            }
        }
        this.networkProviderType = networkProviderType;
        this.unicodeNetAndDeviceName = unicodeNetAndDeviceName;
        this.netName = netName;
        this.netNameUnicode = netNameUnicode;
        this.deviceName = deviceName;
        this.deviceNameUnicode = deviceNameUnicode;
    }

    public String getArguments() {
        return arguments;
    }

    public List<CommonNetworkRelativeLinkFlags> getCommonNetworkRelativeListFlags() {
        return commonNetworkRelativeListFlags;
    }

    public String getCommonPathSuffix() {
        return commonPathSuffix;
    }

    public String getCommonPathSuffixUnicode() {
        return commonPathSuffixUnicode;
    }

    public long getCrtime() {
        return crtime;
    }

    public long getCtime() {
        return atime;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceNameUnicode() {
        return deviceNameUnicode;
    }

    public int getDriveSerialNumber() {
        return driveSerialNumber;
    }

    public DriveType getDriveType() {
        return driveType;
    }

    public List<FileAttributesFlags> getFileAttributesFlags() {
        return fileAttributesFlags;
    }

    public int getFileSize() {
        return fileSize;
    }

    public boolean isHasUnicodeLocalBaseAndCommonSuffixOffset() {
        return hasUnicodeLocalBaseAndCommonSuffixOffset;
    }

    public int getHeader() {
        return header;
    }

    public short getHotKey() {
        return hotKey;
    }
    
    public List<String> getLinkTargetIdList() {
        return linkTargetIdList;
    }

    public int getIconIndex() {
        return iconIndex;
    }

    public String getIconLocation() {
        return iconLocation;
    }

    public byte[] getLinkClassIdentifier() {
        return linkClassIdentifier;
    }

    public List<LinkFlags> getLinkFlags() {
        return linkFlags;
    }

    public String getLocalBasePath() {
        return localBasePath;
    }

    public String getLocalBasePathUnicode() {
        return localBasePathUnicode;
    }

    public long getMtime() {
        return mtime;
    }

    public String getName() {
        return name;
    }

    public String getNetName() {
        return netName;
    }

    public String getNetNameUnicode() {
        return netNameUnicode;
    }

    public NetworkProviderType getNetworkProviderType() {
        return networkProviderType;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public int getShowCommand() {
        return showCommand;
    }

    public boolean isUnicodeNetAndDeviceName() {
        return unicodeNetAndDeviceName;
    }

    public String getVolumeLabel() {
        return volumeLabel;
    }

    public String getWorkingDir() {
        return workingDir;
    }
    
    
    
    public String getBestPath() {
        if(localBasePathUnicode != null && !localBasePathUnicode.isEmpty()) {
            if(commonPathSuffixUnicode != null) {
                return localBasePathUnicode + commonPathSuffixUnicode;
            } else if(commonPathSuffix != null) {
                return localBasePathUnicode + commonPathSuffix;
            }
        } else if(localBasePath != null && !localBasePath.isEmpty()) {
            if(commonPathSuffixUnicode != null) {
                return localBasePath + commonPathSuffixUnicode;
            } else if(commonPathSuffix != null) {
                return localBasePath + commonPathSuffix;
            }
        } else if(netNameUnicode != null && !netNameUnicode.isEmpty()) {
            if(commonPathSuffixUnicode != null && !commonPathSuffixUnicode.isEmpty()) {
                return netNameUnicode + "\\" + commonPathSuffixUnicode;
            } else if(commonPathSuffix != null && !commonPathSuffix.isEmpty()) {
                return netNameUnicode + "\\" + commonPathSuffix;
            }
        } else if(netName != null && !netName.isEmpty()) {
            if(commonPathSuffixUnicode != null && !commonPathSuffixUnicode.isEmpty()) {
                return netName + "\\" + commonPathSuffixUnicode;
            } else if(commonPathSuffix != null && !commonPathSuffix.isEmpty()) {
                return netName + "\\" + commonPathSuffix;
            }
        } else if(linkTargetIdList != null && !linkTargetIdList.isEmpty()) {
            String ret = "";
            for(String s : linkTargetIdList) {
                ret += s;
            }
            return ret;
        }
        return NbBundle.getMessage(this.getClass(), "JLNK.noPrefPath.text");
    }
   
    public String getBestName() {
        return new File(getBestPath()).getName(); // not very cross platform :(
    }
    
}
