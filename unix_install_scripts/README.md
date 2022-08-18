# Overview
*The installation process requires some [prerequisites](#installing-prerequisites), [Sleuthkit](#install-sleuthkit), and installing [Autopsy itself](#install-autopsy).  If using Windows, there is a pre-built installer bundling all dependencies that can be found in the [Autopsy downloads section](https://www.autopsy.com/download/) or in the [Releases section on GitHub](https://github.com/sleuthkit/autopsy/releases/).*

# Installing Prerequisites

## On OS X

*A script to install these dependencies that can be found [here](install_prereqs_osx.sh).*
- Using [Homebrew](https://brew.sh/), install dependencies that have formulas:
  ```
  brew install ant automake libtool afflib libewf libpq testdisk imagemagick gstreamer gst-plugins-base gst-plugins-good imagemagick
  ```
- You will also need to install Java 8 and JavaFX to run autopsy.  We recommend Liberica OpenJDK which can be installed by tapping this third-party dependency:
  ```
  brew tap bell-sw/liberica
  ```
- Then, you can install this dependency using `brew`:
  ```
  brew install --cask liberica-jdk8-full
  ```
- You will need the java path for properly setting up autopsy.  You can get the path to java by calling:
  ```
  /usr/libexec/java_home -v 1.8
  ```
  
## On Linux (Ubuntu / Debian-based)

*A script to install these dependencies that can be found [here](install_prereqs_ubuntu.sh).*
- You will need to include some repositories in order to install this software.  One way to do that is to uncomment lines in your `sources.list`:
  ```
  sudo sed -Ei 's/^# deb-src /deb-src /' /etc/apt/sources.list
  ```
- Use `apt` to install dependencies:
  ```
  sudo apt update && \
    sudo apt -y install build-essential autoconf libtool git-core automake git zip wget ant \
      libde265-dev libheif-dev \
      libpq-dev \
      testdisk libafflib-dev libewf-dev libvhdi-dev libvmdk-dev \
      libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
      gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-tools gstreamer1.0-x \
      gstreamer1.0-alsa gstreamer1.0-gl gstreamer1.0-gtk3 gstreamer1.0-qt5 gstreamer1.0-pulseaudio
  ```
- You will also need to install Java 8 and JavaFX to run autopsy.  We recommend Liberica OpenJDK which can be installed as follows:
  ```
  pushd /usr/src/ && \
  wget -q -O - https://download.bell-sw.com/pki/GPG-KEY-bellsoft | sudo apt-key add - && \
  echo "deb [arch=amd64] https://apt.bell-sw.com/ stable main" | sudo tee /etc/apt/sources.list.d/bellsoft.list && \
  sudo apt update && \
  sudo apt -y install bellsoft-java8-full && \
  popd
  ```
- Take note of the location of the java 1.8 install.  This will be necessary to properly setup Autopsy.  If using the recommended method, the path should be `/usr/lib/jvm/bellsoft-java8-full-amd64`

# Install Sleuthkit

## Install Sleuthkit from Source
*A script to install these dependencies on Unix-like systems (i.e. OS X, Linux) that can be found [here](install_tsk_from_src.sh).*
- Please ensure you have all the prerequisites installed on your system (see the directions [here](#installing-prerequisites)).
- If you don't have a copy of the repository on your local machine, clone it (this requires git):
  ```
  git clone https://github.com/sleuthkit/sleuthkit.git
  ```
- If you want to build source from a particular branch or tag (i.e. `develop` or `release-4.11.0`), check out that branch:
  ```
  git checkout <YOUR BRANCH HERE> && git pull
  ```
- Then, with the sleuthkit repo as your working directory, you can build with:
  ```
  ./bootstrap && ./configure && make
  ```
- If the output from `make` looks good, then install:
  ```
  sudo make install
  ```

# Install Autopsy

## Create Autopsy Zip File from Source
*In most instances, you should download the Autopsy Zip file from the [Autopsy downloads section](https://www.autopsy.com/download/) or in the [Releases section on GitHub](https://github.com/sleuthkit/autopsy/releases/), but if you have a special use case you can do the following.  Please make sure you have the [prerequisites installed](#installing-prerequisites) and have [installed Sleuthkit](#install-sleuthkit).* 
- If you haven't already, clone the repo:
  ```
  git clone https://github.com/sleuthkit/autopsy.git
  ```
- With the autopsy repo as your working directory, you can run:
  ```
  ant clean && ant build && ant build-zip
  ```
- The zip file should be created within the `dist` folder of the Autopsy repository and will have the version in the name (i.e. `autopsy-4.18.0.zip`).

## Install Autopsy from Zip File
*These instructions are for Unix-like systems like OS X and Linux.  If you are on Windows, there is an installer that can be downloaded from the [Autopsy downloads section](https://www.autopsy.com/download/) or in the [Releases section on GitHub](https://github.com/sleuthkit/autopsy/releases/). Please make sure you have the [prerequisites installed](#installing-prerequisites) and have [installed Sleuthkit](#install-sleuthkit). A script to perform these steps can be found [here](install_autopsy_from_zip.sh).*

- Download the zip file from the [Autopsy downloads section](https://www.autopsy.com/download/) or in the [Releases section on GitHub](https://github.com/sleuthkit/autopsy/releases/).  You can also create a zip file from source using [these directions](#create-autopsy-zip-file-from-source).
- If you downloaded the zip file, you can verify the zip file with the [Sleuthkit key](https://sleuthkit.org/carrier.asc) and the related `.asc` file found in the [Releases section on GitHub](https://github.com/sleuthkit/autopsy/releases/).  For instance, you would use `autopsy-4.18.0.zip.asc` with `autopsy-4.18.0.zip`.  Here is an example where `$ASC_FILE` is the path to the `.asc` file and `$AUTOPSY_ZIP_PATH` is the path to the autopsy zip file:
  ```
  mkdir -p ${VERIFY_DIR} && \
  pushd ${VERIFY_DIR} && \
  wget https://sleuthkit.org/carrier.asc && \
  gpg --homedir "${VERIFY_DIR}" --import https://sleuthkit.org/carrier.asc && \
  gpg --homedir "${VERIFY_DIR}" --keyring "${VERIFY_DIR}/pubring.kbx" ${ASC_FILE} ${AUTOPSY_ZIP_PATH} && \
  rm -r ${VERIFY_DIR}
  popd
  ```
- Extract the zip file to a location where you would like to have Autopsy installed.
- Set up java path.  There are two ways to provide the path to java: `JAVA_HOME` can be set as an environmental variable or the `autopsy.conf` file can define the home for java.  
  - To update the `autopsy.conf` file, navigate to where autopsy has been extracted and then open `etc/autopsy.conf`.  Within that file, replace the commented line or add a new line specifying the java home like: `jdkhome=<JAVA_PATH>`.  Another option is to provide an argument to `unix_setup.sh` like the following `unix_setup.sh -j <JAVA_PATH>` when performing the next step.
- With the extracted folder as the working directory, you can run the following commands to perform setup:
  ```
  chown -R $(whoami) . && \
  chmod u+x ./unix_setup.sh && \
  ./unix_setup.sh
  ```
- At this point, you should be able to run Autopsy with the command `./autopsy` from within the `bin` directory of the extracted folder.

## Setup Homebrew JNA paths
A few features in Autopsy will only work (i.e. gstreamer) will only work if the JNA paths are specified.  If you installed the necessary dependencies through Homebrew, you will want to either run this [script](add_homebrew_jna.sh) or manually add all the gstreamer lib and dependency lib paths to the env variable `jre_flags`.

# Caveats
- Not all current features in Autopsy are functional in a Linux and Mac environment including but not limited to:
  - Recent Activity
  - The LEAPP processors
  - HEIF processing

# Known Issues
- On initial run, Autopsy shows a window that can appear behind the splash screen.  This looks like Autopsy has stalled during startup.  The easiest way to get around this issue for the first run is to run autopsy with the `--nosplash` flag, which will hide the splash screen on startup.  There will be a lag where no window appears for a bit, so please be patient.
- If a script fails to run due to operation not permitted or something along those lines, you may need to run `chmod u+x <path to script>` from the command line to allow the script to run.
- If you encounter an error like: `getcwd: cannot access parent directories: Operation not permitted` on Mac, you can do the following:
  1. Select System Preferences -> Security & Privacy -> Full Disk Access
  2. Click the lock to make changes
  3. Click '+'
  4. Press 'cmd' + 'shift' + '.' to show hidden files
  5. Select `/bin/sh`
  *Source: [Symscape](https://www.symscape.com/node/1727)*