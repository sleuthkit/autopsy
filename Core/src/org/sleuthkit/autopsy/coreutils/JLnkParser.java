/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.LnkEnums.CommonCLSIDS;
import org.sleuthkit.autopsy.coreutils.LnkEnums.DriveType;
import org.sleuthkit.autopsy.coreutils.LnkEnums.NetworkProviderType;

/**
 * Parse lnk files using documentation from:
 * http://msdn.microsoft.com/en-us/library/dd871305(v=prot.13).aspx
 * http://msdn.microsoft.com/en-us/library/windows/desktop/cc144090(v=vs.85).aspx#unknown_74413
 * http://blog.0x01000000.org/2010/08/10/lnk-parsing-youre-doing-it-wrong-i/
 */
public class JLnkParser {

    private byte[] content;
    private static final Logger logger = Logger.getLogger(JLnkParser.class.getName());

    public JLnkParser(InputStream is, int length) {
        content = new byte[length];
        try {
            is.read(content);
        } catch (IOException ex) {
            Logger.getLogger(JLnkParser.class.getName()).log(Level.WARNING, "Error reading input stream", ex); //NON-NLS
        }
    }

    public JLNK parse() throws JLnkParserException {
        try {
            ByteBuffer bb = ByteBuffer.wrap(content);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            int header = bb.getInt();
            ByteBuffer linkClassIdentifier = bb.get(new byte[16]);
            int linkFlags = bb.getInt();
            int fileAttributes = bb.getInt();
            long crtime = bb.getLong();
            long atime = bb.getLong();
            long mtime = bb.getLong();
            int fileSize = bb.getInt();
            int iconIndex = bb.getInt();
            int showCommand = bb.getInt();
            short hotkey = bb.getShort();
            bb.get(new byte[10]); // reserved (???)
            List<String> linkTargetIdList = new ArrayList<>();
            if ((linkFlags & LnkEnums.LinkFlags.HasLinkTargetIDList.getFlag())
                    == LnkEnums.LinkFlags.HasLinkTargetIDList.getFlag()) {
                int idListSize = bb.getShort();
                int bytesRead = 0;
                List<byte[]> linkTargetIdListBytes = new ArrayList<>();
                while (true) {
                    short itemIdSize = bb.getShort();
                    if (itemIdSize == 0) {
                        bytesRead += 2; // two null bytes to terminate id list
                        break;
                    }
                    byte[] theArray = new byte[itemIdSize - 2];
                    bb.get(theArray); // an idlist data object
                    linkTargetIdListBytes.add(theArray);
                    bytesRead += itemIdSize;
                }
                linkTargetIdList = parseLinkTargetIdList(linkTargetIdListBytes);
            }
            boolean hasUnicodeLocalBaseAndCommonSuffixOffset = false;
            String localBasePath = null;
            String commonPathSuffix = null;
            String localBasePathUnicode = null;
            String commonPathSuffixUnicode = null;
            int driveSerialNumber = -1;
            DriveType driveType = null;
            String volumeLabel = null;
            int commonNetworkRelativeLinkFlags = -1;
            NetworkProviderType networkProviderType = null;
            boolean unicodeNetAndDeviceName = false;
            String netName = null;
            String netNameUnicode = null;
            String deviceName = null;
            String deviceNameUnicode = null;

            if ((linkFlags & LnkEnums.LinkFlags.HasLinkInfo.getFlag())
                    == LnkEnums.LinkFlags.HasLinkInfo.getFlag()) {
                int startOfLinkInfo = bb.position();
                int linkInfoSize = bb.getInt();
                int linkInfoHeaderSize = bb.getInt();
                hasUnicodeLocalBaseAndCommonSuffixOffset = linkInfoHeaderSize >= 0x24;
                int linkInfoFlags = bb.getInt();
                int volumeIdOffset = bb.getInt();
                int localBasePathOffset = bb.getInt();
                int commonNetworkRelativeLinkOffset = bb.getInt();
                int commonPathSuffixOffset = bb.getInt();
                int localBasePathOffsetUnicode = 0;
                int commonPathSuffixOffsetUnicode = 0;
                if (hasUnicodeLocalBaseAndCommonSuffixOffset) {
                    localBasePathOffsetUnicode = bb.getInt();
                    commonPathSuffixOffsetUnicode = bb.getInt();
                }
                if ((linkInfoFlags & LnkEnums.LinkInfoFlags.VolumeIDAndLocalBasePath.getFlag())
                        == LnkEnums.LinkInfoFlags.VolumeIDAndLocalBasePath.getFlag()) {
                    bb.position(startOfLinkInfo + volumeIdOffset);
                    int volumeIdSize = bb.getInt();
                    driveType = DriveType.valueOf(bb.getInt());
                    driveSerialNumber = bb.getInt();
                    int volumeLabelOffset = bb.getInt();
                    if (volumeLabelOffset != 0x14) {
                        volumeLabel = parseString(startOfLinkInfo + volumeIdOffset + volumeLabelOffset, false, volumeIdSize - 0x10);
                    } else {
                        int volumeLabelOffsetUnicode = bb.getInt();
                        volumeLabel = parseString(startOfLinkInfo + volumeIdOffset + volumeLabelOffsetUnicode, false, volumeIdSize - 0x14);
                    }
                    localBasePath = parseLocalBasePath(startOfLinkInfo + localBasePathOffset, false);
                }
                if ((linkInfoFlags & LnkEnums.LinkInfoFlags.CommonNetworkRelativeLinkAndPathSuffix.getFlag())
                        == LnkEnums.LinkInfoFlags.CommonNetworkRelativeLinkAndPathSuffix.getFlag()) {
                    bb.position(startOfLinkInfo + commonNetworkRelativeLinkOffset);
                    int commonNetworkRelativeLinkSize = bb.getInt();
                    commonNetworkRelativeLinkFlags = bb.getInt();
                    int netNameOffset = bb.getInt();
                    unicodeNetAndDeviceName = netNameOffset > 0x14;
                    int deviceNameOffset = bb.getInt();
                    int netType = bb.getInt();
                    int netNameOffsetUnicode = 0;
                    int deviceNameOffsetUnicode = 0;
                    if (unicodeNetAndDeviceName) {
                        netNameOffsetUnicode = bb.getInt();
                        deviceNameOffsetUnicode = bb.getInt();
                    }
                    netName = parseNetName(startOfLinkInfo + commonNetworkRelativeLinkOffset + netNameOffset, false);
                    if (unicodeNetAndDeviceName) {
                        netNameUnicode = parseNetName(startOfLinkInfo + commonNetworkRelativeLinkOffset + netNameOffsetUnicode, true);
                    }
                    if ((commonNetworkRelativeLinkFlags & LnkEnums.CommonNetworkRelativeLinkFlags.ValidNetType.getFlag())
                            == LnkEnums.CommonNetworkRelativeLinkFlags.ValidNetType.getFlag()) {
                        networkProviderType = LnkEnums.NetworkProviderType.valueOf(netType);
                    }
                    if ((commonNetworkRelativeLinkFlags & LnkEnums.CommonNetworkRelativeLinkFlags.ValidDevice.getFlag())
                            == LnkEnums.CommonNetworkRelativeLinkFlags.ValidDevice.getFlag()) {
                        deviceName = parseDeviceName(startOfLinkInfo + commonNetworkRelativeLinkOffset + deviceNameOffset, false);
                        if (unicodeNetAndDeviceName) {
                            deviceNameUnicode = parseDeviceName(startOfLinkInfo + commonNetworkRelativeLinkOffset + deviceNameOffsetUnicode, true);
                        }
                    }
                }
                commonPathSuffix = parseCommonPathSuffix(startOfLinkInfo + commonPathSuffixOffset, false);
                if (((linkInfoFlags & LnkEnums.LinkInfoFlags.VolumeIDAndLocalBasePath.getFlag())
                        == LnkEnums.LinkInfoFlags.VolumeIDAndLocalBasePath.getFlag())
                        && hasUnicodeLocalBaseAndCommonSuffixOffset) {
                    localBasePathUnicode = parseLocalBasePath(startOfLinkInfo + localBasePathOffsetUnicode, true);
                    commonPathSuffixUnicode = parseCommonPathSuffix(startOfLinkInfo + commonPathSuffixOffsetUnicode, true);
                }

                bb.position(startOfLinkInfo + linkInfoSize);
            }
            String name = null;
            if ((linkFlags & LnkEnums.LinkFlags.HasName.getFlag())
                    == LnkEnums.LinkFlags.HasName.getFlag()) {
                name = readStringData(bb);
            }
            String relativePath = null;
            if ((linkFlags & LnkEnums.LinkFlags.HasRelativePath.getFlag())
                    == LnkEnums.LinkFlags.HasRelativePath.getFlag()) {
                relativePath = readStringData(bb);
            }
            String workingDir = null;
            if ((linkFlags & LnkEnums.LinkFlags.HasWorkingDir.getFlag())
                    == LnkEnums.LinkFlags.HasWorkingDir.getFlag()) {
                workingDir = readStringData(bb);
            }
            String arguments = null;
            if ((linkFlags & LnkEnums.LinkFlags.HasArguments.getFlag())
                    == LnkEnums.LinkFlags.HasArguments.getFlag()) {
                arguments = readStringData(bb);
            }
            String iconLocation = null;
            if ((linkFlags & LnkEnums.LinkFlags.HasIconLocation.getFlag())
                    == LnkEnums.LinkFlags.HasIconLocation.getFlag()) {
                iconLocation = readStringData(bb);
            }

            return new JLNK(header, linkClassIdentifier.array(), linkFlags, fileAttributes,
                    crtime, atime, mtime, fileSize, iconIndex, showCommand, hotkey,
                    linkTargetIdList,
                    hasUnicodeLocalBaseAndCommonSuffixOffset, localBasePath,
                    commonPathSuffix, localBasePathUnicode, commonPathSuffixUnicode,
                    name, relativePath, workingDir, arguments, iconLocation, driveSerialNumber,
                    driveType, volumeLabel, commonNetworkRelativeLinkFlags,
                    networkProviderType, unicodeNetAndDeviceName, netName, netNameUnicode,
                    deviceName, deviceNameUnicode);
        } catch (Exception e) {
            throw new JLnkParserException(e);
        }
    }

    private String readStringData(ByteBuffer bb) {
        short countCharacters = bb.getShort();
        if (countCharacters == 0) {
            return null;
        }
        byte[] theString = new byte[countCharacters * 2];
        bb.get(theString);
        try {
            return new String(theString, "UTF-16LE");
        } catch (UnsupportedEncodingException ex) {
            logger.info("Shouldn't happen"); //NON-NLS
            return null;
        }
    }

    private String parseString(int offset, boolean unicode, int maxlen) {
        ByteBuffer bb = ByteBuffer.wrap(content);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.position(offset);
        StringBuilder sb = new StringBuilder(bb.limit());
        int i = 0;
        while (bb.remaining() > 0 && (i < maxlen || maxlen == -1)) // safer  
        {
            char c;
            if (unicode) {
                c = bb.getChar();
            } else {
                c = (char) bb.get();
            }
            if (c == '\0') {
                break;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private String parseCommonPathSuffix(int offset, boolean unicode) {
        return parseString(offset, unicode, -1);

    }

    private String parseLocalBasePath(int offset, boolean unicode) {
        return parseString(offset, unicode, -1);

    }

    private String parseNetName(int offset, boolean unicode) {
        return parseString(offset, unicode, -1);
    }

    private String parseDeviceName(int offset, boolean unicode) {
        return parseString(offset, unicode, -1);
    }

    private List<String> parseLinkTargetIdList(List<byte[]> idList) {
        List<String> ret = new ArrayList<>();
        if (!idList.isEmpty()) {
            CommonCLSIDS clsid = CommonCLSIDS.valueOf(Arrays.copyOfRange(idList.remove(0), 2, 18));
            switch (clsid) {
                case CDrivesFolder:
                    ret.add(new String(Arrays.copyOfRange(idList.remove(0), 1, 17)).split("\0")[0]);
                    ret.addAll(parsePathElements(idList));
                    break;
                case CMyDocsFolder:
                    ret.addAll(parsePathElements(idList));
                    break;
                case IEFrameDLL:
                    break;
                case Unknown:
                    break;
                default:
                    break;
            }
        }
        return ret;
    }

    private List<String> parsePathElements(List<byte[]> idList) {
        List<String> ret = new ArrayList<>();
        for (byte[] pathElement : idList) {
            ByteBuffer bb = ByteBuffer.wrap(pathElement);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            int offset = bb.getShort(bb.limit() - 2) - 2;
            if (pathElement[offset + 0x02] < 0x03
                    || pathElement[offset + 0x10] >= pathElement[offset]
                    || pathElement[offset + 0x10] < 0x14) {
                ret.add(get0xC(bb));
                continue;
            }
            if (pathElement[offset + 0x10] != 0) {
                ret.add(getStringAt(bb, offset + pathElement[offset + 0x10], true));
                continue;
            } else {
                if (pathElement[offset + 0x12] >= pathElement[offset]
                        || pathElement[offset + 0x12] < 0x14) {
                    ret.add(get0xC(bb));
                    continue;
                } else {
                    ret.add(getStringAt(bb, offset + pathElement[offset + 0x12], false));
                    continue;
                }
            }
        }
        return ret;
    }

    private String get0xC(ByteBuffer bb) {
        return getStringAt(bb, 0xC, false);
    }

    private String getStringAt(ByteBuffer bb, int offset, boolean unicode) {
        byte[] nameArr = Arrays.copyOfRange(bb.array(), offset, bb.limit());
        if (unicode) {
            try {
                return new String(nameArr, "UTF-16LE").split("\0")[0];
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(JLnkParser.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return new String(nameArr).split("\0")[0];
    }
}
