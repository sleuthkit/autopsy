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
package org.sleuthkit.autopsy.contentviewers.osaccount;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Data model for OsAccount panels, but could be reused in other places. The
 * model represents a titled section of key\value pairs.
 */
final class Section implements Iterable<Section.SectionData> {

    private final String title;
    private final List<SectionData> sectionData = new ArrayList<>();

    Section(String title) {
        this.title = title;
    }

    /**
     * Add data to this section.
     * 
     * @param data Section data to add.
     */
    void addSectionData(SectionData data) {
        sectionData.add(data);
    }

    /**
     * Returns the section title.
     * 
     * @return The section title.
     */
    String getTitle() {
        return title;
    }

    @Override
    public Iterator<SectionData> iterator() {
        return sectionData.iterator();
    }

    final static class SectionData implements Iterable<Section.RowData<String, String>> {

        private final String title;
        private final List<RowData<String, String>> data;

        SectionData() {
            this(null);
        }

        /**
         * Construct a new SectionData object.
         *
         * @param title
         */
        SectionData(String title) {
            this.title = title;
            this.data = new ArrayList<>();
        }

        /**
         * Returns the title for this section.
         *
         * @return The section title.
         */
        String getTitle() {
            return title;
        }

        /**
         * Add a new property name/property value pair.
         *
         * @param properytName  The property display name.
         * @param propertyValue The property value.
         */
        void addData(String properytName, String propertyValue) {
            data.add(new RowData<>(properytName, propertyValue));
        }

        @Override
        public Iterator<RowData<String, String>> iterator() {
            return data.iterator();
        }
    }

    /**
     * Represents a row of data. In this case it is just a key value pair.
     *
     * @param <K> Property Name.
     * @param <V> Property Value.
     */
    static class RowData<K, V> {

        private final K key;
        private final V value;

        /**
         * Construct a new row of data for the model.
         *
         * @param key
         * @param value
         */
        RowData(K key, V value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Returns the key.
         *
         * @return The key value.
         */
        K getKey() {
            return key;
        }

        /**
         * Returns the value.
         *
         * @return The value.
         */
        V getValue() {
            return value;
        }
    }
}
