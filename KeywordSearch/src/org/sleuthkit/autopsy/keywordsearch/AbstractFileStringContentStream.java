/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import org.openide.util.NbBundle;
import org.apache.solr.common.util.ContentStream;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Wrapper over InputStream that implements ContentStream to feed to Solr.
 */
class AbstractFileStringContentStream implements ContentStream {
    //input

    private final AbstractFile content;
    private final Charset charset;
    //converted
    private final InputStream stream;

    public AbstractFileStringContentStream(AbstractFile content, Charset charset, InputStream inputStream) {
        this.content = content;
        this.charset = charset;
        this.stream = inputStream;
    }

    public AbstractContent getSourceContent() {
        return content;
    }

    @Override
    public String getContentType() {
        return "text/plain;charset=" + charset.name(); //NON-NLS
    }

    @Override
    public String getName() {
        return content.getName();
    }

    @Override
    public Reader getReader() throws IOException {
        return new InputStreamReader(stream);

    }

    @Override
    public Long getSize() {
        //return convertedLength;
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "AbstractFileStringContentStream.getSize.exception.msg"));
    }

    @Override
    public String getSourceInfo() {
        return NbBundle.getMessage(this.getClass(), "AbstractFileStringContentStream.getSrcInfo.text", content.getId());
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
