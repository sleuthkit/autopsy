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

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.openide.util.NbBundle.Messages;

/**
 * Document sub-node filters
 */
@Messages({
    "FileExtDocumentFilter_html_displayName=HTML",
    "FileExtDocumentFilter_office_displayName=Office",
    "FileExtDocumentFilter_pdf_displayName=PDF",
    "FileExtDocumentFilter_txt_displayName=Plain Text",
    "FileExtDocumentFilter_rtf_displayName=Rich Text",})
public enum FileExtDocumentFilter implements FileExtSearchFilter {
    AUT_DOC_HTML(0, "AUT_DOC_HTML", Bundle.FileExtDocumentFilter_html_displayName(), ImmutableSet.of(".htm", ".html")), //NON-NLS
    AUT_DOC_OFFICE(1, "AUT_DOC_OFFICE", Bundle.FileExtDocumentFilter_office_displayName(), ImmutableSet.of(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx")), //NON-NLS
    AUT_DOC_PDF(2, "AUT_DOC_PDF", Bundle.FileExtDocumentFilter_pdf_displayName(), ImmutableSet.of(".pdf")), //NON-NLS
    AUT_DOC_TXT(3, "AUT_DOC_TXT", Bundle.FileExtDocumentFilter_txt_displayName(), ImmutableSet.of(".txt")), //NON-NLS
    AUT_DOC_RTF(4, "AUT_DOC_RTF", Bundle.FileExtDocumentFilter_rtf_displayName(), ImmutableSet.of(".rtf"));
    //NON-NLS
    final int id;
    final String name;
    final String displayName;
    final Set<String> filter;

    private FileExtDocumentFilter(int id, String name, String displayName, Set<String> filter) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.filter = filter;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public Set<String> getFilter() {
        return this.filter;
    }

}
