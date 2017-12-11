Most of the Autopsy development occurs to be run on Windows systems, but it is 
possible to run Autopsy on Linux and OS X.  This file contains the instructions 
for building Autopsy on Linux and getting it working.

This guide assumes Ubuntu 16.04 LTS for linux building instructions.


# Prerequisites

### Dependencies

```bash
# JAVA
add-apt-repository "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main"
sudo apt-get update
sudo apt-get install oracle-java8-installer oracle-java8-set-default

# DEFAULT TOOLS
sudo apt-get install software-properties-common automake autotools-dev \
pkg-config autopoint libtoolwget xauth git git-svn build-essential libssl-dev \
libbz2-dev libz-dev ant automake autoconf libtool vim python-dev gstreamer1.0 \
byacc flex autoconf automake autopoint libtool pkg-config sharutils \
libopencv-dev python-opencv

# TOMCAT SOLR
sudo apt-get install solr-tomcat
```

### Setting up the environment

Place the following in ~/.bashrc or similar (e.g. ~/.zshrc)
```bash
export JAVA_HOME=/usr/lib/jvm/java-8-oracle
export CLASSPATH=$JAVA_HOME/lib/:$CLASSPATH
export PATH=$JAVA_HOME/bin/:$PATH
export JDK_HOME="/usr/lib/jvm/java-8-oracle/"
export JRE_HOME="/usr/lib/jvm/java-8-oracle/jre/"
export TSK_HOME="/opt/tsk"
```

### Set up TSK_HOME
TSK_HOME is the path where we will store 'The Slueth Kit' bindings for java and
python. It is not necessary for running Autopsy but it is necessary for building.
```bash
source ~/.bashrc
sudo mkdir -p ${TSK_HOME}
sudo chmod 0777 ${TSK_HOME}
mkdir -p ${TSK_HOME}/bindings/java/dist
mkdir -p ${TSK_HOME}/bindings/java/lib
```

## Libraries (Building from source)
Your linux distribution may have packages for these libraries and so you may not
need to build and install from source. However, following this procedure will 
ensure that you have the latest version of the libraries required.

### Set up a development folder
You may choose any location you wish, this is merely an example:
```bash
mkdir ~/autopsy-build
cd ~/autopsy-build
export WORKDIR=`pwd`
```


### libewf
```bash
git clone https://github.com/libyal/libewf.git
cd libewf
export LIBEWF_SRC=`pwd`
./synclibs.sh
./autogen.sh
./configure --enable-python --prefix=${TSK_HOME}
make 
make install
cd ${WORKDIR}
```

### The Slueth Kit (TSK)
```bash
git clone https://github.com/sleuthkit/sleuthkit.git
cd sleuthkit
export TSK_SRC=`pwd`
./bootstrap
./configure --prefix=${TSK_HOME} --with-libewf=${LIBEWF_SRC}
sed -i '67s/libewf_handle_read_random/libewf_handle_read_buffer_at_offset/' ${TSK_SRC}/tsk/img/ewf.c
make
make install
cd ./tsk/bindings/java
make
cd ${TSK_SRC}
cp ./bindings/java/dist/Tsk_DataModel.jar ${TSK_HOME}/bindings/java/dist/Tsk_DataModel_PostgreSQL.jar
cp ./bindings/java/lib/mchange-commons-java-0.2.9.jar ${TSK_HOME}/bindings/java/lib/
cd ${WORKDIR}
```

### Java & Python bindings
```bash
mkdir -p ${TSK_HOME}/tsk/bindings/java/lib
cd ${TSK_HOME}/tsk/bindings/java/lib
wget -c https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.8.11/sqlite-jdbc-3.8.11.jar
wget -c http://central.maven.org/maven2/org/postgresql/postgresql/9.4.1211.jre7/postgresql-9.4.1211.jre7.jar 
wget -c http://central.maven.org/maven2/com/mchange/c3p0/0.9.5/c3p0-0.9.5.jar
```

# Building Autopsy

#### Get a local copy of autopsy (if you haven't already)
```bash
cd ${WORKDIR}
git clone git@github.com:seannicholls/autopsy.git
git pull 4.5.1
```

#### Build Autopsy
At this point you should be able to use Netbeans IDE to build the project, or
alternatively, you can build directly from the commandline using ant:

##### Bug-fix for Ubuntu Java tmp folder writing permissions
```bash
mkdir -p ./tmp
export _JAVA_OPTIONS=-Djava.io.tmpdir=`pwd`/tmp
```

##### Build Autopsy
```bash
ant clean
ant build
```

##### run Autopsy
```bash
ant run
```
