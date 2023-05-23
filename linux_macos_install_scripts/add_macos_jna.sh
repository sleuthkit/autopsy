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

GSTREAMER_LOC=$(brew --prefix gstreamer)
if [[ $? -ne 0 ]] 
then 
    echo "Unable to find homebrew installation of gstreamer" >> /dev/stderr
    exit 1
fi

 awk '!/^ *#? *export +?(jreflags|GST_PLUGIN_SYSTEM_PATH|GST_PLUGIN_SCANNER)=.*$/' $INSTALL_LOC/etc/$APPLICATION_NAME.conf > $INSTALL_LOC/etc/$APPLICATION_NAME.conf.tmp && \
mv $INSTALL_LOC/etc/$APPLICATION_NAME.conf.tmp $INSTALL_LOC/etc/$APPLICATION_NAME.conf && \
echo "
export jreflags=\"-Djna.library.path=\\\"/usr/local/lib\\\" \$jreflags\"
export GST_PLUGIN_SYSTEM_PATH=\"/usr/local/lib/gstreamer-1.0\"
export GST_PLUGIN_SCANNER=\"${GSTREAMER_LOC}/libexec/gstreamer-1.0/gst-plugin-scanner\"" >> $INSTALL_LOC/etc/$APPLICATION_NAME.conf

