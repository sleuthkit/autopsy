REM Updates the 32-bit and 64-bit VS Runtime dlls
REM Needs to be run from a 64-bit command prompt
REM Otherwise Windows will put 32-bit dlls in system32
copy c:\windows\system32\msvcr100.dll win64
copy c:\windows\system32\msvcp100.dll win64
copy c:\windows\sysWoW64\msvcr100.dll win32
copy c:\windows\sysWow64\msvcp100.dll win32
