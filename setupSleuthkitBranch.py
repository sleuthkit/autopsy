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
ORIGIN_OWNER="origin"
DEVELOP_BRANCH='develop'

passed = 1

def getSleuthkitBranchList(branchOwner):
    # Returns the list of sleuthkit branches
    cmd = ['git','branch','-a']
    retdir = os.getcwd()
    os.chdir(TSK_HOME)
    output = subprocess.check_output(cmd)
    branches = []
    for branch in output.strip().split():
        if branch.startswith('remotes/'+branchOwner):
            branches.append(branch.split('/')[2])
    os.chdir(retdir)
    return branches

def gitSleuthkitCheckout(branch, branchOwner):
    '''
        Checksout sleuthkit branch
        Args:
            branch: String, which branch to checkout
    '''
    # passed is a global variable that gets set to non-zero integer
    # When an error occurs
    global passed
    if (branchOwner==ORIGIN_OWNER):
        cmd = ['git','checkout', branch]
    else:
        #add the remotes
        checkout=['git','checkout','-b',branchOwner+'-'+branch]
        passed = subprocess.call(checkout, stdout=sys.stdout,cwd=TSK_HOME)
        cmd = ['git','pull', "/".join(["https://github.com", branchOwner, "sleuthkit.git"]), branch]
        if passed != 0: #0 would be success
            #unable to create new branch return instead of pulling
            return
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
        CURRENT_BRANCH=os.getenv("TRAVIS_PULL_REQUEST_BRANCH",False)
        BRANCH_OWNER=os.getenv("TRAVIS_PULL_REQUEST_SLUG", False).split('/')[0]
    elif APPVEYOR:
        CURRENT_BRANCH=os.getenv("APPVEYOR_PULL_REQUEST_HEAD_REPO_BRANCH",False)
        BRANCH_OWNER=os.getenv("APPVEYOR_PULL_REQUEST_HEAD_REPO_NAME", False).split('/')[0]
    else:
        cmd=['git','rev-parse','--abbrev-ref','HEAD']
        output = subprocess.check_output(cmd)
        CURRENT_BRANCH=output.strip()
        BRANCH_OWNER=ORIGIN_OWNER
    # If we are in an Autopsy release branch, then use the
    # info in TSKVersion.xml to find the corresponding TSK 
    # release branch.  For other branches, we don't always
    # trust that TSKVersion has been updated.
    if CURRENT_BRANCH.startswith('release'):
        version = parseXML('TSKVersion.xml')
        RELEASE_BRANCH = "release-"+version
        gitSleuthkitCheckout(RELEASE_BRANCH, BRANCH_OWNER)
    # Check if the same branch exists in TSK (develop->develop, custom1->custom1, etc.)
    else: 
        gitSleuthkitCheckout(CURRENT_BRANCH, BRANCH_OWNER)

    # Otherwise, default to origin develop
    if passed != 0:
        gitSleuthkitCheckout(DEVELOP_BRANCH, ORIGIN_OWNER)
        
    if passed != 0:
        print('Error checking out a Sleuth Kit branch')
        sys.exit(1)
        
if __name__ == '__main__':
    main()
