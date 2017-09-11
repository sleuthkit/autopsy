/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import javafx.fxml.FXMLLoader;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * This class supports programmer productivity by abstracting frequently used
 * code to load FXML-defined GUI components,
 *
 * TODO? improve performance by implementing a caching FXMLLoader as described
 * at
 * http://stackoverflow.com/questions/11734885/javafx2-very-poor-performance-when-adding-custom-made-fxmlpanels-to-gridpane.
 */
public class FXMLConstructor {

    private static Logger logger = Logger.getLogger(FXMLConstructor.class.getName());

    static public void construct(Object n, String fxmlFileName) {
        final String name = "nbres:/" + StringUtils.replace(n.getClass().getPackage().getName(), ".", "/") + "/" + fxmlFileName; //NON-NLS

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(new URL(name));
            fxmlLoader.setRoot(n);
            fxmlLoader.setController(n);

            try {
                fxmlLoader.load();
            } catch (IOException exception) {
                try {
                    fxmlLoader.setClassLoader(FXMLLoader.getDefaultClassLoader());
                    fxmlLoader.load();
                } catch (IOException ex) {
                    String msg = String.format("Failed to load fxml file %s", fxmlFileName); //NON-NLS
                    logger.log(Level.SEVERE, msg, ex);
                }
            }
        } catch (MalformedURLException ex) {
            String msg = String.format("Malformed URL %s", name); //NON-NLS
            logger.log(Level.SEVERE, msg, ex);
        }

    }

    private FXMLConstructor() {
    }
}
