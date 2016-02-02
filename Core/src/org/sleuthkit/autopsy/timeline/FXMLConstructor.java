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
package org.sleuthkit.autopsy.timeline;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 * This class supports programmer productivity by abstracting frequently used
 * code to load FXML-defined GUI components,
 *
 * TODO? improve performance by implementing a caching FXMLLoader as described
 * at
 * http://stackoverflow.com/questions/11734885/javafx2-very-poor-performance-when-adding-custom-made-fxmlpanels-to-gridpane.
 *
 * NOTE: As described in the link above above, using FXMLConstructor will be
 * inefficient if FXML is used as a template for many similar items. In that use
 * case, it is much faster to build the entire hierarchy in Java. This class is
 * intended only to remove the boilerplate initialization code when defining a
 * relatively static layout
 *
 * TODO: move this to CoreUtils and remove duplicate verison in image analyzer
 */
public class FXMLConstructor {

    private static final Logger LOGGER = Logger.getLogger(FXMLConstructor.class.getName());

    /**
     * Load an fxml file and initialize a node with it. Since this manipulates
     * the node, it must be called on the JFX thread.
     *
     *
     * @param node         a node to initialize from a loaded FXML
     * @param fxmlFileName the file name of the FXML to load, relative to the
     *                     package that the class of node is defined in.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    static public void construct(Node node, String fxmlFileName) {
        construct(node, node.getClass(), fxmlFileName);
    }

    /**
     * Load an fxml file and initialize a node with it. Since this manipulates
     * the node, it must be called on the JFX thread.
     *
     *
     * @param node         a node to initialize from a loaded FXML
     * @param clazz        a class to use for relative location of the fxml
     * @param fxmlFileName the file name of the FXML to load, relative to the
     *                     package of clazz.
     *
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    static public void construct(Node node, Class<? extends Node> clazz, String fxmlFileName) {
        final String name = "nbres:/" + StringUtils.replace(clazz.getPackage().getName(), ".", "/") + "/" + fxmlFileName; // NON-NLS

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(new URL(name));
            fxmlLoader.setRoot(node);
            fxmlLoader.setController(node);

            try {
                fxmlLoader.load();
            } catch (IOException exception) {
                LOGGER.log(Level.SEVERE, "FXMLConstructor was unable to load FXML, falling back on default Class Loader, and trying again.", exception); //NON-NLS
                try {
                    fxmlLoader.setClassLoader(FXMLLoader.getDefaultClassLoader());
                    fxmlLoader.load();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "FXMLConstructor was unable to load FXML, node initialization may not be complete.", ex); //NON-NLS
                }
            }
        } catch (MalformedURLException ex) {
            LOGGER.log(Level.SEVERE, "FXMLConstructor was unable to load FXML, node initialization may not be complete.", ex); //NON-NLS
        }
    }

    private FXMLConstructor() {
    }
}
