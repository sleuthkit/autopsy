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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Utility class for creating consistent table data.
 */
public class FileSystemColumnUtils {
    
    private static final Logger logger = Logger.getLogger(FileSystemColumnUtils.class.getName());
    
    enum ContentType {
        IMAGE,
        POOL,
        VOLUME,
        ABSTRACT_FILE,
        UNSUPPORTED;
    }
    
    @Messages({"FileSystemColumnUtils.nameColumn.name=Name",
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
    "FileSystemColumnUtils.volumeColumns.id=ID",
    "FileSystemColumnUtils.volumeColumns.startingSector=Starting Sector",
    "FileSystemColumnUtils.volumeColumns.length=Length in Sectors",
    "FileSystemColumnUtils.volumeColumns.desc=Description",
    "FileSystemColumnUtils.volumeColumns.flags=Flags",
    "FileSystemColumnUtils.imageColumns.type=Type",
    "FileSystemColumnUtils.imageColumns.typeValue=Image",
    "FileSystemColumnUtils.imageColumns.size=Size (Bytes)",
    "FileSystemColumnUtils.imageColumns.sectorSize=Sector Size (Bytes)",
    "FileSystemColumnUtils.imageColumns.timezone=Timezone",
    "FileSystemColumnUtils.imageColumns.devID=Device ID",
    "FileSystemColumnUtils.poolColumns.type=Type",
    
    "FileSystemColumnUtils.noDescription=No Description"})
    
    private static final ColumnKey NAME_COLUMN = getColumnKey(Bundle.FileSystemColumnUtils_nameColumn_name());
    
    private static final List<ColumnKey> ABSTRACT_FILE_COLUMNS = Arrays.asList(
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
    
    private static final List<ColumnKey> VOLUME_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_id()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_startingSector()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_length()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_desc()),
        getColumnKey(Bundle.FileSystemColumnUtils_volumeColumns_flags()));
    
    private static final List<ColumnKey> IMAGE_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_type()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_size()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_sectorSize()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_timezone()),
        getColumnKey(Bundle.FileSystemColumnUtils_imageColumns_devID())
    );
    
    // Note that Hosts aren't content and will not be combined with other types, so we include the name here
    private static final List<ColumnKey> HOST_COLUMNS = Arrays.asList(
        NAME_COLUMN
    );
    
    private static final List<ColumnKey> POOL_COLUMNS = Arrays.asList(
        getColumnKey(Bundle.FileSystemColumnUtils_poolColumns_type())
    );
    
    /**
     * Convert a given Content object to an enum.
     * 
     * @param content The Content object.
     * 
     * @return The type corresponding to the content; UNSUPPORTED if the 
     *         content will not be displayed in the file system section of the tree.
     */
    private static ContentType getDisplayableContentType(Content content) {
        if (content instanceof Image) {
            return ContentType.IMAGE;
        } else if (content instanceof Volume) {
            return ContentType.VOLUME;
        } else if (content instanceof Pool) {
            return ContentType.POOL;
        } else if (content instanceof AbstractFile) {
            return ContentType.ABSTRACT_FILE;
        }
        return ContentType.UNSUPPORTED;
    }
    
    /**
     * Check whether a given content object should be displayed in the
     * file system section of the tree.
     * We can display an object if ContentType is not UNSUPPORTED
     * and if it is not the root directory. We can not display
     * file systems, volume systems, artifacts, etc.
     * 
     * @param content The content.
     * 
     * @return True if the content is displayable, false otherwise.
     */
    static boolean isDisplayable(Content content) {
        if (content instanceof AbstractFile) {
            // .. directories near the top of the directory structure can
            // pass the isRoot() check, so first check if the name is empty
            // (real root directories will have a blank name field)
            if (!content.getName().isEmpty()) {
                return true;
            }
            return ! ((AbstractFile)content).isRoot();
        }
        return (getDisplayableContentType(content) != ContentType.UNSUPPORTED);
    }
    
    /**
     * Get a list of the content types from the list that will be displayed.
     * Call this before getColumnKeysForContent() and getCellValuesForContent() 
     * to ensure consistent columns.
     * 
     * @param contentList List of content.
     * 
     * @return List of types that will be displayed.
     */
    static List<ContentType> getDisplayableTypesForContentList(List<Content> contentList) {
        List<ContentType> displayableTypes = new ArrayList<>();
        for (Content content : contentList) {
            ContentType type = getDisplayableContentType(content);
            if (type != ContentType.UNSUPPORTED && ! displayableTypes.contains(type)) {
                displayableTypes.add(type);
            }
        }
        Collections.sort(displayableTypes);
        return displayableTypes;
    }
    
    /**
     * Get the column keys corresponding to the given list of types.
     * 
     * @param contentTypes The list of types.
     * 
     * @return The list of column keys.
     */
    static List<ColumnKey> getColumnKeysForContent(List<ContentType> contentTypes) {
        List<ColumnKey> colKeys = new ArrayList<>();
        colKeys.add(NAME_COLUMN);
        
        // Make sure content types are processed in the same order as in getCellValuesForContent()
        if (contentTypes.contains(ContentType.IMAGE)) {
            colKeys.addAll(IMAGE_COLUMNS);
        }
        if (contentTypes.contains(ContentType.POOL)) {
            colKeys.addAll(POOL_COLUMNS);
        }
        if (contentTypes.contains(ContentType.VOLUME)) {
            colKeys.addAll(VOLUME_COLUMNS);
        }
        if (contentTypes.contains(ContentType.ABSTRACT_FILE)) {
            colKeys.addAll(ABSTRACT_FILE_COLUMNS);
        }
        return colKeys;
    }
    
    /**
     * Get the column keys for a Host.
     * 
     * @return The column keys.
     */
    static List<ColumnKey> getColumnKeysForHost() {
        return Arrays.asList(NAME_COLUMN);
    }
    
    /**
     * Get the cell values for a given content object.
     * 
     * @param content      The content to display.
     * @param contentTypes The content types being displayed in the table.
     * 
     * @return The cell values for this row.
     * 
     * @throws TskCoreException 
     */
    static List<Object> getCellValuesForContent(Content content, List<ContentType> contentTypes) throws TskCoreException {
        List<Object> cellValues = new ArrayList<>();
        cellValues.add(getNameValueForContent(content));
        
        // Make sure content types are processed in the same order as in getColumnKeysForContent()
        if (contentTypes.contains(ContentType.IMAGE)) {
            cellValues.addAll(getNonNameCellValuesForImage(content));
        }
        if (contentTypes.contains(ContentType.POOL)) {
            cellValues.addAll(getNonNameCellValuesForPool(content));
        }
        if (contentTypes.contains(ContentType.VOLUME)) {
            cellValues.addAll(getNonNameCellValuesForVolume(content));
        }
        if (contentTypes.contains(ContentType.ABSTRACT_FILE)) {
            cellValues.addAll(getNonNameCellValuesForAbstractFile(content));
        }
        return cellValues;
    }
    
    /**
     * Get the value for the name column for the given content.
     * 
     * @param content The content.
     * 
     * @return The display name for the content.
     */
    private static String getNameValueForContent(Content content) {
        if (content instanceof Image) {
            Image image = (Image)content;
            return image.getName();
        } else if (content instanceof Volume) {
            Volume vol = (Volume)content;
            return getVolumeDisplayName(vol);
        } else if (content instanceof Pool) {
            Pool pool = (Pool)content;
            return pool.getType().getName(); // We currently use the type name for both the name and type fields
        }
        return content.getName();
    }
    
    /**
     * Get the column keys for an abstract file object.
     * Only use this method if all rows contain AbstractFile objects.
     * Make sure the order here matches that in getCellValuesForAbstractFile();
     * 
     * @return The list of column keys.
     */
    static List<ColumnKey> getColumnKeysForAbstractfile() {
        List<ColumnKey> colKeys = new ArrayList<>(); 
        colKeys.add(NAME_COLUMN);
        colKeys.addAll(ABSTRACT_FILE_COLUMNS);
        return colKeys;
    }
    
    /**
     * Get the cell values for an abstract file.
     * Only use this method if all rows contain AbstractFile objects.
     * Make sure the order here matches that in getColumnKeysForAbstractfile();
     * 
     * @param file The file to use to populate the cells.
     * 
     * @return List of cell values.
     */
    static List<Object> getCellValuesForAbstractFile(AbstractFile file) throws TskCoreException {
        List<Object> cells = new ArrayList<>();
        cells.add(getNameValueForContent(file));
        cells.addAll(getNonNameCellValuesForAbstractFile(file));
        return cells;
    }
    
    /**
     * Make sure the order here matches that in ABSTRACT_FILE_COLUMNS
     * 
     * @param content The content to use to populate the cells (may not be an abstract file)
     * 
     * @return List of cell values
     */
    private static List<Object> getNonNameCellValuesForAbstractFile(Content content) throws TskCoreException {
        final int nColumns = 17;
        if (! (content instanceof AbstractFile)) {
            return Collections.nCopies(nColumns, null);
        }
        
        // Make sure to update nColumns if the number of columns here changes
        AbstractFile file = (AbstractFile) content;
        return Arrays.asList(
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
    
    /**
     * Make sure the order here matches that in POOL_COLUMNS
     * 
     * @param conent The content to use to populate the cells (may not be a pool)
     * 
     * @return List of cell values
     */
    private static List<Object> getNonNameCellValuesForPool(Content content) throws TskCoreException {
        final int nColumns = 1;
        if (! (content instanceof Pool)) {
            return Collections.nCopies(nColumns, null);
        }
        
        // Make sure to update nColumns if the number of columns here changes
        Pool pool = (Pool) content;
        return Arrays.asList(
            pool.getType().getName() // We currently use the type name for both the name and type fields
        );
    }
    
    /**
     * Make sure the order here matches that in HOST_COLUMNS
     * 
     * @param host The host to use to populate the cells
     * 
     * @return List of cell values
     */
    static List<Object> getCellValuesForHost(Host host) throws TskCoreException {
        return Arrays.asList(
            host.getName()
        );    
    }
    
    /**
     * Make sure the order here matches that in IMAGE_COLUMNS
     * 
     * @param content The content to use to populate the cells (may not be an image)
     * 
     * @return List of cell values
     */
    private static List<Object> getNonNameCellValuesForImage(Content content) throws TskCoreException {
        final int nColumns = 5;
        if (! (content instanceof Image)) {
            return Collections.nCopies(nColumns, null);
        }
        
        // Make sure to update nColumns if the number of columns here changes
        Image image = (Image) content;        
        return Arrays.asList(
            Bundle.FileSystemColumnUtils_imageColumns_typeValue(),
            image.getSize(),
            image.getSsize(),
            image.getTimeZone(),
            image.getDeviceId()
        );    
    }
    
    /**
     * Make sure the order here matches that in VOLUME_COLUMNS
     * 
     * @param content The content to use to populate the cells (may not be a volume)
     * 
     * @return List of cell values
     */
    private static List<Object> getNonNameCellValuesForVolume(Content content) throws TskCoreException {
        final int nColumns = 5;
        if (! (content instanceof Volume)) {
            return Collections.nCopies(nColumns, null);
        }
        
        // Make sure to update nColumns if the number of columns here changes
        Volume vol = (Volume) content;   
        return Arrays.asList(
            vol.getAddr(),
            vol.getStart(),
            vol.getLength(),
            vol.getDescription(),
            vol.getFlagsAsString()
        );
    }
    
    /**
     * Get the display name for a volume.
     * 
     * @param vol The volume.
     * 
     * @return The display name.
     */
    public static String getVolumeDisplayName(Volume vol) {
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
    
    /**
     * Get the content that should be displayed in the table based on the given object.
     * Algorithm:
     * - If content is known and known files are being hidden, return an empty list
     * - If content is a slack file and slack files are being hidden, return an empty list
     * - If content is a displayable type, return it
     * - If content is a volume system, return its displayable children
     * - If content is a file system, return the displayable children of the root folder
     * - If content is the root folder, return the displayable children of the root folder
     * 
     * @param content The base content.
     * 
     * @return List of content to add to the table.
     */
    static List<Content> getDisplayableContentForTable(Content content) throws TskCoreException {
        
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile)content;
            // Skip known files if requested
            if (UserPreferences.hideKnownFilesInDataSourcesTree() 
                    && file.getKnown().equals(TskData.FileKnown.KNOWN)) {
                return new ArrayList<>();
            }
            
            // Skip slack files if requested
            if (UserPreferences.hideSlackFilesInDataSourcesTree()
                    && file instanceof SlackFile) {
                return new ArrayList<>();
            }
        }
        
        return getDisplayableContentForTableAndTree(content);
    }
    
    /**
     * Get the content that should be displayed in the table based on the given object.
     * Algorithm:
     * - If content is a displayable type, return it
     * - If content is a volume system, return its displayable children
     * - If content is a file system, return the displayable children of the root folder
     * - If content is the root folder, return the displayable children of the root folder
     *
     * @param content The base content.
     * 
     * @return List of content to add to the table/tree.
     * 
     * @throws TskCoreException 
     */
    private static List<Content> getDisplayableContentForTableAndTree(Content content) throws TskCoreException {    
        // If the given content is displayable, return it
        if (FileSystemColumnUtils.isDisplayable(content)) {
            return Arrays.asList(content);
        }
        
        List<Content> contentToDisplay = new ArrayList<>();
        if (content instanceof VolumeSystem) {
            // Return all children that can be displayed
            VolumeSystem vs = (VolumeSystem)content;
            for (Content child : vs.getChildren()) {
                if (isDisplayable(child)) {
                    contentToDisplay.add(child);
                }
            }
        } else if (content instanceof FileSystem) {
            // Return the children of the root node
            FileSystem fs = (FileSystem)content;
            for (Content child : fs.getRootDirectory().getChildren()) {
                if (isDisplayable(child)) {
                    contentToDisplay.add(child);
                }
            }
        } else if (content instanceof AbstractFile) {
            if (((AbstractFile) content).isRoot()) {
                // If we have the root folder, skip it and display the children
                for (Content child : content.getChildren()) {
                    if (isDisplayable(child)) {
                        contentToDisplay.add(child);
                    }
                }
            } else {
                return Arrays.asList(content);
            }
        }
        
        return contentToDisplay;
    }
        
    /**
     * Create a column key from a string.
     * 
     * @param name The column name
     * 
     * @return The column key
     */
    private static ColumnKey getColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.FileSystemColumnUtils_noDescription());
    }
    
    /**
     * Get the children of a given content ID that will be visible in the tree.
     * 
     * @param contentId The ID of the parent content.
     * 
     * @return The visible children of the given content.
     * 
     * @throws TskCoreException
     * @throws NoCurrentCaseException 
     */
    public static List<Content> getVisibleTreeNodeChildren(Long contentId) throws TskCoreException, NoCurrentCaseException {
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Content content = skCase.getContentById(contentId);
        List<Content> originalChildren = content.getChildren();

        // First, advance past anything we don't display (volume systems, file systems, root folders)
        List<Content> treeChildren = new ArrayList<>();
        for (Content child : originalChildren) {
            treeChildren.addAll(FileSystemColumnUtils.getDisplayableContentForTableAndTree(child));
        }
            
        // Filter out the . and .. directories
        for (Iterator<Content> iter = treeChildren.listIterator(); iter.hasNext(); ) {
            Content c = iter.next();
            if ((c instanceof AbstractFile) && ContentUtils.isDotDirectory((AbstractFile)c)) {
                iter.remove();
            }
        }

        // Filter out any files that aren't directories and do not have children
        for (Iterator<Content> iter = treeChildren.listIterator(); iter.hasNext(); ) {
            Content c = iter.next();
            if (c instanceof AbstractFile 
                    && (! ((AbstractFile)c).isDir())
                    && (! hasDisplayableContentChildren((AbstractFile)c))) {
                iter.remove();
            }
        }
        
        return treeChildren;
    }
    
    /**
     * Check whether a file has displayable children.
     * 
     * @param file The file to check.
     * 
     * @return True if the file has displayable children, false otherwise.
     */
    private static boolean hasDisplayableContentChildren(AbstractFile file) {
        if (file != null) {
            try {
                // If the file has no children at all, then it has no displayable children.
                // NOTE: AbstractContent.hasChildren() uses in-memory data to determine children 
                // and no DB query is required. 
                if (!file.hasChildren()) {
                    return false;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking if the node has children for file with ID: " + file.getId(), ex); //NON-NLS
                return false;
            }

            String query = "SELECT COUNT(obj_id) AS count FROM "
                    + " ( SELECT obj_id FROM tsk_objects WHERE par_obj_id = " + file.getId() + " AND type = "
                    + TskData.ObjectType.ARTIFACT.getObjectType()
                    + "   INTERSECT SELECT artifact_obj_id FROM blackboard_artifacts WHERE obj_id = " + file.getId()
                    + "     AND (artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()
                    + " OR artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID() + ") "
                    + "   UNION SELECT obj_id FROM tsk_objects WHERE par_obj_id = " + file.getId()
                    + "     AND type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + ") AS OBJECT_IDS"; //NON-NLS;

            try (SleuthkitCase.CaseDbQuery dbQuery = Case.getCurrentCaseThrows().getSleuthkitCase().executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                if (resultSet.next()) {
                    return (0 < resultSet.getInt("count"));
                }
            } catch (TskCoreException | SQLException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error checking if the node has children for file with ID: " + file.getId(), ex); //NON-NLS
            }
        }
        return false;
    }    
}
