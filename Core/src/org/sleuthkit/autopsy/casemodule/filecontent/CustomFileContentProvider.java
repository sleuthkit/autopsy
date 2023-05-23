/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.filecontent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FileContentStream;
import org.sleuthkit.datamodel.FileContentStream.FileContentProvider;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class CustomFileContentProvider implements FileContentProvider {

    private static final String MD5_KEY = "md5";
    private static final String APP_DATA_DIR_KEY = "appdatadir";
    private static final String USER_DIR_KEY = "userdir";
    private static final String APPLICATION_DIR_KEY = "applicationdir";

    private static String namedGroup(String key, String regex, boolean required) {
        return "(?<" + key + ">" + regex + ")" + ((required) ? "" : "?");
    }

    private static final String VAR_KEY = "var";
    private static final String SUBSTR_KEY = "substr";
    private static final String FROM_IDX_KEY = "fromidx";
    private static final String COLON_KEY = "colon";
    private static final String TO_IDX_KEY = "toidx";
    private static final String INT_REGEX_STR = "\\-?\\d+?";

    // processes variables kind of like python substrings (i.e. varname[1:3] )
    private static final Pattern VAR_REGEX = Pattern.compile("^\\s*"
            + namedGroup(VAR_KEY, "[a-zA-Z0-9\\\\-_\\\\.]+?", true)
            + namedGroup(SUBSTR_KEY,
                    "\\s*"
                    + "\\["
                    + "\\s*"
                    + namedGroup(FROM_IDX_KEY, INT_REGEX_STR, false)
                    + "\\s*"
                    + namedGroup(COLON_KEY, ":", false)
                    + "\\s*"
                    + namedGroup(TO_IDX_KEY, INT_REGEX_STR, false)
                    + "\\s*"
                    + "\\]",
                    false)
            + "\\s*$");

    private static final String SUB_DELIM = "$";
    private static final char DELIMITER = SUB_DELIM.charAt(0);
    private static final String VAR_PREFIX = SUB_DELIM;
    private static final String VAR_SUFFIX = SUB_DELIM;

    private final String stringTemplate;
    private final GlobalVars globalVars;

    public static CustomFileContentProvider getProvider(String stringTemplate) throws IllegalStateException {
        return StringUtils.isBlank(stringTemplate)
                ? null
                : new CustomFileContentProvider(stringTemplate, GlobalVars.getDefault());
    }

    CustomFileContentProvider(String stringTemplate, GlobalVars globalVars) {
        this.globalVars = globalVars;
        //this.stringSub = 
        this.stringTemplate = stringTemplate;
    }

    @Override
    public FileContentStream getFileContentStream(AbstractFile af) throws TskCoreException {
        File localFile = getFilePath(af);
        if (localFile != null && localFile.exists() && localFile.isFile()) {
            try {
                RandomAccessFile fileHandle = new RandomAccessFile(localFile, "r");
                return new CustomFileContentStream(fileHandle);
            } catch (FileNotFoundException ex) {
                throw new TskCoreException("File could not be read", ex);
            }
        }
        return null;
    }

    File getFilePath(AbstractFile af) {
        StringLookup lookup = (key) -> getKeyValue(key, af, this.globalVars);
        StringSubstitutor stringSub = new StringSubstitutor(lookup, VAR_PREFIX, VAR_SUFFIX, DELIMITER);
        String filePath = stringSub.replace(stringTemplate);

        return new File(filePath);
    }

    static String getKeyValue(String key, AbstractFile af, GlobalVars globalVars) {
        // variable regex processing
        Matcher matcher = VAR_REGEX.matcher(key);
        if (!matcher.find()) {
            return "";
        }

        String variable = matcher.group(VAR_KEY).toLowerCase();

        Integer fromIdx = null;
        Integer toIdx = null;
        boolean hasColon = false;

        String fromIdxStr = matcher.group(FROM_IDX_KEY);
        String toIdxStr = matcher.group(TO_IDX_KEY);
        if (StringUtils.isNotBlank(toIdxStr) || StringUtils.isNotBlank(fromIdxStr)) {
            toIdx = tryParse(toIdxStr);
            fromIdx = tryParse(fromIdxStr);
            hasColon = StringUtils.isNotBlank(matcher.group(COLON_KEY));
        }

        String varVal = getVarVal(variable, af, globalVars);

        if (toIdx != null || fromIdx != null) {
            if (fromIdx == null) {
                fromIdx = 0;
            } else if (fromIdx < 0) {
                fromIdx = varVal.length() + fromIdx;
            }

            if (toIdx == null) {
                if (hasColon) {
                    toIdx = varVal.length();
                } else {
                    toIdx = fromIdx + 1;
                }
            } else if (toIdx < 0) {
                toIdx = varVal.length() + fromIdx;
            }

            return varVal.substring(fromIdx, toIdx);
        } else {
            return varVal;
        }
    }

    static String getVarVal(String var, AbstractFile af, GlobalVars globalVars) {
        switch (var) {
            case MD5_KEY:
                return af.getMd5Hash();
            case APP_DATA_DIR_KEY:
                return globalVars.getAppDataDir();
            case USER_DIR_KEY:
                return globalVars.getUserDir();
            case APPLICATION_DIR_KEY:
                return globalVars.getApplicationDir();
            default:
                return "";
        }
    }

    static Integer tryParse(String intVal) {
        if (StringUtils.isNotBlank(intVal)) {
            try {
                return Integer.parseInt(intVal);
            } catch (NumberFormatException ex) {
                // ignore and just return null
            }
        }
        return null;
    }

    public static class GlobalVars {

        private final String appDataDir;
        private final String userDir;
        private final String applicationDir;

        public static GlobalVars getDefault() throws IllegalStateException {
            try {
                return new GlobalVars(
                        // taken from https://stackoverflow.com/a/1198954/2375948
                        System.getenv("APPDATA"),
                        // taken from https://stackoverflow.com/a/586345/2375948
                        System.getProperty("user.home"),
                        // taken from https://stackoverflow.com/a/4033033/2375948
                        new File(".").getCanonicalPath()
                );
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to get application directory", ex);
            }
        }

        GlobalVars(String appDataDir, String userDir, String applicationDir) {
            this.appDataDir = appDataDir;
            this.userDir = userDir;
            this.applicationDir = applicationDir;
        }

        public String getAppDataDir() {
            return appDataDir;
        }

        public String getUserDir() {
            return userDir;
        }

        public String getApplicationDir() {
            return applicationDir;
        }
    }

    static class CustomFileContentStream implements FileContentStream {

        private final RandomAccessFile localFileHandle;

        public CustomFileContentStream(RandomAccessFile localFileHandle) {
            this.localFileHandle = localFileHandle;
        }

        @Override
        public int read(byte[] buf, long offset, long len) throws TskCoreException {
            try {
                //move to the user request offset in the stream
                long curOffset = localFileHandle.getFilePointer();
                if (curOffset != offset) {
                    localFileHandle.seek(offset);
                }
                //note, we are always writing at 0 offset of user buffer
                return localFileHandle.read(buf, 0, (int) len);
            } catch (IOException ex) {
                throw new TskCoreException(MessageFormat.format("An exception occurred while reading offset: {0}, length {1} of file", offset, len), ex);
            }
        }

    }
}
