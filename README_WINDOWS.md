## Autopsy Development Environment Setup Guide for Windows

### Install Dependencies / Development Tools

1. Install cygwin
    1. [http://www.cygwin.com/install.html](http://www.cygwin.com/install.html)
    2. Make sure you select the git, python 2 and python 3 add-ons.
1. Install Visual Studio Community 2015.
    1. [https://visualstudio.microsoft.com/vs/older-downloads](https://visualstudio.microsoft.com/vs/older-downloads)
    2. A Microsoft account and joining the Visual Studio Dev Essentials program will be required to enable the download.
2. Install Java Development Kit (JDK). 
    1. [https://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html](https://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html)
    2. Install **_Java 8 update 181_** 64-bit for Windows. Later updates to Java 8 have a file chooser bug. **Note**: An Oracle account will be required to download the JDK.
3. Install NetBeans 8.2 IDE.
    1. [https://netbeans.org/downloads/](https://netbeans.org/downloads/)
    2. Download ivy [https://ant.apache.org/ivy/download.cgi](https://ant.apache.org/ivy/download.cgi) and copy the ivy-{version}.jar file into netbeans\extide\ant\lib\
4. Install Doxygen.
    1. [http://www.doxygen.nl/download.html](http://www.doxygen.nl/download.html)
    2. Download and run the Windows installer. 
5. Install PostgreSQL 9.5.
    1. [https://www.postgresql.org/download/windows](https://www.postgresql.org/download/windows)
    2. Install 64-bit PostgreSQL 9.5 for Windows.


### Define Environment Variables

Define the following environment variables ([this guide](https://www.java.com/en/download/help/path.xml) explains how to add new variables as well as edit the PATH, which we will do soon as well):

1. Add new environment variables
    1. `JAVA_HOME`: Path to your 64-bit JDK installation, e.g., C:\Program Files\Java\jdk1.8.0_181.
    2. `JRE_HOME`: Path to the JRE within your 64-bit JDK installation, e.g., C:\Program Files\Java\jdk1.8.0_181/jre.
    3. `JDK_HOME` : Path to your 64-bit JDK installation, e.g., C:\Program Files\Java\jdk1.8.0_181.
    4. `LIBEWF_HOME`: Path to an appropriate place for this dependency repo, e.g., C:\Users\user\repos\libewf_64bit.
    5. `LIBVHDI_HOME`: Path to Path to an appropriate place for this dependency repo, e.g., C:\Users\user\repos\libvhdi_64bit.
    6. `LIBVMDK_HOME`: Path to an appropriate place for this dependency repo, e.g., **Note the difference - this one includes a subdirectory of the libvmdk repo. **C:\Users\user\repos\libvmdk_64bit\libvmdk.
    7. `POSTGRESQL_HOME_64`: Path to your 64-bit PostgreSQL directory, e.g., C:\Program Files\PostgreSQL\9.5.
    8. `TSK_HOME`: Path to where you will clone sleuthkit, e.g., C:\Users\user\repos\sleuthkit.
2. Add directories to the system PATH** **variable
    1. The JDK bin directory, e.g., C:\Program Files\Java\jdk1.8.0_181\bin.
    2. The PostgreSQL bin, e.g., C:\Program Files\PostgreSQL\9.5\bin
    3. The ant directory, e.g., C:\Program Files\NetBeans 8.2\extide\ant. 


### Clone Sleuthkit and its Dependencies

1. Clone sleuthkit [https://github.com/sleuthkit/sleuthkit](https://github.com/sleuthkit/sleuthkit) to the path specified earlier via TSK_HOME.
2. With the aforementioned environment variables defined, simply run the repo setup script: `python setupDevRepos.py`


### Build

1. To build the sleuthkit dependencies, navigate to the `win32` subdirectory in the sleuthkit repo. With python, run the following:
`python updateAndBuildAll.py -m`
2. Open the sleuthkit Java bindings project in the NetBeans IDE. The project is located in the bindings/java subdirectory of your sleuthkit clone. 
    1. Build the **dist-PostgreSQL** target of the sleuthkit Java bindings in the NetBeans IDE by selecting the build.xml file and then Run Target from the context menu. 
3. Build Autopsy - it can be built from Netbeans **(i)**, or from the command line **(ii)**
    1. **From Netbeans**:
        1. Open the Autopsy project in the NetBeans IDE. The project is located in the top level directory of your autopsy clone.
        2.  Press the green ‘play’ button to build and run Autopsy
    2. **From cygwin command line**:
        1. inside the autopsy repo, run the following commands. **Note:** for convenience, you may want to add ant to your system PATH so you can simply type “ant” on the command line. Otherwise, you will need to supply the full path to the ant bin, e.g., /cygdrive/c/Users/user/Desktop/netbeans/extide/ant/bin/ant

                ant
                ant run

