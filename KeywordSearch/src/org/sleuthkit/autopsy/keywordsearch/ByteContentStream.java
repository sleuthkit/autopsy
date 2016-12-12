/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.apache.solr.common.util.ContentStream;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractContent;

/**
 * Stream of bytes representing string with specified encoding to feed into Solr
 * as ContentStream
 */
class ByteContentStream implements ContentStream {

    //input
    private final byte[] content; //extracted subcontent
    private long contentSize;
    private final AbstractContent aContent; //origin

    private final InputStream stream;

    private static final Logger logger = Logger.getLogger(ByteContentStream.class.getName());

    public ByteContentStream(byte[] content, long contentSize, AbstractContent aContent) {
        this.content = content;
        this.aContent = aContent;
        stream = new ByteArrayInputStream(content, 0, (int) contentSize);
    }

    public byte[] getByteContent() {
        return content;
    }

    public AbstractContent getSourceContent() {
        return aContent;
    }

    @Override
    public String getContentType() {
        return "text/plain;charset=" + Server.DEFAULT_INDEXED_TEXT_CHARSET.name(); //NON-NLS
    }

    @Override
    public String getName() {
        return aContent.getName();
    }

    @Override
    public Reader getReader() throws IOException {
        return new InputStreamReader(stream);

    }

    @Override
    public Long getSize() {
        return contentSize;
    }

    @Override
    public String getSourceInfo() {
        return NbBundle.getMessage(this.getClass(), "ByteContentStream.getSrcInfo.text", aContent.getId());
    }

    @Override
    public InputStream getStream() throws IOException {
        return stream;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        stream.close();
    }

}
