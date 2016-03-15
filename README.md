Autopsy 4

http://www.sleuthkit.org/

March 15, 2016


##OVERVIEW

Autopsy is a graphical interface to The Sleuth Kit and other open source digital forensics tools. 
Autopsy 3 was a complete rewrite from Autopsy 2 to make it Java-based.
Autopsy 4 improves on Autopsy 3 by supporting collaboration on a single case by multiple users.     

Although Autopsy is designed to be cross-platform (Windows, Linux, MacOSX), the current version is fully functional and fully tested only on Windows. 
We have run it on XP, Vista, and Windows 7 with no problems. 

Autopsy 4 is released under the Apache 2.0 license.
Some libraries Autopsy uses may have different, but similar, open source licenses. 


##INSTALLATION

For a Windows installation, all Autopsy dependencies are bundled with the installer provided.
There is no need for manual installation of additional dependencies if the Windows installer is used.

If you want the Japanese localized version, you must have the Japanese language pack (http://support.microsoft.com/kb/972813) installed and the default locale set to JA. (http://windows.microsoft.com/en-us/windows/change-system-locale#1TC=windows-7).

Refer to the KNOWN_ISSUES.txt file for known bugs that could cause investigation problems. 


##SUPPORT

There is a built-in help system in Autopsy once you get it started.  There is also a QuickStart Guide that comes with the installer.

Send any bug reports or feature requests to the sleuthkit-users e-mail list.
    http://www.sleuthkit.org/support.php


##LICENSE

The Autopsy code is released under the Apache License, Version 2.  See LICENSE-2.0.txt for details.


##EMBEDDED SOFTWARE

This section lists the software components and libraries that are used by of
Autopsy.   These tools are bundled with the Windows installer, unless specified otherwise.

JRE (Java Runtime Environment) 1.8
- Web page: http://www.oracle.com/technetwork/java/index.html
- License: http://www.oracle.com/technetwork/java/javase/terms/license/index.html

Netbeans 8.0.2 RCP platform and .jar files bundled with the platform
- Web page: http://netbeans.org/features/platform/
- License: 
https://netbeans.org/downloads/jdk-bundle/8.0.2/nb802-LICENSE.txt

Sleuth Kit for analyzing disk images.
- Web page: http://www.sleuthkit.org/sleuthkit/
- License: http://sleuthkit.org/sleuthkit/licenses.php

Libewf for opening E01 files
- Web page: https://github.com/libyal/libewf
- License: http://www.gnu.org/licenses/lgpl.html

zlib for opening E01 files
- Web page: http://zlib.net/
- License: http://zlib.net/zlib_license.html

Solr (including Lucene and TIKA) for keyword search
- Web page: http://projects.apache.org/projects/solr.html
- License: http://www.apache.org/licenses/LICENSE-2.0

GStreamer for viewing video files
- Web page: http://gstreamer.freedesktop.org/
- License: http://www.gnu.org/licenses/lgpl.html

GStreamer-java for viewing video files
- Web page: http://code.google.com/p/gstreamer-java/
- License: http://www.gnu.org/licenses/lgpl.html

Regripper for pulling recently activity
(Including custom plugins)
- Web page: http://regripper.wordpress.com/
- License: http://www.gnu.org/licenses/gpl.html

Pasco2 for pulling Internet Explorer activity
- Web page: http://sourceforge.net/projects/pasco2/
- License: http://www.gnu.org/licenses/gpl.html

Jericho for extracting content from HTML files
- Web page: http://jerichohtml.sourceforge.net/
- License: http://www.gnu.org/copyleft/lesser.html

Advanced installer 9 (Freeware)
(not embedded in Autopsy, but used to generate Autopsy installer.)
- Web page: http://www.advancedinstaller.com/

Metadata Extractor 2.6.2 for extracting Exif metadata
- Web page: http://www.drewnoakes.com/code/exif/
- License: http://www.apache.org/licenses/LICENSE-2.0

Reflections 0.9.8 for ingest module loading
- Web page: http://code.google.com/p/reflections 
- License: http://en.wikipedia.org/wiki/WTFPL

Sigar for process monitoring
- Web page: http://support.hyperic.com/display/SIGAR/Home
- License: http://support.hyperic.com/display/SIGAR/Home#Home-license

7Zip and 7Zip java bindings for 7Zip extractor module
- Web page: http://sevenzipjbind.sourceforge.net/
- License: http://sourceforge.net/directory/license:lgpl/

ImgScalr 4.2 for image resizing in image viewers
- Web page: http://www.thebuzzmedia.com/software/imgscalr-java-image-scaling-library/
- License: http://www.thebuzzmedia.com/software/imgscalr-java-image-scaling-library/#license


##EMBEDDED RESOURCES

This section lists other resources, such as icons, that are used by Autopsy.   

FAMFAMFAM Silk Icons v1.3
- Web page: http://www.famfamfam.com/lab/icons/silk/
- License: http://creativecommons.org/licenses/by/3.0/

Fugue Icons v3.5.6
- Web page: http://p.yusukekamiyamane.com/
- License: http://creativecommons.org/licenses/by/3.0/

WebHostingHub Glyphs
- Web page: http://www.webhostinghub.com/glyphs/
- License: http://creativecommons.org/licenses/by/3.0/

Splashy Icons (free as in free) 
- Web page: http://splashyfish.com/icons/