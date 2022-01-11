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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceNameChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsAddedToPersonEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsRemovedFromPersonEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsUpdatedEvent;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_DURATION_UNITS;
import static org.sleuthkit.autopsy.mainui.datamodel.AbstractDAO.CACHE_SIZE;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEventUtils;
import static org.sleuthkit.autopsy.mainui.datamodel.MediaTypeUtils.getExtensionMediaType;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.DirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.ImageRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.VolumeRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.LocalDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.LocalFileDataSourceRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.VirtualDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.LayoutFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.SlackFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.PoolRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.FileSystemContentEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.FileSystemHostEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.FileSystemPersonEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.DbType;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_TYPE_ENUM;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 *
 */
public class FileSystemDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(FileSystemDAO.class.getName());

    private static final Comparator<FileSystemTreeItem> TREE_ITEM_COMPARATOR = (fs1, fs2) -> {

        // ordering taken from SELECT_FILES_BY_PARENT in SleuthkitCase
        if ((fs1.getMetaType() != null) && (fs2.getMetaType() != null)
                && (fs1.getMetaType().getValue() != fs2.getMetaType().getValue())) {
            return -Short.compare(fs1.getMetaType().getValue(), fs2.getMetaType().getValue());
        }

        // The case where both meta types are null will fall through to the name comparison.
        if (fs1.getMetaType() == null) {
            if (fs2.getMetaType() != null) {
                return -1;
            }
        } else if (fs2.getMetaType() != null) {
            return 1;
        }
        return fs1.getDisplayName().compareToIgnoreCase(fs2.getDisplayName());
    };

    private static final Set<String> HOST_LEVEL_EVTS = ImmutableSet.of(
            Case.Events.DATA_SOURCE_ADDED.toString(),
            // this should trigger the case to be reopened
            // Case.Events.DATA_SOURCE_DELETED.toString(),
            Case.Events.DATA_SOURCE_NAME_CHANGED.toString(),
            Case.Events.HOSTS_ADDED.toString(),
            Case.Events.HOSTS_DELETED.toString(),
            Case.Events.HOSTS_UPDATED.toString()
    );

    private static final Set<String> PERSON_LEVEL_EVTS = ImmutableSet.of(
            Case.Events.HOSTS_ADDED_TO_PERSON.toString(),
            Case.Events.HOSTS_REMOVED_FROM_PERSON.toString()
    );

    private final Cache<SearchParams<?>, BaseSearchResultsDTO> searchParamsCache
            = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private final TreeCounts<DAOEvent> treeCounts = new TreeCounts<>();

    private static final String FILE_SYSTEM_TYPE_ID = "FILE_SYSTEM";

    private static FileSystemDAO instance = null;

    synchronized static FileSystemDAO getInstance() {
        if (instance == null) {
            instance = new FileSystemDAO();
        }
        return instance;
    }

    private boolean isSystemContentInvalidating(FileSystemContentSearchParam key, DAOEvent daoEvent) {
        if (!(daoEvent instanceof FileSystemContentEvent)) {
            return false;
        }

        FileSystemContentEvent contentEvt = (FileSystemContentEvent) daoEvent;

        return contentEvt.getContentObjectId() == null || Objects.equals(key.getContentObjectId(), contentEvt.getContentObjectId());
    }

    private boolean isSystemHostInvalidating(FileSystemHostSearchParam key, DAOEvent daoEvent) {
        if (!(daoEvent instanceof FileSystemHostEvent)) {
            return false;
        }

        return key.getHostObjectId() == ((FileSystemHostEvent) daoEvent).getHostObjectId();
    }

    private BaseSearchResultsDTO fetchContentForTableFromContent(SearchParams<FileSystemContentSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();

        Long objectId = cacheKey.getParamData().getContentObjectId();
        List<Content> contentForTable = new ArrayList<>();
        String parentName = "";
        Content parentContent = skCase.getContentById(objectId);
        if (parentContent == null) {
            throw new TskCoreException("Error loading children of object with ID " + objectId);
        }

        parentName = parentContent.getName();
        for (Content content : parentContent.getChildren()) {
            contentForTable.addAll(FileSystemColumnUtils.getDisplayableContentForTable(content));
        }

        return fetchContentForTable(cacheKey, contentForTable, parentName);
    }

    private BaseSearchResultsDTO fetchContentForTableFromHost(SearchParams<FileSystemHostSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();

        Long objectId = cacheKey.getParamData().getHostObjectId();
        List<Content> contentForTable = new ArrayList<>();
        String parentName = "";
        Optional<Host> host = skCase.getHostManager().getHostById(objectId);
        if (host.isPresent()) {
            parentName = host.get().getName();
            contentForTable.addAll(skCase.getHostManager().getDataSourcesForHost(host.get()));
        } else {
            throw new TskCoreException("Error loading host with ID " + objectId);
        }
        return fetchContentForTable(cacheKey, contentForTable, parentName);
    }

    private BaseSearchResultsDTO fetchHostsForTable(SearchParams<FileSystemPersonSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();

        Long objectId = cacheKey.getParamData().getPersonObjectId();
        List<Host> hostsForTable = new ArrayList<>();
        String parentName = "";

        if (objectId != null) {
            Optional<Person> person = skCase.getPersonManager().getPerson(objectId);
            if (person.isPresent()) {
                parentName = person.get().getName();
                hostsForTable.addAll(skCase.getPersonManager().getHostsForPerson(person.get()));
            } else {
                throw new TskCoreException("Error loading person with ID " + objectId);
            }
        } else {
            hostsForTable.addAll(skCase.getPersonManager().getHostsWithoutPersons());
        }

        Stream<Host> pagedHostsStream = hostsForTable.stream()
                .sorted(Comparator.comparing((host) -> host.getHostId()))
                .skip(cacheKey.getStartItem());

        if (cacheKey.getMaxResultsCount() != null) {
            pagedHostsStream = pagedHostsStream.limit(cacheKey.getMaxResultsCount());
        }

        List<Host> pagedHosts = pagedHostsStream.collect(Collectors.toList());
        List<ColumnKey> columnKeys = FileSystemColumnUtils.getColumnKeysForHost();

        List<RowDTO> rows = new ArrayList<>();
        for (Host host : pagedHosts) {
            List<Object> cellValues = FileSystemColumnUtils.getCellValuesForHost(host);
            rows.add(new BaseRowDTO(cellValues, FILE_SYSTEM_TYPE_ID, host.getHostId()));
        }
        return new BaseSearchResultsDTO(FILE_SYSTEM_TYPE_ID, parentName, columnKeys, rows, Host.class.getName(), cacheKey.getStartItem(), hostsForTable.size());
    }

    private BaseSearchResultsDTO fetchContentForTable(SearchParams<?> cacheKey, List<Content> contentForTable,
            String parentName) throws NoCurrentCaseException, TskCoreException {
        // Ensure consistent columns for each page by doing this before paging
        List<FileSystemColumnUtils.ContentType> displayableTypes = FileSystemColumnUtils.getDisplayableTypesForContentList(contentForTable);

        List<Content> pagedContent = getPaged(contentForTable, cacheKey);
        List<ColumnKey> columnKeys = FileSystemColumnUtils.getColumnKeysForContent(displayableTypes);

        List<RowDTO> rows = new ArrayList<>();
        for (Content content : pagedContent) {
            List<Object> cellValues = FileSystemColumnUtils.getCellValuesForContent(content, displayableTypes);
            if (content instanceof Image) {
                rows.add(new ImageRowDTO((Image) content, cellValues));
            } else if (content instanceof LocalFilesDataSource) {
                rows.add(new LocalFileDataSourceRowDTO((LocalFilesDataSource) content, cellValues));
            } else if (content instanceof LocalDirectory) {
                rows.add(new LocalDirectoryRowDTO((LocalDirectory) content, cellValues));
            } else if (content instanceof VirtualDirectory) {
                rows.add(new VirtualDirectoryRowDTO((VirtualDirectory) content, cellValues));
            } else if (content instanceof Volume) {
                rows.add(new VolumeRowDTO((Volume) content, cellValues));
            } else if (content instanceof Directory) {
                rows.add(new DirectoryRowDTO((Directory) content, cellValues));
            } else if (content instanceof Pool) {
                rows.add(new PoolRowDTO((Pool) content, cellValues));
            } else if (content instanceof SlackFile) {
                AbstractFile file = (AbstractFile) content;
                rows.add(new SlackFileRowDTO(
                        (SlackFile) file,
                        file.getId(),
                        file.getName(),
                        file.getNameExtension(),
                        getExtensionMediaType(file.getNameExtension()),
                        file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                        file.getType(),
                        cellValues));
            } else if (content instanceof LayoutFile) {
                AbstractFile file = (AbstractFile) content;
                rows.add(new LayoutFileRowDTO(
                        (LayoutFile) file,
                        file.getId(),
                        file.getName(),
                        file.getNameExtension(),
                        getExtensionMediaType(file.getNameExtension()),
                        file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                        file.getType(),
                        cellValues));
            } else if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                rows.add(new FileRowDTO(
                        file,
                        file.getId(),
                        file.getName(),
                        file.getNameExtension(),
                        getExtensionMediaType(file.getNameExtension()),
                        file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                        file.getType(),
                        cellValues));
            }
        }
        return new BaseSearchResultsDTO(FILE_SYSTEM_TYPE_ID, parentName, columnKeys, rows, FILE_SYSTEM_TYPE_ID, cacheKey.getStartItem(), contentForTable.size());
    }

    /**
     * Returns a list of paged content.
     *
     * @param contentObjects The content objects.
     * @param searchParams   The search parameters including the paging.
     *
     * @return The list of paged content.
     */
    private List<Content> getPaged(List<? extends Content> contentObjects, SearchParams<?> searchParams) {
        Stream<? extends Content> pagedArtsStream = contentObjects.stream()
                .sorted(Comparator.comparing((conent) -> conent.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedArtsStream = pagedArtsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedArtsStream.collect(Collectors.toList());
    }

    public BaseSearchResultsDTO getContentForTable(FileSystemContentSearchParam objectKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        SearchParams<FileSystemContentSearchParam> searchParams = new SearchParams<>(objectKey, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchContentForTableFromContent(searchParams));
    }

    public BaseSearchResultsDTO getContentForTable(FileSystemHostSearchParam objectKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        SearchParams<FileSystemHostSearchParam> searchParams = new SearchParams<>(objectKey, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchContentForTableFromHost(searchParams));
    }

    public BaseSearchResultsDTO getHostsForTable(FileSystemPersonSearchParam objectKey, long startItem, Long maxCount) throws ExecutionException, IllegalArgumentException {
        SearchParams<FileSystemPersonSearchParam> searchParams = new SearchParams<>(objectKey, startItem, maxCount);
        return searchParamsCache.get(searchParams, () -> fetchHostsForTable(searchParams));
    }

    private Host getHostFromDs(Content dataSource) {
        if (!(dataSource instanceof DataSource)) {
            return null;
        }

        try {
            return ((DataSource) dataSource).getHost();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "There was an error getting the host for data source with id: " + dataSource.getId(), ex);
            return null;
        }
    }

    /**
     * In instances where parents are hidden, refresh the entire tree.
     *
     * @param parentContent The parent content.
     *
     * @return True if full tree should be refreshed.
     */
    private boolean invalidatesAllFileSystem(Content parentContent) {
        if (parentContent instanceof VolumeSystem || parentContent instanceof FileSystem || parentContent instanceof Image) {
            return true;
        }

        if (parentContent instanceof Directory) {
            Directory dir = (Directory) parentContent;
            return dir.isRoot() && !dir.getName().equals(".") && !dir.getName().equals("..");
        }

        if (parentContent instanceof LocalDirectory) {
            return ((LocalDirectory) parentContent).isRoot();
        }

        return false;
    }

    @Override
    Set<DAOEvent> processEvent(PropertyChangeEvent evt) {
        Content affectedContent = null;
        Content affectedParentContent = null;
        Host affectedParentHost = null;

        Optional<Person> affectedParentPerson = Optional.empty();

        boolean refreshAllContent = false;

        Content content = DAOEventUtils.getDerivedFileContentFromFileEvent(evt);
        if (content != null) {
            Content parentContent;
            try {
                parentContent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get parent content of content with id: " + content.getId(), ex);
                return Collections.emptySet();
            }

            if (invalidatesAllFileSystem(parentContent)) {
                refreshAllContent = true;
            } else {
                affectedContent = content;
                affectedParentContent = parentContent;
            }
        } else if (evt instanceof DataSourceAddedEvent) {
            Host host = getHostFromDs(((DataSourceAddedEvent) evt).getDataSource());
            affectedParentHost = host;

        } else if (evt instanceof DataSourceNameChangedEvent) {
            Host host = getHostFromDs(((DataSourceNameChangedEvent) evt).getDataSource());
            affectedParentHost = host;

        } else if (evt instanceof HostsAddedEvent) {

        } else if (evt instanceof HostsUpdatedEvent) {

        } else if (evt instanceof HostsAddedToPersonEvent) {
            Person person = ((HostsAddedToPersonEvent) evt).getPerson();
            affectedParentPerson = Optional.of(person);
        } else if (evt instanceof HostsRemovedFromPersonEvent) {
            Person person = ((HostsRemovedFromPersonEvent) evt).getPerson();
            affectedParentPerson = Optional.of(person);
        }

        // if nothing affected, return no events
        if (!refreshAllContent && affectedContent == null && affectedParentHost == null && !affectedParentPerson.isPresent()) {
            return Collections.emptySet();
        }

        invalidateKeys(affectedParentPerson, affectedParentHost, affectedContent, refreshAllContent);

        return getDAOEvents(affectedParentPerson, affectedParentHost, affectedContent, affectedParentContent, refreshAllContent);
    }

    private Set<DAOEvent> getDAOEvents(Optional<Person> affectedPerson, Host affectedHost, Content affectedContent, Content affectedParentContent, boolean triggerFullRefresh) {
        List<DAOEvent> daoEvents = new ArrayList<>();

        if (triggerFullRefresh) {
            daoEvents.add(new FileSystemContentEvent(null, null, null));
        } else if (affectedContent != null) {
            Host parentHost = null;

            try {
                parentHost = (affectedContent instanceof DataSource)
                        ? ((DataSource) affectedContent).getHost()
                        : null;
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching content id and host id for content with id of: " + affectedContent.getId(), ex);
            }

            daoEvents.add(new FileSystemContentEvent(affectedContent, affectedParentContent == null ? null : affectedParentContent.getId(), parentHost));
        }

        if (affectedHost != null) {
            daoEvents.add(new FileSystemHostEvent(affectedHost.getHostId()));
        }

        affectedPerson.ifPresent((person) -> {
            daoEvents.add(new FileSystemPersonEvent(person == null ? null : person.getPersonId()));
        });

        List<TreeEvent> treeEvents = this.treeCounts.enqueueAll(daoEvents).stream()
                .map(daoEvt -> createTreeEvent(daoEvt, TreeDisplayCount.INDETERMINATE, false))
                .filter(evt -> evt != null)
                .collect(Collectors.toList());

        return Stream.of(daoEvents, treeEvents)
                .flatMap(lst -> lst.stream())
                .collect(Collectors.toSet());
    }

    private void invalidateKeys(Optional<Person> affectedPerson, Host affectedHost, Content affectedContent, boolean triggerFullRefresh) {
        ConcurrentMap<SearchParams<?>, ?> concurrentMap = this.searchParamsCache.asMap();
        concurrentMap.forEach((k, v) -> {
            Object searchParams = k.getParamData();
            boolean shouldInvalidate = false;
            if (searchParams instanceof FileSystemPersonSearchParam && affectedPerson.isPresent()) {
                shouldInvalidate = Objects.equals(
                        ((FileSystemPersonSearchParam) searchParams).getPersonObjectId(),
                        // to allow for null parent person
                        affectedPerson.flatMap(p -> Optional.ofNullable(p.getPersonId())).orElse(null)
                );

            } else if (searchParams instanceof FileSystemHostSearchParam && affectedHost != null) {
                shouldInvalidate = Objects.equals(
                        ((FileSystemHostSearchParam) searchParams).getHostObjectId(),
                        affectedHost.getHostId()
                );

            } else if (searchParams instanceof FileSystemContentSearchParam) {
                if (triggerFullRefresh) {
                    shouldInvalidate = true;
                } else if (affectedContent != null) {
                    shouldInvalidate = Objects.equals(
                            ((FileSystemContentSearchParam) searchParams).getContentObjectId(),
                            affectedContent.getId()
                    );
                }
            }

            if (shouldInvalidate) {
                concurrentMap.remove(k);
            }
        });
    }

    private static String getTreeContentQuery(DbType dbType, String whereQuery, boolean fetchCount, boolean hideKnown, boolean hideSlack) {
        // where query references an alias of query that has fields of par_obj_id and obj_id
        String imagePathParseSql;
        String volumeDescValue;
        switch (dbType) {
            case POSTGRESQL:
                // adapted from https://stackoverflow.com/a/25447017/2375948
                imagePathParseSql = "(SELECT a.arr[array_upper(a.arr, 1)] FROM (SELECT regexp_split_to_array(image_names.name, '[\\/\\\\]') AS arr) a)";
                volumeDescValue = "v.descr";
                break;
            case SQLITE:
            default:
                // taken from https://stackoverflow.com/a/38330814/2375948
                imagePathParseSql = "(SELECT REPLACE(image_names.name, RTRIM(image_names.name, REPLACE(REPLACE(image_names.name, '\\', ''), '/', '')), ''))";
                volumeDescValue = "v.desc";
                break;
        }

        return "WITH content_query AS (\n"
                + "  SELECT\n"
                + "    o2.*\n"
                + "    -- determine which child nodes should in turn have their children determined\n"
                + "    ,(CASE\n"
                + "        -- handle volume systems and file systems\n"
                + "        WHEN o2.object_type IN (" + TskData.ObjectType.VS.getObjectType() + ", " + TskData.ObjectType.FS.getObjectType() + ") THEN 1\n"
                + "        -- handle files under certain situations if root file\n"
                + "        WHEN o2.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " AND o2.is_displayable <> 1 THEN \n"
                + "          (SELECT\n"
                + "            (CASE \n"
                + "              WHEN \n"
                + "                -- ignore . and .. directories"
                + "                f.name NOT IN ('.', '..')\n"
                + "                -- taken from FsContent.isRoot determining if file system root file is this\n"
                + "                ((f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + " AND (SELECT fs.root_inum FROM tsk_fs_info fs WHERE fs.obj_id = f.fs_obj_id LIMIT 1) = f.meta_addr) OR \n"
                + "                -- taken from LocalDirectory.isRoot determining if file is root by seeing if parent is volume system\n"
                + "                (f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL_DIR.getFileType() + " AND (SELECT o3.type FROM tsk_objects o3 WHERE o3.obj_id = o2.par_obj_id) = " + TskData.ObjectType.VS.getObjectType() + ")) \n"
                + "              THEN 1 \n"
                + "              ELSE 0\n"
                + "            END)\n"
                + "          FROM tsk_files f WHERE f.obj_id = o2.obj_id\n"
                + "          LIMIT 1)\n"
                + "        ELSE 0\n"
                + "      END) AS is_transparent_parent\n"
                + "  FROM (\n"
                + "    SELECT \n"
                + "      o.obj_id\n"
                + "      ,o.par_obj_id\n"
                + "      ,o.type AS object_type\n"
                + "      -- determine file name based on relevant table information (i.e. going to images / files for appropriate name and concatenating a string for volume systems)\n"
                + "      ,(CASE \n"
                + "        WHEN o.type = " + TskData.ObjectType.IMG.getObjectType() + " THEN \n"
                + "          (SELECT \n"
                + "            -- this is based on how an image is created from a result set\n"
                + "            CASE \n"
                + "              WHEN image_info.display_name IS NOT NULL AND LENGTH(image_info.display_name) > 0 THEN\n"
                + "                image_info.display_name\n"
                + "              ELSE\n"
                + "                " + imagePathParseSql + "\n"
                + "              END\n"
                + "          FROM tsk_image_info image_info \n"
                + "          INNER JOIN tsk_image_names image_names\n"
                + "          ON image_info.obj_id = image_names.obj_id\n"
                + "          WHERE image_names.obj_id = o.obj_id)\n"
                + "        WHEN o.type = " + TskData.ObjectType.VOL.getObjectType() + " THEN (SELECT ('vol ' || v.addr || ' (' || " + volumeDescValue + " || ':' || v.start || '-' || (v.start + v.length) || ')') AS name FROM tsk_vs_parts v WHERE v.obj_id = o.obj_id LIMIT 1)\n"
                + "        WHEN o.type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " THEN (SELECT name FROM tsk_files f WHERE f.obj_id = o.obj_id LIMIT 1)\n"
                + "        WHEN o.type = " + TskData.ObjectType.POOL.getObjectType() + " THEN \n"
                + "          (SELECT\n"
                + "            (CASE \n"
                + "              WHEN p.pool_type = 0 THEN 'Auto detect'\n"
                + "              WHEN p.pool_type = 1 THEN 'APFS Pool'\n"
                + "              ELSE 'Unsupported'\n"
                + "            END) AS name\n"
                + "          FROM tsk_pool_info p\n"
                + "          WHERE p.obj_id = o.obj_id)\n"
                + "        ELSE NULL\n"
                + "      END) AS name\n"
                + "      ,(CASE\n"
                + "        WHEN o.type = 4 THEN \n"
                + "          (SELECT f.meta_type FROM tsk_files f WHERE o.obj_id = f.obj_id LIMIT 1)\n"
                + "        ELSE\n"
                + "          NULL\n"
                + "        END) AS meta_type"
                + "      -- content is visible if it is an image, a pool, a volume, or a file under certain situations\n"
                + "      ,(CASE \n"
                + "        WHEN o.type IN (" + TskData.ObjectType.IMG.getObjectType() + ", " + TskData.ObjectType.POOL.getObjectType() + ", " + TskData.ObjectType.VOL.getObjectType() + ") THEN 1\n"
                + "        WHEN o.type = " + TskData.ObjectType.ARTIFACT.getObjectType() + " THEN\n"
                + "          (SELECT \n"
                + "            CASE WHEN art.artifact_type_id IN (24, 13) THEN 1 ELSE 0 END\n"
                + "            FROM blackboard_artifacts art\n"
                + "            WHERE art.artifact_obj_id = o.obj_id LIMIT 1)"
                + "        WHEN o.type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " THEN \n"
                + "          (SELECT\n"
                + "            (CASE \n"
                + "              -- file is not displayable without a name\n"
                + "              WHEN f.name IS NOT NULL AND f.name NOT IN ('.', '..') AND LENGTH(f.name) > 0\n"
                + "                -- hide known files if applicable based on settings\n"
                + (hideKnown ? "                AND (f.known IS NULL OR f.known <> " + TskData.FileKnown.KNOWN + ")\n" : "")
                + "                -- hide slack files if applicable based on settings\n"
                + (hideSlack ? "                AND (f.type IS NULL OR f.type <> " + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType() + ")\n" : "")
                + "                \n"
                + "              THEN 1 \n"
                + "              ELSE 0\n"
                + "            END)\n"
                + "          FROM tsk_files f WHERE f.obj_id = o.obj_id\n"
                + "          LIMIT 1)\n"
                + "        ELSE 0 \n"
                + "      END) AS is_displayable \n"
                + "    FROM tsk_objects o \n"
                + "  ) o2\n"
                + ")\n"
                + "SELECT \n"
                + "  query.obj_id\n"
                + "  ,query.name\n"
                + "  ,query.is_transparent_parent\n"
                + (fetchCount ? "  ,query.child_count\n" : "")
                + "  ,query.object_type\n"
                + "  ,query.file_type\n"
                + "  ,query.has_tree_children\n"
                + "  ,query.meta_type\n"
                + "FROM (\n"
                + "  SELECT \n"
                + "    c.* \n"
                + "    -- get the child count to display\n"
                + (fetchCount
                        ? "    ,(SELECT SUM(\n"
                        + "      (CASE \n"
                        + "        -- determine child count by summing children that are displayable \n"
                        + "        WHEN c2.is_displayable = 1 THEN 1\n"
                        + "        -- or the total of transparent parent content's children (only one layer deep)\n"
                        + "        WHEN c2.is_transparent_parent = 1 THEN\n"
                        + "          (SELECT SUM(\n"
                        + "            CASE \n"
                        + "              WHEN c3.is_displayable = 1 THEN 1\n"
                        + "              WHEN c3.is_transparent_parent = 1 THEN\n"
                        + "                (SELECT COUNT(*) AS count FROM content_query c4 WHERE c4.par_obj_id = c3.obj_id AND (c4.is_displayable = 1 OR c4.is_transparent_parent = 1))\n"
                        + "              ELSE 0\n"
                        + "            END\n"
                        + "          ) FROM content_query c3 WHERE c3.par_obj_id = c2.obj_id)\n"
                        + "        ELSE 0\n"
                        + "      END)) FROM content_query c2\n"
                        + "    WHERE c2.par_obj_id = c.obj_id) AS child_count\n"
                        : "")
                + "    -- determine whether any of those children have children (making this expandable)\n"
                + "    ,(CASE\n"
                + "      -- all transparent parents will be resolved\n"
                + "      WHEN (c.is_transparent_parent <> 1 AND (\n"
                + "        -- determine any children of children (limit to 1, because we only need to know about 1)\n"
                + "        (SELECT COUNT(*)\n"
                + "          FROM content_query c2 \n"
                + "          WHERE c2.par_obj_id = c.obj_id\n"
                + "          AND ((\n"
                + "            -- if grandchild is immediately displayable, then child should be in tree and this should be expandable\n"
                + "            c2.is_displayable = 1\n"
                + "            AND (SELECT COUNT(*) \n"
                + "              FROM content_query c3 \n"
                + "              WHERE c3.par_obj_id = c2.obj_id \n"
                + "              AND (c3.is_displayable = 1 \n"
                + "                OR (SELECT COUNT(*)\n"
                + "                FROM content_query c4\n"
                + "                WHERE c4.par_obj_id = c3.obj_id\n"
                + "                AND c4.is_displayable = 1 LIMIT 1) > 0)\n"
                + "              LIMIT 1) > 0\n"
                + "          ) OR (\n"
                + "            -- if grandchild is a transparent parent, then check children of that child\n"
                + "            c2.is_transparent_parent = 1\n"
                + "            -- only perform check if not '.' or '..' file\n"
                + "            AND (c2.object_type <> 4 OR c2.name NOT IN ('.', '..'))\n"
                + "            AND (SELECT COUNT(*)\n"
                + "              FROM content_query c3 \n"
                + "              WHERE c3.par_obj_id = c2.obj_id\n"
                + "              AND (\n"
                + "                c3.is_displayable = 1\n"
                + "                AND ((SELECT COUNT(*) \n"
                + "                  FROM content_query c4 \n"
                + "                  WHERE c4.par_obj_id = c3.obj_id \n"
                + "                  AND (c4.is_displayable = 1 \n"
                + "                    OR (SELECT COUNT(*)\n"
                + "                    FROM content_query c5\n"
                + "                    WHERE c5.par_obj_id = c4.obj_id\n"
                + "                    AND c5.is_displayable = 1 LIMIT 1) > 0)\n"
                + "                  LIMIT 1) > 0) OR (\n"
                + "                    -- for file system children, go one level deeper (file system -> root file -> children of that)\n"
                + "                    c2.object_type = " + TskData.ObjectType.FS.getObjectType() + " AND -- handle file system children of children\n"
                + "                    ((SELECT COUNT(*) \n"
                + "                    FROM content_query c4 \n"
                + "                    INNER JOIN content_query c5 ON c4.obj_id = c5.par_obj_id\n"
                + "                    WHERE c4.par_obj_id = c3.obj_id \n"
                + "                    AND (c5.is_displayable = 1 \n"
                + "                      OR (SELECT COUNT(*)\n"
                + "                      FROM content_query c6\n"
                + "                      WHERE c6.par_obj_id = c5.obj_id\n"
                + "                      AND c6.is_displayable = 1 LIMIT 1) > 0)\n"
                + "                    LIMIT 1) > 0)\n"
                + "                  ))\n"
                + "              LIMIT 1) > 0))\n"
                + "            LIMIT 1) > 0)\n"
                + "      ) THEN 1\n"
                + "      ELSE 0\n"
                + "    END) AS has_tree_children\n"
                + "    -- determine icon to display in table based on the content\n"
                + "    ,(CASE\n"
                + "      WHEN c.object_type = " + TskData.ObjectType.IMG.getObjectType() + " OR\n"
                + "        (c.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " AND c.obj_id IN (SELECT dsi.obj_id FROM data_source_info dsi)) THEN \n"
                + "          (SELECT \n"
                + "            CASE \n"
                + "              WHEN img.type IS NULL THEN " + TreeFileType.LOCAL_FILES_DATA_SOURCE.getId() + "\n"
                + "              ELSE " + TreeFileType.IMAGE.getId() + "\n"
                + "            END\n"
                + "            FROM data_source_info AS ds\n"
                + "            LEFT JOIN tsk_image_info AS img ON ds.obj_id = img.obj_id\n"
                + "            WHERE ds.obj_id = c.obj_id\n"
                + "          )\n"
                + "      WHEN c.object_type = " + TskData.ObjectType.VOL.getObjectType() + " THEN " + TreeFileType.VOLUME.getId() + "\n"
                + "      WHEN c.object_type = " + TskData.ObjectType.POOL.getObjectType() + " THEN " + TreeFileType.POOL.getId() + "\n"
                + "      WHEN c.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " THEN\n"
                + "        (SELECT\n"
                + "          (CASE\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + " THEN\n"
                + "              (CASE \n"
                + "                WHEN f.meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT_DIR.getValue() + " THEN " + TreeFileType.VIRTUAL_DIRECTORY.getId() + "\n"
                + "                WHEN f.meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue() + " THEN " + TreeFileType.DIRECTORY.getId() + "\n"
                + "                WHEN f.dir_flags = " + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue() + " THEN " + TreeFileType.UNALLOC_FILE.getId() + "\n"
                + "                ELSE " + TreeFileType.FILE.getId() + " \n"
                + "              END)\n"
                + "            WHEN f.type IN (" + TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType()
                + ", " + TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS.getFileType()
                + ", " + TskData.TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE.getFileType() + ") THEN " + TreeFileType.UNALLOC_FILE.getId() + "\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType() + " THEN " + TreeFileType.VIRTUAL_DIRECTORY.getId() + "\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL_DIR.getFileType() + " THEN " + TreeFileType.LOCAL_DIRECTORY.getId() + "\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.getFileType() + " THEN " + TreeFileType.CARVED_FILE.getId() + "\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.getFileType() + " THEN " + TreeFileType.LOCAL_FILE.getId() + "\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.getFileType() + " THEN (\n"
                + "              CASE\n"
                + "                WHEN f.meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue() + " THEN " + TreeFileType.DERIVED_DIRECTORY.getId() + "\n"
                + "                ELSE " + TreeFileType.DERIVED_FILE.getId() + "\n"
                + "              END)\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType() + " THEN " + TreeFileType.SLACK_FILE.getId() + "\n"
                + "            ELSE " + TreeFileType.UNKNOWN.getId() + "\n"
                + "          END)\n"
                + "        FROM tsk_files f\n"
                + "        WHERE f.obj_id = c.obj_id\n"
                + "        LIMIT 1)\n"
                + "      ELSE NULL\n"
                + "    END) AS file_type\n"
                + "  FROM content_query c  \n"
                + ") query\n"
                + "-- only return displayable items with a child count (for the tree) or transparent parents that should be recursed upon\n"
                + "WHERE ((query.is_displayable = 1" + (fetchCount ? " AND query.child_count > 0" : "") + ") OR query.is_transparent_parent = 1)\n"
                + "AND " + whereQuery;
    }

    private List<FileSystemTreeItem> runTreeCountsQuery(String whereQuery, boolean fetchCount, boolean hideKnown, boolean hideSlack) throws NoCurrentCaseException, TskCoreException {
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        String sql = getTreeContentQuery(skCase.getDatabaseType(), whereQuery, fetchCount, hideKnown, hideSlack);

        List<FileSystemTreeItem> treeItems = new ArrayList<>();
        List<Long> transparentParents = new ArrayList<>();
        skCase.getCaseDbAccessManager().query(sql, (rs) -> {
            try {
                while (rs.next()) {
                    if (rs.getByte("is_transparent_parent") > 0) {
                        //if (!fetchCount || rs.getLong("child_count") > 0) {
                        transparentParents.add(rs.getLong("obj_id"));
                        //}
                    } else {
                        short metaTypeNum = rs.getShort("meta_type");
                        TSK_FS_META_TYPE_ENUM metaType = rs.wasNull() ? null : TSK_FS_META_TYPE_ENUM.valueOf(metaTypeNum);

                        treeItems.add(new FileSystemTreeItem(
                                rs.getLong("obj_id"),
                                rs.getString("name"),
                                (fetchCount ? TreeDisplayCount.getDeterminate(rs.getLong("child_count")) : TreeDisplayCount.NOT_SHOWN),
                                TreeFileType.valueOf(rs.getShort("file_type")),
                                metaType,
                                rs.getByte("has_tree_children") < 1
                        ));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "There was an error fetching results for query:\n" + sql, ex);
            }
        });

        for (Long transparentParent : transparentParents) {
            treeItems.addAll(runTreeCountsQuery("query.par_obj_id = " + transparentParent, fetchCount, hideKnown, hideSlack));
        }

        return treeItems;
    }

    private List<FileSystemTreeItem> fetchTreeContent(String whereQuery, boolean fetchCount) throws NoCurrentCaseException, TskCoreException {
        List<FileSystemTreeItem> toRet = runTreeCountsQuery(whereQuery, fetchCount, UserPreferences.hideKnownFilesInDataSourcesTree(), UserPreferences.hideSlackFilesInDataSourcesTree());
        toRet.sort(TREE_ITEM_COMPARATOR);
        return toRet;
    }

    private TreeFileType getContentType(Content content) {
        // GVDTODO handle
        return null;
    }

    public static Comparator<FileSystemTreeItem> getTreeItemComparator() {
        return TREE_ITEM_COMPARATOR;
    }

    /**
     * Get all data sources belonging to a given host.
     *
     * @param host The host.
     *
     * @return Results containing all data sources for the given host.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<FileSystemContentSearchParam> getDataSourcesForHost(Host host) throws ExecutionException {
        try {
            @SuppressWarnings("unchecked")
            List<TreeItemDTO<FileSystemContentSearchParam>> treeItemRows
                    = (List<TreeItemDTO<FileSystemContentSearchParam>>) (List<? extends TreeItemDTO<FileSystemContentSearchParam>>) fetchTreeContent(
                            "query.obj_id IN (SELECT dsi.obj_id FROM data_source_info dsi WHERE dsi.host_id = " + host.getHostId() + ")", false);
            return new TreeResultsDTO<>(treeItemRows);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching images for host with ID " + host.getHostId(), ex);
        }
    }

    /**
     * Create results for a single given data source ID (not its children).
     *
     * @param dataSourceObjId The data source object ID.
     *
     * @return Results containing just this data source.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<FileSystemContentSearchParam> getSingleDataSource(long dataSourceObjId) throws ExecutionException {
        try {
            @SuppressWarnings("unchecked")
            List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows
                    = (List<TreeItemDTO<FileSystemContentSearchParam>>) (List<? extends TreeItemDTO<FileSystemContentSearchParam>>) fetchTreeContent(
                            "query.obj_id = " + dataSourceObjId, false);
            return new TreeResultsDTO<>(treeItemRows);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data source with ID " + dataSourceObjId, ex);
        }
    }

    /**
     * Get the children that will be displayed in the tree for a given content
     * ID.
     *
     * @param contentId Object ID of parent content.
     *
     * @return The results.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<FileSystemContentSearchParam> getDisplayableContentChildren(Long contentId) throws ExecutionException {
        try {

            List<Content> treeChildren = FileSystemColumnUtils.getVisibleTreeNodeChildren(contentId);

            @SuppressWarnings("unchecked")
            List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows
                    = (List<TreeItemDTO<FileSystemContentSearchParam>>) (List<? extends TreeItemDTO<FileSystemContentSearchParam>>) fetchTreeContent(
                            "query.par_obj_id = " + contentId, true);
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }

    /**
     * Get display name for the given content.
     *
     * @param content The content.
     *
     * @return Display name for the content.
     */
    private String getNameForContent(Content content) {
        if (content instanceof Volume) {
            return FileSystemColumnUtils.getVolumeDisplayName((Volume) content);
        }
        return content.getName();
    }

    private TreeEvent createTreeEvent(DAOEvent daoEvent, TreeDisplayCount count, boolean fullRefresh) {

        if (daoEvent instanceof FileSystemContentEvent) {
            FileSystemContentEvent contentEvt = (FileSystemContentEvent) daoEvent;
            Content child = contentEvt.getContent();

            return new FileSystemTreeEvent(
                    contentEvt.getParentObjId(),
                    contentEvt.getParentHost(),
                    // in order to trigger a refresh
                    new FileSystemTreeItem(
                            child.getId(),
                            child.getName(),
                            count,
                            getContentType(child),
                            ((contentEvt.getContent() instanceof AbstractFile) ? ((AbstractFile) contentEvt.getContent()).getMetaType() : null),
                            null),
                    fullRefresh);

        } else if (daoEvent instanceof FileSystemHostEvent) {

        } else if (daoEvent instanceof FileSystemPersonEvent) {

        }

        return null;
    }

    @Override
    void clearCaches() {
        this.searchParamsCache.invalidateAll();
        handleIngestComplete();
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return treeCounts.flushEvents().stream()
                .map(daoEvt -> createTreeEvent(daoEvt, TreeDisplayCount.UNSPECIFIED, true))
                .filter(evt -> evt != null)
                .collect(Collectors.toSet());
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return treeCounts.getEventTimeouts().stream()
                .map(daoEvt -> createTreeEvent(daoEvt, TreeDisplayCount.UNSPECIFIED, true))
                .filter(evt -> evt != null)
                .collect(Collectors.toSet());
    }

    public static class DataSourceRefreshTreeEvent extends TreeEvent {

        public DataSourceRefreshTreeEvent(boolean refresh) {
            super(null, refresh);
        }

    }

    public enum TreeFileType {
        IMAGE(1),
        LOCAL_FILES_DATA_SOURCE(2),
        POOL(3),
        VOLUME(4),
        DIRECTORY(5),
        UNALLOC_DIRECTORY(6),
        LOCAL_DIRECTORY(7),
        VIRTUAL_DIRECTORY(8),
        DERIVED_DIRECTORY(9),
        FILE(10),
        LOCAL_FILE(11),
        UNALLOC_FILE(12),
        CARVED_FILE(13),
        DERIVED_FILE(14),
        SLACK_FILE(15),
        UNKNOWN(16);

        private static final Map<Short, TreeFileType> MAPPING = Stream.of(TreeFileType.values())
                .collect(Collectors.toMap(tft -> tft.getId(), tft -> tft));

        private short id;

        TreeFileType(int id) {
            this.id = (short) id;
        }

        public short getId() {
            return id;
        }

        public static TreeFileType valueOf(short shortVal) {
            return MAPPING.get(shortVal);
        }

    }

    public static class FileSystemTreeItem extends TreeItemDTO<FileSystemContentSearchParam> {

        private final TskData.TSK_FS_META_TYPE_ENUM metaType;
        private final TreeFileType contentType;
        private final Boolean leaf;

        FileSystemTreeItem(FileSystemTreeItem treeItem, TreeDisplayCount count) {
            this(treeItem.getSearchParams().getContentObjectId(), treeItem.getDisplayName(), count, treeItem.getContentType(), treeItem.getMetaType(), treeItem.isLeaf());
        }

        FileSystemTreeItem(
                long contentId,
                String displayName,
                TreeDisplayCount count,
                TreeFileType contentType,
                TskData.TSK_FS_META_TYPE_ENUM metaType,
                Boolean isLeaf) {

            super(FileSystemContentSearchParam.getTypeId(), new FileSystemContentSearchParam(contentId), contentId, displayName, count);
            this.metaType = metaType;
            this.leaf = isLeaf;
            this.contentType = contentType;
        }

        public TskData.TSK_FS_META_TYPE_ENUM getMetaType() {
            return metaType;
        }

        public TreeFileType getContentType() {
            return contentType;
        }

        /**
         * @return Whether or not this tree item is a leaf. If null, leaf status
         *         is unknown.
         */
        public Boolean isLeaf() {
            return leaf;
        }
    }

    public static class FileSystemTreeEvent extends TreeEvent {

        private final Long parentContentId;
        private final Host parentHost;
        private final FileSystemTreeItem itemRecord;

        FileSystemTreeEvent(Long parentContentId, Host parentHost, FileSystemTreeItem itemRecord, boolean refreshRequired) {
            super(itemRecord, refreshRequired);
            this.parentContentId = parentContentId;
            this.parentHost = parentHost;
            this.itemRecord = itemRecord;
        }

        public Long getParentContentId() {
            return parentContentId;
        }

        public Host getParentHost() {
            return parentHost;
        }

        @Override
        public FileSystemTreeItem getItemRecord() {
            // override to be typed and contain extra information
            return itemRecord;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 31 * hash + Objects.hashCode(this.itemRecord);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FileSystemTreeEvent other = (FileSystemTreeEvent) obj;
            if (!Objects.equals(this.itemRecord, other.itemRecord)) {
                return false;
            }
            return true;
        }

    }

    /**
     * Handles fetching and paging of data for file types by mime type.
     */
    public static class FileSystemFetcher extends DAOFetcher<FileSystemContentSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public FileSystemFetcher(FileSystemContentSearchParam params) {
            super(params);
        }

        protected FileSystemDAO getDAO() {
            return MainDAO.getInstance().getFileSystemDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getContentForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isSystemContentInvalidating(this.getParameters(), evt);
        }
    }

    public static class FileSystemHostFetcher extends DAOFetcher<FileSystemHostSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public FileSystemHostFetcher(FileSystemHostSearchParam params) {
            super(params);
        }

        protected FileSystemDAO getDAO() {
            return MainDAO.getInstance().getFileSystemDAO();
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx) throws ExecutionException {
            return getDAO().getContentForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize);
        }

        @Override
        public boolean isRefreshRequired(DAOEvent evt) {
            return getDAO().isSystemHostInvalidating(this.getParameters(), evt);
        }
    }
}
