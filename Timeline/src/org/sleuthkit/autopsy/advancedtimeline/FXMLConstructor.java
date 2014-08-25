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
package org.sleuthkit.autopsy.advancedtimeline;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.openide.util.Exceptions;

/**
 * This class support both programmer productivity by abstracting frequently
 * used code to load FXML-defined GUI components, and code performance by
 * implementing a caching FXMLLoader as described at
 * http://stackoverflow.com/questions/11734885/javafx2-very-poor-performance-when-adding-custom-made-fxmlpanels-to-gridpane.
 *
 * TODO: this code is duplicated in the Image Analyze module, we should move it
 * into a centralized places like a JavaFX utils class/package/module in
 * Autopsy- jm
 */
public class FXMLConstructor {

    private static final CachingClassLoader CACHING_CLASS_LOADER = new CachingClassLoader((FXMLLoader.getDefaultClassLoader()));

    static public void construct(Node n, String fxmlFileName) {
        FXMLLoader fxmlLoader = new FXMLLoader(n.getClass().getResource(fxmlFileName));
        fxmlLoader.setRoot(n);
        fxmlLoader.setController(n);
        fxmlLoader.setClassLoader(CACHING_CLASS_LOADER);

        try {
            fxmlLoader.load();
        } catch (Exception exception) {
            try {
                fxmlLoader.setClassLoader(FXMLLoader.getDefaultClassLoader());
                fxmlLoader.load();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    /**
     * The default FXMLLoader does not cache information about previously loaded
     * FXML files. See
     * http://stackoverflow.com/questions/11734885/javafx2-very-poor-performance-when-adding-custom-made-fxmlpanels-to-gridpane.
     * for more details. As a partial workaround, we cache information on
     * previously loaded classes. This does not solve all performance issues,
     * but is a big improvement.
     */
    static public class CachingClassLoader extends ClassLoader {

        private final Map<String, Class<?>> classes = new HashMap<>();
        private final ClassLoader parent;

        public CachingClassLoader(ClassLoader parent) {
            this.parent = parent;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> c = findClass(name);
            if (c == null) {
                throw new ClassNotFoundException(name);
            }
            return c;
        }

        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
// System.out.print("try to load " + className); 
            if (classes.containsKey(className)) {
                Class<?> result = classes.get(className);
                return result;
            } else {
                try {
                    Class<?> result = parent.loadClass(className);
// System.out.println(" -> success!"); 
                    classes.put(className, result);
                    return result;
                } catch (ClassNotFoundException ignore) {
// System.out.println(); 
                    classes.put(className, null);
                    return null;
                }
            }
        }

        // ========= delegating methods ============= 
        @Override
        public URL getResource(String name) {
            return parent.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return parent.getResources(name);
        }

        @Override
        public String toString() {
            return parent.toString();
        }

        @Override
        public void setDefaultAssertionStatus(boolean enabled) {
            parent.setDefaultAssertionStatus(enabled);
        }

        @Override
        public void setPackageAssertionStatus(String packageName, boolean enabled) {
            parent.setPackageAssertionStatus(packageName, enabled);
        }

        @Override
        public void setClassAssertionStatus(String className, boolean enabled) {
            parent.setClassAssertionStatus(className, enabled);
        }

        @Override
        public void clearAssertionStatus() {
            parent.clearAssertionStatus();
        }
    }
}
