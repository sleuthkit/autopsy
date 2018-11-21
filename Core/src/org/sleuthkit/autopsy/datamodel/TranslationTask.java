/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Completes the tasks needed to populate the Translation columns in the
 * background so that the UI is not blocked while waiting for responses from the
 * translation service. Once the event is done, it fires a PropertyChangeEvent
 * to let the AbstractAbstractFileNode know it's time to update.
 */
class TranslationTask implements Runnable {

    private final WeakReference<AbstractAbstractFileNode<?>> weakNodeRef;
    private final PropertyChangeListener listener;

    public TranslationTask(WeakReference<AbstractAbstractFileNode<?>> weakContentRef, PropertyChangeListener listener) {
        this.weakNodeRef = weakContentRef;
        this.listener = listener;
    }

    @Override
    public void run() {
        AbstractAbstractFileNode<?> fileNode = weakNodeRef.get();
        //Check for stale reference
        if (fileNode == null) {
            return;
        }

        String translatedFileName = fileNode.getTranslatedFileName();
        if (!translatedFileName.isEmpty() && listener != null) {
            //Only fire if the result is meaningful and the listener is not a stale reference
            listener.propertyChange(new PropertyChangeEvent(
                    AutopsyEvent.SourceType.LOCAL.toString(),
                    AbstractAbstractFileNode.NodeSpecificEvents.TRANSLATION_AVAILABLE.toString(),
                    null, translatedFileName));
        }
    }
}
