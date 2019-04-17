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

def gitSleuthkitCheckout(branch, branchOwner):
    '''
        Checksout sleuthkit branch
        Args:
            branch: String, which branch to checkout
    '''
    # passed is a global variable that gets set to non-zero integer
    # When an error occurs
    global passed
    #add the remotes
    #if the branch owner was origin substitute in the name of that owner
    if (branchOwner==ORIGIN_OWNER):
        gitHubUser="sleuthkit"
    else:
        gitHubUser=branchOwner
    checkout=['git','checkout','-b',branchOwner+'-'+branch]
    print("Command run:" + " ".join(checkout))
    passed = subprocess.call(checkout, stdout=sys.stdout,cwd=TSK_HOME)
    cmd = ['git','pull', "/".join(["https://github.com", gitHubUser, "sleuthkit.git"]), branch]
    if passed != 0: #0 would be success
        #unable to create new branch return instead of pulling
        return
    print("Command run:" + " ".join(cmd))
    passed = subprocess.call(cmd,stdout=sys.stdout,cwd=TSK_HOME)
    if (passed == 0):
        sys.exit() #exit if successful
    else:
        print("Branch: " + branch + " does not exist for github user: " + gitHubUser)

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
        CURRENT_BRANCH=os.getenv("TRAVIS_PULL_REQUEST_BRANCH","") #make default empty string which is same vaule used when not a PR
        if (CURRENT_BRANCH != ""): #if it is a PR
             BRANCH_OWNER=os.getenv("TRAVIS_PULL_REQUEST_SLUG", ORIGIN_OWNER+"/"+CURRENT_BRANCH).split('/')[0]  #default owner is ORIGIN_OWNER
             gitSleuthkitCheckout(CURRENT_BRANCH, BRANCH_OWNER)
        TARGET_BRANCH=os.getenv("TRAVIS_BRANCH",DEVELOP_BRANCH)
    elif APPVEYOR:
        CURRENT_BRANCH=os.getenv("APPVEYOR_PULL_REQUEST_HEAD_REPO_BRANCH","") #make default same as value used by travis for readability of code
        if (CURRENT_BRANCH != ""): #if it is a PR
             BRANCH_OWNER=os.getenv("APPVEYOR_PULL_REQUEST_HEAD_REPO_NAME", ORIGIN_OWNER+"/"+CURRENT_BRANCH).split('/')[0] #default owner is ORIGIN_OWNER
             gitSleuthkitCheckout(CURRENT_BRANCH, BRANCH_OWNER)
        TARGET_BRANCH=os.getenv("APPVEYOR_REPO_BRANCH",DEVELOP_BRANCH)
    else:
        cmd=['git','rev-parse','--abbrev-ref','HEAD']
        output = subprocess.check_output(cmd)
        TARGET_BRANCH=output.strip()
    # If we are in an Autopsy release branch, then use the
    # info in TSKVersion.xml to find the corresponding TSK 
    # release branch.  For other branches, we don't always
    # trust that TSKVersion has been updated.
    if TARGET_BRANCH.startswith('release'):
        version = parseXML('TSKVersion.xml')
        RELEASE_BRANCH = "release-"+version
        #Check if the same user has a release branch which corresponds to this release branch
        gitSleuthkitCheckout(RELEASE_BRANCH, ORIGIN_OWNER)
    else: 
        gitSleuthkitCheckout(TARGET_BRANCH, ORIGIN_OWNER)
    # Otherwise, default to origin develop
    gitSleuthkitCheckout(DEVELOP_BRANCH, ORIGIN_OWNER)
        
    if passed != 0:
        print('Error checking out a Sleuth Kit branch')
        sys.exit(1)
        
if __name__ == '__main__':
    main()
