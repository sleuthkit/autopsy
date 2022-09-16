#!/bin/bash
echo "Installing dependencies..."
# dependencies taken from: https://github.com/sleuthkit/autopsy/pull/5111/files
# brew install gettext cppunit && \
brew install ant automake libtool afflib libewf postgresql testdisk
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install necessary dependencies" >> /dev/stderr
    exit 1
fi

# brew gstreamer packages don't seem to play nice with autopsy.  Installing directly from gstreamer
echo "Installing gstreamer..."
gstreamer_tmp_path=$TMPDIR/gstreamer-1.0-1.20.3-universal.pkg
curl -k -o $gstreamer_tmp_path 'https://gstreamer.freedesktop.org/data/pkg/osx/1.20.3/gstreamer-1.0-1.20.3-universal.pkg' && \
sudo installer -pkg //Users/4911_admin/Downloads/gstreamer-1.0-1.20.3-universal.pkg -target / 
gstreamer_install_result=$?
rm $gstreamer_tmp_path
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install gstreamer" >> /dev/stderr
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

java_path=$(/usr/libexec/java_home -v 1.8)
echo "Java 1.8 path: $java_path"