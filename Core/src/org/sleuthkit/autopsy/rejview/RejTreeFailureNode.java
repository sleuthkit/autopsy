/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.rejview;

import java.util.ArrayList;
import java.util.List;

/**
 * Node to serve as a place holder in the RejTree in when a failure occurs
 */
public class RejTreeFailureNode implements RejTreeNode {

    private final String failureText;

    /**
     * Construct a RejTreeFailureNode
     *
     * @param text the text to display due to the failure, will appear as the
     *             nodes name
     */
    public RejTreeFailureNode(String text) {
        this.failureText = text;
    }

    @Override
    public String toString() {
        return failureText;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public List<RejTreeNode> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public RejTreeNodeView getView() {
        return new RejTreeNodeView();
    }

}
