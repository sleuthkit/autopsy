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
import com.williballenthin.rejistry.REGFHeader;
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
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

@ServiceProvider(service = DataContentViewer.class)
public class WindowsRegistryContentViewer extends JPanel implements DataContentViewer {

//    private static final int ONE_HUNDRED_MEGABYTES = 1024 * 1024 * 100;
    private RejView _regview;
    private static final Logger logger = Logger.getLogger(WindowsRegistryContentViewer.class.getName());

    public WindowsRegistryContentViewer() {
        super(new BorderLayout());
        logger.log(Level.INFO, "Created Windows Registry Viewer instance: {0}", this);
    }

    private void setDataView(Content content) {
        logger.log(Level.INFO, "setDataView: {0}", this);
        if (content == null) {
            this.resetComponent();
            return;
        }

        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        /*
         * if (content.getSize() > ONE_HUNDRED_MEGABYTES) {
         * logger.log(Level.WARNING, "Unable to view Registry hives greater than
         * 100MB"); return; }
         */
        if (content.getSize() == 0) {
            return;
        }

        byte[] data = new byte[(int) content.getSize()];

        // TODO(wb): Lazy!
        int bytesRead = 0;
        try {
            bytesRead += content.read(data, 0x0, content.getSize());
        } catch (TskException ex) {
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

    @Override
    public String getTitle() {
        logger.log(Level.INFO, "getTitle: " + this);
        return "Windows Registry View";
    }

    @Override
    public String getToolTip() {
        logger.log(Level.INFO, "getToolTip: " + this);
        return "Displays a Windows Registry hive as a tree-like structure of "
                + "keys and values.";
    }

    @Override
    public DataContentViewer createInstance() {
        logger.log(Level.INFO, "createInstance: " + this);
        return new WindowsRegistryContentViewer();
    }

    @Override
    public Component getComponent() {
        logger.log(Level.INFO, "getComponent: " + this);
        return this;
    }

    @Override
    public void resetComponent() {
        logger.log(Level.INFO, "resetComponent: " + this);
        // cleanup anything
        if (this._regview != null) {
            this.remove(this._regview);
            this._regview = null;
        }
    }

    @Override
    public boolean isSupported(Node node) {
        logger.log(Level.INFO, "isSupported: " + this);
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

        int bytesRead = 0;
        try {
            // TODO(wb): Lazy!
            bytesRead += content.read(header, 0x0, Math.min(0x4000, content.getSize()));
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Failed to read file content.", ex);
        }
        ByteBuffer buf = ByteBuffer.wrap(header);

        RegistryHive hive = new RegistryHiveBuffer(buf);
        try {
            REGFHeader h = hive.getHeader();
            return true;
        } catch (RegistryParseException ex) {
            return false;
        }
    }

    @Override
    public int isPreferred(Node node) {
        logger.log(Level.INFO, "isPreferred: " + this);
        if (isSupported(node)) {
            return 1;
        } else {
            return 0;
        }
    }
}
