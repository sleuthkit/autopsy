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
package org.sleuthkit.autopsy.datamodel;

import java.lang.reflect.InvocationTargetException;
import org.openide.nodes.PropertySupport;

/**
 * This class is used to represent the properties for each node.
 *
 */
public class NodeProperty<T> extends PropertySupport.ReadOnly<T> {

    private T value;

    @SuppressWarnings("unchecked")
    public NodeProperty(String name, String displayName, String desc, T value) {
        super(name, (Class<T>) value.getClass(), displayName, desc);
        setValue("suppressCustomEditor", Boolean.TRUE); // remove the "..." (editing) button NON-NLS
        this.value = value;
    }

    @Override
    public T getValue() throws IllegalAccessException, InvocationTargetException {
        return this.value;
    }
}
