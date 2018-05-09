# This python script sets the sleuthkit branch based on the autopsy build branch
# in appveyor and travis

import os
import sys
import subprocess
import xml.etree.ElementTree as ET

TSK_HOME=os.getenv("TSK_HOME",False)
passed = 1

def gitSleuthkitCheckout(branch):
    global passed
    cmd = ['git','checkout',branch]
    passed = subprocess.call(cmd,stdout=sys.stdout,cwd=TSK_HOME)

def parseXML(xmlFile):
    tree = ET.parse('TSKVersion.xml')
    root = tree.getroot()
    for child in root:
        if child.attrib['name']=='TSK_VERSION':
            return child.attrib['value']
    return None

def main():
    global passed

    if not TSK_HOME:
        sys.exit(1)
        print('Please set TSK_HOME env variable')

    TRAVIS=os.getenv("TRAVIS",False)
    APPVEYOR=os.getenv("APPVEYOR",False)
    if TRAVIS == "true":
        CURRENT_BRANCH=os.getenv("TRAVIS_BRANCH",False)
    elif APPVEYOR:
        CURRENT_BRANCH=os.getenv("APPVEYOR_REPO_BRANCH",False)
    else:
        cmd=['git','rev-parse','--abbrev-ref','HEAD']
        output = subprocess.check_output(cmd)
        CURRENT_BRANCH=output.strip()

    if CURRENT_BRANCH.startswith('custom-'):
        gitSleuthkitCheckout(CURRENT_BRANCH)
    else:
        version = parseXML('TSKVersion.xml')
        RELEASE_BRANCH = "release-"+version
        gitSleuthkitCheckout(RELEASE_BRANCH)

    if passed != 0:
        gitSleuthkitCheckout('develop')
    if passed != 0:
            print('Something gone wrong')
            sys.exit(1)
if __name__ == '__main__':
    main()
