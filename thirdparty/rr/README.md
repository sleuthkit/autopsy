# RegRipper4.0

What's new in RegRipper4.0

## WHAT'S NEW

RegRipper4.0 includes ISO 8601-ish time stamp formatting, MITRE ATT&CK
mapping (for some, albeit not all, plugins), and Analysis Tips. Also, there
are many new plugins since August, 2020.

Yara - https://virustotal.github.io/yara/

You can run Yara rules against Registry data! Go to the Yara site (above)
and download the latest release. Copy the 'yara64.exe' file to the root of
your RR4.0 folder (the same one with rip.exe). The "run_yara.pl" plugin 
provides an example of a RegRipper plugin that implements Yara. Yara rule
files will need to be in the same folder as the Yara executable file.

## LICENSE

This version is free for personal and academic (college/university) use ONLY.

RegRipper4.0 may not be included in vendor products, vendor training, nor in
any distribution.

### NOTE

This tool does NOT automatically process hive transaction logs. If you need
to incorporate data from hive transaction logs into your analysis, consider merging
the data via Maxim Suhanov's `yarp` + `registryFlush.py`, or via Eric Zimmerman's `rla.exe`
which is included in [Eric's Registry Explorer/RECmd](https://f001.backblazeb2.com/file/EricZimmermanTools/RegistryExplorer_RECmd.zip).