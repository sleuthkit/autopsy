# OS detection
if [ "$(uname)" == "Darwin" ]; then
	OS="osx"
elif [ "$(uname)" == "Linux" ]; then
	OS="linux"
fi

# Installing dependencies for Autopsy
echo "Installing dependencies for ${OS}"
if test ${OS} = "linux" ; then
	sudo apt-get -qq update
	sudo apt-get -y install libafflib-dev libewf-dev libpq-dev autopoint libsqlite3-dev libcppunit-dev wget openjdk-8-jdk openjfx=8u161-b12-1ubuntu2 libopenjfx-java=8u161-b12-1ubuntu2 libopenjfx-jni=8u161-b12-1ubuntu2 ant automake libtool testdisk
elif test ${OS} = "osx" ; then
	brew install ant automake libtool postgresql testdisk libewf gettext cppunit afflib
fi

# Cloning sleuthkit
if [ ! -d "${TSK_HOME}" ] ; then
    echo "Cloning sleuthkit to ${TSK_HOME}"
    git clone --depth 1 https://github.com/sleuthkit/sleuthkit.git ${TSK_HOME}
fi

# Sleuthkit
echo "Setting up sleuthkit"
cd ${TSK_HOME}
export TRAVIS_OS_NAME=$OS
./travis_install_libs.sh

./bootstrap && ./configure --prefix=/usr/local && make
cd bindings/java/ && ant -q dist-PostgreSQL