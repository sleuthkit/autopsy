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

import javax.swing.*;
import java.awt.*;
import java.nio.ByteBuffer;

public class RejView extends JPanel implements RejTreeNodeSelectionListener {

    private final RegistryHive _hive;
    private final RejTreeView _tree_view;
    private final JSplitPane _splitPane;

    public RejView(RegistryHive hive) {
        super(new BorderLayout());
        this._hive = hive;

        // have to do these cause they're final
        this._tree_view = new RejTreeView(this._hive);
        this._splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                this._tree_view, new JPanel());

        this.setupUI();
    }

    public RejView(ByteBuffer buf) {
        super(new BorderLayout());
        this._hive = new RegistryHiveBuffer(buf);

        // have to do these cause they're final
        this._tree_view = new RejTreeView(this._hive);
        this._splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                this._tree_view, new JPanel());

        this.setupUI();
    }

    private void setupUI() {
        this._splitPane.setResizeWeight(0);
        this._splitPane.setOneTouchExpandable(true);
        this._splitPane.setContinuousLayout(true);

        this.add(this._splitPane, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(800, 600));

        this._tree_view.addRejTreeNodeSelectionListener(this);
    }

    @Override
    public void nodeSelected(RejTreeNodeSelectionEvent e) {
        RejTreeNodeView v = e.getNode().getView();
        int curDividerLocation = this._splitPane.getDividerLocation();
        this._splitPane.setRightComponent(v);
        this._splitPane.setDividerLocation(curDividerLocation);
    }
}
