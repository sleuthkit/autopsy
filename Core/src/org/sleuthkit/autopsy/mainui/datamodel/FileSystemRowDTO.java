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

import java.util.List;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/*
 * A base class for FileSystem table row DTOs.
 */
public abstract class FileSystemRowDTO<T extends Content> extends BaseRowDTO {

    private final T content;

    /**
     * Constructs a new FileSystemRowDTO.
     *
     * @param content    The content represented by this object.
     * @param cellValues The table cell values.
     * @param typeId     The string type id for this DTO.
     */
    private FileSystemRowDTO(T content, List<Object> cellValues, String typeId) {
        super(cellValues, typeId, content.getId());
        this.content = content;
    }

    /**
     * Returns the content object for this row.
     *
     * @return The content.
     */
    public T getContent() {
        return content;
    }

    /**
     * DTO Representing an Volume in the results view.
     */
    public static class VolumeRowDTO extends FileSystemRowDTO<Volume> {

        private static final String TYPE_ID = "VOLUME";

        /**
         * Constructs a new VolumeRowDTO.
         *
         * @param volume     The volume represented by this DTO.
         * @param cellValues The table cell values.
         */
        public VolumeRowDTO(Volume volume, List<Object> cellValues) {
            super(volume, cellValues, TYPE_ID);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }

    /**
     * DTO Representing an Image in the results view.
     */
    public static class ImageRowDTO extends FileSystemRowDTO<Image> {

        private static final String TYPE_ID = "IMAGE";

        /**
         * Constructs a new ImageRowDTO.
         *
         * @param image      The image represented by this DTO.
         * @param cellValues The table cell values.
         */
        public ImageRowDTO(Image image, List<Object> cellValues) {
            super(image, cellValues, TYPE_ID);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }

    /**
     * DTO Representing a LocalDirectory file in the results view.
     */
    public static class LocalDirectoryRowDTO extends FileSystemRowDTO<LocalDirectory> {

        private static final String TYPE_ID = "LOCAL_DIRECTORY";

        /**
         * Constructs a new LocalDirectoryRowDTO.
         *
         * @param localDir   The LocalDirectory represented by this DTO.
         * @param cellValues The table cell values.
         */
        public LocalDirectoryRowDTO(LocalDirectory localDir, List<Object> cellValues) {
            super(localDir, cellValues, TYPE_ID);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }

    /**
     * DTO Representing a VirtualDirectory in the results view.
     */
    public static class VirtualDirectoryRowDTO extends FileSystemRowDTO<VirtualDirectory> {

        private static final String TYPE_ID = "VIRTUAL_DIRECTORY";

        /**
         * Constructs a new VirtualDirectoryRowDTO.
         *
         * @param virtualDir The VirtualDirectory represented by this DTO.
         * @param cellValues The table cell values.
         */
        public VirtualDirectoryRowDTO(VirtualDirectory virtualDir, List<Object> cellValues) {
            this(virtualDir, cellValues, TYPE_ID);
        }

        /**
         * Constructs a new VirtualDirectoryRowDTO.
         *
         * @param virtualDir The VirtualDirectory represented by this DTO.
         * @param cellValues The table cell values.
         * @param typeId     The type id for this object.
         */
        private VirtualDirectoryRowDTO(VirtualDirectory localDir, List<Object> cellValues, String typeId) {
            super(localDir, cellValues, typeId);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }

    /**
     * DTO Representing LocalFile in the results view.
     */
    public static class LocalFileDataSourceRowDTO extends VirtualDirectoryRowDTO {

        private static final String TYPE_ID = "LOCAL_FILE_DATA_SOURCE";

        /**
         * Constructs a new LocalFileDataSourceRowDTO.
         *
         * @param localFilesDataSource The LocalFilesDataSource represented by
         *                             this DTO.
         * @param cellValues           The table cell values.
         */
        public LocalFileDataSourceRowDTO(LocalFilesDataSource localFilesDataSource, List<Object> cellValues) {
            super(localFilesDataSource, cellValues, TYPE_ID);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }

    /**
     * DTO Representing a Directory in the results view.
     */
    public static class DirectoryRowDTO extends FileSystemRowDTO<Directory> {

        private static final String TYPE_ID = "DIRECTORY";

        /**
         * Constructs a new DirectoryRowDTO.
         *
         * @param dir        The directory represented by this DTO.
         * @param cellValues The table cell values.
         */
        public DirectoryRowDTO(Directory dir, List<Object> cellValues) {
            super(dir, cellValues, TYPE_ID);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }

    /**
     * DTO representing a pool in the results view.
     */
    public static class PoolRowDTO extends FileSystemRowDTO<Pool> {

        private static final String TYPE_ID = "POOL";

        /**
         * Constructs a new PoolRowDTO.
         *
         * @param pool       The pool represented by this DTO.
         * @param cellValues The table cell values.
         */
        public PoolRowDTO(Pool pool, List<Object> cellValues) {
            super(pool, cellValues, TYPE_ID);
        }

        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }
}
