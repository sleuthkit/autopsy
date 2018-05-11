# This python script is used to automatically set the branch in The Sleuth Kit repository
# for use in automated build environments. 
#
# Basic idea is that it determines what Autopsy branch is being used and then checksout
# a corresponding TSK branch.  
#
# TSK_HOME environment variable must be set for this to work.

import os
import sys
import subprocess
import xml.etree.ElementTree as ET

TSK_HOME=os.getenv("TSK_HOME",False)
passed = 1

def getSleuthkitBranchList():
    # Returns the list of sleuthkit branches
    cmd = ['git','branch','-a']
    retdir = os.getcwd()
    os.chdir(TSK_HOME)
    output = subprocess.check_output(cmd)
    branches = []
    for branch in output.strip().split():
        if branch.startswith('remotes/origin'):
            branches.append(branch.split('/')[2])
    os.chdir(retdir)
    return branches

def gitSleuthkitCheckout(branch):
    '''
        Checksout sleuthkit branch
        Args:
            branch: String, which branch to checkout
    '''
    # passed is a global variable that gets set to non-zero integer
    # When an error occurs
    global passed
    cmd = ['git','checkout',branch]
    passed = subprocess.call(cmd,stdout=sys.stdout,cwd=TSK_HOME)

def parseXML(xmlFile):
    '''
        parses the TSKVersion.xml file for sleuthkit version
        Args:
            xmlFile: String, xml file to parse
    '''
    tree = ET.parse(xmlFile)
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

    # Get the Autopsy branch being used.  Travis and Appveyor
    # will tell us where a pull request is directed
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
        
    # If we are in an Autopsy release branch, then use the
    # info in TSKVersion.xml to find the corresponding TSK 
    # release branch.  For other branches, we don't always
    # trust that TSKVersion has been updated.
    if CURRENT_BRANCH.startswith('release'):
        version = parseXML('TSKVersion.xml')
        RELEASE_BRANCH = "release-"+version
        gitSleuthkitCheckout(RELEASE_BRANCH)
    # Check if the same branch exists in TSK (develop->develop, custom1->custom1, etc.)
    elif CURRENT_BRANCH in getSleuthkitBranchList(): 
        gitSleuthkitCheckout(CURRENT_BRANCH)

    # Otherwise, default to develop
    if passed != 0:
        gitSleuthkitCheckout('develop')
        
    if passed != 0:
        print('Error checking out a Sleuth Kit branch')
        sys.exit(1)
        
if __name__ == '__main__':
    main()
