/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
class FileSystemColumnUtils {
    
    private static final Logger logger = Logger.getLogger(FileSystemColumnUtils.class.getName());
    
    @Messages({"FileSystemColumnUtils.abstractFileColumns.nameColLbl=Name",
    "FileSystemColumnUtils.abstractFileColumns.originalName=Original Name",
    "FileSystemColumnUtils.abstractFileColumns.scoreName=S",
    "FileSystemColumnUtils.abstractFileColumns.commentName=C",
    "FileSystemColumnUtils.abstractFileColumns.countName=O",
    "FileSystemColumnUtils.abstractFileColumns.locationColLbl=Location",
    "FileSystemColumnUtils.abstractFileColumns.modifiedTimeColLbl=Modified Time",
    "FileSystemColumnUtils.abstractFileColumns.changeTimeColLbl=Change Time",
    "FileSystemColumnUtils.abstractFileColumns.accessTimeColLbl=Access Time",
    "FileSystemColumnUtils.abstractFileColumns.createdTimeColLbl=Created Time",
    "FileSystemColumnUtils.abstractFileColumns.sizeColLbl=Size",
    "FileSystemColumnUtils.abstractFileColumns.flagsDirColLbl=Flags(Dir)",
    "FileSystemColumnUtils.abstractFileColumns.flagsMetaColLbl=Flags(Meta)",
    "FileSystemColumnUtils.abstractFileColumns.modeColLbl=Mode",
    "FileSystemColumnUtils.abstractFileColumns.useridColLbl=UserID",
    "FileSystemColumnUtils.abstractFileColumns.groupidColLbl=GroupID",
    "FileSystemColumnUtils.abstractFileColumns.metaAddrColLbl=Meta Addr.",
    "FileSystemColumnUtils.abstractFileColumns.attrAddrColLbl=Attr. Addr.",
    "FileSystemColumnUtils.abstractFileColumns.typeDirColLbl=Type(Dir)",
    "FileSystemColumnUtils.abstractFileColumns.typeMetaColLbl=Type(Meta)",
    "FileSystemColumnUtils.abstractFileColumns.knownColLbl=Known",
    "FileSystemColumnUtils.abstractFileColumns.md5HashColLbl=MD5 Hash",
    "FileSystemColumnUtils.abstractFileColumns.sha256HashColLbl=SHA-256 Hash",
    "FileSystemColumnUtils.abstractFileColumns.objectId=Object ID",
    "FileSystemColumnUtils.abstractFileColumns.mimeType=MIME Type",
    "FileSystemColumnUtils.abstractFileColumns.extensionColLbl=Extension",
    "FileSystemColumnUtils.volumeColumns.nameColLbl=Name",
    "FileSystemColumnUtils.volumeColumns.id=ID",
    "FileSystemColumnUtils.volumeColumns.startingSector=Starting Sector",
    "FileSystemColumnUtils.volumeColumns.length=Length in Sectors",
    "FileSystemColumnUtils.volumeColumns.desc=Description",
    "FileSystemColumnUtils.volumeColumns.flags=Flags",
    "FileSystemColumnUtils.imageColumns.nameColLbl=Name",
    "FileSystemColumnUtils.imageColumns.type=Type",
    "FileSystemColumnUtils.imageColumns.typeValue=Image",
    "FileSystemColumnUtils.imageColumns.size=Size (Bytes)",
    "FileSystemColumnUtils.imageColumns.sectorSize=Sector Size (Bytes)",
    "FileSystemColumnUtils.imageColumns.timezone=Timezone",
    "FileSystemColumnUtils.imageColumns.devID=Device ID",
    "FileSystemColumnUtils.hostColumns.nameColLbl=Name",
    "FileSystemColumnUtils.poolColumns.nameColLbl=Name",
    "FileSystemColumnUtils.poolColumns.type=Type",
    
    "FileSystemColumnUtils.noDescription=No Description"})
    


    
    static final List<ColumnKey> ABSTRACT_FILE_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_nameColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_originalName()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_scoreName()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_commentName()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_countName()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_locationColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_modifiedTimeColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_changeTimeColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_accessTimeColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_createdTimeColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_sizeColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_flagsDirColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_flagsMetaColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_modeColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_useridColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_groupidColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_metaAddrColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_attrAddrColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_typeDirColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_typeMetaColLbl()),
getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_knownColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_md5HashColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_sha256HashColLbl()),
        // getFileColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_objectId()),
getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_mimeType()),
        getColumnKey(Bundle.FileSystemColumnUtils_abstractFileColumns_extensionColLbl()));
    
    static final List<ColumnKey> VOLUME_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_nameColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_id()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_startingSector()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_length()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_desc()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_flags()));
    
    static final List<ColumnKey> IMAGE_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_nameColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_type()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_size()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_sectorSize()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_timezone()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_devID())
    );
    
    static final List<ColumnKey> HOST_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_hostColumns_nameColLbl())
    );
    
    static final List<ColumnKey> POOL_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_poolColumns_nameColLbl()),
        getColumnKey(Bundle.FileSystemColumnUtils_poolColumns_type())
    );
    
    static List<Object> getCellValuesForPool(Pool pool) throws TskCoreException {
        return Arrays.asList(
            pool.getType().getName(),
            pool.getType().getName()
        );    
    }
    
    static List<Object> getCellValuesForHost(Host host) throws TskCoreException {
        return Arrays.asList(
            host.getName()
        );    
    }
    
    static List<Object> getCellValuesForImage(Image image) throws TskCoreException {
        return Arrays.asList(
            image.getName(),
            Bundle.FileSystemColumnUtils_imageColumns_typeValue(),
            image.getSize(),
            image.getSsize(),
            image.getTimeZone(),
            image.getDeviceId()
        );    
    }
    
    static List<Object> getCellValuesForVolume(Volume vol) throws TskCoreException {
        return Arrays.asList(
            getVolumeDisplayName(vol),
            vol.getAddr(),
            vol.getStart(),
            vol.getLength(),
            vol.getDescription(),
            vol.getFlagsAsString()
        );
    }
    
    private static String getVolumeDisplayName(Volume vol) {
        // set name, display name, and icon
        String volName = "vol" + Long.toString(vol.getAddr());
        long end = vol.getStart() + (vol.getLength() - 1);
        String tempVolName = volName + " (" + vol.getDescription() + ": " + vol.getStart() + "-" + end + ")";

        // If this is a pool volume use a custom display name
        try {
            if (vol.getParent() != null
                    && vol.getParent().getParent() instanceof Pool) {
                // Pool volumes are not contiguous so printing a range of blocks is inaccurate
                tempVolName = volName + " (" + vol.getDescription() + ": " + vol.getStart() + ")";
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error looking up parent(s) of volume with obj ID = " + vol.getId(), ex);
        }
        return tempVolName;
    }
        
    private static ColumnKey getColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.FileSystemColumnUtils_noDescription());
    }
    
    /**
     * Make sure the order here matches that in ABSTRACT_FILE_COLUMNS
     * 
     * @param file
     * 
     * @return 
     */
    static List<Object> getCellValuesForAbstractFile(AbstractFile file) throws TskCoreException {
        return Arrays.asList(
            file.getName(), // GVDTODO handle . and .. from getContentDisplayName()
            // GVDTODO translation column
            null,
            //GVDTDO replace nulls with SCO
            null,
            null,
            null,
            file.getUniquePath(),
            TimeZoneUtils.getFormattedTime(file.getMtime()),
            TimeZoneUtils.getFormattedTime(file.getCtime()),
            TimeZoneUtils.getFormattedTime(file.getAtime()),
            TimeZoneUtils.getFormattedTime(file.getCrtime()),
            file.getSize(),
            file.getDirFlagAsString(),
            file.getMetaFlagsAsString(),
            // mode,
            // userid,
            // groupid,
            // metaAddr,
            // attrAddr,
            // typeDir,
            // typeMeta,

            file.getKnown().getName(),
            StringUtils.defaultString(file.getMd5Hash()),
            StringUtils.defaultString(file.getSha256Hash()),
            // objectId,

            StringUtils.defaultString(file.getMIMEType()),
            file.getNameExtension()
        );
    }
}
