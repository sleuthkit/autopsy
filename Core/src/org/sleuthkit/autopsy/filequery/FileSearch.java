/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Files;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.coreutils.VideoUtils.getVideoFileInTempDir;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileSize;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.autopsy.filequery.FileSearchData.Frequency;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Main class to perform the file search.
 */
class FileSearch {

    private final static Logger logger = Logger.getLogger(FileSearch.class.getName());
    private static final int MAXIMUM_CACHE_SIZE = 10;
    private static final Cache<SearchKey, Map<GroupKey, List<ResultFile>>> searchCache = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHE_SIZE)
            .build();

    /**
     * Run the file search and returns the SearchResults object for debugging.
     * Caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     *
     * @return The raw search results
     *
     * @throws FileSearchException
     */
    static SearchResults runFileSearchDebug(String userName,
            List<FileSearchFiltering.FileFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        // Make a list of attributes that we want to add values for. This ensures the
        // ResultFile objects will have all needed fields set when it's time to group
        // and sort them. For example, if we're grouping by central repo frequency, we need
        // to make sure we've loaded those values before grouping.
        List<AttributeType> attributesNeededForGroupingOrSorting = new ArrayList<>();
        attributesNeededForGroupingOrSorting.add(groupAttributeType);
        attributesNeededForGroupingOrSorting.addAll(fileSortingMethod.getRequiredAttributes());

        // Run the queries for each filter
        List<ResultFile> resultFiles = FileSearchFiltering.runQueries(filters, caseDb, centralRepoDb);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, resultFiles, caseDb, centralRepoDb);

        // Collect everything in the search results
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        searchResults.add(resultFiles);

        // Sort and group the results
        searchResults.sortGroupsAndFiles();
        Map<GroupKey, List<ResultFile>> resultHashMap = searchResults.toLinkedHashMap();
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        synchronized (searchCache) {
            searchCache.put(searchKey, resultHashMap);
        }
        return searchResults;
    }

    /**
     * Run the file search to get the group keys and sizes. Clears cache of
     * search results, caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws FileSearchException
     */
    static Map<GroupKey, Integer> getGroupSizes(String userName,
            List<FileSearchFiltering.FileFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        Map<GroupKey, List<ResultFile>> searchResults = runFileSearch(userName, filters,
                groupAttributeType, groupSortingType, fileSortingMethod, caseDb, centralRepoDb);
        LinkedHashMap<GroupKey, Integer> groupSizes = new LinkedHashMap<>();
        for (GroupKey groupKey : searchResults.keySet()) {
            groupSizes.put(groupKey, searchResults.get(groupKey).size());
        }
        return groupSizes;
    }

    /**
     * Get the files from the specified group from the cache, if the the group
     * was not cached perform a search caching the groups.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param groupKey           The key which uniquely identifies the group to
     *                           get entries from
     * @param startingEntry      The first entry to return
     * @param numberOfEntries    The number of entries to return
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws FileSearchException
     */
    static List<ResultFile> getFilesInGroup(String userName,
            List<FileSearchFiltering.FileFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            GroupKey groupKey,
            int startingEntry,
            int numberOfEntries,
            SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
        //the group should be in the cache at this point
        List<ResultFile> filesInGroup = null;
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        Map<GroupKey, List<ResultFile>> resultsMap;
        synchronized (searchCache) {
            resultsMap = searchCache.getIfPresent(searchKey);
        }
        if (resultsMap != null) {
            filesInGroup = resultsMap.get(groupKey);
        }
        List<ResultFile> page = new ArrayList<>();
        if (filesInGroup == null) {
            logger.log(Level.INFO, "Group {0} was not cached, performing search to cache all groups again", groupKey);
            runFileSearch(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod, caseDb, centralRepoDb);
            synchronized (searchCache) {
                resultsMap = searchCache.getIfPresent(searchKey.getKeyString());
            }
            if (resultsMap != null) {
                filesInGroup = resultsMap.get(groupKey);
            }
            if (filesInGroup == null) {
                logger.log(Level.WARNING, "Group {0} did not exist in cache or new search results", groupKey);
                return page; //group does not exist
            }
        }
        // Check that there is data after the starting point
        if (filesInGroup.size() < startingEntry) {
            logger.log(Level.WARNING, "Group only contains {0} files, starting entry of {1} is too large.", new Object[]{filesInGroup.size(), startingEntry});
            return page;
        }
        // Add files to the page
        for (int i = startingEntry; (i < startingEntry + numberOfEntries)
                && (i < filesInGroup.size()); i++) {
            page.add(filesInGroup.get(i));
        }
        return page;
    }

    /**
     * Run the file search. Caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws FileSearchException
     */
    private static Map<GroupKey, List<ResultFile>> runFileSearch(String userName,
            List<FileSearchFiltering.FileFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {

        // Make a list of attributes that we want to add values for. This ensures the
        // ResultFile objects will have all needed fields set when it's time to group
        // and sort them. For example, if we're grouping by central repo frequency, we need
        // to make sure we've loaded those values before grouping.
        List<AttributeType> attributesNeededForGroupingOrSorting = new ArrayList<>();
        attributesNeededForGroupingOrSorting.add(groupAttributeType);
        attributesNeededForGroupingOrSorting.addAll(fileSortingMethod.getRequiredAttributes());

        // Run the queries for each filter
        List<ResultFile> resultFiles = FileSearchFiltering.runQueries(filters, caseDb, centralRepoDb);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, resultFiles, caseDb, centralRepoDb);

        // Collect everything in the search results
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        searchResults.add(resultFiles);
        Map<GroupKey, List<ResultFile>> resultHashMap = searchResults.toLinkedHashMap();
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        synchronized (searchCache) {
            searchCache.put(searchKey, resultHashMap);
        }
        // Return a version of the results in general Java objects
        return resultHashMap;
    }

    /**
     * Add any attributes corresponding to the attribute list to the given
     * result files. For example, specifying the KeywordListAttribute will
     * populate the list of keyword set names in the ResultFile objects.
     *
     * @param attrs         The attributes to add to the list of result files
     * @param resultFiles   The result files
     * @param caseDb        The case database
     * @param centralRepoDb The central repository database. Can be null if not
     *                      needed.
     *
     * @throws FileSearchException
     */
    private static void addAttributes(List<AttributeType> attrs, List<ResultFile> resultFiles, SleuthkitCase caseDb, EamDb centralRepoDb)
            throws FileSearchException {
        for (AttributeType attr : attrs) {
            attr.addAttributeToResultFiles(resultFiles, caseDb, centralRepoDb);
        }
    }

    /**
     * Computes the CR frequency of all the given hashes and updates the list of
     * files.
     *
     * @param hashesToLookUp Hashes to find the frequency of
     * @param currentFiles   List of files to update with frequencies
     */
    private static void computeFrequency(Set<String> hashesToLookUp, List<ResultFile> currentFiles, EamDb centralRepoDb) {

        if (hashesToLookUp.isEmpty()) {
            return;
        }

        String hashes = String.join("','", hashesToLookUp);
        hashes = "'" + hashes + "'";
        try {
            CorrelationAttributeInstance.Type attributeType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
            String tableName = EamDbUtil.correlationTypeToInstanceTableName(attributeType);

            String selectClause = " value, COUNT(value) FROM "
                    + "(SELECT DISTINCT case_id, value FROM " + tableName
                    + " WHERE value IN ("
                    + hashes
                    + ")) AS foo GROUP BY value";

            FrequencyCallback callback = new FrequencyCallback(currentFiles);
            centralRepoDb.processSelectClause(selectClause, callback);

        } catch (EamDbException ex) {
            logger.log(Level.WARNING, "Error getting frequency counts from Central Repository", ex); // NON-NLS
        }

    }

    private static String createSetNameClause(List<ResultFile> files,
            int artifactTypeID, int setNameAttrID) throws FileSearchException {

        // Concatenate the object IDs in the list of files
        String objIdList = ""; // NON-NLS
        for (ResultFile file : files) {
            if (!objIdList.isEmpty()) {
                objIdList += ","; // NON-NLS
            }
            objIdList += "\'" + file.getFirstInstance().getId() + "\'"; // NON-NLS
        }

        // Get pairs of (object ID, set name) for all files in the list of files that have
        // the given artifact type.
        return "blackboard_artifacts.obj_id AS object_id, blackboard_attributes.value_text AS set_name "
                + "FROM blackboard_artifacts "
                + "INNER JOIN blackboard_attributes ON blackboard_artifacts.artifact_id=blackboard_attributes.artifact_id "
                + "WHERE blackboard_attributes.artifact_type_id=\'" + artifactTypeID + "\' "
                + "AND blackboard_attributes.attribute_type_id=\'" + setNameAttrID + "\' "
                + "AND blackboard_artifacts.obj_id IN (" + objIdList + ") "; // NON-NLS
    }

    /**
     * Get the video thumbnails for a file which exists in a
     * VideoThumbnailsWrapper and update the VideoThumbnailsWrapper to include
     * them.
     *
     * @param thumbnailWrapper the object which contains the file to generate
     *                         thumbnails for.
     *
     */
    @NbBundle.Messages({"# {0} - file name",
        "FileSearch.genVideoThumb.progress.text=extracting temporary file {0}"})
    static void getVideoThumbnails(VideoThumbnailsWrapper thumbnailWrapper) {
        AbstractFile file = thumbnailWrapper.getResultFile().getFirstInstance();
        //Currently this method always creates the thumbnails 
        java.io.File tempFile;
        try {
            tempFile = getVideoFileInTempDir(file);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Exception while getting open case.", ex); //NON-NLS
            int[] framePositions = new int[]{
                0,
                0,
                0,
                0};
            thumbnailWrapper.setThumbnails(createDefaultThumbnailList(), framePositions);
            return;
        }
        if (tempFile.exists() == false || tempFile.length() < file.getSize()) {
            ProgressHandle progress = ProgressHandle.createHandle(Bundle.FileSearch_genVideoThumb_progress_text(file.getName()));
            progress.start(100);
            try {
                Files.createParentDirs(tempFile);
                if (Thread.interrupted()) {
                    int[] framePositions = new int[]{
                        0,
                        0,
                        0,
                        0};
                    thumbnailWrapper.setThumbnails(createDefaultThumbnailList(), framePositions);
                    return;
                }
                ContentUtils.writeToFile(file, tempFile, progress, null, true);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error extracting temporary file for " + file.getParentPath() + "/" + file.getName(), ex); //NON-NLS
            } finally {
                progress.finish();
            }
        }
        VideoCapture videoFile = new VideoCapture(); // will contain the video
        BufferedImage bufferedImage = null;

        try {
            if (!videoFile.open(tempFile.toString())) {
                logger.log(Level.WARNING, "Error opening {0} for preview generation.", file.getParentPath() + "/" + file.getName()); //NON-NLS
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                thumbnailWrapper.setThumbnails(createDefaultThumbnailList(), framePositions);
                return;
            }
            double fps = videoFile.get(5); // gets frame per second
            double totalFrames = videoFile.get(7); // gets total frames
            if (fps <= 0 || totalFrames <= 0) {
                logger.log(Level.WARNING, "Error getting fps or total frames for {0}", file.getParentPath() + "/" + file.getName()); //NON-NLS
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                thumbnailWrapper.setThumbnails(createDefaultThumbnailList(), framePositions);
                return;
            }
            if (Thread.interrupted()) {
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                thumbnailWrapper.setThumbnails(createDefaultThumbnailList(), framePositions);
                return;
            }

            double duration = 1000 * (totalFrames / fps); //total milliseconds

            int[] framePositions = new int[]{
                (int) (duration * .01),
                (int) (duration * .25),
                (int) (duration * .5),
                (int) (duration * .75),};

            Mat imageMatrix = new Mat();
            List<Image> videoThumbnails = new ArrayList<>();
            for (int i = 0; i < framePositions.length; i++) {
                if (!videoFile.set(0, framePositions[i])) {
                    logger.log(Level.WARNING, "Error seeking to " + framePositions[i] + "ms in {0}", file.getParentPath() + "/" + file.getName()); //NON-NLS
                    // If we can't set the time, continue to the next frame position and try again.

                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    continue;
                }
                // Read the frame into the image/matrix.
                if (!videoFile.read(imageMatrix)) {
                    logger.log(Level.WARNING, "Error reading frame at " + framePositions[i] + "ms from {0}", file.getParentPath() + "/" + file.getName()); //NON-NLS
                    // If the image is bad for some reason, continue to the next frame position and try again.
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    continue;
                }
                // If the image is empty, return since no buffered image can be created.
                if (imageMatrix.empty()) {
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    continue;
                }

                int matrixColumns = imageMatrix.cols();
                int matrixRows = imageMatrix.rows();

                // Convert the matrix that contains the frame to a buffered image.
                if (bufferedImage == null) {
                    bufferedImage = new BufferedImage(matrixColumns, matrixRows, BufferedImage.TYPE_3BYTE_BGR);
                }

                byte[] data = new byte[matrixRows * matrixColumns * (int) (imageMatrix.elemSize())];
                imageMatrix.get(0, 0, data); //copy the image to data

                if (imageMatrix.channels() == 3) {
                    for (int k = 0; k < data.length; k += 3) {
                        byte temp = data[k];
                        data[k] = data[k + 2];
                        data[k + 2] = temp;
                    }
                }

                bufferedImage.getRaster().setDataElements(0, 0, matrixColumns, matrixRows, data);
                if (Thread.interrupted()) {
                    thumbnailWrapper.setThumbnails(videoThumbnails, framePositions);
                    return;
                }
                videoThumbnails.add(ScalrWrapper.resizeFast(bufferedImage, ImageUtils.ICON_SIZE_LARGE));
            }
            thumbnailWrapper.setThumbnails(videoThumbnails, framePositions);
        } finally {
            videoFile.release(); // close the file}
        }
    }

    /**
     * Private helper method for creating video thumbnails, for use when no
     * thumbnails are created.
     *
     * @return List containing the default thumbnail.
     */
    private static List<Image> createDefaultThumbnailList() {
        List<Image> videoThumbnails = new ArrayList<>();
        videoThumbnails.add(ImageUtils.getDefaultThumbnail());
        videoThumbnails.add(ImageUtils.getDefaultThumbnail());
        videoThumbnails.add(ImageUtils.getDefaultThumbnail());
        videoThumbnails.add(ImageUtils.getDefaultThumbnail());
        return videoThumbnails;
    }

    private FileSearch() {
        // Class should not be instantiated
    }

    /**
     * Base class for the grouping attributes.
     */
    abstract static class AttributeType {

        /**
         * For a given file, return the key for the group it belongs to for this
         * attribute type.
         *
         * @param file the result file to be grouped
         *
         * @return the key for the group this file goes in
         */
        abstract GroupKey getGroupKey(ResultFile file);

        /**
         * Add any extra data to the ResultFile object from this attribute.
         *
         * @param files         The list of files to enhance
         * @param caseDb        The case database
         * @param centralRepoDb The central repository database. Can be null if
         *                      not needed.
         *
         * @throws FileSearchException
         */
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb, EamDb centralRepoDb) throws FileSearchException {
            // Default is to do nothing
        }
    }

    /**
     * The key used for grouping for each attribute type.
     */
    abstract static class GroupKey implements Comparable<GroupKey> {

        /**
         * Get the string version of the group key for display. Each display
         * name should correspond to a unique GroupKey object.
         *
         * @return The display name for this key
         */
        abstract String getDisplayName();

        /**
         * Subclasses must implement equals().
         *
         * @param otherKey
         *
         * @return true if the keys are equal, false otherwise
         */
        @Override
        abstract public boolean equals(Object otherKey);

        /**
         * Subclasses must implement hashCode().
         *
         * @return the hash code
         */
        @Override
        abstract public int hashCode();

        /**
         * It should not happen with the current setup, but we need to cover the
         * case where two different GroupKey subclasses are compared against
         * each other. Use a lexicographic comparison on the class names.
         *
         * @param otherGroupKey The other group key
         *
         * @return result of alphabetical comparison on the class name
         */
        int compareClassNames(GroupKey otherGroupKey) {
            return this.getClass().getName().compareTo(otherGroupKey.getClass().getName());
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    /**
     * Attribute for grouping/sorting by file size
     */
    static class FileSizeAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FileSizeGroupKey(file);
        }
    }

    /**
     * Key representing a file size group
     */
    private static class FileSizeGroupKey extends GroupKey {

        private final FileSize fileSize;

        FileSizeGroupKey(ResultFile file) {
            fileSize = FileSize.fromSize(file.getFirstInstance().getSize());
        }

        @Override
        String getDisplayName() {
            return fileSize.toString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileSizeGroupKey) {
                FileSizeGroupKey otherFileSizeGroupKey = (FileSizeGroupKey) otherGroupKey;
                return Integer.compare(fileSize.getRanking(), otherFileSizeGroupKey.fileSize.getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FileSizeGroupKey)) {
                return false;
            }

            FileSizeGroupKey otherFileSizeGroupKey = (FileSizeGroupKey) otherKey;
            return fileSize.equals(otherFileSizeGroupKey.fileSize);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileSize.getRanking());
        }
    }

    /**
     * Attribute for grouping/sorting by parent path
     */
    static class ParentPathAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new ParentPathGroupKey(file);
        }
    }

    /**
     * Key representing a parent path group
     */
    private static class ParentPathGroupKey extends GroupKey {

        private String parentPath;
        private Long parentID;

        ParentPathGroupKey(ResultFile file) {
            if (file.getFirstInstance().getParentPath() != null) {

                try {
                    parentPath = file.getFirstInstance().getParent().getUniquePath();
                    parentID = file.getFirstInstance().getParent().getId();
                } catch (TskCoreException ingored) {
                    parentPath = file.getFirstInstance().getParentPath();
                    parentID = -1L;
                }
            } else {
                parentPath = ""; // NON-NLS
                parentID = -1L;
            }
        }

        @Override
        String getDisplayName() {
            return parentPath;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof ParentPathGroupKey) {
                ParentPathGroupKey otherParentPathGroupKey = (ParentPathGroupKey) otherGroupKey;
                int comparisonResult = parentPath.compareTo(otherParentPathGroupKey.parentPath);
                if (comparisonResult == 0) {
                    comparisonResult = parentID.compareTo(otherParentPathGroupKey.parentID);
                }
                return comparisonResult;
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof ParentPathGroupKey)) {
                return false;
            }

            ParentPathGroupKey otherParentPathGroupKey = (ParentPathGroupKey) otherKey;
            return parentPath.equals(otherParentPathGroupKey.parentPath) && parentID.equals(otherParentPathGroupKey.parentID);
        }

        @Override
        public int hashCode() {
            int hashCode = 11;
            hashCode = 61 * hashCode + Objects.hash(parentPath);
            hashCode = 61 * hashCode + Objects.hash(parentID);
            return hashCode;
        }
    }

    /**
     * Attribute for grouping/sorting by data source
     */
    static class DataSourceAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new DataSourceGroupKey(file);
        }
    }

    /**
     * Key representing a data source group
     */
    private static class DataSourceGroupKey extends GroupKey {

        private final long dataSourceID;
        private String displayName;

        @NbBundle.Messages({
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "FileSearch.DataSourceGroupKey.datasourceAndID={0}(ID: {1})",
            "# {0} - Data source ID",
            "FileSearch.DataSourceGroupKey.idOnly=Data source (ID: {0})"})
        DataSourceGroupKey(ResultFile file) {
            dataSourceID = file.getFirstInstance().getDataSourceObjectId();

            try {
                // The data source should be cached so this won't actually be a database query.
                Content ds = file.getFirstInstance().getDataSource();
                displayName = Bundle.FileSearch_DataSourceGroupKey_datasourceAndID(ds.getName(), ds.getId());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error looking up data source with ID " + dataSourceID, ex); // NON-NLS
                displayName = Bundle.FileSearch_DataSourceGroupKey_idOnly(dataSourceID);
            }
        }

        @Override
        String getDisplayName() {
            return displayName;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof DataSourceGroupKey) {
                DataSourceGroupKey otherDataSourceGroupKey = (DataSourceGroupKey) otherGroupKey;
                return Long.compare(dataSourceID, otherDataSourceGroupKey.dataSourceID);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof DataSourceGroupKey)) {
                return false;
            }

            DataSourceGroupKey otherDataSourceGroupKey = (DataSourceGroupKey) otherKey;
            return dataSourceID == otherDataSourceGroupKey.dataSourceID;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataSourceID);
        }
    }

    /**
     * Attribute for grouping/sorting by file type
     */
    static class FileTypeAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FileTypeGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {
            for (ResultFile file : files) {
                if (file.getFileType().equals(FileType.OTHER)) {
                    file.setFileType(FileType.fromMIMEtype(file.getFirstInstance().getMIMEType()));
                }
            }
        }
    }

    /**
     * Key representing a file type group
     */
    private static class FileTypeGroupKey extends GroupKey {

        private final FileType fileType;

        FileTypeGroupKey(ResultFile file) {
            fileType = file.getFileType();
        }

        @Override
        String getDisplayName() {
            return fileType.toString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileTypeGroupKey) {
                FileTypeGroupKey otherFileTypeGroupKey = (FileTypeGroupKey) otherGroupKey;
                return Integer.compare(fileType.getRanking(), otherFileTypeGroupKey.fileType.getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FileTypeGroupKey)) {
                return false;
            }

            FileTypeGroupKey otherFileTypeGroupKey = (FileTypeGroupKey) otherKey;
            return fileType.equals(otherFileTypeGroupKey.fileType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileType.getRanking());
        }
    }

    /**
     * Attribute for grouping/sorting by keyword lists
     */
    static class KeywordListAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new KeywordListGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, keyword list name) for all files in the list of files that have
            // keyword list hits.
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            SetKeywordListNamesCallback callback = new SetKeywordListNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up keyword list attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the keyword list names to the list of ResultFile
         * objects.
         */
        private static class SetKeywordListNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add keyword list names to
             */
            SetKeywordListNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String keywordListName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addKeywordListName(keywordListName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get keyword list names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Key representing a keyword list group
     */
    private static class KeywordListGroupKey extends GroupKey {

        private final List<String> keywordListNames;
        private final String keywordListNamesString;

        @NbBundle.Messages({
            "FileSearch.KeywordListGroupKey.noKeywords=None"})
        KeywordListGroupKey(ResultFile file) {
            keywordListNames = file.getKeywordListNames();

            if (keywordListNames.isEmpty()) {
                keywordListNamesString = Bundle.FileSearch_KeywordListGroupKey_noKeywords();
            } else {
                keywordListNamesString = String.join(",", keywordListNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return keywordListNamesString;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof KeywordListGroupKey) {
                KeywordListGroupKey otherKeywordListNamesGroupKey = (KeywordListGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (keywordListNames.isEmpty()) {
                    if (otherKeywordListNamesGroupKey.keywordListNames.isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherKeywordListNamesGroupKey.keywordListNames.isEmpty()) {
                    return -1;
                }

                return keywordListNamesString.compareTo(otherKeywordListNamesGroupKey.keywordListNamesString);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof KeywordListGroupKey)) {
                return false;
            }

            KeywordListGroupKey otherKeywordListGroupKey = (KeywordListGroupKey) otherKey;
            return keywordListNamesString.equals(otherKeywordListGroupKey.keywordListNamesString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keywordListNamesString);
        }
    }

    /**
     * Attribute for grouping/sorting by frequency in the central repository
     */
    static class FrequencyAttribute extends AttributeType {

        static final int BATCH_SIZE = 50; // Number of hashes to look up at one time

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FrequencyGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {

            if (centralRepoDb == null) {
                throw new FileSearchException("Central Repository is not enabled - can not add frequency data"); // NON-NLS
            }

            // Set frequency in batches
            Set<String> hashesToLookUp = new HashSet<>();
            List<ResultFile> currentFiles = new ArrayList<>();
            for (ResultFile file : files) {
                if (file.getFrequency() == Frequency.UNKNOWN
                        && file.getFirstInstance().getMd5Hash() != null
                        && !file.getFirstInstance().getMd5Hash().isEmpty()) {
                    hashesToLookUp.add(file.getFirstInstance().getMd5Hash());
                    currentFiles.add(file);
                }

                if (hashesToLookUp.size() >= BATCH_SIZE) {
                    computeFrequency(hashesToLookUp, currentFiles, centralRepoDb);

                    hashesToLookUp.clear();
                    currentFiles.clear();
                }
            }
            computeFrequency(hashesToLookUp, currentFiles, centralRepoDb);
        }
    }

    /**
     * Callback to use with findInterCaseValuesByCount which generates a list of
     * values for common property search
     */
    private static class FrequencyCallback implements InstanceTableCallback {

        private final List<ResultFile> files;

        private FrequencyCallback(List<ResultFile> files) {
            this.files = new ArrayList<>(files);
        }

        @Override
        public void process(ResultSet resultSet) {
            try {

                while (resultSet.next()) {
                    String hash = resultSet.getString(1);
                    int count = resultSet.getInt(2);
                    for (Iterator<ResultFile> iterator = files.iterator(); iterator.hasNext();) {
                        ResultFile file = iterator.next();
                        if (file.getFirstInstance().getMd5Hash().equalsIgnoreCase(hash)) {
                            file.setFrequency(Frequency.fromCount(count));
                            iterator.remove();
                        }
                    }
                }

                // The files left had no matching entries in the CR, so mark them as unique
                for (ResultFile file : files) {
                    file.setFrequency(Frequency.UNIQUE);
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error getting frequency counts from Central Repository", ex); // NON-NLS
            }
        }
    }

    /**
     * Key representing a central repository frequency group
     */
    private static class FrequencyGroupKey extends GroupKey {

        private final Frequency frequency;

        FrequencyGroupKey(ResultFile file) {
            frequency = file.getFrequency();
        }

        @Override
        String getDisplayName() {
            return frequency.toString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FrequencyGroupKey) {
                FrequencyGroupKey otherFrequencyGroupKey = (FrequencyGroupKey) otherGroupKey;
                return Integer.compare(frequency.getRanking(), otherFrequencyGroupKey.frequency.getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FrequencyGroupKey)) {
                return false;
            }

            FrequencyGroupKey otherFrequencyGroupKey = (FrequencyGroupKey) otherKey;
            return frequency.equals(otherFrequencyGroupKey.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(frequency.getRanking());
        }
    }

    /**
     * Attribute for grouping/sorting by hash set lists
     */
    static class HashHitsAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new HashHitsGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, hash set name) for all files in the list of files that have
            // hash set hits.
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            HashSetNamesCallback callback = new HashSetNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up hash set attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the hash set names to the list of ResultFile objects.
         */
        private static class HashSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add hash set names to
             */
            HashSetNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String hashSetName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addHashSetName(hashSetName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get hash set names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Key representing a hash hits group
     */
    private static class HashHitsGroupKey extends GroupKey {

        private final List<String> hashSetNames;
        private final String hashSetNamesString;

        @NbBundle.Messages({
            "FileSearch.HashHitsGroupKey.noHashHits=None"})
        HashHitsGroupKey(ResultFile file) {
            hashSetNames = file.getHashSetNames();

            if (hashSetNames.isEmpty()) {
                hashSetNamesString = Bundle.FileSearch_HashHitsGroupKey_noHashHits();
            } else {
                hashSetNamesString = String.join(",", hashSetNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return hashSetNamesString;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof HashHitsGroupKey) {
                HashHitsGroupKey otherHashHitsGroupKey = (HashHitsGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (hashSetNames.isEmpty()) {
                    if (otherHashHitsGroupKey.hashSetNames.isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherHashHitsGroupKey.hashSetNames.isEmpty()) {
                    return -1;
                }

                return hashSetNamesString.compareTo(otherHashHitsGroupKey.hashSetNamesString);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof HashHitsGroupKey)) {
                return false;
            }

            HashHitsGroupKey otherHashHitsGroupKey = (HashHitsGroupKey) otherKey;
            return hashSetNamesString.equals(otherHashHitsGroupKey.hashSetNamesString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hashSetNamesString);
        }
    }

    /**
     * Attribute for grouping/sorting by interesting item set lists
     */
    static class InterestingItemAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new InterestingItemGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, interesting item set name) for all files in the list of files that have
            // interesting file set hits.
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            InterestingFileSetNamesCallback callback = new InterestingFileSetNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up interesting file set attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the interesting file set names to the list of
         * ResultFile objects.
         */
        private static class InterestingFileSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add interesting file set
             *                    names to
             */
            InterestingFileSetNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String setName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addInterestingSetName(setName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get interesting file set names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Key representing a interesting item set group
     */
    private static class InterestingItemGroupKey extends GroupKey {

        private final List<String> interestingItemSetNames;
        private final String interestingItemSetNamesString;

        @NbBundle.Messages({
            "FileSearch.InterestingItemGroupKey.noSets=None"})
        InterestingItemGroupKey(ResultFile file) {
            interestingItemSetNames = file.getInterestingSetNames();

            if (interestingItemSetNames.isEmpty()) {
                interestingItemSetNamesString = Bundle.FileSearch_InterestingItemGroupKey_noSets();
            } else {
                interestingItemSetNamesString = String.join(",", interestingItemSetNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return interestingItemSetNamesString;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof InterestingItemGroupKey) {
                InterestingItemGroupKey otherInterestingItemGroupKey = (InterestingItemGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (this.interestingItemSetNames.isEmpty()) {
                    if (otherInterestingItemGroupKey.interestingItemSetNames.isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherInterestingItemGroupKey.interestingItemSetNames.isEmpty()) {
                    return -1;
                }

                return interestingItemSetNamesString.compareTo(otherInterestingItemGroupKey.interestingItemSetNamesString);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof InterestingItemGroupKey)) {
                return false;
            }

            InterestingItemGroupKey otherInterestingItemGroupKey = (InterestingItemGroupKey) otherKey;
            return interestingItemSetNamesString.equals(otherInterestingItemGroupKey.interestingItemSetNamesString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(interestingItemSetNamesString);
        }
    }

    /**
     * Attribute for grouping/sorting by objects detected
     */
    static class ObjectDetectedAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new ObjectDetectedGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, object type name) for all files in the list of files that have
            // objects detected
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID());

            ObjectDetectedNamesCallback callback = new ObjectDetectedNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up object detected attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the object type names to the list of ResultFile
         * objects.
         */
        private static class ObjectDetectedNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add object detected names to
             */
            ObjectDetectedNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String setName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addObjectDetectedName(setName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get object detected names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Key representing an object detected group
     */
    private static class ObjectDetectedGroupKey extends GroupKey {

        private final List<String> objectDetectedNames;
        private final String objectDetectedNamesString;

        @NbBundle.Messages({
            "FileSearch.ObjectDetectedGroupKey.noSets=None"})
        ObjectDetectedGroupKey(ResultFile file) {
            objectDetectedNames = file.getObjectDetectedNames();

            if (objectDetectedNames.isEmpty()) {
                objectDetectedNamesString = Bundle.FileSearch_ObjectDetectedGroupKey_noSets();
            } else {
                objectDetectedNamesString = String.join(",", objectDetectedNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return objectDetectedNamesString;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof ObjectDetectedGroupKey) {
                ObjectDetectedGroupKey otherObjectDetectedGroupKey = (ObjectDetectedGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (this.objectDetectedNames.isEmpty()) {
                    if (otherObjectDetectedGroupKey.objectDetectedNames.isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherObjectDetectedGroupKey.objectDetectedNames.isEmpty()) {
                    return -1;
                }

                return objectDetectedNamesString.compareTo(otherObjectDetectedGroupKey.objectDetectedNamesString);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof ObjectDetectedGroupKey)) {
                return false;
            }

            ObjectDetectedGroupKey otherObjectDetectedGroupKey = (ObjectDetectedGroupKey) otherKey;
            return objectDetectedNamesString.equals(otherObjectDetectedGroupKey.objectDetectedNamesString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectDetectedNamesString);
        }
    }

    /**
     * Attribute for grouping/sorting by tag name
     */
    static class FileTagAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new FileTagGroupKey(file);
        }

        @Override
        void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                EamDb centralRepoDb) throws FileSearchException {

            try {
                for (ResultFile resultFile : files) {
                    List<ContentTag> contentTags = caseDb.getContentTagsByContent(resultFile.getFirstInstance());

                    for (ContentTag tag : contentTags) {
                        resultFile.addTagName(tag.getName().getDisplayName());
                    }
                }
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up file tag attributes", ex); // NON-NLS
            }
        }
    }

    /**
     * Represents a key for a specific search for a specific user.
     */
    private static class SearchKey implements Comparable<SearchKey> {

        private final String keyString;

        /**
         * Construct a new SearchKey with all information that defines a search.
         *
         * @param userName           The name of the user performing the search.
         * @param filters            The FileFilters being used for the search.
         * @param groupAttributeType The AttributeType to group by.
         * @param groupSortingType   The algorithm to sort the groups by.
         * @param fileSortingMethod  The method to sort the files by.
         */
        SearchKey(String userName, List<FileSearchFiltering.FileFilter> filters,
                AttributeType groupAttributeType,
                FileGroup.GroupSortingAlgorithm groupSortingType,
                FileSorter.SortingMethod fileSortingMethod) {
            StringBuilder searchStringBuilder = new StringBuilder();
            searchStringBuilder.append(userName);
            for (FileSearchFiltering.FileFilter filter : filters) {
                searchStringBuilder.append(filter.toString());
            }
            searchStringBuilder.append(groupAttributeType).append(groupSortingType).append(fileSortingMethod);
            keyString = searchStringBuilder.toString();
        }

        @Override
        public int compareTo(SearchKey otherSearchKey) {
            return getKeyString().compareTo(otherSearchKey.getKeyString());
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof SearchKey)) {
                return false;
            }

            SearchKey otherSearchKey = (SearchKey) otherKey;
            return getKeyString().equals(otherSearchKey.getKeyString());
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(getKeyString());
            return hash;
        }

        /**
         * @return the keyString
         */
        String getKeyString() {
            return keyString;
        }
    }

    /**
     * Key representing a file tag group
     */
    private static class FileTagGroupKey extends GroupKey {

        private final List<String> tagNames;
        private final String tagNamesString;

        @NbBundle.Messages({
            "FileSearch.FileTagGroupKey.noSets=None"})
        FileTagGroupKey(ResultFile file) {
            tagNames = file.getTagNames();

            if (tagNames.isEmpty()) {
                tagNamesString = Bundle.FileSearch_FileTagGroupKey_noSets();
            } else {
                tagNamesString = String.join(",", tagNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return tagNamesString;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileTagGroupKey) {
                FileTagGroupKey otherFileTagGroupKey = (FileTagGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (tagNames.isEmpty()) {
                    if (otherFileTagGroupKey.tagNames.isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherFileTagGroupKey.tagNames.isEmpty()) {
                    return -1;
                }

                return tagNamesString.compareTo(otherFileTagGroupKey.tagNamesString);
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FileTagGroupKey)) {
                return false;
            }

            FileTagGroupKey otherFileTagGroupKey = (FileTagGroupKey) otherKey;
            return tagNamesString.equals(otherFileTagGroupKey.tagNamesString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tagNamesString);
        }
    }

    /**
     * Default attribute used to make one group
     */
    static class NoGroupingAttribute extends AttributeType {

        @Override
        GroupKey getGroupKey(ResultFile file) {
            return new NoGroupingGroupKey();
        }
    }

    /**
     * Dummy key for when there is no grouping. All files will have the same
     * key.
     */
    private static class NoGroupingGroupKey extends GroupKey {

        NoGroupingGroupKey() {
            // Nothing to save - all files will get the same GroupKey
        }

        @NbBundle.Messages({
            "FileSearch.NoGroupingGroupKey.allFiles=All Files"})
        @Override
        String getDisplayName() {
            return Bundle.FileSearch_NoGroupingGroupKey_allFiles();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            // As long as the other key is the same type, they are equal
            if (otherGroupKey instanceof NoGroupingGroupKey) {
                return 0;
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof NoGroupingGroupKey)) {
                return false;
            }

            // As long as the other key is the same type, they are equal
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /**
     * Enum for the attribute types that can be used for grouping.
     */
    @NbBundle.Messages({
        "FileSearch.GroupingAttributeType.fileType.displayName=File type",
        "FileSearch.GroupingAttributeType.frequency.displayName=Past occurrences",
        "FileSearch.GroupingAttributeType.keywordList.displayName=Keyword list names",
        "FileSearch.GroupingAttributeType.size.displayName=Size",
        "FileSearch.GroupingAttributeType.datasource.displayName=Data source",
        "FileSearch.GroupingAttributeType.parent.displayName=Parent folder",
        "FileSearch.GroupingAttributeType.hash.displayName=Hash set",
        "FileSearch.GroupingAttributeType.interestingItem.displayName=Interesting item set",
        "FileSearch.GroupingAttributeType.tag.displayName=File tag",
        "FileSearch.GroupingAttributeType.object.displayName=Object detected",
        "FileSearch.GroupingAttributeType.none.displayName=None"})
    enum GroupingAttributeType {
        FILE_SIZE(new FileSizeAttribute(), Bundle.FileSearch_GroupingAttributeType_size_displayName()),
        FREQUENCY(new FrequencyAttribute(), Bundle.FileSearch_GroupingAttributeType_frequency_displayName()),
        KEYWORD_LIST_NAME(new KeywordListAttribute(), Bundle.FileSearch_GroupingAttributeType_keywordList_displayName()),
        DATA_SOURCE(new DataSourceAttribute(), Bundle.FileSearch_GroupingAttributeType_datasource_displayName()),
        PARENT_PATH(new ParentPathAttribute(), Bundle.FileSearch_GroupingAttributeType_parent_displayName()),
        HASH_LIST_NAME(new HashHitsAttribute(), Bundle.FileSearch_GroupingAttributeType_hash_displayName()),
        INTERESTING_ITEM_SET(new InterestingItemAttribute(), Bundle.FileSearch_GroupingAttributeType_interestingItem_displayName()),
        FILE_TAG(new FileTagAttribute(), Bundle.FileSearch_GroupingAttributeType_tag_displayName()),
        OBJECT_DETECTED(new ObjectDetectedAttribute(), Bundle.FileSearch_GroupingAttributeType_object_displayName()),
        NO_GROUPING(new NoGroupingAttribute(), Bundle.FileSearch_GroupingAttributeType_none_displayName());

        private final AttributeType attributeType;
        private final String displayName;

        GroupingAttributeType(AttributeType attributeType, String displayName) {
            this.attributeType = attributeType;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        AttributeType getAttributeType() {
            return attributeType;
        }
    }
}
