/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013-2018 Willi Ballenthin
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
package org.sleuthkit.autopsy.contentviewers.registryViewer;

import com.williballenthin.RejistryView.RejView;
import com.williballenthin.rejistry.RegistryHive;
import com.williballenthin.rejistry.RegistryHiveBuffer;
import com.williballenthin.rejistry.RegistryParseException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

@ServiceProvider(service = DataContentViewer.class)
public class WindowsRegistryContentViewer extends JPanel implements DataContentViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(WindowsRegistryContentViewer.class.getName());
    private RejView _regview;

    public WindowsRegistryContentViewer() {
        super(new BorderLayout());
    }

    private void setDataView(Content content) {
        if (content == null) {
            this.resetComponent();
            return;
        }

        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (content.getSize() == 0) {
            return;
        }

        byte[] data = new byte[(int) content.getSize()];

        try {
            content.read(data, 0x0, content.getSize());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to read file content.", ex);
        }
        ByteBuffer buf = ByteBuffer.wrap(data);

        RegistryHive h = new RegistryHiveBuffer(buf);
        this._regview = new RejView(h);
        this.add(this._regview, BorderLayout.CENTER);

        this.setCursor(null);
    }

    @Override
    public void setNode(Node node) {
        logger.log(Level.INFO, "setNode: {0}", this);
        if (!isSupported(node)) {
            setDataView(null);
            return;
        }
        if (node != null) {
            Content content = (node).getLookup().lookup(Content.class);
            if (content != null) {
                this.setDataView(content);
                return;
            }
        }
        this.setDataView(null);
    }

    @Messages({"WindowsRegistryContentViewer.title.text=Windows Registry View"})
    @Override
    public String getTitle() {
        return Bundle.WindowsRegistryContentViewer_title_text();
    }

    @Messages({"WindowsRegistryContentViewer.tooltip.text=Displays a Windows Registry hive as a tree-like structure of keys and values."})
    @Override
    public String getToolTip() {
        return Bundle.WindowsRegistryContentViewer_tooltip_text();
    }

    @Override
    public DataContentViewer createInstance() {
        return new WindowsRegistryContentViewer();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        // cleanup anything
        if (this._regview != null) {
            this.remove(this._regview);
            this._regview = null;
        }
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }
        Content content = node.getLookup().lookup(Content.class);
        if (content == null) {
            return false;
        }
        if (content.getSize() == 0) {
            return false;
        }
        byte[] header = new byte[0x4000];

        try {
            content.read(header, 0x0, Math.min(0x4000, content.getSize()));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to read file content.", ex);
        }
        ByteBuffer buf = ByteBuffer.wrap(header);

        RegistryHive hive = new RegistryHiveBuffer(buf);
        try {
            hive.getHeader();
            return true;
        } catch (RegistryParseException ex) {
            return false;
        }
    }

    @Override
    public int isPreferred(Node node) {
        return 5;
    }
}
