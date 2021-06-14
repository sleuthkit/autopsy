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
package org.sleuthkit.autopsy.textextractors.configs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.coreutils.ExecUtil.TimedProcessTerminator;

/**
 * Allows for configuration of OCR on image files. Extractors that use
 * ImageConfig can be obtained through TextExtractoryFactory.getExtractor().
 *
 * @see org.openide.util.Lookup
 */
public class ImageConfig {

    private static final int OCR_TIMEOUT_SECONDS = 30 * 60;

    private boolean OCREnabled = false;
    private List<String> ocrLanguages = null;
    private final TimedProcessTerminator ocrTimedTerminator = new TimedProcessTerminator(OCR_TIMEOUT_SECONDS);

    /**
     * Enables OCR to be run on the text reader responsible for handling image
     * files.
     *
     * @param enabled Flag indicating if OCR is enabled.
     */
    public void setOCREnabled(boolean enabled) {
        this.OCREnabled = enabled;
    }

    /**
     * Gets the OCR flag that has been set. By default this flag is turned off.
     *
     * @return Flag indicating if OCR is enabled.
     */
    public boolean getOCREnabled() {
        return this.OCREnabled;
    }
    
    /**
     * Sets languages for OCR.  Can be null.
     *
     * See PlatformUtil for list of installed language packs.
     *
     * @param languages List of languages to use
     */
    public void setOCRLanguages(List<String> languages) {
        this.ocrLanguages = languages == null ? 
                null : 
                Collections.unmodifiableList(new ArrayList<>(languages));
    }

    /**
     * Gets the list of languages OCR should perform.  Can be null.
     *
     * @return Collection of OCR languages
     */
    public List<String> getOCRLanguages() {
        return this.ocrLanguages;
    }

    /**
     * Returns a ProcessTerminator for timing out the OCR process.
     *
     * @return ProcessTerminator instance.
     */
    public ProcessTerminator getOCRTimeoutTerminator() {
        return ocrTimedTerminator;
    }
}
