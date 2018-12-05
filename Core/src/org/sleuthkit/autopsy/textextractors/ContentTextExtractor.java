/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextractors;

import com.google.common.collect.ImmutableList;
import java.io.Reader;
import java.util.List;
import org.sleuthkit.datamodel.Content;

/**
 * Common methods for utilities that extract text and content and divide into
 * chunks
 */
public abstract class ContentTextExtractor implements TextExtractor<Content> {
    
    //Mimetype groups to aassist extractor implementations in ignoring binary and 
    //archive files.
    public static final List<String> BINARY_MIME_TYPES
            = ImmutableList.of(
                    //ignore binary blob data, for which string extraction will be used
                    "application/octet-stream", //NON-NLS
                    "application/x-msdownload"); //NON-NLS

    /** generally text extractors should ignore archives and let unpacking
     * modules take care of them */
    public static final List<String> ARCHIVE_MIME_TYPES
            = ImmutableList.of(
                    //ignore unstructured binary and compressed data, for which string extraction or unzipper works better
                    "application/x-7z-compressed", //NON-NLS
                    "application/x-ace-compressed", //NON-NLS
                    "application/x-alz-compressed", //NON-NLS
                    "application/x-arj", //NON-NLS
                    "application/vnd.ms-cab-compressed", //NON-NLS
                    "application/x-cfs-compressed", //NON-NLS
                    "application/x-dgc-compressed", //NON-NLS
                    "application/x-apple-diskimage", //NON-NLS
                    "application/x-gca-compressed", //NON-NLS
                    "application/x-dar", //NON-NLS
                    "application/x-lzx", //NON-NLS
                    "application/x-lzh", //NON-NLS
                    "application/x-rar-compressed", //NON-NLS
                    "application/x-stuffit", //NON-NLS
                    "application/x-stuffitx", //NON-NLS
                    "application/x-gtar", //NON-NLS
                    "application/x-archive", //NON-NLS
                    "application/x-executable", //NON-NLS
                    "application/x-gzip", //NON-NLS
                    "application/zip", //NON-NLS
                    "application/x-zoo", //NON-NLS
                    "application/x-cpio", //NON-NLS
                    "application/x-shar", //NON-NLS
                    "application/x-tar", //NON-NLS
                    "application/x-bzip", //NON-NLS
                    "application/x-bzip2", //NON-NLS
                    "application/x-lzip", //NON-NLS
                    "application/x-lzma", //NON-NLS
                    "application/x-lzop", //NON-NLS
                    "application/x-z", //NON-NLS
                    "application/x-compress"); //NON-NLS
    
    /**
     * Determines if the extractor works only for specified types is
     * supportedTypes() or whether is a generic content extractor (such as
     * string extractor)
     *
     * @return
     */
    public abstract boolean isContentTypeSpecific();

    /**
     * Determines if the file content is supported by the extractor if
     * isContentTypeSpecific() returns true.
     *
     * @param file           to test if its content should be supported
     * @param detectedFormat mime-type with detected format (such as text/plain)
     *                       or null if not detected
     *
     * @return true if the file content is supported, false otherwise
     */
    public abstract boolean isSupported(Content file, String detectedFormat);

    /**
     * Returns a reader that will iterate over the text of the source content.
     * 
     * @param source Content source to read
     * @return A reader that contains all source text
     * @throws TextExtractorException Error encountered during extraction
     */
    @Override
    public abstract Reader getReader(Content source) throws TextExtractorException;

    /**
     * Get the object id of the content source.
     * 
     * @param source source content
     * @return object id associated with this source content
     */
    @Override
    public long getID(Content source) {
        return source.getId();
    }

    /**
     * Returns the human-readable name of the given content source.
     * 
     * @param source source content
     * @return name of source content
     */
    @Override
    public String getName(Content source) {
        return source.getName();
    }
}
