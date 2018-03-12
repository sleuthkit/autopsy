#!/bin/bash

photorec_filepath=/usr/bin/photorec
if [ -f "$photorec_filepath"  ]; then
	echo "$photorec_filepath found"
else
	echo "Photorec not found, please install testdisk for the photorec carver functionality"
	echo "run the command: sudo apt-get install testdisk"
	exit 1
fi

if [ -n "$JAVA_HOME" ];  then
	if [ -x "$JAVA_HOME/bin/java" ]; then
		echo "found java executable in $JAVA_HOME"
	else
		echo "no executable found in $JAVA_HOME"
		exit 1
	fi
else
    	echo "Set JAVA_HOME env variable"
    	exit 1	
fi

TSK_VERSION=4.6.0
sleuthkit_jar_filepath=/usr/share/java/sleuthkit-$TSK_VERSION.jar;
ext_jar_filepath=$PWD/autopsy/modules/ext/sleuthkit-postgresql-$TSK_VERSION.jar;
if [ -f "$sleuthkit_jar_filepath" ]; then
	echo "$sleuthkit_jar_filepath found"
	echo "copying $sleuthkit_jar_filepath to the autopsy directory"
    	echo "deleting $ext_jar_filepath"
    	rm $ext_jar_filepath;
    	if [ "$?" -gt 0 ]; then  #checking if remove operation failed
        	echo "exiting .."
        	exit 1
    	else
        	echo "Successfully removed $ext_jar_filepath"
        	cp $sleuthkit_jar_filepath $ext_jar_filepath
        	if [ "$?" -eq 0 ]; then # checking copy operation was successful
            		echo "Successfully copied $sleuthkit_jar_filepath"
        	else
        		echo "exiting..."
        		exit 1
        	fi
    	fi
else
	echo "$sleuthkit_jar_filepath not found, please install the sleuthkit-java.deb file"
	exit 1
fi

echo "Autopsy is now configured. You can execute bin/autopsy to start it"
