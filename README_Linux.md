# Linux Installation

Most of the Autopsy development occurs to be run on Windows systems, but it is possible to run Autopsy on Linux and OS X. This file contains the instructions for building and running Autopsy on [Ubuntu 18.10 (Cosmic Cuttlefish)](https://wiki.ubuntu.com/CosmicCuttlefish/ReleaseNotes). The same instructions with minor modifications, should probably work on [Ubuntu 18.04.2 LTS (Bionic Beaver)](https://wiki.ubuntu.com/BionicBeaver/ReleaseNotes), but this has not yet been tested.

# Prerequisites

It appears that Autopsy [relies on JavaFX](https://www.batland.de/v4.1/index.php/forensics1/item/how-to-run-autopsy-on-linux), and therefore will not successfully run when compiled against OpenJDK. Unfortunately, as of 16 April, 2019, [Oracle has changed Java SE Licensing](https://www.oracle.com/technetwork/java/javase/overview/oracle-jdk-faqs.html). This has resulted in the popular ["WebUpd8" team PPA](https://launchpad.net/~webupd8team/+archive/ubuntu/java) being discontinued due to licensing concerns.

Fortunately, there appears to be a functional alternative, which is not encumbered by the same issues, and is theoretically binary-compatible with Oracle's Java 8 SE implementation: [Amazon Corretto 8](https://github.com/corretto/corretto-8). The [official installation documentation](https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/generic-linux-install.html) includes instructions for both `.rpm` and `.deb` package formats, which Amazon provides.

The below instructions seem to create a working Autopsy 4.10.0 build on Ubuntu. However, some of the packages installed are probably not-necessary. Further testing can help eliminate unnecessary prerequisites.

## Install prerequisites

First install the necessary packages from the Ubuntu repositories:

```console
$ sudo apt-get update && sudo apt-get install \
    ant \
    ca-certificates \
    g++ \
    gcc \
    gpg \
    java-common \
    libafflib-dev \
    libboost-dev \
    libewf-dev \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    libsolr-java \
    libsqlite3-dev \
    libswt-gtk-4-java \
    libtika-java \
    libtool \
    libtsk-dev \
    libvhdi-dev \
    libvmdk-dev \
    make \
    openjfx \
    postgresql \
    software-properties-common \
    sqlite3 \
    testdisk \
    wget \
    zip \
    zlib1g-dev
```

Next, download and install [Amazon Corretto 8](https://aws.amazon.com/corretto/):

```console
$ wget "https://d3pxv6yz143wms.cloudfront.net/8.212.04.1/java-1.8.0-amazon-corretto-jdk_8.212.04-1_amd64.deb" \
    && sudo apt-get install -y ./java-1.8.0-amazon-corretto-jdk_8.212.04-1_amd64.deb \
    && sudo apt-get install --fix-missing  # This may not be necesary
```

# Building

## Building The Sleuth Kit


1. Download [The Sleuth Kit](https://github.com/sleuthkit/sleuthkit/releases/tag/sleuthkit-4.6.5) and associated signature.

    ```console
    $ wget "https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.6.5/sleuthkit-4.6.5.tar.gz" \
        && wget "https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.6.5/sleuthkit-4.6.5.tar.gz.asc"
    ```

2. Fetch the GPG key and verify and unpack the tarball:

    ```console
    $ gpg --recv-keys "0917A7EE58A9308B13D3963338AD602EC7454C8B" \
        && gpg --verify sleuthkit-4.6.5.tar.gz.asc \
        && tar -xf sleuthkit-4.6.5.tar.gz
    ```

3. Build and install The Sleuth Kit:

    ```console
    $ cd sleuthkit-4.6.5 \
        && export JAVA_HOME="/usr/lib/jvm/java-1.8.0-amazon-corretto/" \
        && ./configure \
        && make \
        && sudo make install
    ```

## Install Autopsy

1. Download [Autopsy](https://github.com/sleuthkit/autopsy/releases/tag/autopsy-4.10.0) and signature:

```console
$ wget "https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.10.0/autopsy-4.10.0.zip" \
    && wget "https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.10.0/autopsy-4.10.0.zip.asc"
```

2. Verify and unzip the archive:

    ```console
    $ gpg --verify autopsy-4.10.0.zip.asc \
    && unzip autopsy-4.10.0.zip
    ```

3. Run the autopsy setup script:
    
    ```console
    $ sudo cp -r autopsy-4.10.0 /opt/autopsy-4.10.0 \
        && sudo chown -R "${user}:${group}" /opt/autopsy-4.10.0 \
        && cd /opt/autopsy-4.10.0 \
        && chmod +x ./unix_setup.sh \
        && export JAVA_HOME="/usr/lib/jvm/java-1.8.0-amazon-corretto/" \
        && ./unix_setup.sh \
        && apt-get install -y --fix-broken \
        && chmod +x /opt/autopsy-4.10.0/bin/autopsy
    ```

4. Add `autopsy` to your `${USER}`'s `${PATH}`:

    ```console
    $ echo 'PATH="/opt/autopsy-4.10.0/bin:${PATH}"' >> "${HOME}/.bashrc"
    ```

## TODO

* Prune dependencies
* Add instructions to build Autopsy from source
