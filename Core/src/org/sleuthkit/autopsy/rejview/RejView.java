/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
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

import com.williballenthin.rejistry.RegistryHive;
import com.williballenthin.rejistry.RegistryHiveBuffer;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.nio.ByteBuffer;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public final class RejView extends JPanel implements RejTreeNodeSelectionListener {

    private static final long serialVersionUID = 1L;
    private final RegistryHive hive;
    private final RejTreeView treeView;
    private final JSplitPane splitPane;

    public RejView(RegistryHive hive) {
        super(new BorderLayout());
        this.hive = hive;
        // have to do these cause they're final
        this.treeView = new RejTreeView(this.hive);
        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                this.treeView, new JPanel());
        this.setupUI();
    }

    public RejView(ByteBuffer buf) {
        super(new BorderLayout());
        this.hive = new RegistryHiveBuffer(buf);

        // have to do these cause they're final
        this.treeView = new RejTreeView(this.hive);
        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                this.treeView, new JPanel());
        this.setupUI();
    }

    private void setupUI() {
        this.splitPane.setResizeWeight(0);
        this.splitPane.setOneTouchExpandable(true);
        this.splitPane.setContinuousLayout(true);
        this.add(this.splitPane, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(0,0));
        this.treeView.addRejTreeNodeSelectionListener(this);
    }

    @Override
    public void nodeSelected(RejTreeNodeSelectionEvent e) {
        RejTreeNodeView v = e.getNode().getView();
        int curDividerLocation = this.splitPane.getDividerLocation();
        this.splitPane.setRightComponent(v);
        this.splitPane.setDividerLocation(curDividerLocation);
    }
}
