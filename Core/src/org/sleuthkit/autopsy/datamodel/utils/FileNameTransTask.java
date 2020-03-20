/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.utils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.nodes.AbstractNode;
import org.sleuthkit.autopsy.texttranslation.utils.FileNameTranslationUtil;

/**
 * An AbstractNodePropertySheetTask that translates a file name for an
 * AbstractNode's property sheet.
 */
public class FileNameTransTask extends AbstractNodePropertySheetTask<AbstractNode> {

    private final static String EVENT_SOURCE = FileNameTransTask.class.getName();
    private final static String PROPERTY_NAME = EVENT_SOURCE + ".TranslatedFileName";
    private final String fileName;

    public static String getPropertyName() {
        return PROPERTY_NAME;
    }

    /**
     * Constructs an AbstractNodePropertySheetTask that translates a file name
     * for an AbstractNode's property sheet. When the translation is complete, a
     * PropertyChangeEvent will be fired to the node's PropertyChangeListener.
     * Call getPropertyName() to identify the property.
     *
     * @param node     The node.
     * @param listener The node's PropertyChangeListener.
     * @param fileName THe file name.
     */
    public FileNameTransTask(String fileName, AbstractNode node, PropertyChangeListener listener) {
        super(node, listener);
        this.fileName = fileName;
    }

    @Override
    protected PropertyChangeEvent computePropertyValue(AbstractNode node) throws Exception {
        String translatedFileName = FileNameTranslationUtil.translate(fileName);
        return translatedFileName.isEmpty() ? null : new PropertyChangeEvent(EVENT_SOURCE, PROPERTY_NAME, fileName, translatedFileName);
    }

}
