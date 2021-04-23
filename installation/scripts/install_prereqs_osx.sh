#!/bin/bash
echo "Installing dependencies..."
# dependencies taken from: https://github.com/sleuthkit/autopsy/pull/5111/files
# brew install postgresql gettext cppunit && \
brew install ant automake libtool afflib libewf libpq testdisk imagemagick gstreamer gst-plugins-base gst-plugins-good
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install necessary dependencies" >> /dev/stderr
    exit 1
fi

echo "Installing liberica java 8..."
brew tap bell-sw/liberica && \
brew install --cask liberica-jdk8-full
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install liberica java" >> /dev/stderr
    exit 1
fi

# put this in bash_profile
# https://stackoverflow.com/questions/22502759/mac-os-x-10-9-setting-permanent-environment-variables
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8) && \
echo "Java home is now: $JAVA_HOME" && \
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)' | tee ~/.bash_profile ~/.bashrc
if [[ $? -ne 0 ]] 
then
    echo "Unable to properly set up JAVA_HOME." >> /dev/stderr
    exit 1
fi

OPEN_JDK_LN=/usr/local/opt/openjdk && \
rm $ && \
ln -s $JAVA_HOME $OPEN_JDK_LN
if [[ $? -ne 0 ]] 
then
    echo "Unable to properly set up $OPEN_JDK_LN." >> /dev/stderr
    exit 1
fi

# Test your link file creation to ensure it is pointing at the correct java developement kit:
echo "/usr/local/opt/openjdk now is:"
ls -l /usr/local/opt/openjdk 

# check version
echo "Java Version is:"
java -version