#!/bin/bash

set -e
echo "Building TSK..."
cd sleuthkit/sleuthkit
./bootstrap && ./configure --prefix=/usr && make
pushd bindings/java && ant -q dist && popd

echo "Building Autopsy..." && echo -en 'travis_fold:start:script.build\\r'
cd $TRAVIS_BUILD_DIR/
ant -q build
echo -en 'travis_fold:end:script.build\\r'

echo "Testing Autopsy..." && echo -en 'travis_fold:start:script.tests\\r'
echo "Free Space:"
echo `df -h .` 

if [ "${TRAVIS_OS_NAME}" = "linux" ]; then
    # if linux use xvfb
    xvfb-run ant -q test-no-regression
fi

echo -en 'travis_fold:end:script.tests\\r'
