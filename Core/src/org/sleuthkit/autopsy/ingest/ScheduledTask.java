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
import org.sleuthkit.datamodel.Content;

/**
 * A task that will be scheduled. Contains the top-level data to analyze and the pipeline.
 * Children of the data will also be scheduled. 
 *
 * @param T type of Ingest Module / Pipeline (file or data source content) associated with this task
 */
class ScheduledTask<T extends IngestModuleAbstract> {

    private Content input;
    private List<T> modules;

    public ScheduledTask(Content input, List<T> modules) {
        this.input = input;
        this.modules = modules;
    }

    public Content getContent() {
        return input;
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
        return "ScheduledTask{" + "input=" + input + ", modules=" + modules + '}';
    }

    /**
     * Two scheduled tasks are equal when the content and modules are the same.
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
        final ScheduledTask<T> other = (ScheduledTask<T>) obj;
        if (this.input != other.input && (this.input == null || !this.input.equals(other.input))) {
            return false;
        }
        if (this.modules != other.modules && (this.modules == null || !this.modules.equals(other.modules))) {
            return false;
        }

        return true;
    }
}
