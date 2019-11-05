#!/bin/bash

set -e
echo "Building TSK..."
cd sleuthkit/sleuthkit
./bootstrap && ./configure --prefix=/usr && make
pushd bindings/java && ant -q dist-PostgreSQL && popd

echo "Building Autopsy..." && echo -en 'travis_fold:start:script.build\\r'
cd $TRAVIS_BUILD_DIR/
ant build
echo -en 'travis_fold:end:script.build\\r'
