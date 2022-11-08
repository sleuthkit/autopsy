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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary.ContainerDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary.ImageDetails;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ExcelItemExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.KeyValueItemExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.SingleCellExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.TitledExportable;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class to export additional details associated with a specific DataSource
 */
class ExportContainerInfo {

    private final ContainerSummary containerSummary;

    /**
     * Creates new form ExportContainerInfo.
     */
    ExportContainerInfo() {
        containerSummary = new ContainerSummary();
    }

    /**
     * Divides acquisition details into key/value pairs to be displayed in
     * separate cells in an excel export.
     *
     * @param acquisitionDetails The acquisition details.
     *
     * @return The list of key value pairs that can be incorporated into the
     *         excel export.
     */
    private static List<? extends ExcelItemExportable> getAcquisitionDetails(String acquisitionDetails) {
        if (StringUtils.isBlank(acquisitionDetails)) {
            return Collections.emptyList();
        } else {
            return Stream.of(acquisitionDetails.split("\\r?\\n"))
                    .map((line) -> (StringUtils.isBlank(line)) ? null : new SingleCellExportable(line))
                    .filter(item -> item != null)
                    .collect(Collectors.toList());
        }
    }

    @Messages({
        "ExportContainerInfo_setFieldsForNonImageDataSource_na=N/A",
        "ExportContainerInfo_tabName=Container",
        "ExportContainerInfo_export_displayName=Display Name:",
        "ExportContainerInfo_export_originalName=Name:",
        "ExportContainerInfo_export_deviceId=Device ID:",
        "ExportContainerInfo_export_timeZone=Time Zone:",
        "ExportContainerInfo_export_acquisitionDetails=Acquisition Details:",
        "ExportContainerInfo_export_imageType=Image Type:",
        "ExportContainerInfo_export_size=Size:",
        "ExportContainerInfo_export_sectorSize=Sector Size:",
        "ExportContainerInfo_export_md5=MD5:",
        "ExportContainerInfo_export_sha1=SHA1:",
        "ExportContainerInfo_export_sha256=SHA256:",
        "ExportContainerInfo_export_unallocatedSize=Unallocated Space:",
        "ExportContainerInfo_export_filePaths=File Paths:",})
    List<ExcelSheetExport> getExports(DataSource ds) {
        DataFetcher<DataSource, ContainerDetails> containerDataFetcher = (dataSource) -> containerSummary.getContainerDetails(dataSource);
        ContainerDetails containerDetails = ExcelExportAction.getFetchResult(containerDataFetcher, "Container sheets", ds);
        if (ds == null || containerDetails == null) {
            return Collections.emptyList();
        }

        String NA = Bundle.ExportContainerInfo_setFieldsForNonImageDataSource_na();
        DefaultCellModel<?> NACell = new DefaultCellModel<>(NA);

        ImageDetails imageDetails = containerDetails.getImageDetails();
        boolean hasImage = imageDetails != null;

        DefaultCellModel<?> timeZone = hasImage ? new DefaultCellModel<>(imageDetails.getTimeZone()) : NACell;
        DefaultCellModel<?> imageType = hasImage ? new DefaultCellModel<>(imageDetails.getImageType()) : NACell;
        DefaultCellModel<?> size = hasImage ? SizeRepresentationUtil.getBytesCell(imageDetails.getSize()) : NACell;
        DefaultCellModel<?> sectorSize = hasImage ? SizeRepresentationUtil.getBytesCell(imageDetails.getSectorSize()) : NACell;
        DefaultCellModel<?> md5 = hasImage ? new DefaultCellModel<>(imageDetails.getMd5Hash()) : NACell;
        DefaultCellModel<?> sha1 = hasImage ? new DefaultCellModel<>(imageDetails.getSha1Hash()) : NACell;
        DefaultCellModel<?> sha256 = hasImage ? new DefaultCellModel<>(imageDetails.getSha256Hash()) : NACell;

        DefaultCellModel<?> unallocatedSize;
        if (hasImage) {
            Long unallocatedSizeVal = imageDetails.getUnallocatedSize();
            if (unallocatedSizeVal != null) {
                unallocatedSize = SizeRepresentationUtil.getBytesCell(unallocatedSizeVal);
            } else {
                unallocatedSize = NACell;
            }
        } else {
            unallocatedSize = NACell;
        }

        List<String> paths = containerDetails.getImageDetails() == null ? Collections.singletonList(NA) : containerDetails.getImageDetails().getPaths();
        List<SingleCellExportable> cellPaths = paths.stream()
                .map(SingleCellExportable::new)
                .collect(Collectors.toList());

        return Arrays.asList(new ExcelSpecialFormatExport(Bundle.ExportContainerInfo_tabName(), Arrays.asList(new KeyValueItemExportable(Bundle.ExportContainerInfo_export_displayName(), new DefaultCellModel<>(containerDetails.getDisplayName())),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_originalName(), new DefaultCellModel<>(containerDetails.getOriginalName())),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_deviceId(), new DefaultCellModel<>(containerDetails.getDeviceId())),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_timeZone(), timeZone),
                new TitledExportable(Bundle.ExportContainerInfo_export_acquisitionDetails(), getAcquisitionDetails(containerDetails.getAcquisitionDetails())),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_imageType(), imageType),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_size(), size),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_sectorSize(), sectorSize),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_md5(), md5),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_sha1(), sha1),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_sha256(), sha256),
                new KeyValueItemExportable(Bundle.ExportContainerInfo_export_unallocatedSize(), unallocatedSize),
                new TitledExportable(Bundle.ExportContainerInfo_export_filePaths(), cellPaths)
        )));

    }
}
