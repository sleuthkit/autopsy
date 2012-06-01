#!/usr/bin/python 
import sys
import sqlite3
import re
import subprocess
import os.path
import shutil
import time

#  Usage: ./regression.py [-i FILE] [OPTIONS]
#  Run the RegressionTest.java file, and compare the result with a gold standard
#  When the -i flag is set, this script only tests the image given by FILE.
#  By default, it tests every image in ./input/
#  An indexed NSRL database is expected at ./input/nsrl.txt-md5.idx,
#  and an indexed notable hash database at ./input/notablehashes.txt-md5.idx
#  In addition, any keywords to search for must be in ./input/notablekeywords.xml
#    Options:
#    -r, --rebuild      Rebuild the gold standards from the test results for each image

hadErrors = False # If any of the tests failed
results = {}      # Dictionary in which to store map ({imgname}->errors)
goldDir = "gold"  # Directory for gold standards (files should be ./gold/{imgname}/standard.db)
inDir = "input"   # Image files, hash dbs, and keywords.
# Results will be in ./output/{datetime}/{imgname}/
outDir = os.path.join("output",time.strftime("%Y.%m.%d-%H.%M")) 


# Run ingest on all the images in 'input', using notablekeywords.xml and notablehashes.txt-md5.idx
def testAddImageIngest(inFile):
  print "================================================"
  print "Ingesting Image: " + inFile

  # Set up case directory path
  testCaseName = imageName(inFile)
  if os.path.exists(os.path.join(outDir,testCaseName)):
    shutil.rmtree(os.path.join(outDir,testCaseName))
  os.makedirs(os.path.join(outDir,testCaseName))

  cwd = wgetcwd()
  testInFile = wabspath(inFile)
  knownBadPath = os.path.join(cwd,inDir,"notablehashes.txt-md5.idx")
  keywordPath = os.path.join(cwd,inDir,"notablekeywords.xml")
  nsrlPath = os.path.join(cwd,inDir,"nsrl.txt-md5.idx")

  # set up ant target
  args = ["ant"]
  args.append("-q")
  args.append("-f")
  args.append(os.path.join("..","build.xml"))
  args.append("regression-test")
  args.append("-l")
  args.append(os.path.join(cwd,outDir,testCaseName,"antlog.txt"))
  args.append("-Dimg_path=" + testInFile)
  args.append("-Dknown_bad_path=" + knownBadPath)
  args.append("-Dkeyword_path=" + keywordPath)
  args.append("-Dnsrl_path=" + nsrlPath)
  args.append("-Dgold_path=" + os.path.join(cwd,goldDir))
  args.append("-Dout_path=" + os.path.join(cwd,outDir,testCaseName))

  # print the ant testing command
  print "CMD: " + " ".join(args)

  print "Starting test..."
  #fnull = open(os.devnull, 'w')
  #subprocess.call(args, stderr=subprocess.STDOUT, stdout=fnull)
  #fnull.close();
  subprocess.call(args)

def testCompareToGold(inFile):
  print "-----------------------------------------------"
  print "Comparing results for " + inFile + " with gold."

  name = imageName(inFile)
  cwd = wgetcwd()
  goldFile = os.path.join(cwd,goldDir,name,"standard.db")
  testFile = os.path.join(cwd,outDir,name,"AutopsyTestCase","autopsy.db")
  if os.path.isfile(goldFile) == False:
    markError("No gold standard exists", inFile)
    return
  if os.path.isfile(testFile) == False:
    markError("No database exists", inFile)
    return

  # For now, comparing size of blackboard_artifacts,
  #                            blackboard_attributes,
  #                        and tsk_objects.
  goldConn = sqlite3.connect(goldFile)
  goldC = goldConn.cursor()
  testConn = sqlite3.connect(testFile)
  testC = testConn.cursor()

  print("Comparing Artifacts: ")
  goldC.execute("select count(*) from blackboard_artifacts")
  goldArtifacts = goldC.fetchone()[0]
  testC.execute("select count(*) from blackboard_artifacts")
  testArtifacts = testC.fetchone()[0]
  if(goldArtifacts != testArtifacts):
      errString = "Artifact counts do not match!: "
      errString += str("Gold: %d, Test: %d" % (goldArtifacts, testArtifacts))
      markError(errString, inFile)
  else:
      print("Artifact counts match!")
  print("Comparing Attributes: ")
  goldC.execute("select count(*) from blackboard_attributes")
  goldAttributes = goldC.fetchone()[0]
  testC.execute("select count(*) from blackboard_attributes")
  testAttributes = testC.fetchone()[0]
  if(goldAttributes != testAttributes):
      errString = "Attribute counts do not match!: "
      errString += str("Gold: %d, Test: %d" % (goldAttributes, testAttributes))
      markError(errString, inFile)
  else:
      print("Attribute counts match!")
  print("Comparing TSK Objects: ")
  goldC.execute("select count(*) from tsk_objects")
  goldObjects = goldC.fetchone()[0]
  testC.execute("select count(*) from tsk_objects")
  testObjects = testC.fetchone()[0]
  if(goldObjects != testObjects):
      errString = "TSK Object counts do not match!: "
      errString += str("Gold: %d, Test: %d" % (goldObjects, testObjects))
      markError(errString, inFile)
  else:
      print("Object counts match!")

def copyTestToGold(inFile): 
  print "------------------------------------------------"
  print "Recreating gold standard from results."
  inFile = imageName(inFile)
  cwd = wgetcwd()
  goldFile = os.path.join(cwd,goldDir,inFile,"standard.db")
  testFile = os.path.join(cwd,outDir,inFile,"AutopsyTestCase","autopsy.db")
  if os.path.exists(os.path.join(cwd,goldDir,inFile)):
      shutil.rmtree(os.path.join(cwd,goldDir,inFile))
  os.makedirs(os.path.join(cwd,goldDir,inFile))
  shutil.copy(testFile, goldFile)

class ImgType:
  RAW, ENCASE, SPLIT, UNKNOWN = range(4)

def imageType(inFile):
  extStart = inFile.rfind(".")
  if (extStart == -1):
    return ImgType.UNKNOWN
  ext = inFile[extStart:].lower()
  if (ext == ".img" or ext == ".dd"):
    return ImgType.RAW
  elif (ext == ".e01"):
    return ImgType.ENCASE
  elif (ext == ".aa" or ext == ".001"):
    return ImgType.SPLIT
  else:
    return ImgType.UNKNOWN

def imageName(inFile):
    pathEnd = inFile.rfind("/")
    extStart = inFile.rfind(".")
    if(extStart == -1 and extStart == -1):
        return inFile
    elif(extStart == -1):
        return inFile[pathEnd+1:]
    elif(pathEnd == -1):
        return inFile[:extStart]
    else:
        return inFile[pathEnd+1:extStart]

def markError(errString, inFile):
    global hadErrors
    hadErrors = True
    errors = results.get(inFile, [])
    errors.append(errString)
    results[inFile] = errors
    print errString

def wgetcwd():
    proc = subprocess.Popen(("cygpath", "-m", os.getcwd()), stdout=subprocess.PIPE)
    out,err = proc.communicate()
    return out.rstrip()

def wabspath(inFile):
    proc = subprocess.Popen(("cygpath", "-m", os.path.abspath(inFile)), stdout=subprocess.PIPE)
    out,err = proc.communicate()
    return out.rstrip()

def copyLogs(inFile):
  logDir = os.path.join("..","build","test","qa-functional","work","userdir0","var","log")
  shutil.copytree(logDir,os.path.join(outDir,imageName(inFile),"logs"))

def testFile(image, rebuild):
  if imageType(image) != ImgType.UNKNOWN:
    testAddImageIngest(image)
    #print imageName(image)
    copyLogs(image)
    if rebuild:
      copyTestToGold(image)
    else:
      testCompareToGold(image)

def usage() :
  usage = "\
  Usage: ./regression.py [-i FILE] [OPTIONS] \n\n\
  Run the RegressionTest.java file, and compare the result with a gold standard \n\n\
  When the -i flag is set, this script only tests the image given by FILE.\n\
  By default, it tests every image in ./input/\n\n\
  An indexed NSRL database is expected at ./input/nsrl.txt-md5.idx,\n\
  and an indexed notable hash database at ./input/notablehashes.txt-md5.idx\n\
  In addition, any keywords to search for must be in ./input/notablekeywords.xml\n\n\
    Options:\n\n\
    -r, --rebuild\t\tRebuild the gold standards from the test results for each image"
  return usage

def main():
  rebuild = False
  single = False
  test = True
  argi = 1
  while argi < len(sys.argv):
      arg = sys.argv[argi]
      if arg == "-i" and argi+1 < len(sys.argv):
          single = True
          argi+=1
          image = sys.argv[argi]
          print "Running on single image: " + image
      elif (arg  == "--rebuild") or (arg == "-r"):
          rebuild = True
          print "Running in REBUILD mode"
      else:
          test = False
          print usage()
      argi+=1
  if single:
    testFile(image, rebuild)
  elif test:
    for inFile in os.listdir(inDir):
      testFile(os.path.join(inDir,inFile), rebuild)

  if hadErrors == True:
    print "**********************************************"
    print "Tests complete: There were errors"

  for k,v in results.items():
    print k
    for errString in v:
      print("\t%s" % errString)

if __name__ == "__main__":
  main()
