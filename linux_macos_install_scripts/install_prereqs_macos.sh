#!/bin/bash
echo "Installing dependencies..."
brew install openjdk@17 ant automake libtool afflib libewf postgresql@15 testdisk libheif gstreamer 
    
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install necessary dependencies" >> /dev/stderr
    exit 1
fi

java_path=$(/usr/libexec/java_home -v 17)
echo "Java 17 path: $java_path"