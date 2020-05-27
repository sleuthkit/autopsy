RegRipper2.8
============

RegRipper version 2.8

This is the GitHub repository for RegRipper version 2.8

Note: This tool does NOT automatically process hive transaction logs.  If you need
to incorporate data from hive transaction logs into your analysis, consider merging
the data via Maxim Suhanov's yarp + registryFlush.py, or via Eric Zimmerman's rla.exe.

Updates 20200220
- Added warning that tool does not automatically process Registry hive transaction logs
- Added check for dirty hives
- Modified C:\Perl\site\lib\Parse\Win32Registry\WinNT\File.pm  
  - if you're using the Perl version of this tool (Linux, Mac) be sure to copy File.pm  
    from the repository and replace the appropriate file
	
Updates 20200104
- Fixed issue with processing of key LastWrite times
- Modified C:\Perl\site\lib\Parse\Win32Registry\WinNT\Base.pm
  - if you're using the Perl version of this tool (Linux, Mac) be sure to copy Base.pm
    from the repository and replace the appropriate file
	
Updates 20190128
- added Time::Local module 
  - this allows plugins to be written that parse string-based date/time stamps, converting 
    them to epochs (for timelining, etc.)
- modified C:\Perl\site\lib\Parse\Win32Registry\WinNT\Key.pm
  - extract access_bits and largest_subkey_name_length values from Key node structure
  - call 'get_access_bits()', 'get_largest_subkey_name_length()' to retrieve the values for parsing/display
  - IAW https://github.com/msuhanov/regf/blob/master/Windows%20registry%20file%20format%20specification.md

Note: The modifications to Key.pm are 'compiled' into the EXE versions of RegRipper.  In order to fully take
advantage of them with the .pl versions:
- got to \Perl\site\lib\Parse\Win32Registry\WinNT\
- rename Key.pm to Key_old.pm 
- copy Key.pm from this distro to the folder

Updates 20200104
Based on how key LastWrite times were being converted from FILETIME objects to Unix epoch format, the function 
appears to have 'broke' as of 1 Jan 2020.  As such, I modified/fixed the code, and have updated the compiled 
EXEs for the tools.  I've also provided an updated Base.pm file, with instructions below as to how to update
your local copy of the file.

- Navigate to the \site\lib\Parse\Win32Registry\ folder in your Perl installation, and remove any restrictions
  or attributes from Base.pm (i.e., 'attrib -r Base.pm')
- Rename Base.pm to Base_old.pm
- Copy the Base.pm from this repository  
=======

