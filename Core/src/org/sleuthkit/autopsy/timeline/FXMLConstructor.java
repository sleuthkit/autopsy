/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;

/**
 * This class supports programmer productivity by abstracting frequently used
 * code to load FXML-defined GUI components,
 *
 * TODO? improve performance by implementing a caching FXMLLoader as described
 * at
 * http://stackoverflow.com/questions/11734885/javafx2-very-poor-performance-when-adding-custom-made-fxmlpanels-to-gridpane.
 *
 * TODO: find a way to move this to CoreUtils and remove duplicate verison in
 * image analyzer
 */
public class FXMLConstructor {

    static public void construct(Node n, String fxmlFileName) {
        final String name = "nbres:/" + StringUtils.replace(n.getClass().getPackage().getName(), ".", "/") + "/" + fxmlFileName; // NON-NLS
        System.out.println(name);

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
                    Exceptions.printStackTrace(ex);
                }
            }
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
