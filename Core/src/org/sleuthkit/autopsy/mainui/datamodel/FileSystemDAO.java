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
import java.text.MessageFormat;
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
import org.apache.commons.lang3.StringUtils;
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
import org.sleuthkit.datamodel.BlackboardArtifact;
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

        return StringUtils.defaultString(fs1.getDisplayName()).compareToIgnoreCase(StringUtils.defaultString(fs2.getDisplayName()));
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

    // TODO change to long
    private final Cache<String, List<TreeItemRecord>> treeItemCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private final Set<String> dotDirs = ImmutableSet.of(".", "..");

    private final Set<TreeFileType> dirTypes = ImmutableSet.of(
            TreeFileType.DIRECTORY,
            TreeFileType.LOCAL_DIRECTORY,
            TreeFileType.UNALLOC_DIRECTORY,
            TreeFileType.VIRTUAL_DIRECTORY
    );

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

        if (affectedHost != null) {
            this.treeItemCache.invalidate(getHostChildrenClause(affectedHost.getHostId()));
        }

        if (affectedContent != null) {
            this.treeItemCache.invalidate(getContentParentClause(affectedContent.getId()));

            if (affectedContent instanceof DataSource) {
                this.treeItemCache.invalidate(getDataSourceClause(affectedContent.getId()));
            }
        }
    }

    private static String getTreeContentQuery(DbType dbType, String whereQuery, boolean hideKnown, boolean hideSlack) {
        // where query references an alias of query that has fields of par_obj_id and obj_id
        String volumeDescValue;
        switch (dbType) {
            case POSTGRESQL:
                volumeDescValue = "v.descr";
                break;
            case SQLITE:
            default:
                volumeDescValue = "v.desc";
                break;
        }

        return "\n  query.*\n"
                + "FROM (\n"
                + "  SELECT\n"
                + "    o2.obj_id\n"
                + "    ,o2.meta_type\n"
                + "    ,o2.is_displayable\n"
                + "	,o2.par_obj_id\n"
                + "    -- determine file name based on relevant table information (i.e. going to images / files for appropriate name and concatenating a string for volume systems)\n"
                + "    ,(CASE \n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.IMG.getObjectType() + " THEN \n"
                + "        (SELECT \n"
                + "          -- this is based on how an image is created from a result set\n"
                + "          CASE \n"
                + "            WHEN image_info.display_name IS NOT NULL AND LENGTH(image_info.display_name) > 0 THEN\n"
                + "              image_info.display_name\n"
                + "            ELSE\n"
                + "              (SELECT REPLACE(image_names.name, RTRIM(image_names.name, REPLACE(REPLACE(image_names.name, '\\', ''), '/', '')), ''))\n"
                + "            END\n"
                + "        FROM tsk_image_info image_info \n"
                + "        INNER JOIN tsk_image_names image_names\n"
                + "        ON image_info.obj_id = image_names.obj_id\n"
                + "        WHERE image_names.obj_id = o2.obj_id)\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.VOL.getObjectType() + " THEN (SELECT ('vol ' || v.addr || ' (' || " + volumeDescValue + " || ':' || v.start || '-' || (v.start + v.length) || ')') AS name FROM tsk_vs_parts v WHERE v.obj_id = o2.obj_id LIMIT 1)\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " THEN (SELECT name FROM tsk_files f WHERE f.obj_id = o2.obj_id LIMIT 1)\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.POOL.getObjectType() + " THEN \n"
                + "        (SELECT\n"
                + "          (CASE \n"
                + "            WHEN p.pool_type = 0 THEN 'Auto detect'\n"
                + "            WHEN p.pool_type = 1 THEN 'APFS Pool'\n"
                + "            ELSE 'Unsupported'\n"
                + "          END) AS name\n"
                + "        FROM tsk_pool_info p\n"
                + "        WHERE p.obj_id = o2.obj_id)\n"
                + "      ELSE NULL\n"
                + "    END) AS name\n"
                + "    -- determine icon to display in table based on the content\n"
                + "    ,(CASE\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.IMG.getObjectType() + " OR\n"
                + "        (o2.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " AND o2.obj_id IN (SELECT dsi.obj_id FROM data_source_info dsi)) THEN \n"
                + "          (SELECT \n"
                + "            CASE \n"
                + "              WHEN img.type IS NULL THEN " + TreeFileType.LOCAL_FILES_DATA_SOURCE.getId() + "\n"
                + "              ELSE " + TreeFileType.IMAGE.getId() + "\n"
                + "            END\n"
                + "            FROM data_source_info AS ds\n"
                + "            LEFT JOIN tsk_image_info AS img ON ds.obj_id = img.obj_id\n"
                + "            WHERE ds.obj_id = o2.obj_id\n"
                + "          )\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.ARTIFACT.getObjectType() + " THEN " + TreeFileType.ARTIFACT.getId() + "\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.VOL.getObjectType() + " THEN " + TreeFileType.VOLUME.getId() + "\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.POOL.getObjectType() + " THEN " + TreeFileType.POOL.getId() + "\n"
                + "      WHEN o2.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " THEN\n"
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
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.getFileType() + " THEN " + TreeFileType.FILE.getId() + "\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.getFileType() + " THEN (\n"
                + "              CASE\n"
                + "                WHEN f.meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue() + " THEN " + TreeFileType.DIRECTORY.getId() + "\n"
                + "                ELSE " + TreeFileType.FILE.getId() + "\n"
                + "              END)\n"
                + "            WHEN f.type = " + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType() + " THEN " + TreeFileType.FILE.getId() + "\n"
                + "            ELSE " + TreeFileType.UNKNOWN.getId() + "\n"
                + "          END)\n"
                + "        FROM tsk_files f\n"
                + "        WHERE f.obj_id = o2.obj_id\n"
                + "        LIMIT 1)\n"
                + "      ELSE NULL\n"
                + "    END) AS tree_type\n"
                + "    -- determine which child nodes should in turn have their children determined\n"
                + "    ,(CASE\n"
                + "        -- handle volume systems and file systems\n"
                + "        WHEN o2.object_type IN (" + TskData.ObjectType.VS.getObjectType() + ", " + TskData.ObjectType.FS.getObjectType() + ") THEN 1\n"
                + "        -- handle files under certain situations if root file\n"
                + "        WHEN o2.object_type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " AND o2.is_displayable <> 1 THEN \n"
                + "          (SELECT\n"
                + "            (CASE \n"
                + "              WHEN \n"
                + "                -- ignore . and .. directories\n"
                + "                (o2.file_name IS NULL OR o2.file_name NOT IN ('.', '..')) AND \n"
                + "                -- taken from LocalDirectory.isRoot determining if file is root by seeing if parent is volume system\n"
                + "                ((o2.file_type = " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL_DIR.getFileType() + " AND (SELECT o3.type FROM tsk_objects o3 WHERE o3.obj_id = o2.par_obj_id) = 1)\n"
                + "                -- taken from FsContent.isRoot determining if file system root file is this\n"
                + "                OR ((o2.file_type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + " AND o2.root_inum = o2.meta_addr))) \n"
                + "              THEN 1 \n"
                + "              ELSE 0\n"
                + "            END)\n"
                + "          FROM tsk_files f WHERE f.obj_id = o2.obj_id\n"
                + "          LIMIT 1)\n"
                + "        ELSE 0\n"
                + "      END) AS is_transparent_parent\n"
                + "  FROM (\n"
                + "    SELECT \n"
                + "      o.*\n"
                + "      -- content is visible if it is an image, a pool, a volume, or a file/artifact under certain situations\n"
                + "      ,(CASE \n"
                + "        WHEN o.object_type IN (" + TskData.ObjectType.IMG.getObjectType() + ", " + TskData.ObjectType.POOL.getObjectType() + ", " + TskData.ObjectType.VOL.getObjectType() + ") \n"
                + "          OR o.artifact_type_id IN (" + BlackboardArtifact.Type.TSK_MESSAGE.getTypeID() + ", " + BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() + ") \n"
                + "          -- file is not displayable without a name\n"
                + "          OR (o.file_name IS NOT NULL AND LENGTH(o.file_name) > 0 \n"
                + "            -- hide known files if applicable based on settings\n"
                + (hideKnown ? "            AND (o.known IS NULL OR o.known <> " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")\n" : "")
                + "            -- hide slack files if applicable based on settings\n"
                + (hideSlack ? "            AND (o.file_type IS NULL OR o.file_type <> " + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType() + ")) \n" : "")
                + "        THEN 1 \n"
                + "        ELSE 0\n"
                + "      END) AS is_displayable \n"
                + "    FROM (\n"
                + "        SELECT \n"
                + "            inner_o.obj_id\n"
                + "            ,inner_o.type AS object_type\n"
                + "            ,inner_o.par_obj_id\n"
                + "            ,f.type AS file_type\n"
                + "            ,f.meta_type\n"
                + "            ,f.meta_addr\n"
                + "            ,f.name AS file_name\n"
                + "            ,f.dir_flags\n"
                + "            ,f.known\n"
                + "            ,f.fs_obj_id\n"
                + "            ,art.artifact_type_id\n"
                + "            ,fs.root_inum\n"
                + "        FROM tsk_objects inner_o\n"
                + "        LEFT JOIN tsk_files f ON inner_o.type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " AND inner_o.obj_id = f.obj_id\n"
                + "        LEFT JOIN tsk_fs_info fs ON inner_o.type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + " AND fs.obj_id = f.fs_obj_id\n"
                + "        LEFT JOIN blackboard_artifacts art ON inner_o.type = " + TskData.ObjectType.ARTIFACT.getObjectType() + " AND inner_o.obj_id = art.artifact_obj_id\n"
                + "    ) o \n"
                + "  ) o2\n"
                + ") query\n"
                + "WHERE (query.is_displayable = 1 OR query.is_transparent_parent = 1) \n"
                + "AND " + whereQuery;
    }

    /**
     * Fetch tree records for the file tree. This query doesn't include the
     * count or whether or not it has tree children. That is handled in a
     * different query.
     *
     * @param whereQuery The query statement where query can be used as an alias
     *                   (i.e. query.par_obj_id = ...)
     * @param hideKnown  Whether or not to hide known files.
     * @param hideSlack  Whether or not to hide slack files.
     *
     * @return The found tree items.
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    private List<TreeItemRecord> fetchRecords(String whereQuery, boolean hideKnown, boolean hideSlack) throws NoCurrentCaseException, TskCoreException {
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        String sql = getTreeContentQuery(skCase.getDatabaseType(), whereQuery, hideKnown, hideSlack);

        List<TreeItemRecord> treeItems = new ArrayList<>();
        List<Long> transparentParents = new ArrayList<>();
        skCase.getCaseDbAccessManager().select(sql, (rs) -> {
            try {
                while (rs.next()) {
                    // if transparent parent, the node itself shouldn't be shown but it's children should
                    if (rs.getByte("is_transparent_parent") > 0) {
                        transparentParents.add(rs.getLong("obj_id"));
                    } else {
                        // otherwise add the item
                        short metaTypeNum = rs.getShort("meta_type");
                        TSK_FS_META_TYPE_ENUM metaType = rs.wasNull() ? null : TSK_FS_META_TYPE_ENUM.valueOf(metaTypeNum);

                        treeItems.add(new TreeItemRecord(
                                rs.getLong("par_obj_id"),
                                rs.getLong("obj_id"),
                                rs.getString("name"),
                                TreeFileType.valueOf(rs.getShort("tree_type")),
                                metaType
                        ));
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "There was an error fetching results for query:\n" + sql, ex);
            }
        });

        // if there are items that shouldn't be shown directly, but the children should, fetch and add those
        if (!transparentParents.isEmpty()) {
            treeItems.addAll(fetchRecords(MessageFormat.format(" query.par_obj_id IN ({0}) ",
                    transparentParents.stream().map(l -> Long.toString(l)).collect(Collectors.joining(", "))),
                    hideKnown, hideSlack));
        }

        return treeItems;
    }

    /**
     * Fetches tree cound data to display in the tree.
     *
     * @param whereQuery The query statement where query can be used as an alias
     *                   (i.e. query.par_obj_id = ...)
     * @param fetchCount Whether or not to include the count for the item.
     *
     * @return The list of tree items to display in proper order.
     *
     * @throws ExecutionException
     */
    private List<FileSystemTreeItem> fetchTreeContent(String whereQuery, boolean fetchCount) throws ExecutionException {
        boolean hideKnown = UserPreferences.hideKnownFilesInDataSourcesTree();
        boolean hideSlack = UserPreferences.hideSlackFilesInDataSourcesTree();

        List<FileSystemTreeItem> toRet = new ArrayList<>();

        for (TreeItemRecord record : this.treeItemCache.get(whereQuery, () -> fetchRecords(whereQuery, hideKnown, hideSlack))) {
            // determine if they are items that are not visible in the tree
            if ((dotDirs.contains(record.getName()) && dirTypes.contains(record.getTreeType())) || TreeFileType.ARTIFACT.equals(record.getTreeType())) {
                continue;
            }

            // otherwise, get grand children to determine count and whether or not this child is expandable 
            List<TreeItemRecord> grandChildren = this.treeItemCache.get(getContentParentClause(record.getObjId()),
                    () -> fetchRecords(getContentParentClause(record.getObjId()), hideKnown, hideSlack));

            if (grandChildren.isEmpty()) {
                continue;
            }

            // this child is expandable in the tree if one of the grandchildren has displayable children
            boolean isLeaf = true;
            for (TreeItemRecord grandChild : grandChildren) {
                if (!this.treeItemCache.get(getContentParentClause(grandChild.getObjId()),
                        () -> fetchRecords(getContentParentClause(grandChild.getObjId()), hideKnown, hideSlack)).isEmpty()) {
                    isLeaf = false;
                    break;
                }
            }

            toRet.add(new FileSystemTreeItem(
                    record.getObjId(),
                    record.getName(),
                    fetchCount ? TreeDisplayCount.getDeterminate(grandChildren.size()) : TreeDisplayCount.NOT_SHOWN,
                    record.getTreeType(),
                    record.getMetaType(),
                    isLeaf
            ));
        }

        toRet.sort(TREE_ITEM_COMPARATOR);
        return toRet;
    }

    /**
     * The clause to use when searching for children of a content parent for the
     * tree.
     *
     * @param parObjId The parent object id.
     *
     * @return The clause to use.
     */
    private String getContentParentClause(long parObjId) {
        return " query.par_obj_id = " + parObjId;
    }

    /**
     * The clause to use when searching for children of a host for the tree.
     *
     * @param hostId The host id.
     *
     * @return The clause to use.
     */
    private String getHostChildrenClause(long hostId) {
        return " query.obj_id IN (SELECT dsi.obj_id FROM data_source_info dsi WHERE dsi.host_id = " + hostId + ")";
    }

    /**
     * The clause to use when searching for data on a data source.
     *
     * @param dsId The data source id.
     *
     * @return The clause to use.
     */
    private String getDataSourceClause(long dsId) {
        return " query.obj_id = " + dsId;
    }

    /**
     * Determines the tree content type based on the content object.
     *
     * @param content The content type.
     *
     * @return The tree type.
     */
    private TreeFileType getContentType(Content content) {
        if (content instanceof Image) {
            return TreeFileType.IMAGE;
        } else if (content instanceof Volume) {
            return TreeFileType.VOLUME;
        } else if (content instanceof Pool) {
            return TreeFileType.POOL;
        } else if (content instanceof LocalFilesDataSource) {
            return TreeFileType.LOCAL_FILES_DATA_SOURCE;
        } else if (content instanceof LocalDirectory) {
            return TreeFileType.LOCAL_DIRECTORY;
        } else if (content instanceof VirtualDirectory) {
            return TreeFileType.VIRTUAL_DIRECTORY;
        } else if (content instanceof Volume) {
            return TreeFileType.VOLUME;
        } else if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            boolean isUnalloc = file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
            boolean isCarved = isUnalloc && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED);

            if (file.isDir()) {
                return isUnalloc ? TreeFileType.UNALLOC_DIRECTORY : TreeFileType.DIRECTORY;
            } else {
                if (isCarved) {
                    return TreeFileType.CARVED_FILE;
                } else if (isUnalloc) {
                    return TreeFileType.UNALLOC_FILE;
                } else {
                    return TreeFileType.FILE;
                }
            }
        } else {
            return TreeFileType.UNKNOWN;
        }
    }

    /**
     * Orders FileSystemTreeItem's to be displayed in the tree.
     *
     * @return The tree comparator to handle sorting.
     */
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
        @SuppressWarnings("unchecked")
        List<TreeItemDTO<FileSystemContentSearchParam>> treeItemRows
                = (List<TreeItemDTO<FileSystemContentSearchParam>>) (List<? extends TreeItemDTO<FileSystemContentSearchParam>>) fetchTreeContent(
                        getHostChildrenClause(host.getHostId()), false);
        return new TreeResultsDTO<>(treeItemRows);
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
        @SuppressWarnings("unchecked")
        List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows
                = (List<TreeItemDTO<FileSystemContentSearchParam>>) (List<? extends TreeItemDTO<FileSystemContentSearchParam>>) fetchTreeContent(
                        getDataSourceClause(dataSourceObjId), false);
        return new TreeResultsDTO<>(treeItemRows);
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
        @SuppressWarnings("unchecked")
        List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows
                = (List<TreeItemDTO<FileSystemContentSearchParam>>) (List<? extends TreeItemDTO<FileSystemContentSearchParam>>) fetchTreeContent(
                        getContentParentClause(contentId), true);
        return new TreeResultsDTO<>(treeItemRows);
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
        this.treeItemCache.invalidateAll();
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

    private static class TreeItemRecord {

        private final long parObjId;
        private final long objId;
        private final String name;
        private final TreeFileType treeType;
        private final TSK_FS_META_TYPE_ENUM metaType;

        public TreeItemRecord(long parObjId, long objId, String name, TreeFileType treeType, TSK_FS_META_TYPE_ENUM metaType) {
            this.parObjId = parObjId;
            this.objId = objId;
            this.name = name;
            this.treeType = treeType;
            this.metaType = metaType;
        }

        public long getParObjId() {
            return parObjId;
        }

        public long getObjId() {
            return objId;
        }

        public String getName() {
            return name;
        }

        public TreeFileType getTreeType() {
            return treeType;
        }

        public TSK_FS_META_TYPE_ENUM getMetaType() {
            return metaType;
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
        FILE(9),
        UNALLOC_FILE(10),
        CARVED_FILE(11),
        ARTIFACT(12),
        UNKNOWN(13);

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
