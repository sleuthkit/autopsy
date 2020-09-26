#!/bin/bash
#
# Verifies programs are installed and copies native code into the Autopsy folder structure
#

# NOTE: update_sleuthkit_version.pl updates this value and relies
# on it keeping the same name and whitespace.  Don't change it.
TSK_VERSION=4.10.1


# In the beginning...
echo "---------------------------------------------"
echo "Checking prerequisites and preparing Autopsy:"
echo "---------------------------------------------"

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
if [ -n "$JAVA_HOME" ];  then
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
echo -n "Copying sleuthkit-$TSK_VERSION.jar into the Autopsy directory..."
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

# make sure it is executable
chmod u+x bin/autopsy

echo
echo "Autopsy is now configured. You can execute bin/autopsy to start it"
echo
