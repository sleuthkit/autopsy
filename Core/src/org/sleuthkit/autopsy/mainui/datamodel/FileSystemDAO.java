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
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.DirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.ImageRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.VolumeRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.LocalDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.LocalFileDataSourceRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.VirtualDirectoryRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.LayoutFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.SlackFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.PoolRowDTO;
import static org.sleuthkit.autopsy.mainui.datamodel.ViewsDAO.getExtensionMediaType;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
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
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/**
 *
 */
public class FileSystemDAO {

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;
    private final Cache<SearchParams<?>, BaseSearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();

    private static final String FILE_SYSTEM_TYPE_ID = "FILE_SYSTEM";

    private static FileSystemDAO instance = null;

    synchronized static FileSystemDAO getInstance() {
        if (instance == null) {
            instance = new FileSystemDAO();
        }
        return instance;
    }
    
    public boolean isSystemContentInvalidating(FileSystemContentSearchParam key, Content eventContent) {
        if(!(eventContent instanceof Content)) {
            return false;
        }
        
        try {
            return key.getContentObjectId() != eventContent.getParent().getId();
        } catch (TskCoreException ex) {
            // There is nothing we can do with the exception.
            return false;
        }
    }
    
    public boolean isSystemHostInvalidating(FileSystemHostSearchParam key, Host eventHost) {
        if(!(eventHost instanceof Host)) {
            return false;
        }
        
        return key.getHostObjectId() != eventHost.getHostId();
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
            contentForTable.addAll(FileSystemColumnUtils.getNextDisplayableContent(content));
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
        return new BaseSearchResultsDTO(FILE_SYSTEM_TYPE_ID, parentName, columnKeys, rows, cacheKey.getStartItem(), hostsForTable.size());
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
        return new BaseSearchResultsDTO(FILE_SYSTEM_TYPE_ID, parentName, columnKeys, rows, cacheKey.getStartItem(), contentForTable.size());
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

    public BaseSearchResultsDTO getContentForTable(FileSystemContentSearchParam objectKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {

        SearchParams<FileSystemContentSearchParam> searchParams = new SearchParams<>(objectKey, startItem, maxCount);
        if (hardRefresh) {
            searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchContentForTableFromContent(searchParams));
    }

    public BaseSearchResultsDTO getContentForTable(FileSystemHostSearchParam objectKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {

        SearchParams<FileSystemHostSearchParam> searchParams = new SearchParams<>(objectKey, startItem, maxCount);
        if (hardRefresh) {
            searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchContentForTableFromHost(searchParams));
    }

    public BaseSearchResultsDTO getHostsForTable(FileSystemPersonSearchParam objectKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {

        SearchParams<FileSystemPersonSearchParam> searchParams = new SearchParams<>(objectKey, startItem, maxCount);
        if (hardRefresh) {
            searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchHostsForTable(searchParams));
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

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getFileSystemDAO().getContentForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            Content content = getContentFromEvt(evt);
            if (content == null) {
                return false;
            }

            return MainDAO.getInstance().getFileSystemDAO().isSystemContentInvalidating(getParameters(), content);
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

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getFileSystemDAO().getContentForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            // TODO implement the method for determining if 
            // a refresh is needed.
            return false;
        }
    }
}
