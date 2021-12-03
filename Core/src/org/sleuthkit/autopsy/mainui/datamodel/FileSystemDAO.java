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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
import org.sleuthkit.autopsy.coreutils.Logger;
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
import org.sleuthkit.datamodel.AbstractContent;
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
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 *
 */
public class FileSystemDAO extends AbstractDAO {

    private static final Logger logger = Logger.getLogger(FileSystemDAO.class.getName());

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;

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
                        FileSystemColumnUtils.convertDotDirName(file),
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
        if (parentContent instanceof VolumeSystem || parentContent instanceof FileSystem) {
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
        Content affectedParentContent = null;
        Host affectedParentHost = null;

        // GVDTODO person parents and parent of persons not handled yet
        // optional present but null indicates no person parent
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
                affectedParentContent = parentContent;
            }
        } else if (evt instanceof DataSourceAddedEvent) {
            Host host = getHostFromDs(((DataSourceAddedEvent) evt).getDataSource());
            affectedParentHost = host;

        } else if (evt instanceof DataSourceNameChangedEvent) {
            Host host = getHostFromDs(((DataSourceNameChangedEvent) evt).getDataSource());
            affectedParentHost = host;

        } else if (evt instanceof HostsAddedEvent) {
            // GVDTODO how best to handle host added?
        } else if (evt instanceof HostsUpdatedEvent) {
            // GVDTODO how best to handle host updated?
        } else if (evt instanceof HostsAddedToPersonEvent) {
            Person person = ((HostsAddedToPersonEvent) evt).getPerson();
            affectedParentPerson = Optional.of(person);
        } else if (evt instanceof HostsRemovedFromPersonEvent) {
            Person person = ((HostsRemovedFromPersonEvent) evt).getPerson();
            affectedParentPerson = Optional.of(person);
        }

        // if nothing affected, return no events
        if (!refreshAllContent && affectedParentContent == null && affectedParentHost == null && !affectedParentPerson.isPresent()) {
            return Collections.emptySet();
        }

        invalidateKeys(affectedParentPerson, affectedParentHost, affectedParentContent, refreshAllContent);

        return getDAOEvents(affectedParentPerson, affectedParentHost, affectedParentContent, refreshAllContent);
    }

    private Set<DAOEvent> getDAOEvents(Optional<Person> affectedPerson, Host affectedHost, Content affectedContent, boolean triggerFullRefresh) {
        List<DAOEvent> daoEvents = new ArrayList<>();

        if (triggerFullRefresh) {
            daoEvents.add(new FileSystemContentEvent(null, null, null));
        } else if (affectedContent != null) {
            Long parentContentId = null;
            Host parentHost = null;

            try {
                parentContentId = (affectedContent instanceof AbstractContent)
                        ? ((AbstractContent) affectedContent).getParentId().orElse(null)
                        : null;

                parentHost = (affectedContent instanceof DataSource)
                        ? ((DataSource) affectedContent).getHost()
                        : null;
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching content id and host id for content with id of: " + affectedContent.getId(), ex);
            }

            daoEvents.add(new FileSystemContentEvent(affectedContent, parentContentId, parentHost));
        }

        if (affectedHost != null) {
            daoEvents.add(new FileSystemHostEvent(affectedHost.getHostId()));
        }

        affectedPerson.ifPresent((person) -> {
            daoEvents.add(new FileSystemPersonEvent(person == null ? null : person.getPersonId()));
        });

        List<TreeEvent> treeEvents = this.treeCounts.enqueueAll(daoEvents).stream()
                .map(daoEvt -> createTreeEvent(daoEvt, TreeDisplayCount.INDETERMINATE, false))
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
            List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows = new ArrayList<>();
            for (DataSource ds : Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getDataSourcesForHost(host)) {
                treeItemRows.add(createDisplayableContentTreeItem(ds, TreeDisplayCount.NOT_SHOWN));
            }
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
            List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows = new ArrayList<>();
            DataSource ds = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSourceObjId);
            treeItemRows.add(createDisplayableContentTreeItem(ds, TreeDisplayCount.NOT_SHOWN));
            return new TreeResultsDTO<>(treeItemRows);
        } catch (NoCurrentCaseException | TskCoreException | TskDataException ex) {
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

            List<TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam>> treeItemRows = new ArrayList<>();
            for (Content child : treeChildren) {
                Long countForNode = null;
                if ((child instanceof AbstractFile)
                        && !(child instanceof LocalFilesDataSource)) {
                    countForNode = getContentForTable(new FileSystemContentSearchParam(child.getId()), 0, null).getTotalResultsCount();
                }
                TreeDisplayCount displayCount = countForNode == null ? TreeDisplayCount.NOT_SHOWN : TreeDisplayCount.getDeterminate(countForNode);
                treeItemRows.add(createDisplayableContentTreeItem(child, displayCount));
            }
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching data artifact counts.", ex);
        }
    }

    private TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam> createDisplayableContentTreeItem(Content child, TreeDisplayCount displayCount) {
        return new TreeResultsDTO.TreeItemDTO<>(
                FileSystemContentSearchParam.getTypeId(),
                new FileSystemContentSearchParam(child == null ? null : child.getId()),
                child,
                child == null ? null : getNameForContent(child),
                displayCount
        );
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
        } else if (content instanceof AbstractFile) {
            return FileSystemColumnUtils.convertDotDirName((AbstractFile) content);
        }
        return content.getName();
    }

    private TreeEvent createTreeEvent(DAOEvent daoEvent, TreeDisplayCount count, boolean fullRefresh) {

        if (daoEvent instanceof FileSystemContentEvent) {
            FileSystemContentEvent contentEvt = (FileSystemContentEvent) daoEvent;
            return new FileSystemTreeEvent(
                    contentEvt.getParentObjId(),
                    contentEvt.getParentHost(),
                    createDisplayableContentTreeItem(contentEvt.getContent(), count),
                    fullRefresh);
        } else if (daoEvent instanceof FileSystemHostEvent) {
            // GVDTODO not currently integrated into tree
        } else if (daoEvent instanceof FileSystemPersonEvent) {
            // GVDTODO not currently integrated into tree
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
                .collect(Collectors.toSet());
    }

    @Override
    Set<TreeEvent> shouldRefreshTree() {
        return treeCounts.getEventTimeouts().stream()
                .map(daoEvt -> createTreeEvent(daoEvt, TreeDisplayCount.UNSPECIFIED, true))
                .collect(Collectors.toSet());
    }

    public static class DataSourceRefreshTreeEvent extends TreeEvent {

        public DataSourceRefreshTreeEvent(boolean refresh) {
            super(null, refresh);
        }

    }

    public static class FileSystemTreeEvent extends TreeEvent {

        private final Long parentContentId;
        private final Host parentHost;
        private final TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam> itemRecord;

        FileSystemTreeEvent(Long parentContentId, Host parentHost, TreeItemDTO<FileSystemContentSearchParam> itemRecord, boolean refreshRequired) {
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
        public TreeResultsDTO.TreeItemDTO<FileSystemContentSearchParam> getItemRecord() {
            // override to be typed to FileSystemContentSearchParam
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
