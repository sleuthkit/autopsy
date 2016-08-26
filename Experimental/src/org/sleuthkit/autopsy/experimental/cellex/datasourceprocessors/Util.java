/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cellex.datasourceprocessors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import static java.lang.Math.min;
import java.util.Collection;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author flynn
 */
public class Util {

    public interface Mapper<T1, T2> {

        public T2 apply(T1 x);
    }

    public static void copyToFile(AbstractFile inputFile, String destPath)
            throws IOException, TskCoreException {
        long inputSize = inputFile.getSize();
        long bufSize = min(inputSize, 65536L);

        byte[] buffer = new byte[(int) bufSize];

        FileOutputStream output = new FileOutputStream(destPath);

        long offset = 0;
        long bytesLeft = inputSize;

        try {
            while (bytesLeft > 0) {
                int bytesRead = inputFile.read(buffer, offset, bufSize);

                if (bytesRead < 0) {
                    throw new IOException("I/O error (rc " + bytesRead + ")");
                }

                output.write(buffer);

                bytesLeft -= bytesRead;
                offset += bytesRead;
            }
        } finally {
            // Always always always close output, no matter what.
            output.close();
        }
    }

    public static String getBasename(String path) {
        String baseName = path;
        int lastSlash = baseName.lastIndexOf(File.separator);

        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }

        return baseName;
    }

    public static String stripExtension(String path) {
        String noExt = path;

        int lastPeriod = path.lastIndexOf('.');

        if (lastPeriod > 0) {   // Not >=, >.  A single "." should be preserved.
            noExt = path.substring(0, lastPeriod);
        }

        return noExt;
    }

    public static String joinPath(String... elements) {
        return join(File.separator, elements);
    }

    public static String join(String delim, Mapper<String, String> mapFunc,
            String... elements) {
        String joined = "";

        for (String element : elements) {
            if (mapFunc != null) {
                element = mapFunc.apply(element);
            }

            if ((element != null) && (element.length() > 0)) {
                if (joined.length() > 0) {
                    joined += delim;
                }

                joined += element;
            }
        }

        return joined;
    }

    public static String join(String delim, Mapper<String, String> mapFunc,
            Collection<String> elements) {
        return join(delim, mapFunc,
                elements.toArray(new String[elements.size()]));
    }

    public static String join(String delim, String... elements) {
        return join(delim, null, elements);
    }

    public static String join(String delim, Collection<String> elements) {
        return join(delim, null, elements);
    }
}
