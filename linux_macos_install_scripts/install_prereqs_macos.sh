#!/bin/bash
echo "Installing dependencies..."
brew install ant automake libtool afflib libewf postgresql testdisk libheif \
    gst-libav gst-plugins-bad gst-plugins-base gst-plugins-good gst-plugins-ugly gstreamer
    
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install necessary dependencies" >> /dev/stderr
    exit 1
fi

echo "Installing liberica java 17..."
brew tap bell-sw/liberica && \
brew install --cask liberica-jdk17-full
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install liberica java" >> /dev/stderr
    exit 1
fi

java_path=$(/usr/libexec/java_home -v 17)
echo "Java 17 path: $java_path"