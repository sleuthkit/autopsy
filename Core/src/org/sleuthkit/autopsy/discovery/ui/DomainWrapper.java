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
package org.sleuthkit.autopsy.discovery.ui;

import java.awt.Image;
import org.sleuthkit.autopsy.discovery.search.ResultDomain;

/**
 * Class to wrap all the information necessary for a domain summary to be
 * displayed.
 */
public class DomainWrapper {

    private final ResultDomain domain;
    private Image thumbnail = null;

    /**
     * Construct a new DocumentWrapper.
     *
     * @param file The ResultFile which represents the document which the
     *             summary is created for.
     */
    DomainWrapper(ResultDomain domain) {
        this.domain = domain;
    }

    /**
     * Set the thumbnail which exists.
     *
     * @param thumbnail The image object which will be used to represent this
     *                  domain object.
     */
    void setThumbnail(Image thumbnail) {
        this.thumbnail = thumbnail;
    }

    /**
     * Get the ResultDomain which represents the Domain the summary was created
     * for.
     *
     * @return The ResultDomain which represents the domain attribute which the
     *         summary was created for.
     */
    ResultDomain getResultDomain() {
        return domain;
    }

    /**
     * Get the image to be used for the domain.
     *
     * @return The image which represents the domain.
     */
    Image getThumbnail() {
        return thumbnail;
    }
}
