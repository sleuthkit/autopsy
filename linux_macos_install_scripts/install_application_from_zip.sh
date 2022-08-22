#!/bin/bash
# Unzips an application platform zip to specified directory and does setup

usage() {
    echo "Usage: install_application_from_zip.sh [-z zip_path] [-i install_directory] [-j java_home] [-n application_name] [-v asc_file]" 1>&2
    echo "If specifying a .asc verification file (with -v flag), the program will attempt to create a temp folder in the working directory and verify the signature with gpg." 1>&2
}

APPLICATION_NAME="autopsy";

while getopts "n:z:i:j:v:" o; do
    case "${o}" in
    n)
        APPLICATION_NAME=${OPTARG}
        ;;
    z)
        APPLICATION_ZIP_PATH=${OPTARG}
        ;;
    i)
        INSTALL_DIR=${OPTARG}
        ;;
    v)
        ASC_FILE=${OPTARG}
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

if [[ -z "$APPLICATION_ZIP_PATH" ]] || [[ -z "$INSTALL_DIR" ]]; then
    usage
    exit 1
fi

if [[ -n "$ASC_FILE" ]]; then
    VERIFY_DIR=$(pwd)/temp
    KEY_DIR=$VERIFY_DIR/private
    mkdir -p $VERIFY_DIR &&
        sudo wget -O $VERIFY_DIR/carrier.asc https://sleuthkit.org/carrier.asc &&
        mkdir -p $KEY_DIR &&
        sudo chmod 600 $KEY_DIR &&
        sudo gpg --homedir "$KEY_DIR" --import $VERIFY_DIR/carrier.asc &&
        sudo gpgv --homedir "$KEY_DIR" --keyring "$KEY_DIR/pubring.kbx" $ASC_FILE $APPLICATION_ZIP_PATH &&
        sudo rm -r $VERIFY_DIR
    if [[ $? -ne 0 ]]; then
        echo "Unable to successfully verify $APPLICATION_ZIP_PATH with $ASC_FILE" >>/dev/stderr
        exit 1
    fi
fi

ZIP_FILE_NAME=$(basename -- "$APPLICATION_ZIP_PATH")
ZIP_NAME="${ZIP_FILE_NAME%.*}"
APPLICATION_EXTRACTED_PATH=$INSTALL_DIR/$ZIP_NAME/

if [[ -d $APPLICATION_EXTRACTED_PATH || -f $APPLICATION_EXTRACTED_PATH ]]; then
    echo "A file or directory already exists at $APPLICATION_EXTRACTED_PATH" >>/dev/stderr
    exit 1
fi

echo "Extracting $APPLICATION_ZIP_PATH to $APPLICATION_EXTRACTED_PATH..."
mkdir -p $APPLICATION_EXTRACTED_PATH &&
    unzip $APPLICATION_ZIP_PATH -d $INSTALL_DIR
if [[ $? -ne 0 ]]; then
    echo "Unable to successfully extract $APPLICATION_ZIP_PATH to $INSTALL_DIR" >>/dev/stderr
    exit 1
fi

echo "Setting up application at $APPLICATION_EXTRACTED_PATH..."
pushd $APPLICATION_EXTRACTED_PATH &&
    chown -R $(whoami) . &&
    chmod u+x ./unix_setup.sh &&
    ./unix_setup.sh -j $JAVA_PATH -n $APPLICATION_NAME &&
    popd
if [[ $? -ne 0 ]]; then
    echo "Unable to setup permissions for application binaries" >>/dev/stderr
    exit 1
else
    echo "Application setup done."
fi
