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

package org.sleuthkit.autopsy.ingest;

import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract;
import org.sleuthkit.datamodel.Image;

/**
 * Scheduled task added to the scheduler
 *
 * @param T type of ingest modules (file or image) associated with this task
 */
class ScheduledImageTask<T extends IngestModuleAbstract> {

    private Image image;
    private List<T> modules;

    public ScheduledImageTask(Image image, List<T> modules) {
        this.image = image;
        this.modules = modules;
    }

    public Image getImage() {
        return image;
    }

    public List<T> getModules() {
        return modules;
    }

    void addModules(List<T> newModules) {
        for (T newModule : newModules) {
            if (!modules.contains(newModule)) {
                modules.add(newModule);
            }
        }
    }

    @Override
    public String toString() {
        return "ScheduledImageTask{" + "image=" + image + ", modules=" + modules + '}';
    }

    /**
     * Two scheduled tasks are equal when the image and modules are the same.
     * This enables us not to enqueue the equal schedules tasks twice into the
     * queue/set
     *
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ScheduledImageTask<T> other = (ScheduledImageTask<T>) obj;
        if (this.image != other.image && (this.image == null || !this.image.equals(other.image))) {
            return false;
        }
        if (this.modules != other.modules && (this.modules == null || !this.modules.equals(other.modules))) {
            return false;
        }

        return true;
    }
}
