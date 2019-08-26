# Linux/OSX Quick Developer Setup

**Requirements / assumptions**:
* git installed on the development machine (can be obtained via apt-get, brew, etc)
* All commands are run from the root of the autopsy repo
* autopsy repo forked and cloned

`git clone git@github.com:[YOUR_GIT_USERNAME]/autopsy.git`
* JAVA_HOME environment variable - set this to the location of a JDK 8 installation that includes JavaFX

`export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/`
* TSK_HOME environment variable - set this to any directory; this will become the location of the sleuthkit repo

`export TSK_HOME=/home/[user]/sleuthkit`

**Setup instructions**

1. Run the setup script:
`./easy_setup_linux_osx.sh`

2. Build autopsy:
`ant`

3. Run autopsy:
`ant run`
