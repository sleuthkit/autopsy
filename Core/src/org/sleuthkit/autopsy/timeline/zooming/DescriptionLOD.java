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
package org.sleuthkit.autopsy.timeline.zooming;

import org.openide.util.NbBundle;

/**
 * Enumeration of all description levels of detail.
 */
public enum DescriptionLOD {

    SHORT(NbBundle.getMessage(DescriptionLOD.class, "DescriptionLOD.short")),
    MEDIUM(NbBundle.getMessage(DescriptionLOD.class, "DescriptionLOD.medium")),
    FULL(NbBundle.getMessage(DescriptionLOD.class, "DescriptionLOD.full"));

    private final String displayName;

    public String getDisplayName() {
        return displayName;
    }

    private DescriptionLOD(String displayName) {
        this.displayName = displayName;
    }

    public DescriptionLOD moreDetailed() {
        try {
            return values()[ordinal() + 1];
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    public DescriptionLOD lessDetailed() {
        try {
            return values()[ordinal() - 1];
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    public DescriptionLOD withRelativeDetail(RelativeDetail relativeDetail) {
        switch (relativeDetail) {
            case EQUAL:
                return this;
            case MORE:
                return moreDetailed();
            case LESS:
                return lessDetailed();
            default:
                throw new IllegalArgumentException("Unknown RelativeDetail value " + relativeDetail);
        }
    }

    public enum RelativeDetail {

        EQUAL,
        MORE,
        LESS;
    }
}
