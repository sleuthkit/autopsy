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
     * @param summary       - The text portion of the summary.
     * @param image         - The Image portion of the summary
     * @param countOfImages - The number of images including the one provided in
     *                      the document.
     */
    public TextSummary(String summary, Image image, int countOfImages) {
        summaryText = summary;
        sampleImage = image;
        numberOfImages = countOfImages;
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

    /**
     * @return the numberOfImages
     */
    public int getNumberOfImages() {
        return numberOfImages;
    }

}
