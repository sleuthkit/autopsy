/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.integrationtesting;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Handles creating diffs with files.
 */
public class DiffService {

    /**
     * An exception thrown during the normal operation of the diff service.
     */
    public static class DiffServiceException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructor accepting a message.
         *
         * @param message The message.
         */
        DiffServiceException(String message) {
            super(message);
        }

        /**
         * Constructor accepting a message and inner exception.
         *
         * @param message The message.
         * @param exception The inner exception.
         */
        DiffServiceException(String message, Throwable exception) {
            super(message, exception);
        }
    }

    private static final String ORIG_LINE_PREFIX = "< ";
    private static final String CUR_LINE_PREFIX = "> ";
    private static final String[] DIFF_BREAK = new String[]{"", "", ""};
    private static final String[] FILE_DIFF_BREAK = new String[]{"", "", "", ""};
    private static final String NEW_LINE = System.getProperty("line.separator");

    /**
     * Creates a diff of all the files found in the directories provided or
     * between two files.
     *
     * @param prevResult The previous file or directory. Must be of same type as
     * curResult (file/directory).
     * @param curResult The current file or directory. Must be of same type as
     * prevResult (file/directory).
     * @return The string contents of the diff.
     * @throws DiffServiceException
     */
    String diffFilesOrDirs(File prevResult, File curResult) throws DiffServiceException {
        if (prevResult.isDirectory() && curResult.isDirectory()) {
            final Map<String, File> prevFiles = FileUtils.listFiles(prevResult, null, true).stream()
                    .collect(Collectors.toMap(f -> getRelative(prevResult, f), f -> f, (f1, f2) -> f1));

            final Map<String, File> curFiles = FileUtils.listFiles(curResult, null, true).stream()
                    .collect(Collectors.toMap(f -> getRelative(curResult, f), f -> f, (f1, f2) -> f1));

            Map<String, Pair<File, File>> prevCurMapping = Stream.of(prevFiles, curFiles)
                    .flatMap((map) -> map.keySet().stream())
                    .collect(Collectors.toMap(k -> k, k -> Pair.of(prevFiles.get(k), curFiles.get(k)), (v1, v2) -> v1));

            String fullDiff = prevCurMapping.entrySet().stream()
                    .map((entry) -> getFileDiffs(entry.getValue().getLeft(), entry.getValue().getRight(), entry.getKey()))
                    .filter((val) -> val != null)
                    .collect(Collectors.joining(String.join(NEW_LINE, FILE_DIFF_BREAK)));

            return fullDiff;

        } else if (prevResult.isFile() && curResult.isFile()) {
            return getFileDiffs(prevResult, curResult, prevResult.toString() + " / " + curResult.toString());

        } else {
            throw new DiffServiceException(String.format("%s and %s must be of same type (directory/file).", prevResult.toString(), curResult.toString()));
        }
    }

    /**
     * Handles creating a diff between files noting if one of them is not
     * present. If both are not present or both are the same, null is returned.
     *
     * @param orig The original file.
     * @param cur The current file.
     * @param identifier The identifier for the header.
     * @return The String representing the differences.
     */
    private String getFileDiffs(File orig, File cur, String identifier) {
        boolean hasOrig = (orig != null && orig.exists());
        boolean hasCur = (cur != null && cur.exists());
        if (!hasOrig && !hasCur) {
            return null;
        } else if (!hasOrig && hasCur) {
            return getHeaderWithDivider("ADDITIONAL FILE IN CURRENT: " + identifier);
        } else if (hasOrig && !hasCur) {
            return getHeaderWithDivider("MISSING FILE IN CURRENT: " + identifier);
        } else {
            try {
                return diffLines(Files.readAllLines(orig.toPath()), Files.readAllLines(cur.toPath()), getHeaderWithDivider(identifier + ":"));
            } catch (IOException ex) {
                return getHeaderWithDivider(String.format("ERROR reading files at %s / %s %s%s",
                        orig.toString(), cur.toString(), NEW_LINE, ExceptionUtils.getStackTrace(ex)));
            }
        }
    }

    private String getChunkLineNumString(Chunk<?> chunk) {
        return String.format("%d,%d", chunk.getPosition() + 1, chunk.getLines().size());
    }

    /**
     * Gets a github-like line difference (i.e. -88,3 +90,3) of the form
     * -orig_line_num,orig_lines, +new_line_num,new_lines.
     *
     * @param orig The previous chunk.
     * @param cur The current chunk.
     * @return The line number difference.
     */
    private String getDiffLineNumString(Chunk<?> orig, Chunk<?> cur) {
        return String.format("-%s +%s", getChunkLineNumString(orig), getChunkLineNumString(cur));
    }

    /**
     * Creates a line by line difference similar to integration tests like:
     * < original
     * > new
     *
     * @param orig The original chunk.
     * @param cur The new chunk.
     * @return The lines representing the diff.
     */
    private List<String> getLinesDiff(Chunk<String> orig, Chunk<String> cur) {
        Stream<String> origPrefixed = orig.getLines().stream()
                .map((line) -> ORIG_LINE_PREFIX + line);

        Stream<String> curPrefixed = cur.getLines().stream()
                .map((line) -> CUR_LINE_PREFIX + line);

        return Stream.concat(origPrefixed, curPrefixed)
                .collect(Collectors.toList());
    }

    private String getLinesDiffString(AbstractDelta<String> delta) {
        String lineNums = getDiffLineNumString(delta.getSource(), delta.getTarget());
        List<String> linesDiff = getLinesDiff(delta.getSource(), delta.getTarget());

        return Stream.concat(Stream.of(lineNums), linesDiff.stream())
                .collect(Collectors.joining(NEW_LINE)) + NEW_LINE;
    }

    /**
     * Creates a line difference String with a header if non-null. Null is
     * returned if there is no diff.
     *
     * @param orig The original lines.
     * @param cur The current lines.
     * @param header The header to be used if non-null diff. If header is null,
     * no header included.
     * @return The pretty-printed diff.
     */
    private String diffLines(List<String> orig, List<String> cur, String header) {
        //compute the patch: this is the diffutils part
        Patch<String> patch = DiffUtils.diff(orig, cur);

        String diff = patch.getDeltas().stream()
                .map(delta -> getLinesDiffString(delta))
                .collect(Collectors.joining(String.join(NEW_LINE, DIFF_BREAK)));

        if (StringUtils.isBlank(diff)) {
            return null;
        }

        return (header != null)
                ? header + NEW_LINE + diff
                : diff;
    }

    private String getHeaderWithDivider(String remark) {
        String divider = "-----------------------------------------------------------";
        return String.join(NEW_LINE, divider, remark, divider);
    }

    private String getRelative(File rootDirectory, File file) {
        return rootDirectory.toURI().relativize(file.toURI()).getPath();
    }
}
