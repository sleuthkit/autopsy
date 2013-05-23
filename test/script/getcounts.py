#!/usr/bin/python
import os
import sys
import sqlite3

def getNumbers(inFile):

  if not inFile.endswith(".db") or not os.path.exists(inFile):
    print("Not a database file: " + inFile)
    return
  # For now, comparing size of blackboard_artifacts,
  #                            blackboard_attributes,
  #                        and tsk_objects.
  inFileConn = sqlite3.connect(inFile)
  inFileC = inFileConn.cursor()
  print(inFile)
  inFileC.execute("select count(*) from tsk_objects")
  inFileObjects = inFileC.fetchone()[0]
  print("Objects: %d" % inFileObjects)
  inFileC.execute("select count(*) from blackboard_artifacts")
  inFileArtifacts = inFileC.fetchone()[0]
  print("Artifacts: %d" % inFileArtifacts)
  inFileC.execute("select count(*) from blackboard_attributes")
  inFileAttributes = inFileC.fetchone()[0]
  print("Attributes: %d" % inFileAttributes)

def usage():
    print("This script queries the databases given as arguments for \n\
    TSK Object, Blackboard Artifact, and Blackboard Attribute counts.")

if __name__ == "__main__":
    if len(sys.argv) == 1:
        usage()
    argi = 1
    while argi < len(sys.argv):
        getNumbers(sys.argv[argi])
        argi+=1

