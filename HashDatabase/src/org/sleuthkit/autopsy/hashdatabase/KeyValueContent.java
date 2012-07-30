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
package org.sleuthkit.autopsy.hashdatabase;

import java.util.Map;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.datamodel.Content;

/**
 * A KeyValue mapping where specific content needs to be recognizable.
 */
public class KeyValueContent extends KeyValue {
    Content content;
    
    KeyValueContent(String name, Map<String, Object> map, int id, Content content) {
        super(name, map, id);
        this.content = content;
    }
    
    Content getContent() {
        return content;
    }
}
