/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
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
package org.sleuthkit.autopsy.rejview;

import com.williballenthin.rejistry.HexDump;
import com.williballenthin.rejistry.RegistryParseException;
import com.williballenthin.rejistry.ValueData;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import org.openide.util.NbBundle.Messages;

/**
 * Formats a ValueData to a string similar to Regedit.exe on Windows. For an
 * example, see the nice sample here:
 * http://raja5.files.wordpress.com/2009/08/wincleanup_regedit.jpg
 */
final class RegeditExeValueFormatter {

    @Messages({"RegeditExeValueFormatter.valueNotSet.text=(value not set)"})
    static String format(ValueData val) throws UnsupportedEncodingException, RegistryParseException {
        StringBuilder sb = new StringBuilder();

        switch (val.getValueType()) {
            case REG_SZ: // empty case - intentional fall-through
            case REG_EXPAND_SZ: {

                String valString = val.getAsString();
                if (valString.length() == 0) {
                    sb.append(Bundle.RegeditExeValueFormatter_valueNotSet_text());
                } else {
                    sb.append(valString);
                }
                if (sb.length() > 48) {
                    sb.setLength(45);
                    sb.append("...");
                }
                break;
            }
            case REG_MULTI_SZ: {
                Iterator<String> it = val.getAsStringList().iterator();
                while (it.hasNext()) {
                    sb.append(it.next());
                    if (it.hasNext()) {
                        sb.append(", ");
                    }
                }
                if (sb.length() > 48) {
                    sb.setLength(45);
                    sb.append("...");
                }
                break;
            }
            case REG_DWORD: // empty case - intentional fall-through
            case REG_BIG_ENDIAN: {
                sb.append(String.format("0x%08x (%d)", val.getAsNumber(), val.getAsNumber()));
                break;
            }
            case REG_QWORD: {
                sb.append(String.format("0x%016x (%d)", val.getAsNumber(), val.getAsNumber()));   // can you even do %016x?
                break;
            }
            default: {
                ByteBuffer valData = val.getAsRawData();
                valData.position(0x0);
                for (int i = 0; i < Math.min(16, valData.limit()); i++) {
                    byte b = valData.get();
                    sb.append(HexDump.toHexString(b));
                    if (i != 15) { // magic number, sorry.
                        sb.append(' ');
                    }
                }
                if (valData.limit() > 16) {
                    sb.append("...");
                }
            }
        }
        return sb.toString();
    }

    private RegeditExeValueFormatter() {
        //contrsuctor intentially left blank
    }
}
