#!/bin/bash
#
# Verifies programs are installed and copies native code into the Application folder structure
#

# NOTE: update_sleuthkit_version.pl updates this value and relies
# on it keeping the same name and whitespace.  Don't change it.
TSK_VERSION=4.12.0


usage() { 
    echo "Usage: unix_setup.sh [-j java_home] [-n application_name]" 1>&2;
}

APPLICATION_NAME="autopsy";

while getopts "j:n:" o; do
    case "${o}" in
        n)
            APPLICATION_NAME=${OPTARG}
            ;;
        j)
            JAVA_PATH=${OPTARG}
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done


# In the beginning...
echo "---------------------------------------------"
echo "Checking prerequisites and preparing ${APPLICATION_NAME}:"
echo "---------------------------------------------"

# make sure cwd is same as script's
SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
pushd $SCRIPTPATH

# Verify PhotoRec was installed
echo -n "Checking for PhotoRec..."
photorec_filepath=/usr/bin/photorec
photorec_osx_filepath=/usr/local/bin/photorec
if [ -f "$photorec_filepath" ]; then
    echo "found in $(dirname $photorec_filepath)"
elif [ -f "$photorec_osx_filepath" ]; then
    echo "found in $(dirname $photorec_osx_filepath)"
else
    echo "ERROR: PhotoRec not found, please install the testdisk package."
    exit 1
fi

# Verify Java was installed and configured
echo -n "Checking for Java..."
if [ -n "$JAVA_PATH" ]; then 
    if [ -x "$JAVA_PATH/bin/java" ]; then
        # only works on linux; not os x
        awk '!/^\s*#?\s*jdkhome=.*$/' etc/$APPLICATION_NAME.conf > etc/$APPLICATION_NAME.conf.tmp && \
        mv etc/$APPLICATION_NAME.conf.tmp etc/$APPLICATION_NAME.conf && \
        echo "jdkhome=$JAVA_PATH" >> etc/$APPLICATION_NAME.conf
    else
        echo "ERROR: Java was not found in $JAVA_PATH."
        exit 1
    fi
elif [ -n "$JAVA_HOME" ];  then
    if [ -x "$JAVA_HOME/bin/java" ]; then
	    echo "found in $JAVA_HOME"
    else
        echo "ERROR: Java was not found in $JAVA_HOME."
        exit 1
    fi
else
    echo "ERROR: JAVA_HOME environment variable must be defined."
    exit 1
fi

# Verify Sleuth Kit Java was installed
echo -n "Checking for Sleuth Kit Java bindings..."
if [ -f "/usr/share/java/sleuthkit-$TSK_VERSION.jar" ]; then
    sleuthkit_jar_filepath=/usr/share/java/sleuthkit-$TSK_VERSION.jar
    echo "found in $(dirname $sleuthkit_jar_filepath)"
elif [ -f "/usr/local/share/java/sleuthkit-$TSK_VERSION.jar" ]; then
    sleuthkit_jar_filepath=/usr/local/share/java/sleuthkit-$TSK_VERSION.jar
    echo "found in $(dirname $sleuthkit_jar_filepath)"
else
    echo "ERROR: sleuthkit-$TSK_VERSION.jar not found in /usr/share/java/ or /usr/local/share/java/."
    echo "Please install the Sleuth Kit Java bindings file."
    echo "See https://github.com/sleuthkit/sleuthkit/releases."
    exit 1
fi

ext_jar_filepath=$PWD/autopsy/modules/ext/sleuthkit-$TSK_VERSION.jar;
echo -n "Copying sleuthkit-$TSK_VERSION.jar into the $APPLICATION_NAME directory..."
rm -f "$ext_jar_filepath";
if [ "$?" -gt 0 ]; then  #checking if remove operation failed
    echo "ERROR: Deleting $ext_jar_filepath failed."
    echo "Please check your permissions."
    exit 1
else
    cp $sleuthkit_jar_filepath "$ext_jar_filepath"
    if [ "$?" -ne 0 ]; then # checking copy operation was successful
        echo "ERROR: Copying $sleuthkit_jar_filepath to $ext_jar_filepath failed."
	echo "Please check your permissions."
        exit 1
    fi
    echo "done"
fi

# make sure thirdparty files are executable
chmod u+x autopsy/markmckinnon/Export*
chmod u+x autopsy/markmckinnon/parse*

# allow solr dependencies to execute
chmod -R u+x autopsy/solr/bin

# make sure it is executable
chmod u+x bin/$APPLICATION_NAME

popd

echo
echo "Application is now configured. You can execute bin/$APPLICATION_NAME to start it"
echo