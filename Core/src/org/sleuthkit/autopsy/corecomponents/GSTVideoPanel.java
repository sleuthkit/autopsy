/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

/**
 * This class exists to support backwards compatibility of an erroneous call to
 * Logger.getLogger(GSTVideoPanel.class.getName()) in OpenCVFrameCapture.java in
 * an older version of the Video Triage Net Beans Module (NBM). It should be
 * removed when the Video Triage NBM changes its dependency on the Autopsy-Core
 * NBM to a major rlease version greater than 10 and a specification version
 * greater than 10.
 */
@Deprecated
public class GSTVideoPanel {

}
