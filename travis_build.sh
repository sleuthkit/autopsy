#!/bin/bash

set -e
echo "Building TSK..."
cd sleuthkit/sleuthkit
./bootstrap && ./configure --prefix=/usr && make
pushd bindings/java && ant -q dist && popd

echo "Building Autopsy..." && echo -en 'travis_fold:start:script.build\\r'
cd $TRAVIS_BUILD_DIR/
ant build
build_rc=$?
echo -en 'travis_fold:end:script.build\\r'

# don't continue to test if build failed.
if [[ $build_rc != 0 ]]; then
   echo "Build failed.  Not continuing to tests."
   exit $build_rc
fi

echo "Testing Autopsy..." && echo -en 'travis_fold:start:script.tests\\r'
echo "Free Space:"
echo `df -h .` 
xvfb-run ant -q test
test_rc=$?
echo -en 'travis_fold:end:script.tests\\r'
exit $test_rc

if [[ $test_rc != 0 ]]; then
    echo "There was a test failure."
    exit $test_rc
fi