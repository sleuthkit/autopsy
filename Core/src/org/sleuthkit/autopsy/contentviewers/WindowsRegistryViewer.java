/*
 * Autopsy
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
package org.sleuthkit.autopsy.contentviewers;

import com.williballenthin.rejistry.RegistryHive;
import com.williballenthin.rejistry.RegistryHiveBuffer;
import com.williballenthin.rejistry.RegistryParseException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.rejview.RejView;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * FileTypeViewer which displays information for Windows Registry files
 */
class WindowsRegistryViewer extends JPanel implements FileTypeViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(WindowsRegistryViewer.class.getName());
    private static final String[] SUPPORTED_MIMETYPES = new String[]{"application/x.windows-registry"};
    //Registry log files which should be ignored share the same signature as Registry files but appear to have a size of 1024
    private static final String LOG_FILE_EXTENSION = "log"; //base extension for log files
    private RejView regview;
    private AbstractFile lastFile;

    WindowsRegistryViewer() {
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
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);

        RegistryHive h = new RegistryHiveBuffer(buf);
        this.regview = new RejView(h);
        this.add(this.regview, BorderLayout.CENTER);

        this.setCursor(null);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        // cleanup anything
        if (this.regview != null) {
            this.remove(this.regview);
            this.regview = null;
        }
        lastFile = null;
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        if (file == null) {
            return false;
        }
        if (file.getSize() == 0) {
            return false;
        }

        if (file.getNameExtension().toLowerCase().startsWith(LOG_FILE_EXTENSION)) {
            return false;
        }
        byte[] header = new byte[0x4000];

        try {
            file.read(header, 0x0, Math.min(0x4000, file.getSize()));
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to read file content", ex);
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(header);

        RegistryHive hive = new RegistryHiveBuffer(buf);
        try {
            hive.getHeader();
            return true;
        } catch (RegistryParseException ex) {
             logger.log(Level.WARNING, "Failed to get hive header", ex);
            return false;
        }
    }

    @Override
    public List<String> getSupportedMIMETypes() {
        return Arrays.asList(SUPPORTED_MIMETYPES);
    }

    @Override
    public void setFile(AbstractFile file) {
        if (file == null) {
            resetComponent();
            return;
        }
        if (file.equals(lastFile)) {
            return; //prevent from loading twice if setNode() called mult. times
        }
        lastFile = file;
        this.setDataView(file);
    }
}
