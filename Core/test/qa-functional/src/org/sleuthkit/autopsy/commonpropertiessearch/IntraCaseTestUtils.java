/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;
import junit.framework.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.utils.DataSourceLoader;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.testutils.CaseUtils;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.autopsy.testutils.TestUtilsException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Provides setup and utility for testing presence of files in different data
 * sets discoverable by Common Files Features.
 *
 * Data set definitions:
 *
 * set 1 + file1 - IMG_6175.jpg + file2 - IMG_6175.jpg + file3 -
 * BasicStyleGuide.doc
 *
 * set 2 - adsf.pdf - IMG_6175.jpg
 *
 * set 3 - BasicStyleGuide.doc - IMG_6175.jpg
 *
 * set 4 - file.dat (empty file)
 */
class IntraCaseTestUtils {

    private static final String CASE_NAME = "IntraCaseCommonFilesSearchTest";
    static final Path CASE_DIRECTORY_PATH = Paths.get(System.getProperty("java.io.tmpdir"), CASE_NAME);

    private final Path imagePath1;
    private final Path imagePath2;
    private final Path imagePath3;
    private final Path imagePath4;

    static final String IMG = "IMG_6175.jpg";
    static final String DOC = "BasicStyleGuide.doc";
    static final String PDF = "adsf.pdf"; //not a typo - it appears this way in the test image
    static final String EMPTY = "file.dat";

    static final String SET1 = "CommonFiles_img1_v1.vhd";
    static final String SET2 = "CommonFiles_img2_v1.vhd";
    static final String SET3 = "CommonFiles_img3_v1.vhd";
    static final String SET4 = "CommonFiles_img4_v1.vhd";

    private final String caseName;

    IntraCaseTestUtils(NbTestCase nbTestCase, String caseName) {
        this.imagePath1 = Paths.get(nbTestCase.getDataDir().toString(), SET1);
        this.imagePath2 = Paths.get(nbTestCase.getDataDir().toString(), SET2);
        this.imagePath3 = Paths.get(nbTestCase.getDataDir().toString(), SET3);
        this.imagePath4 = Paths.get(nbTestCase.getDataDir().toString(), SET4);

        this.caseName = caseName;
    }

    void setUp() {
        this.createAsCurrentCase();

        final ImageDSProcessor imageDSProcessor = new ImageDSProcessor();

        this.addImageOne(imageDSProcessor);
        this.addImageTwo(imageDSProcessor);
        this.addImageThree(imageDSProcessor);
        this.addImageFour(imageDSProcessor);
    }

    void addImageFour(final ImageDSProcessor imageDSProcessor) {
        addImage(imageDSProcessor, imagePath4);
    }

    void addImageThree(final ImageDSProcessor imageDSProcessor) {
        addImage(imageDSProcessor, imagePath3);
    }

    void addImageTwo(final ImageDSProcessor imageDSProcessor) {
        addImage(imageDSProcessor, imagePath2);
    }

    void addImageOne(final ImageDSProcessor imageDSProcessor) {
        addImage(imageDSProcessor, imagePath1);
    }

    private void addImage(final ImageDSProcessor imageDSProcessor, Path path) {
        try {
            IngestUtils.addDataSource(imageDSProcessor, path);
        } catch (TestUtilsException ex) {
            failOnException(ex);
        }
    }

    void createAsCurrentCase() {
        try {
            CaseUtils.createAsCurrentCase(this.caseName + "_" + TimeStampUtils.createTimeStamp());
        } catch (TestUtilsException ex) {
            failOnException(ex);
        }
    }

    private void failOnException(Exception ex) {
        Exceptions.printStackTrace(ex);
        Assert.fail(ex.getMessage());
    }

    Map<Long, String> getDataSourceMap() throws NoCurrentCaseException, TskCoreException, SQLException {
        return DataSourceLoader.getAllDataSources();
    }

    void tearDown() {
        try {
            CaseUtils.closeCurrentCase();
        } catch (TestUtilsException ex) {
            failOnException(ex);
        }
    }

    /**
     * Verify that the given file appears a precise number times in the given
     * data source.
     *
     * @param searchDomain search domain
     * @param objectIdToDataSourceMap mapping of file ids to data source names
     * @param fileName name of file to search for
     * @param dataSource name of data source where file should appear
     * @param instanceCount number of appearances of the given file
     * @return true if a file with the given name exists the specified number of
     * times in the given data source
     */
    static boolean verifyInstanceExistanceAndCount(List<AbstractFile> searchDomain, Map<Long, String> objectIdToDataSourceMap, String fileName, String dataSource, int instanceCount) {

        int tally = 0;

        for (AbstractFile file : searchDomain) {

            Long objectId = file.getId();

            String name = file.getName();

            String dataSourceName = objectIdToDataSourceMap.get(objectId);

            if (name.equalsIgnoreCase(fileName) && dataSourceName.equalsIgnoreCase(dataSource)) {
                tally++;
            }
        }

        return tally == instanceCount;
    }

    /**
     * Verify that the given file appears a precise number times in the given
     * data source.
     *
     * @param searchDomain search domain
     * @param objectIdToDataSourceMap mapping of file ids to data source names
     * @param fileName name of file to search for
     * @param dataSource name of data source where file should appear
     * @param instanceCount number of appearances of the given file
     * @return The count of items found.
     */
    static int getInstanceCount(List<AbstractFile> searchDomain, Map<Long, String> objectIdToDataSourceMap, String fileName, String dataSource) {

        int tally = 0;

        for (AbstractFile file : searchDomain) {

            Long objectId = file.getId();

            String name = file.getName();

            String dataSourceName = objectIdToDataSourceMap.get(objectId);

            if (name.equalsIgnoreCase(fileName) && dataSourceName.equalsIgnoreCase(dataSource)) {
                tally++;
            }
        }

        return tally;
    }

    /**
     * Convenience method which verifies that a file exists within a given data
     * source exactly once.
     *
     * @param files search domain
     * @param objectIdToDataSource mapping of file ids to data source names
     * @param name name of file to search for
     * @param dataSource name of data source where file should appear
     * @return true if a file with the given name exists once in the given data
     * source
     */
    static boolean verifySingularInstanceExistance(List<AbstractFile> files, Map<Long, String> objectIdToDataSource, String name, String dataSource) {
        return verifyInstanceExistanceAndCount(files, objectIdToDataSource, name, dataSource, 1);
    }

    /**
     * Create a convenience lookup table mapping file instance object ids to the
     * data source they appear in.
     *
     * @param metadata object returned by the code under test
     * @return mapping of objectId to data source name
     */
    static Map<Long, String> mapFileInstancesToDataSources(CommonAttributeCountSearchResults metadata) {
        Map<Long, String> instanceIdToDataSource = new HashMap<>();
        for (Map.Entry<Integer, CommonAttributeValueList> entry : metadata.getMetadata().entrySet()) {
            entry.getValue().displayDelayedMetadata();
            for (CommonAttributeValue md : entry.getValue().getMetadataList()) {
                for (AbstractCommonAttributeInstance fim : md.getInstances()) {
                    instanceIdToDataSource.put(fim.getAbstractFileObjectId(), fim.getDataSource());
                }
            }
        }
        return instanceIdToDataSource;
    }

    static List<AbstractFile> getFiles(Set<Long> objectIds) {
        List<AbstractFile> files = new ArrayList<>(objectIds.size());

        for (Long id : objectIds) {
            try {
                AbstractFile file = Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(id);
                files.add(file);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex.getMessage());
            }
        }

        return files;
    }

    static Long getDataSourceIdByName(String name, Map<Long, String> dataSources) {

        if (dataSources.containsValue(name)) {
            for (Map.Entry<Long, String> dataSource : dataSources.entrySet()) {
                if (dataSource.getValue().equals(name)) {
                    return dataSource.getKey();
                }
            }
        } else {
            throw new IndexOutOfBoundsException(String.format("Name should be one of: {0}", String.join(",", dataSources.values())));
        }
        return null;
    }
}
