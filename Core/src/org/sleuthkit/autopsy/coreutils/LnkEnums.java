/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
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

/**
 *
 * @author dick
 */
class LnkEnums {

    private static final byte[] CDRIVES = new byte[]{(byte) 0xe0, 0x4f, (byte) 0xd0, 0x20,
        (byte) 0xea, 0x3a, 0x69, 0x10, (byte) 0xa2, (byte) 0xd8, 0x08, 0x00, 0x2b, 0x30, 0x30, (byte) 0x9d};
    private static final byte[] CMYDOCS = new byte[]{(byte) 0xba, (byte) 0x8a, 0x0d,
        0x45, 0x25, (byte) 0xad, (byte) 0xd0, 0x11, (byte) 0x98, (byte) 0xa8, 0x08, 0x00, 0x36, 0x1b, 0x11, 0x03};
    private static final byte[] IEFRAME = new byte[]{(byte) 0x80, 0x53, 0x1c, (byte) 0x87, (byte) 0xa0,
        0x42, 0x69, 0x10, (byte) 0xa2, (byte) 0xea, 0x08, 0x00, 0x2b, 0x30, 0x30, (byte) 0x9d};

    private LnkEnums() {
        //private constructor for utility class
    }

    public enum CommonCLSIDS {

        CDrivesFolder(CDRIVES),
        CMyDocsFolder(CMYDOCS),
        IEFrameDLL(IEFRAME),
        Unknown(new byte[16]);

        private final byte[] flag;

        private CommonCLSIDS(byte[] flag) {
            this.flag = flag.clone();
        }

        static CommonCLSIDS valueOf(byte[] type) {
            for (CommonCLSIDS value : CommonCLSIDS.values()) {
                if (java.util.Arrays.equals(value.getFlag(), type)) {
                    return value;
                }
            }
            return Unknown;
        }

        byte[] getFlag() {
            return flag.clone();
        }
    }

    public enum LinkFlags {

        HasLinkTargetIDList(0x00000001),
        HasLinkInfo(0x00000002),
        HasName(0x00000004),
        HasRelativePath(0x00000008),
        HasWorkingDir(0x00000010),
        HasArguments(0x00000020),
        HasIconLocation(0x00000040),
        IsUnicode(0x00000080),
        ForceNoLinkInfo(0x00000100),
        HasExpString(0x00000200),
        RunInSeparateProcess(0x00000400),
        Unused1(0x00000800),
        HasDarwinID(0x00001000),
        RunAsUser(0x00002000),
        HasExpIcon(0x00004000),
        NoPidlAlias(0x00008000),
        Unused2(0x00010000),
        RunWithShimLayer(0x00020000),
        ForceNoLinkTrack(0x00040000),
        EnableTargetMetaData(0x00080000),
        DisableLinkPathTracking(0x00100000),
        DisableKnownFolderTracking(0x00200000),
        DisableKnownFolderAlias(0x00400000),
        AllowLinkToLink(0x00800000),
        UnaliasOnSave(0x01000000),
        PreferEnvironmentPath(0x02000000),
        KeepLocalIDListForUNCTarget(0x04000000);

        private final int flag;

        private LinkFlags(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    public enum DriveType {

        DRIVE_UNKNOWN(0x00000000),
        DRIVE_NO_ROOT_DIR(0x00000001),
        DRIVE_REMOVABLE(0x00000002),
        DRIVE_FIXED(0x00000003),
        DRIVE_REMOTE(0x00000004),
        DRIVE_CDROM(0x00000005),
        DRIVE_RAMDISK(0x00000006);

        private final int flag;

        private DriveType(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }

        static DriveType valueOf(int type) {
            for (DriveType value : DriveType.values()) {
                if (value.getFlag() == type) {
                    return value;
                }
            }
            return DRIVE_UNKNOWN;
        }
    }

    public enum FileAttributesFlags {

        READONLY(0x00000001),
        HIDDEN(0x00000002),
        SYSTEM(0x00000004),
        RESERVED1(0x00000008),
        DIRECTORY(0x00000010),
        ARCHIVE(0x00000020),
        RESERVED2(0x00000040),
        NORMAL(0x00000080),
        TEMPORARY(0x00000100),
        SPARSE_FILE(0x00000200),
        REPARSE_POINT(0x00000400),
        COMPRESSED(0x00000800),
        OFFLINE(0x00001000),
        NOT_CONTENT_INDEXED(0x00002000),
        ENCRYPTED(0x00004000);

        private final int flag;

        private FileAttributesFlags(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    public enum LinkInfoFlags {

        VolumeIDAndLocalBasePath(0x00000001),
        CommonNetworkRelativeLinkAndPathSuffix(0x00000002);

        private final int flag;

        private LinkInfoFlags(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    public enum CommonNetworkRelativeLinkFlags {

        ValidDevice(0x00000001),
        ValidNetType(0x00000002);

        private final int flag;

        private CommonNetworkRelativeLinkFlags(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    public enum NetworkProviderType {

        WNNC_NET_AVID(0x001A0000),
        WNNC_NET_DOCUSPACE(0x001B0000),
        WNNC_NET_MANGOSOFT(0x001C0000),
        WNNC_NET_SERNET(0x001D0000),
        WNNC_NET_RIVERFRONT1(0x001E0000),
        WNNC_NET_RIVERFRONT2(0x001F0000),
        WNNC_NET_DECORB(0x00200000),
        WNNC_NET_PROTSTOR(0x00210000),
        WNNC_NET_FJ_REDIR(0x00220000),
        WNNC_NET_DISTINCT(0x00230000),
        WNNC_NET_TWINS(0x00240000),
        WNNC_NET_RDR2SAMPLE(0x00250000),
        WNNC_NET_CSC(0x00260000),
        WNNC_NET_3IN1(0x00270000),
        WNNC_NET_EXTENDNET(0x00290000),
        WNNC_NET_STAC(0x002A0000),
        WNNC_NET_FOXBAT(0x002B0000),
        WNNC_NET_YAHOO(0x002C0000),
        WNNC_NET_EXIFS(0x002D0000),
        WNNC_NET_DAV(0x002E0000),
        WNNC_NET_KNOWARE(0x002F0000),
        WNNC_NET_OBJECT_DIRE(0x00300000),
        WNNC_NET_MASFAX(0x00310000),
        WNNC_NET_HOB_NFS(0x00320000),
        WNNC_NET_SHIVA(0x00330000),
        WNNC_NET_IBMAL(0x00340000),
        WNNC_NET_LOCK(0x00350000),
        WNNC_NET_TERMSRV(0x00360000),
        WNNC_NET_SRT(0x00370000),
        WNNC_NET_QUINCY(0x00380000),
        WNNC_NET_OPENAFS(0x00390000),
        WNNC_NET_AVID1(0x003A0000),
        WNNC_NET_DFS(0x003B0000),
        WNNC_NET_KWNP(0x003C0000),
        WNNC_NET_ZENWORKS(0x003D0000),
        WNNC_NET_DRIVEONWEB(0x003E0000),
        WNNC_NET_VMWARE(0x003F0000),
        WNNC_NET_RSFX(0x00400000),
        WNNC_NET_MFILES(0x00410000),
        WNNC_NET_MS_NFS(0x00420000),
        WNNC_NET_GOOGLE(0x00430000),
        WNNC_NET_UNKNOWN(0x00000000);

        private final int flag;

        private NetworkProviderType(int flag) {
            this.flag = flag;
        }

        static NetworkProviderType valueOf(int type) {
            for (NetworkProviderType value : NetworkProviderType.values()) {
                if (value.getFlag() == type) {
                    return value;
                }
            }
            return WNNC_NET_UNKNOWN;
        }

        public int getFlag() {
            return flag;
        }
    }

}
