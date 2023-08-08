/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import org.apache.commons.lang3.SystemUtils;

/**
 * Wrapper for java.awt.Desktop to handle some situations that java.awt.Desktop
 * doesn't.
 */
public class Desktop {

    private static final Logger LOGGER = Logger.getLogger(Desktop.class.getName());

    private static Boolean xdgSupported = null;

    private static boolean isXdgSupported() {
        if (xdgSupported == null) {
            xdgSupported = false;
            if (SystemUtils.IS_OS_LINUX) {
                try {
                    xdgSupported = Runtime.getRuntime().exec(new String[]{"which", "xdg-open"}).getInputStream().read() != -1;
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "There was an error running 'which xdg-open' ", ex);
                }
            }
        }

        return xdgSupported;
    }

    public static boolean isDesktopSupported() {
        return java.awt.Desktop.isDesktopSupported() || isXdgSupported();
    }

    private static Desktop instance = null;

    public static Desktop getDesktop() {
        if (instance == null) {
            instance = new Desktop(java.awt.Desktop.getDesktop());
        }

        return instance;
    }

    private final java.awt.Desktop awtDesktop;

    private Desktop(java.awt.Desktop awtDesktop) {
        this.awtDesktop = awtDesktop;
    }

    private void xdgOpen(String path) throws IOException {
        Runtime.getRuntime().exec(new String[]{"xdg-open", path});
    }

    public void browse(URI uri) throws IOException {
        if (!awtDesktop.isSupported(java.awt.Desktop.Action.BROWSE) && isXdgSupported()) {
            xdgOpen(uri.toString());
        } else {
            awtDesktop.browse(uri);
        }
    }

    public void open(File file) throws IOException {
        if (!awtDesktop.isSupported(java.awt.Desktop.Action.OPEN) && isXdgSupported()) {
            xdgOpen(file.getAbsolutePath());
        } else {
            awtDesktop.open(file);
        }
    }

    public void edit(File file) throws IOException {
        if (!awtDesktop.isSupported(java.awt.Desktop.Action.EDIT) && isXdgSupported()) {
            xdgOpen(file.getAbsolutePath());
        } else {
            awtDesktop.edit(file);
        }
    }
}
