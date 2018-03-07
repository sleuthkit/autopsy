#!/bin/bash

photorec_filepath=/usr/bin/photorec;
if [ -f "$photorec_filepath"  ]; then
	echo "photorec found"
else
	echo "Photorec not found, please install testdisk for the photorec carver functionality"
	echo "run the command: sudo apt-get install testdisk"
fi

sleuthkit_jar_filepath=/usr/share/java/sleuthkit-4.6.0.jar;
ext_jar_filepath=./autopsy/modules/ext/sleuthkit-postgresql-4.6.0.jar;
if [ -f "$sleuthkit_jar_filepath"  ]; then
	echo "sleuthkit jarfile found";
	echo "copying sleuthkit-jar file to the autopsy directory";
	rm ./autopsy/modules/ext/sleuthkit-postgresql-4.6.0.jar
	if [ ! -f "$ext_jar_filepath" ]; then
		cp $sleuthkit_jar_filepath ./autopsy/modules/ext/sleuthkit-postgresql-4.6.0.jar;
		echo "Successfully copied sleuthkit-jar file";
		echo "run autopsy";
	fi
else
	echo "Sleuthkit-jar not found, please install the sleuthkit-java.deb file"
	echo "run the command: sudo apt install ./sleuthkit-java_4.6.0-1_amd64.deb inside the debian file directory"
fi


