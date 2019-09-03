/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;
import java.util.HashMap;
import java.util.Map;

/*
 * Windows Kernel32 interface
 */
public interface Kernel32 extends StdCallLibrary {

    Map<String, Object> WIN32API_OPTIONS = new HashMap<String, Object>() {
        private static final long serialVersionUID = 1L;
        {
            put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
            put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
        }
    };

    Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("Kernel32", Kernel32.class, WIN32API_OPTIONS);

    /*
    BOOL WINAPI GetVolumeInformation(
            __in_opt   LPCTSTR lpRootPathName,
            __out      LPTSTR lpVolumeNameBuffer,
            __in       DWORD nVolumeNameSize,
            __out_opt  LPDWORD lpVolumeSerialNumber,
            __out_opt  LPDWORD lpMaximumComponentLength,
            __out_opt  LPDWORD lpFileSystemFlags,
            __out      LPTSTR lpFileSystemNameBuffer,
            __in       DWORD nFileSystemNameSize
            );
     */
    boolean GetVolumeInformation(
            String lpRootPathName,
            char[] lpVolumeNameBuffer,
            DWORD nVolumeNameSize,
            IntByReference lpVolumeSerialNumber,
            IntByReference lpMaximumComponentLength,
            IntByReference lpFileSystemFlags,
            char[] lpFileSystemNameBuffer,
            DWORD nFileSystemNameSize
            );

    int GetLastError();
}
