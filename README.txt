                               Autopsy 3.0 (Beta)
                          http://www.sleuthkit.org/

                                June 4, 2012


OVERVIEW

Autopsy is a graphical interface to The Sleuth Kit and other open source digital forensics tools. 
Autopsy 3 is a complete rewrite from Autopsy 2 and it is now Java-based.  This version is currently in a beta state.   

The current beta version works only on Windows.  We have run it on XP, Vista, and Windows 7 with no problems. 

Autopsy 3.0 is released under the Apache 2.0 license. 


INSTALLATION

All Autopsy dependencies are bundled with the installer provided.
There is no need for manual installation of additional dependencies if the installer is used.

Refer to the next section for additional info on third-party software requirements to run Autopsy without installer.


EMBEDDED SOFTWARE

Autopsy (core) utilizes the following third-party software tools.
The tools are bundled with the installer, unless specified otherwise.

* JRE (Java Runtime Environment) 1.6, 32 bit

Web page: http://www.oracle.com/technetwork/java/index.html
Oracle license: http://www.oracle.com/technetwork/java/javase/terms/license/index.html

JRE needs to be manually installed on the system if Autopsy installer is not used.

* Netbeans 7.0.1 RCP platform and .jar files bundled with the platform

Web page: http://netbeans.org/features/platform/
License: 
http://services.netbeans.org/downloads/licence/nb-7.0-final-2011-04-20-license.txt

* Solr (including Lucene and TIKA)
Web page: http://projects.apache.org/projects/solr.html
Apache license: http://www.apache.org/licenses/LICENSE-2.0

* GStreamer
Web page: http://gstreamer.freedesktop.org/
License: http://www.gnu.org/licenses/lgpl.html

If Autopsy installer is not used, add the following entries to Windows PATH environment variable 
(replace GSTREAMER_INSTALL_DIR with the location of gstreamer install root directory):

GSTREAMER_INSTALL_DIR\bin\;
GSTREAMER_INSTALL_DIR\lib\gstreamer-0.10\;


* GStreamer-java
Web page: http://code.google.com/p/gstreamer-java/
License: http://www.gnu.org/licenses/lgpl.html


* Regripper
(regripper and custom plugins found in autopsy/thirdparty)
Web page: http://regripper.wordpress.com/
License: http://www.gnu.org/licenses/gpl.html

* Pasco
Web page: http://sourceforge.net/projects/odessa/files/Pasco/

* Advanced installer 9.0 (Freeware)
(not embedded in Autopsy, but used to generate Autopsy installer.)
If you want to generate Autopsy installer, you will need to install the freeware version of Advanced Installer software)

Web page: http://www.advancedinstaller.com/


CONTRIBUTORS

The primary development of Autopsy 3 has been done at Basis Technology. The following people have developed code in the project:
* Anthony Lawrence
* James Antonius
* Peter Martel
* Adam Malinowski
* Dick Fickling


FEEDBACK

Send any bug reports or feature requests to the sleuthkit-users e-mail list.
    http://www.sleuthkit.org/support.php