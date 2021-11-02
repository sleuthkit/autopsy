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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

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
            if (FileSystemColumnUtils.isDisplayable(content)) {
                contentForTable.add(content);
            }
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
    
    
    private BaseSearchResultsDTO fetchContentForTable(SearchParams<?> cacheKey, List<Content> contentForTable,
            String parentName) throws NoCurrentCaseException, TskCoreException {
        // Ensure consistent columns for each page by doing this before paging
        List<FileSystemColumnUtils.ContentType> displayableTypes = FileSystemColumnUtils.getDisplayableTypesForContentList(contentForTable);
        
        List<Content> pagedContent = getPaged(contentForTable, cacheKey);
        List<ColumnKey> columnKeys = FileSystemColumnUtils.getColumnKeysForContent(displayableTypes);
        
        List<RowDTO> rows = new ArrayList<>();
        for (Content content : pagedContent) {
            List<Object> cellValues = FileSystemColumnUtils.getCellValuesForContent(content, displayableTypes);
            rows.add(new BaseRowDTO(cellValues, FILE_SYSTEM_TYPE_ID, content.getId()));
        }
        return new BaseSearchResultsDTO(FILE_SYSTEM_TYPE_ID, parentName, columnKeys, rows, cacheKey.getStartItem(), contentForTable.size());
    } 
    
    /**
     * Returns a list of paged content.
     *
     * @param contentObjects  The content objects.
     * @param searchParams    The search parameters including the paging.
     *
     * @return The list of paged artifacts.
     */
    List<Content> getPaged(List<? extends Content> contentObjects, SearchParams<?> searchParams) {
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
}
