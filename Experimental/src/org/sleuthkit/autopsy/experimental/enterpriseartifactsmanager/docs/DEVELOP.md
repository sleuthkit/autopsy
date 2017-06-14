# Instructions for doing development of Autopsy Modules

## On Windows, Setup your development environment with Autopsy sources and javadocs

* Install x64 PostgreSQL and setup: 
  * http://sleuthkit.org/autopsy/docs/user-docs/4.3/install_postgresql.html

* Install Oracle Java SE JDK 8 - Windows x64 from Oracle:
  * http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

* Install NetBeans (choose the 'All' version):
  * https://netbeans.org/downloads/

* Install Git for Windows x64:
  * https://git-scm.com/downloads

* Install doxygen and make sure it is added to your PATH
    * http://www.stack.nl/~dimitri/doxygen/download.html

* Sleuthkit and the DataModel java bindings
    * Clone sleuthkit repo and set TSK_HOME environment variable.
    * For the java bindings, there are two ways to get these
        1. [build Sleuthkit and then the java bindings](https://github.com/sleuthkit/sleuthkit/blob/develop/win32/BUILDING.txt), requiring Visual Studio and several
      dependant libraries.
        2. download the [Autopsy dev platform zip](https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.4.0/autopsy-4.4.0-devplatform.zip) and copy autopsy-4.4.0-devplatform/autopsy/modules/ext/Tsk_DataModel_PostgreSQL.jar to TSK_HOME/bindings/java/dist/
    * Set up environment variables, sample values:
        - JAVA_HOME=C:\Program Files\Java\jdk1.8.0_121
        - JDK_HOME=C:\Program Files\Java\jdk1.8.0_121
        - JRE_HOME_64=C:\Program Files\Java\jre1.8.0_121
        - LIBEWF_HOME=C:\libewf_64bit (only needed if you chose option #1 above)
        - LIBVHDI_HOME=C:\libvhdi_64bit (only needed if you chose option #1 above)
        - POSTGRESQL_HOME_64=c:\Program Files\PostgreSQL\9.6 (only needed if you chose option #1 above)
        - TSK_HOME=c:\sleuthkit
        - PATH=...;C:\Program Files\Java\jdk1.8.0_121\bin;C:\Program Files\NetBeans 8.2\extide\ant\bin;C:\Program Files\doxygen\bin

* Build Autopsy platform:
    * Reference: https://github.com/sleuthkit/autopsy/blob/develop/BUILDING.txt
    * Clone Autopsy project
        * git clone git@github.com:sleuthkit/autopsy.git
        * git checkout develop
    * Add Autopsy project to NetBeans
        * File -> Open Project
    * Build the top level Autopsy project
    * Generate javadoc and add doc folder in the documentation tab

If the project builds correctly, everything is installed correctly.

## How to build disk images for development/testing

Refer to MS technet instructions for creating/using a VHD: https://technet.microsoft.com/en-us/library/gg318052(v=ws.10).aspx

But here is the general idea:
* On Windows, use Disk Management tool to create a Virtual Hard Disk (.vhd) using the "dynamically expanding" disk format. Choose a small-ish disk size if you want the testing to be quick.
* Initialize the disk (Initialize Disk).
* Format the disk (New Simple Volume).
* Mount that disk (Attach VHD)
* Copy some files onto the disk.
* Umount that disk (Detach VHD). Do NOT delete the disk when detaching!

Repeat the above steps to create additional disk images.

