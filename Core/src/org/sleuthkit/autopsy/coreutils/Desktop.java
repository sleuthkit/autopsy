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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.lang3.SystemUtils;

/**
 * Wrapper for java.awt.Desktop to handle some situations that java.awt.Desktop
 * doesn't.
 */
public class Desktop {

    private static final Logger LOGGER = Logger.getLogger(Desktop.class.getName());
    private static final long XDG_TIMEOUT_SECS = 30;

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

    /**
     * @return True if this class's external calls can be used on this operating
     * system.
     */
    public static boolean isDesktopSupported() {
        return java.awt.Desktop.isDesktopSupported() || isXdgSupported();
    }

    private static Desktop instance = null;

    /**
     * @return A singleton instance of this class.
     */
    public static Desktop getDesktop() {
        if (instance == null) {
            instance = new Desktop(java.awt.Desktop.getDesktop());
        }

        return instance;
    }

    private final java.awt.Desktop awtDesktop;

    /**
     * Private constructor for this wrapper.
     *
     * @param awtDesktop The delegate java.awt.Desktop.
     */
    private Desktop(java.awt.Desktop awtDesktop) {
        this.awtDesktop = awtDesktop;
    }

    /**
     * Opens a given path using `xdg-open` on linux.
     *
     * @param path The path.
     * @throws IOException
     */
    private void xdgOpen(String path) throws IOException {
        Process process = Runtime.getRuntime().exec(new String[]{"xdg-open", path});
        try {
            process.waitFor(XDG_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new IOException("xdg-open timed out", ex);
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Received non-zero exit code from xdg-open: " + exitCode);
        }
    }

    /**
     * Triggers the OS to navigate to the given uri.
     *
     * @param uri The uri.
     * @throws IOException
     */
    public void browse(URI uri) throws IOException {
        if (!awtDesktop.isSupported(java.awt.Desktop.Action.BROWSE) && isXdgSupported()) {
            xdgOpen(uri.toString());
        } else {
            awtDesktop.browse(uri);
        }
    }

    /**
     * Triggers the OS to open the given file.
     *
     * @param file The file.
     * @throws IOException
     */
    public void open(File file) throws IOException {
        if (!awtDesktop.isSupported(java.awt.Desktop.Action.OPEN) && isXdgSupported()) {
            xdgOpen(file.getAbsolutePath());
        } else {
            awtDesktop.open(file);
        }
    }

    /**
     * Triggers the OS to edit the given file.
     *
     * @param file The file.
     * @throws IOException
     */
    public void edit(File file) throws IOException {
        if (!awtDesktop.isSupported(java.awt.Desktop.Action.EDIT) && isXdgSupported()) {
            xdgOpen(file.getAbsolutePath());
        } else {
            awtDesktop.edit(file);
        }
    }
}
