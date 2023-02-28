#!/bin/bash
# Unzips an application platform zip to specified directory and does setup

usage() {
    echo "Usage: install_application.sh [-z zip_path] [-i install_directory] [-j java_home] [-n application_name] [-v asc_file]" 1>&2
    echo "If specifying a .asc verification file (with -v flag), the program will attempt to create a temp folder in the working directory and verify the signature with gpg.  If you already have an extracted zip, the '-z' flag can be ignored as long as the directory specifying the extracted contents is provided for the installation directory." 1>&2
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

if [[ -z "$INSTALL_DIR" ]]; then
    usage
    exit 1
fi

# If zip path has not been specified and there is nothing at the install directory
if [[ -z "$APPLICATION_ZIP_PATH" ]] && [[ ! -d "$INSTALL_DIR" ]]; then
    usage
    exit 1
fi

# check against the asc file if the zip exists
if [[ -n "$ASC_FILE" ]] && [[ -n "$APPLICATION_ZIP_PATH" ]]; then
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

# if specifying a zip path, ensure directory doesn't exist and then create and extract
if [[ -n "$APPLICATION_ZIP_PATH" ]]; then
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
fi 

echo "Setting up application at $APPLICATION_EXTRACTED_PATH..."
# find unix_setup.sh in least nested path (https://stackoverflow.com/a/40039568/2375948)
UNIX_SETUP_PATH=`find $APPLICATION_EXTRACTED_PATH -maxdepth 2 -name 'unix_setup.sh' | head -n1 | xargs -I{} dirname {}`

pushd $UNIX_SETUP_PATH &&
    chown -R $(whoami) . &&
    chmod u+x ./unix_setup.sh &&
    ./unix_setup.sh -j $JAVA_PATH -n $APPLICATION_NAME &&
    popd
if [[ $? -ne 0 ]]; then
    echo "Unable to setup permissions for application binaries" >>/dev/stderr
    exit 1
else
    echo "Application setup done.  You can run $APPLICATION_NAME from $UNIX_SETUP_PATH/bin/$APPLICATION_NAME."
fi
