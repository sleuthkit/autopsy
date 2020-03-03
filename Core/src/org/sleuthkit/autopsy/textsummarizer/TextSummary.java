/*
 * Autopsy
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
package org.sleuthkit.autopsy.textsummarizer;

import java.awt.Image;
import java.util.List;

/**
 * Class to contain all information necessary to display a summary for a file.s
 */
public class TextSummary {

    private final String summaryText;
    private final Image sampleImage;
    private final int numberOfImages;

    /**
     * Create a new TextSummary object.
     *
     * @param summary - The text portion of the summary.
     * @param images  - The Image portion of the summary
     */
    public TextSummary(String summary, List<Image> images) {
        summaryText = summary;
        if (images == null || images.isEmpty()) {
            sampleImage = null;
            numberOfImages = 0;
        } else {
            sampleImage = selectSummaryImage(images);
            numberOfImages = images.size();
        }
    }

    /**
     * Get the Image from the available images to include as part of the
     * summary.
     *
     * @param images - The list of Images available to choose from.
     *
     * @return The selected image to include in the summary.
     */
    private Image selectSummaryImage(List<Image> images) {
        return images.get(0);
    }

    /**
     * @return the summaryText
     */
    public String getSummaryText() {
        return summaryText;
    }

    /**
     * @return the sampleImage
     */
    public Image getSampleImage() {
        return sampleImage;
    }

}
