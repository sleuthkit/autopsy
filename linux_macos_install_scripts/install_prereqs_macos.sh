#!/bin/bash
echo "Installing liberica java 17..."
brew tap bell-sw/liberica && \
brew install --cask liberica-jdk17-full
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install liberica java" >> /dev/stderr
    exit 1
fi

echo "Installing remaining dependencies..."
brew install ant automake libtool afflib libewf postgresql@15 testdisk libheif gstreamer
    
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install necessary dependencies" >> /dev/stderr
    exit 1
fi

java_path=$(/usr/libexec/java_home -v 17)
echo "Java 17 path: $java_path"