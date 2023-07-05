#!/bin/bash
echo "Installing dependencies..."
brew install openjdk@17 ant automake libtool afflib libewf postgresql@15 testdisk libheif gstreamer 
    
if [[ $? -ne 0 ]] 
then 
    echo "Unable to install necessary dependencies" >> /dev/stderr
    exit 1
fi

sudo ln -sfn $HOMEBREW_PREFIX/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk && \
echo "export PATH=\"$HOMEBREW_PREFIX/opt/openjdk@17/bin:$PATH\"" >> ~/.zshrc && \
source ~/.zshrc

if [[ $? -ne 0 ]] 
then 
    echo "Unable to properly set up java env" >> /dev/stderr
    exit 1
fi

java_path=$(/usr/libexec/java_home -v 17)
echo "Java 17 path: $java_path"