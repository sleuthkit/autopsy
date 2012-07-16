#!/usr/bin/python 
import sys
import sqlite3
import re
import subprocess
import os.path
import shutil
import time

# Last modified 7/13/12 3@ pm
#  Usage: ./regression.py [-i FILE] [OPTIONS]
#  Run the RegressionTest.java file, and compare the result with a gold standard
#  When the -i flag is set, this script only tests the image given by FILE.
#  By default, it tests every image in ./input/
#  An indexed NSRL database is expected at ./input/nsrl.txt-md5.idx,
#  and an indexed notable hash database at ./input/notablehashes.txt-md5.idx
#  In addition, any keywords to search for must be in ./input/notablekeywords.xml
#    Options:
#    -r, --rebuild      Rebuild the gold standards from the test results for each image
#    -u, --ignore

hadErrors = False # If any of the tests failed
results = {}      # Dictionary in which to store map ({imgname}->errors)
goldDir = "gold"  # Directory for gold standards (files should be ./gold/{imgname}/standard.db)
inDir = "input"   # Image files, hash dbs, and keywords.
# Results will be in ./output/{datetime}/{imgname}/
outDir = os.path.join("output",time.strftime("%Y.%m.%d-%H.%M"))


# Run ingest on all the images in 'input', using notablekeywords.xml and notablehashes.txt-md5.idx
def testAddImageIngest(inFile, ignoreUnalloc):
  print "================================================"
  print "Ingesting Image: " + inFile

  # Set up case directory path
  testCaseName = imageName(inFile)
  if ignoreUnalloc:
    testCaseName+="-u"
  if os.path.exists(os.path.join(outDir,testCaseName)):
    shutil.rmtree(os.path.join(outDir,testCaseName))
  os.makedirs(os.path.join(outDir,testCaseName))
  if not os.path.exists(inDir):
    markError("input dir does not exist", inFile)

  cwd = wgetcwd()
  testInFile = wabspath(inFile)
  # NEEDS windows path (backslashes) for .E00 images to work
  testInFile = testInFile.replace("/", "\\")
  knownBadPath = os.path.join(cwd,inDir,"notablehashes.txt-md5.idx")
  knownBadPath = knownBadPath.replace("/", "\\")
  keywordPath = os.path.join(cwd,inDir,"notablekeywords.xml")
  keywordPath = keywordPath.replace("/", "\\")
  nsrlPath = os.path.join(cwd,inDir,"nsrl.txt-md5.idx")
  nsrlPath = nsrlPath.replace("/", "\\")

  antlog = os.path.join(cwd,outDir,testCaseName,"antlog.txt")
  antlog = antlog.replace("/", "\\")

  timeout = 24 * 60 * 60 * 1000    # default of 24 hours, just to be safe
  size = getImageSize(inFile)      # get the size in bytes
  timeout = (size / 1000) / 1000   # convert to MB
  timeout = timeout * 1000         # convert sec to ms
  timeout = timeout * 1.5          # add a little extra umph
  timeout = timeout * 25       # decided we needed A LOT extra to be safe

  # set up ant target
  args = ["ant"]
  args.append("-q")
  args.append("-f")
  args.append(os.path.join("..","build.xml"))
  args.append("regression-test")
  args.append("-l")
  args.append(antlog)
  args.append("-Dimg_path=" + testInFile)
  args.append("-Dknown_bad_path=" + knownBadPath)
  args.append("-Dkeyword_path=" + keywordPath)
  args.append("-Dnsrl_path=" + nsrlPath)
  args.append("-Dgold_path=" + os.path.join(cwd,goldDir).replace("/", "\\"))
  args.append("-Dout_path=" + os.path.join(cwd,outDir,testCaseName).replace("/", "\\"))
  args.append("-Dignore_unalloc=" + "%s" % ignoreUnalloc)
  args.append("-Dtest.timeout=" + str(timeout))


  # print the ant testing command
  print "CMD: " + " ".join(args)

  print "Starting test..."
  #fnull = open(os.devnull, 'w')
  #subprocess.call(args, stderr=subprocess.STDOUT, stdout=fnull)
  #fnull.close();
  subprocess.call(args)

def getImageSize(inFile):
  name = imageName(inFile)
  path = os.path.join(".",inDir)
  size = 0
  for files in os.listdir(path):
    filename = os.path.splitext(files)[0]
    if filename == name:
      filepath = os.path.join(path, files)
      if not os.path.samefile(filepath, inFile):
        size += os.path.getsize(filepath)
  size += os.path.getsize(inFile)
  return size

def testCompareToGold(inFile, ignore):
  print "-----------------------------------------------"
  print "Comparing results for " + inFile + " with gold."

  name = imageName(inFile)
  if ignore:
   name += ("-u")
  cwd = wgetcwd()
  goldFile = os.path.join("./",goldDir,name,"standard.db")  
  testFile = os.path.join("./",outDir,name,"AutopsyTestCase","autopsy.db")
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

  # Keep range in sync with number of items in ARTIFACT_TYPE enum
  for type_id in range(1, 13):
    goldC.execute("select count(*) from blackboard_artifacts where artifact_type_id=%d" % type_id)
    goldArtifacts = goldC.fetchone()[0]
    testC.execute("select count(*) from blackboard_artifacts where artifact_type_id=%d" % type_id)
    testArtifacts = testC.fetchone()[0]
    if(goldArtifacts != testArtifacts):
      errString = str("Artifact counts do not match for type id %d!: " % type_id)
      errString += str("Gold: %d, Test: %d" % (goldArtifacts, testArtifacts))
      markError(errString, inFile)
    else:
      print("Artifact counts for artifact type id %d match!" % type_id)

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

def clearGoldDir(inFile, ignore):
  cwd = wgetcwd()
  inFile = imageName(inFile)
  if ignore:
    inFile += "-u"
  if os.path.exists(os.path.join(cwd,goldDir,inFile)):
    shutil.rmtree(os.path.join(cwd,goldDir,inFile))
  os.makedirs(os.path.join(cwd,goldDir,inFile))

def copyTestToGold(inFile, ignore): 
  print "------------------------------------------------"
  print "Recreating gold standard from results."
  inFile = imageName(inFile)
  if ignore:
    inFile += "-u"
  cwd = wgetcwd()
  goldFile = os.path.join("./",goldDir,inFile,"standard.db")
  testFile = os.path.join("./",outDir,inFile,"AutopsyTestCase","autopsy.db")
  shutil.copy(testFile, goldFile)

def copyReportToGold(inFile, ignore): 
  print "------------------------------------------------"
  print "Recreating gold report from results."
  inFile = imageName(inFile)
  if ignore:
    inFile += "-u"
  cwd = wgetcwd()
  goldReport = os.path.join("./",goldDir,inFile,"report.html")
  testReportPath = os.path.join("./",outDir,inFile,"AutopsyTestCase","Reports")
  # Because Java adds a timestamp to the report file, one can't call it
  # directly, so one must get a list of files in the dir, which are only
  # reports, then filter for the .html report
  testReport = None
  for files in os.listdir(testReportPath):
    if files.endswith(".html"): # Get the HTML one
      testReport = os.path.join("./",outDir,inFile,"AutopsyTestCase","Reports",files)
  if testReport is None:
    markError("No test report exists", inFile)
    return
  else:
    shutil.copy(testReport, goldReport)

def testCompareReports(inFile, ignore):
  print "------------------------------------------------"
  print "Comparing report to golden report."
  name = imageName(inFile)
  if ignore:
    name += "-u"
  goldReport = os.path.join("./",goldDir,name,"report.html")  
  testReportPath = os.path.join("./",outDir,name,"AutopsyTestCase","Reports")
  # Because Java adds a timestamp to the report file, one can't call it
  # directly, so one must get a list of files in the dir, which are only
  # reports, then filter for the .html report
  testReport = None
  for files in os.listdir(testReportPath):
    if files.endswith(".html"): # Get the HTML one
      testReport = os.path.join("./",outDir,name,"AutopsyTestCase","Reports",files)
  if os.path.isfile(goldReport) == False:
    markError("No gold report exists", inFile)
    return
  if testReport is None:
    markError("No test report exists", inFile)
    return
  # Compare the reports
  goldFile = open(goldReport)
  testFile = open(testReport)
  # Search for <ul> because it is first seen in the report
  # immediately after the unnecessary metadata, styles, and timestamp
  gold = goldFile.read()
  test = testFile.read()
  gold = gold[gold.find("<ul>"):]
  test = test[test.find("<ul>"):]
  # Splitting allows for printouts of what the difference is
  goldList = split(gold, 50)
  testList = split(test, 50)
  failed = 0
  for goldSplit, testSplit in zip(goldList, testList):
    if goldSplit != testSplit:
      failed = 1
      #print "Got: " + testSplit
      #print "Expected: " + goldSplit
      break
  if(failed):
    errString = "Reports do not match."
    markError(errString, inFile)
  else:
    print "Reports match."
  
def split(input, size):
  return [input[start:start+size] for start in range(0, len(input), size)]

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

def copyLogs(inFile, ignore):
  name = imageName(inFile)
  if ignore:
   name +="-u"
  logDir = os.path.join("..","build","test","qa-functional","work","userdir0","var","log")
  shutil.copytree(logDir,os.path.join(outDir,name,"logs"))

def testFile(image, rebuild, ignore):
  if imageType(image) != ImgType.UNKNOWN:
    if ignore:
      testAddImageIngest(image, True)
    else:
      testAddImageIngest(image, False)
    copyLogs(image, ignore)
    if rebuild:
      clearGoldDir(image, ignore)
      copyTestToGold(image, ignore)
      copyReportToGold(image, ignore)
    else:
      testCompareToGold(image, ignore)
      testCompareReports(image, ignore)

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
    -r, --rebuild\t\tRebuild the gold standards from the test results for each image\n\n\
    -u, --nounalloc\t\tIgnore unallocated space while ingesting"
  return usage

def main():
  rebuild = False
  single = False
  ignore = False
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
      elif (arg == "--nounalloc") or (arg == "-u"):
          ignore = True
          print "Ignoring unallocated space"
      else:
          test = False
          print usage()
      argi+=1
  if single:
    testFile(image, rebuild, ignore)
  elif test:
    for inFile in os.listdir(inDir):
      testFile(os.path.join(inDir,inFile), rebuild, ignore)

  if hadErrors == True:
    print "**********************************************"
    print "Tests complete: There were errors"
  else:
    print "**********************************************"
    print "Tests complete: All tests passed"

  for k,v in results.items():
    print k
    for errString in v:
      print("\t%s" % errString)

if __name__ == "__main__":
  main()