#!/bin/bash

# Verifies programs are installed and copies native code into the Autopsy folder structure

TSK_VERSION=4.6.0

# Verify PhotoRec was installed
photorec_filepath=/usr/bin/photorec
photorec_osx_filepath=/usr/local/bin/photorec
if [ -f "$photorec_filepath"  ] || [ -f "$photorec_osx_filepath" ]; then
	echo "photorec found"
else
	echo "ERROR: Photorec not found, please install the testdisk package"
	exit 1
fi

# Verify Java was installed and configured
if [ -n "$JAVA_HOME" ];  then
	if [ -x "$JAVA_HOME/bin/java" ]; then
		echo "Java found in $JAVA_HOME"
	else
		echo "ERROR: Java was not found in $JAVA_HOME"
		exit 1
	fi
else
    	echo "ERROR: JAVA_HOME environment variable must be defined"
    	exit 1	
fi

# Verify Sleuth Kit Java was installed


if [ -f "/usr/share/java/sleuthkit-$TSK_VERSION.jar" ]; then
    sleuthkit_jar_filepath=/usr/share/java/sleuthkit-$TSK_VERSION.jar
elif [ -f "/usr/local/share/java/sleuthkit-$TSK_VERSION.jar" ]; then
    sleuthkit_jar_filepath=/usr/local/share/java/sleuthkit-$TSK_VERSION.jar
else
    echo "sleuthkit.jar file not found"
    echo "exiting .."
    exit 1
fi

ext_jar_filepath=$PWD/autopsy/modules/ext/sleuthkit-postgresql-$TSK_VERSION.jar;
if [ -f "$sleuthkit_jar_filepath" ]; then
	echo "$sleuthkit_jar_filepath found"
	echo "Copying into the Autopsy directory"
    	rm $ext_jar_filepath;
    	if [ "$?" -gt 0 ]; then  #checking if remove operation failed
        	echo "exiting .."
        	exit 1
    	else
        	cp $sleuthkit_jar_filepath $ext_jar_filepath
        	if [ "$?" -ne 0 ]; then # checking copy operation was successful
        		echo "exiting..."
        		exit 1
        	fi
    	fi
else
	echo "ERROR: $sleuthkit_jar_filepath not found, please install the sleuthkit-java.deb file"
	exit 1
fi

# make sure it is executable
chmod +x bin/autopsy

echo "Autopsy is now configured. You can execute bin/autopsy to start it"
