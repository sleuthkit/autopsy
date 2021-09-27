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
# other possible dependencies taken from https://github.com/sleuthkit/autopsy/pull/4743/files
# sudo apt -y install ca-certificates g++ gcc gpg java-common libafflib-dev libboost-dev libgl1-mesa-dri libgl1-mesa-glx libsolr-java libsqlite3-dev libswt-gtk-4-java libtika-java openjfx postgresql software-properties-common sqlite3 wget zip zlib1-dev

# other possible dependencies taken from https://github.com/sleuthkit/autopsy/pull/5111/files
# sudo apt -y install autopoint libsqlite3-dev libcppunit-dev

# TODO multi user dependencies?
sudo apt update &&
    sudo apt -y build-dep imagemagick libmagickcore-dev &&
    sudo apt -y install build-essential autoconf libtool git-core automake git zip wget ant \
        libde265-dev libheif-dev \
        libpq-dev \
        testdisk libafflib-dev libewf-dev libvhdi-dev libvmdk-dev \
        libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
        gstreamer1.0-plugins-ugly gstreamer1.0-libav gstreamer1.0-doc gstreamer1.0-tools gstreamer1.0-x \
        gstreamer1.0-alsa gstreamer1.0-gl gstreamer1.0-gtk3 gstreamer1.0-qt5 gstreamer1.0-pulseaudio

if [[ $? -ne 0 ]]; then
    echo "Failed to install necessary dependencies" >>/dev/stderr
    exit 1
fi

# echo "Cloning source for libde265 and libheif..."
# pushd /usr/src/ && \
# sudo git clone https://github.com/strukturag/libde265.git && \
# sudo git clone https://github.com/strukturag/libheif.git && \
# popd
# if [[ $? -ne 0 ]]
# then
#     popd
#     echo "Failed to retrieve libde265 and libheif repos" >> /dev/stderr
#     exit 1
# fi

# echo "Installing libde265..."
# pushd /usr/src/libde265/ && \
# sudo ./autogen.sh && \
# sudo ./configure && \
# sudo make && \
# sudo make install && \
# popd
# if [[ $? -ne 0 ]]
# then
#     popd
#     echo "Failed to install libde265" >> /dev/stderr
#     exit 1
# fi

# echo "Installing libheif..."
# pushd /usr/src/libheif/ && \
# sudo ./autogen.sh && \
# sudo ./configure && \
# sudo make && \
# sudo make install && \
# popd
# if [[ $? -ne 0 ]]
# then
#     popd
#     echo "Failed to install libheif" >> /dev/stderr
#     exit 1
# fi

echo "Installing ImageMagick..."
pushd /usr/src/ &&
    sudo wget https://www.imagemagick.org/download/ImageMagick.tar.gz &&
    sudo tar xf ImageMagick.tar.gz &&
    pushd ImageMagick-7* &&
    sudo ./configure --with-heic=yes &&
    sudo make &&
    sudo make install &&
    popd &&
    popd
if [[ $? -ne 0 ]]; then
    popd && popd
    echo "Failed to install ImageMagick" >>/dev/stderr
    exit 1
fi

sudo ldconfig
if [[ $? -ne 0 ]]; then
    echo "ldconfig call failed" >>/dev/stderr
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

# https://unix.stackexchange.com/questions/117467/how-to-permanently-set-environmental-variables
# echo "Setting JAVA_HOME..."
# export JAVA_HOME=/usr/lib/jvm/bellsoft-java8-full-amd64 && \
# echo "Java home is now: $JAVA_HOME" && \
# echo 'export JAVA_HOME=/usr/lib/jvm/bellsoft-java8-full-amd64' | tee ~/.profile ~/.bashrc
# if [[ $? -ne 0 ]]
# then
#     echo "Failed to set up JAVA_HOME in bash_rc" >> /dev/stderr
#     exit 1
# fi

# echo "Java version is:"
# java -version
