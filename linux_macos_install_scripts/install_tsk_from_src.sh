#!/bin/bash
# Clones sleuthkit repo from github (if necessary) and installs
# this script does require sudo privileges
# called like: install_tsk_from_src.sh -p <repo path to be created or existing> -b <tsk branch to checkout> -r <non-standard remote repo (optional)>

usage() {
    echo "Usage: install_tsk_from_src.sh [-p repo_path (should end with '/sleuthkit')] [-b tsk_branch] [-r sleuthkit_repo]" 1>&2
}

# default repo path
REPO="https://github.com/sleuthkit/sleuthkit.git"
TSK_BRANCH="develop"

while getopts "p:r:b:" o; do
    case "${o}" in
    p)
        SLEUTHKIT_SRC_DIR=${OPTARG}
        ;;
    b)
        TSK_BRANCH=${OPTARG}
        ;;
    r)
        REPO=${OPTARG}
        ;;
    *)
        usage
        exit 1
        ;;
    esac
done

if [[ -z "${SLEUTHKIT_SRC_DIR}" ]]; then
    usage
    exit 1
fi

if [[ ! -d $SLEUTHKIT_SRC_DIR ]]; then
    TSK_REPO_PATH=$(dirname "$SLEUTHKIT_SRC_DIR")
    echo "Cloning Sleuthkit to $TSK_REPO_PATH..."
    mkdir -p $TSK_REPO_PATH &&
        pushd $TSK_REPO_PATH &&
        git clone --depth 1 -b $TSK_BRANCH $REPO &&
        popd
    if [[ ! -d $SLEUTHKIT_SRC_DIR ]]; then
        echo "Unable to successfully clone Sleuthkit" >>/dev/stderr
        exit 1
    fi
else
    echo "Getting latest of Sleuthkit branch: $TSK_BRANCH..."
    pushd $SLEUTHKIT_SRC_DIR &&
        git remote set-branches origin '*' &&
        git fetch -v &&
        git reset --hard &&
        git checkout $TSK_BRANCH &&
        git pull &&
        popd
    if [[ $? -ne 0 ]]; then
        echo "Unable to reset Sleuthkit repo and pull latest on $TSK_BRANCH" >>/dev/stderr
        exit 1
    fi
fi

echo "Installing Sleuthkit..."
pushd $SLEUTHKIT_SRC_DIR &&
    ./bootstrap &&
    ./configure &&
    make &&
    sudo make install &&
    popd
if [[ $? -ne 0 ]]; then
    echo "Unable to build Sleuthkit." >>/dev/stderr
    exit 1
fi

JAVA_INSTALLS=/usr/local/share/java
echo "Sleuthkit in $JAVA_INSTALLS:"
ls $JAVA_INSTALLS | grep sleuthkit
