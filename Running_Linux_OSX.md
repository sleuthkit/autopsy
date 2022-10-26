# Overview

When installing on Debian-based Linux or macOS systems, there are three general steps: [installing prerequisites](#installing-prerequisites), [installing The Sleuth Kit](#installing-the-sleuth-kit), and [installing Autopsy](#installing-autopsy) itself.  On macOS, you will want to [setup the JNA paths](#setup-macos-jna-paths).

# Installing Prerequisites

- **Linux**: Run [`linux_macos_install_scripts/install_prereqs_ubuntu.sh`](./linux_macos_install_scripts/install_prereqs_ubuntu.sh).
- **macOS**: Run [`linux_macos_install_scripts/install_prereqs_macos.sh`](./linux_macos_install_scripts/install_prereqs_macos.sh).  This script requires the package manager: [Homebrew](https://brew.sh/), which has installation steps on their site.

*NOTE: The last output of the script is the path to the Java 8 installation.  You will want to note that path when installing Autopsy.*

# Installing The Sleuth Kit

- **Linux**: Install The Sleuth Kit [package](https://packages.ubuntu.com/search?suite=default&keywords=sleuthkit) from the repositories with the following command: `sudo apt update && sudo apt install sleuthkit`.
- **macOS**: Install The Sleuth Kit from source by running [`linux_macos_install_scripts/install_tsk_from_src.sh`](./linux_macos_install_scripts/install_tsk_from_src.sh), which will download, build, and install The Sleuth Kit.  It can be run as follows: `install_tsk_from_src -p ~/src/sleuthkit -b sleuthkit-4.11.1`.  Make sure that your path to download source ends with "sleuthkit" as the last directory, and the release is the corresponding tag in the [repository](https://github.com/sleuthkit/sleuthkit).

# Installing Autopsy

- Download the Autopsy zip file from [repository releases](https://github.com/sleuthkit/autopsy/releases).  The file will be marked as "autopsy-&ltrelease&gt.zip" (i.e. "autopsy-4.19.2.zip").
- Run [`install_application.sh`](./linux_macos_install_scripts/install_application.sh) with the following parameters: `install_application.sh [-z zip_path] [-i install_directory] [-j java_home]`.  An example would be `install_application.sh -z ~/Downloads/autopsy-4.19.2.zip -i ~/autopsy -j /usr/lib/jvm/bellsoft-java8-full-amd64`.  The path to the Java 8 home is the last output from the [prequisites installation scripts](#installing-prerequisites), but typically, the path will be `/usr/lib/jvm/bellsoft-java8-full-amd64` on Debian-based Linux or the output of running `/usr/libexec/java_home -v 1.8` on macOS.

# Setup macOS JNA paths
If you are on macOS run [linux_macos_install_scripts/add_macos_jna.sh](./linux_macos_install_scripts/add_macos_jna.sh) to properly setup the jna path to get things like gstreamer working.

# Troubleshooting
- If you see something like "Cannot create case: javafx/scene/paint/Color" it is an indication that Java FX
  is not being found.  Confirm that the file `$JAVA_HOME/jre/lib/ext/jfxrt.jar` exists. If it does not exist, return to the Java
  setup steps above.
- If you see something like "An illegal reflective access operation has occurred" it is an indication that
  the wrong version of Java is being used to run Autopsy.
  Check the version of Java reported in the `messages.log` file in the log directory.  The log directory can be found by opening Autopsy, and, with no cases open, go to 'Help' > 'Open Log Folder'. `messages.log` should contain lines that looks like:
  ```
  Java; VM; Vendor        = 1.8.0_342; OpenJDK 64-Bit Server VM 25.342-b07; BellSoft
  Runtime                 = OpenJDK Runtime Environment 1.8.0_342-b07
  Java Home               = /usr/lib/jvm/bellsoft-java8-full-amd64/jre
  ```

  If your `messages.log` file indicates that Java 8 is not being used:
  - Confirm that you have a version of Java 8 installed
  - Confirm that your java path environment variable is set correctly.  Autopsy first uses the value of `jdkhome` in `<autopsy_install_location>/etc/autopsy.conf`, so look for an uncommented line (not starting with '#') that looks like `jdkhome=<java path>`.  If that is not set, check your `$JAVA_HOME` environment variable by running `echo $JAVA_HOME`.
- If you see something like "cannot be opened because the developer cannot be verified." it is an indication that Gatekeeper is running and is stopping a file from being executed.  To fix this open a new terminal window and enter the following command `sudo spctl --master-disable`, you will be required to enter your password.  This will allow any program to be be downloaded from anywhere and executed.
- On initial run, Autopsy shows a window that can appear behind the splash screen.  This looks like Autopsy has stalled during startup.  The easiest way to get around this issue for the first run is to run autopsy with the `--nosplash` flag, which will hide the splash screen on startup.  There will be a lag where no window appears for a bit, so please be patient.
- If a script fails to run due to operation not permitted or something along those lines, you may need to run `chmod u+x <path to script>` from the command line to allow the script to run.
- If you encounter an error like: `getcwd: cannot access parent directories: Operation not permitted` on Mac, you can do the following:
  1. Select System Preferences -> Security & Privacy -> Full Disk Access
  2. Click the lock to make changes
  3. Click '+'
  4. Press 'cmd' + 'shift' + '.' to show hidden files
  5. Select `/bin/sh`
  *Source: [Symscape](https://www.symscape.com/node/1727)*

# Known Issues
- Not all current features in Autopsy are functional in a Linux and Mac environment including but not limited to:
  - Recent Activity
  - The LEAPP processors
  - HEIF processing
  - Video thumbnails
  - VHD and VMDK files not supported on OS X
