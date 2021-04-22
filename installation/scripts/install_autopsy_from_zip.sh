#!/bin/bash
# Unzips an autopsy platform zip to specified directory and does setup
# called like:  install_autopsy.sh [-z zip_path] [-i install_directory]

usage() { 
    echo "Usage: install_autopsy.sh [-z zip_path] [-i install_directory] [-v asc_file]" 1>&2;
    echo "If specifying a .asc verification file (with -v flag), the program will attempt to create a temp folder in the working directory and verify the signature with gpg." 1>&2;
}

while getopts "z:i:v:" o; do
    case "${o}" in
        z)
            AUTOPSY_ZIP_PATH=${OPTARG}
            ;;
        i)
            INSTALL_DIR=${OPTARG}
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done

if [[ -z "${AUTOPSY_ZIP_PATH}" ]] || [[ -z "${INSTALL_DIR}" ]]; then
    usage
    exit 1
fi

if [[ ! -z "${ASC_FILE}" ]]
then
    VERIFY_DIR=$(pwd)/temp
    mkdir -p ${VERIFY_DIR} && \
    pushd ${VERIFY_DIR} && \
    wget https://sleuthkit.org/carrier.asc && \
    gpg --homedir "${VERIFY_DIR}" --import https://sleuthkit.org/carrier.asc && \
    gpgv --homedir "${VERIFY_DIR}" --keyring "${VERIFY_DIR}/pubring.kbx" ${ASC_FILE} ${AUTOPSY_ZIP_PATH} && \
    rm -r ${VERIFY_DIR}
    popd
    if [[ $? -ne 0 ]]
    then
        popd
        echo "Unable to successfully extract $AUTOPSY_ZIP_PATH to $AUTOPSY_EXTRACTED_PATH" >> /dev/stderr
        exit 1
    fi
fi


zip_file_name=$(basename -- "$AUTOPSY_ZIP_PATH")
zip_name="${filename%.*}"
AUTOPSY_EXTRACTED_PATH=$INSTALL_DIR/$zip_name

if [[ -d $AUTOPSY_EXTRACTED_PATH || -f $AUTOPSY_EXTRACTED_PATH ]]
then
    echo "A file or directory already exists at $AUTOPSY_EXTRACTED_PATH" >> /dev/stderr
    exit 1
fi

echo "Extracting $AUTOPSY_ZIP_PATH to $AUTOPSY_EXTRACTED_PATH..."
mkdir -p $AUTOPSY_EXTRACTED_PATH && \
unzip $AUTOPSY_ZIP_PATH -d $AUTOPSY_EXTRACTED_PATH
if [[ $? -ne 0 ]]
then
    echo "Unable to successfully extract $AUTOPSY_ZIP_PATH to $AUTOPSY_EXTRACTED_PATH" >> /dev/stderr
    exit 1
fi

echo "Setting up autopsy at $AUTOPSY_EXTRACTED_PATH..."
pushd $AUTOPSY_EXTRACTED_PATH && \
chown -R $(whoami) . && \
chmod u+x ./unix_setup.sh && \
./unix_setup.sh && \
popd
if [[ $? -ne 0 ]]
then
    popd
    echo "Unable to setup permissions for autopsy binaries" >> /dev/stderr
    exit 1
else
    echo "Autopsy setup done."
fi