The 'bin' folder is the version used when running the PhotoRec ingest module.  It is also the 32-bit version.
When the 64-bit version of the installer is created, the photorec_exec/64-bit/bin folder is placed at photorec_exec/bin. 
When the 32-bit version of the installer is created, the photorec_exec/64-bit folder is deleted.
See 'build-windows-installer.xml' for more details.

Extensions for PhotoRec need to be placed in the PhotoRecCarverFileOptExtensions class so that only valid extensions will be used with PhotoRec.  It can be generated through PhotoRec by launching photorec_win with no arguments, go to "Proceed", go to "File Opt" and press 'b'.  This should generate a photorec.cfg file in the current working directory with a list of all the extensions.