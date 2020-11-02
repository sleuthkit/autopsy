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
cd Core/
echo "Testing Autopsy Core..."
echo "Free Space:"
echo `df -h .` 
xvfb-run ant -q test
core_test_rc=$?
cd ../KeywordSearch
echo "Testing Autopsy Keyword Search..."
echo "Free Space:"
echo `df -h .` 
xvfb-run ant -q test
keywordsearch_test_rc=$?
echo -en 'travis_fold:end:script.tests\\r'

if [ $core_test_rc != 0 ] || [$keywordsearch_test_rc != 0]; then
    echo "There was a test failure."

    if [$core_test_rc != 0]; then
        exit $core_test_rc
    elif [$keywordsearch_test_rc != 0]; then
        exit $keywordsearch_test_rc
    fi
fi