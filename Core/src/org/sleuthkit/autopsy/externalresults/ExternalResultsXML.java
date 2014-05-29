/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.externalresults;

/**
 * Tags for an external results XML file. 
 */
public enum ExternalResultsXML {
    ROOT_ELEM("autopsy_results"), //NON-NLS
    DATA_SRC_ELEM("data_source"), //NON-NLS
    ARTIFACTS_LIST_ELEM("artifacts"), //NON-NLS
    ARTIFACT_ELEM("artifact"), //NON-NLS
    SOURCE_FILE_ELEM("source_file"), //NON-NLS
    PATH_ELEM("path"), //NON-NLS
    ATTRIBUTE_ELEM("attribute"), //NON-NLS
    VALUE_ELEM("value"), //NON-NLS
    SOURCE_MODULE_ELEM("source_module"), //NON-NLS
    REPORTS_LIST_ELEM("reports"), //NON-NLS
    REPORT_ELEM("report"), //NON-NLS
    DISPLAY_NAME_ELEM("display_name"), //NON-NLS
    LOCAL_PATH_ELEM("local_path"), //NON-NLS
    DERIVED_FILES_LIST_ELEM("derived_files"), //NON-NLS
    DERIVED_FILE_ELEM("derived_file"), //NON-NLS
    PARENT_PATH_ELEM("parent_path"), //NON-NLS
    TYPE_ATTR("type"), //NON-NLS
    NAME_ATTR("name"), //NON-NLS 
    VALUE_TYPE_TEXT("text"),
    VALUE_TYPE_INT32("int32"),
    VALUE_TYPE_INT64("int64"),
    VALUE_TYPE_DOUBLE("double");
    
    private final String text;
    
    private ExternalResultsXML(final String text) {
        this.text = text;
    }
    
    @Override
    public String toString() {
        return text;
    }        
}
