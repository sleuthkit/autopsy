/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.imagetagging;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Focus events for ImageTags to consume. These events trigger selection behavior 
 * on ImageTags and are originated from the ImageTagsGroup class.
 */
public class ImageTagControls {
    public static final EventType<Event> NOT_FOCUSED = new EventType<>("NOT_FOCUSED");
    public static final EventType<Event> FOCUSED = new EventType<>("FOCUSED");
}
