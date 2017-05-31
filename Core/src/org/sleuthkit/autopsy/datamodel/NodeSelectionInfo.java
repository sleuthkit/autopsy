/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import org.openide.nodes.Node;

/**
 * Implementations store sufficient information to identify a node that is
 * intended to be selected in a view.
 */
public interface NodeSelectionInfo {

    /**
     * Determines whether or not a given node satisfies the stored node
     * selection criteria.
     *
     * @param candidateNode A node to evaluate.
     *
     * @return True or false.
     */
    boolean matches(Node candidateNode);

}
