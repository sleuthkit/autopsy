#!/bin/bash
# this script is designed to install necessary dependencies on debian
# this script requires elevated privileges

echo "Turning on all repositories for apt..."
sudo sed -Ei 's/^# deb-src /deb-src /' /etc/apt/sources.list
if [[ $? -ne 0 ]]; then
    echo "Failed to turn on all repositories" >>/dev/stderr
    exit 1
fi

echo "Installing all apt dependencies..."
sudo apt update && \
    sudo apt -y install build-essential autoconf libtool automake git zip wget ant \
        libde265-dev libheif-dev \
        libpq-dev \
        testdisk libafflib-dev libewf-dev libvhdi-dev libvmdk-dev \
        libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
        gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-tools gstreamer1.0-x \
        gstreamer1.0-alsa gstreamer1.0-gl gstreamer1.0-gtk3 gstreamer1.0-qt5 gstreamer1.0-pulseaudio

if [[ $? -ne 0 ]]; then
    echo "Failed to install necessary dependencies" >>/dev/stderr
    exit 1
fi

echo "Installing bellsoft Java 8..."
pushd /usr/src/ &&
    wget -q -O - https://download.bell-sw.com/pki/GPG-KEY-bellsoft | sudo apt-key add - &&
    echo "deb [arch=amd64] https://apt.bell-sw.com/ stable main" | sudo tee /etc/apt/sources.list.d/bellsoft.list &&
    sudo apt update &&
    sudo apt -y install bellsoft-java8-full &&
    popd
if [[ $? -ne 0 ]]; then
    echo "Failed to install bellsoft java 8" >>/dev/stderr
    exit 1
fi

echo "Autopsy prerequisites installed."
echo "Java path at /usr/lib/jvm/bellsoft-java8-full-amd64: "
ls /usr/lib/jvm/bellsoft-java8-full-amd64