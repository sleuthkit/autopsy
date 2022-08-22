#!/bin/bash
# Updates jna paths for mac

usage() {
    echo "Usage: add_macos_jna.sh [-i base_install_location (i.e. /home/usr/autopsy)] [-a application_name (default: autopsy)]" 1>&2
}

APPLICATION_NAME="autopsy"

while getopts "a:i:" o; do
    case "${o}" in
    i) 
        INSTALL_LOC=${OPTARG}
        ;;
    a)
        APPLICATION_NAME=${OPTARG}
        ;;
    *)
        usage
        exit 1
        ;;
    esac
done

if [[ -z "$INSTALL_LOC" ]]
then
    usage
    exit 1
fi

awk '!/^\s*#?\s*export jreflags=.*$/' $INSTALL_LOC/etc/$APPLICATION_NAME.conf > $INSTALL_LOC/etc/$APPLICATION_NAME.conf.tmp && \
mv $INSTALL_LOC/etc/$APPLICATION_NAME.conf.tmp $INSTALL_LOC/etc/$APPLICATION_NAME.conf && \
echo -e "\nexport jreflags=-Djna.library.path=\"/Library/Frameworks/GStreamer.framework/Versions/1.0/lib\"" >> $INSTALL_LOC/etc/$APPLICATION_NAME.conf

