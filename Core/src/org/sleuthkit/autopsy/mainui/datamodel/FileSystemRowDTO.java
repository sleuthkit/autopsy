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
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/*
 *
 */
public abstract class FileSystemRowDTO<T> extends BaseRowDTO {

    private final T content;

    private FileSystemRowDTO(T content, List<Object> cellValues, String typeId, long id) {
        super(cellValues, typeId, id);
        this.content = content;
    }

    public T getContent() {
        return content;
    }

    public static class VolumeRowDTO extends FileSystemRowDTO<Volume> {

        private static final String TYPE_ID = "VOLUME";

        public VolumeRowDTO(Volume volume, List<Object> cellValues, long id) {
            super(volume, cellValues, TYPE_ID, id);
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

        public ImageRowDTO(Image image, List<Object> cellValues, long id) {
            super(image, cellValues, TYPE_ID, id);
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

        public LocalDirectoryRowDTO(LocalDirectory localDir, List<Object> cellValues, long id) {
            super(localDir, cellValues, TYPE_ID, id);
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

        public VirtualDirectoryRowDTO(VirtualDirectory localDir, List<Object> cellValues, long id) {
            this(localDir, cellValues, TYPE_ID, id);
        }

        VirtualDirectoryRowDTO(VirtualDirectory localDir, List<Object> cellValues, String typeId, long id) {
            super(localDir, cellValues, typeId, id);
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

        public LocalFileDataSourceRowDTO(LocalFilesDataSource localFilesDataSource, List<Object> cellValues, long id) {
            super(localFilesDataSource, cellValues, TYPE_ID, id);
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

        public DirectoryRowDTO(Directory localDir, List<Object> cellValues, long id) {
            super(localDir, cellValues, TYPE_ID, id);
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
        
        public PoolRowDTO(Pool pool, List<Object> cellValues, long id) {
            super(pool, cellValues, TYPE_ID, id);
        }
        
        public static String getTypeIdForClass() {
            return TYPE_ID;
        }
    }
}
