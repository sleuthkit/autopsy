/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.textclassifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;
import java.util.ArrayList;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

/**
 * A wrapper around another SentenceDetectorME as defined in a model file. It
 * treats all newlines as sentence boundaries unless the following character is
 * lowercase. It also accepts all sentence boundaries from the contained
 * SentenceDetector.
 */
public class NewlineHeuristicSentenceDetector implements SentenceDetector {

    private final SentenceDetector CONTAINED_DETECTOR;

    public NewlineHeuristicSentenceDetector(File modelFile) throws IOException {
        try (FileInputStream stream = new FileInputStream(modelFile)) {
            this.CONTAINED_DETECTOR = new SentenceDetectorME(new SentenceModel(stream));
        }
    }

    @Override
    public String[] sentDetect(String inputString) {
        return Span.spansToStrings(this.sentPosDetect(inputString), inputString);
    }

    @Override
    public Span[] sentPosDetect(String s) {
        // Include a sentence boundary if
        // 1. CONTAINED_DETECTOR says it is a sentence boundary
        // 2. a '\n' character is followed by a lower case letter.
        // This skips all '\r', on the assumption that '\r' is only
        // meaningful when preceding '\n'.

        Span[] oldSpans = CONTAINED_DETECTOR.sentPosDetect(s);

        ArrayList<Span> newSpans = new ArrayList<>();

        for (Span oldSpan : oldSpans) {
            int start = oldSpan.getStart();

            boolean previousIsNewLine = false;
            for (int i = oldSpan.getStart(); i < oldSpan.getEnd(); ++i) {
                char c = s.charAt(i);

                if (previousIsNewLine && c == '\r') {
                    continue;
                }

                if (previousIsNewLine) {
                    if (!Character.isLowerCase(c)) {
                        Span span = (new Span(start, i - 1)).trim(s);
                        if (span.length() > 0) {
                            newSpans.add(span);
                            start = i;
                        }
                    }
                }

                previousIsNewLine = (c == '\n');
            }

            //If we reached the end of the old Span, create a new Span
            if (oldSpan.getEnd() - start > 0) {
                Span span = (new Span(start, oldSpan.getEnd())).trim(s);
                if (span.length() > 0) {
                    newSpans.add(span);
                }
            }
        }

        return newSpans.toArray(new Span[0]);
    }
}
