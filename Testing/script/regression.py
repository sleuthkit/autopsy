#!/usr/bin/python 
#en_US.UTF-8
import sys
import sqlite3
import re
import subprocess
import os.path
import shutil
import time
import xml
from xml.dom.minidom import parse, parseString


#  Last modified 7/23/12 @11:30am
#  Usage: ./regression.py [-f FILE] OR [-l CONFIG]  [OPTIONS]
#  Run the RegressionTest.java file, and compare the result with a gold standard
#  When the -f flag is set, this script only tests the image given by FILE.
#  An indexed NSRL database is expected at ./input/nsrl.txt-md5.idx,
#  and an indexed notable hash database at ./input/notablehashes.txt-md5.idx
#  In addition, any keywords to search for must be in ./input/notablekeywords.xml
#  When the -l flag is set, the script looks for a config.xml file of the given name 
#  where images are stored. The above input files can be outsourced to different locations
#  from the config.xml. For usage notes please see the example "config.xml" in
#  the /script folder.
#    Options: 
#    -r, --rebuild      Rebuild the gold standards from the test results for each image
#    -i, --ignore       Ignores the ./input directory when searching for files
#    -u, --unallocated  Ignores unallocated space when ingesting. Faster, but less accurate results.
  

hadErrors = False # If any of the tests failed
results = {}      # Dictionary in which to store map ({imgname}->errors)
goldDir = "gold"  # Directory for gold standards (files should be ./gold/{imgname}/standard.db)
inDir = "input"   # Image files, hash dbs, and keywords.
# Results will be in ./output/{datetime}/{imgname}/
outDir = os.path.join("output",time.strftime("%Y.%m.%d-%H.%M"))


# Run ingest on all the images in 'input', using notablekeywords.xml and notablehashes.txt-md5.idx
def testAddImageIngest(inFile, ignoreUnalloc, list):
  print "================================================"
  print "Ingesting Image: " + inFile

  # Set up case directory path
  testCaseName = imageName(inFile)
  
  #check for flags to append to folder name
  if ignoreUnalloc:
    testCaseName+="-u"
  if list:
    testCaseName+="-l"
  if os.path.exists(os.path.join(outDir,testCaseName)):
    shutil.rmtree(os.path.join(outDir,testCaseName))
  os.makedirs(os.path.join(outDir,testCaseName))
  if not os.path.exists(inDir):
    markError("input dir does not exist", inFile)

  cwd = wgetcwd()
  testInFile = wabspath(inFile)

  # NEEDS windows path (backslashes) for .E00 images to work
  testInFile = testInFile.replace("/", "\\")
  if list:
    knownBadPath = os.path.join(inDir, "notablehashes.txt-md5.idx")
    keywordPath = os.path.join(inDir, "notablekeywords.xml")
    nsrlPath = os.path.join(inDir, "nsrl.txt-md5.idx")
  else:  
    knownBadPath = os.path.join(cwd,inDir,"notablehashes.txt-md5.idx")
    keywordPath = os.path.join(cwd,inDir,"notablekeywords.xml")
    nsrlPath = os.path.join(cwd,inDir,"nsrl.txt-md5.idx")
    
  knownBadPath = knownBadPath.replace("/", "\\")
  keywordPath = keywordPath.replace("/", "\\")
  nsrlPath = nsrlPath.replace("/", "\\")

  antlog = os.path.join(cwd,outDir,testCaseName,"antlog.txt")
  antlog = antlog.replace("/", "\\")

  timeout = 24 * 60 * 60 * 1000    # default of 24 hours, just to be safe
  size = getImageSize(inFile, list)      # get the size in bytes
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

def getImageSize(inFile, list):
  name = imageName(inFile)
  size = 0
  if list:
    size += os.path.getsize(inFile)
  else: 
    path = os.path.join(".",inDir)
    
    for files in os.listdir(path):
        filename = os.path.splitext(files)[0]
        if filename == name:
            filepath = os.path.join(path, files)
            if not os.path.samefile(filepath, inFile):
                size += os.path.getsize(filepath)
    size += os.path.getsize(inFile)
  return size

def testCompareToGold(inFile, ignoreUnalloc, list):
  print "-----------------------------------------------"
  print "Comparing results for " + inFile + " with gold."

  name = imageName(inFile)
  if ignoreUnalloc:
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

def clearGoldDir(inFile, ignoreUnalloc, list):
  cwd = wgetcwd()
  inFile = imageName(inFile)
  if ignoreUnalloc:
    inFile += "-u"
  if list:
    inFile += "-l"
  if os.path.exists(os.path.join(cwd,goldDir,inFile)):
    shutil.rmtree(os.path.join(cwd,goldDir,inFile))
  os.makedirs(os.path.join(cwd,goldDir,inFile))

def copyTestToGold(inFile, ignoreUnalloc, list): 
  print "------------------------------------------------"
  print "Recreating gold standard from results."
  inFile = imageName(inFile)
  if ignoreUnalloc:
    inFile += "-u"
  if list:
    inFile += "-l"
  cwd = wgetcwd()
  goldFile = os.path.join("./",goldDir,inFile,"standard.db")
  testFile = os.path.join("./",outDir,inFile,"AutopsyTestCase","autopsy.db")
  shutil.copy(testFile, goldFile)

def copyReportToGold(inFile, ignoreUnalloc, list): 
  print "------------------------------------------------"
  print "Recreating gold report from results."
  inFile = imageName(inFile)
  if ignoreUnalloc:
    inFile += "-u"
  if list:
    inFile += "-l"
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

def testCompareReports(inFile, ignoreUnalloc, list):
  print "------------------------------------------------"
  print "Comparing report to golden report."
  name = imageName(inFile)
  if ignoreUnalloc:
    name += "-u"
  if list:
    name += "-l"
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
    pathEnd2 = inFile.rfind("\\")
    extStart = inFile.rfind(".")
    if(extStart == -1 and extStart == -1):
        return inFile
    if(pathEnd2 != -1):
        return inFile[pathEnd2+1:extStart]
    elif(extStart == -1):
        return inFile[pathEnd+1:]
    elif(pathEnd == -1):
        return inFile[:extStart]
    elif(pathEnd!=-1 and extStart!=-1):
        return inFile[pathEnd+1:extStart]
    else:
        return inFile[pathEnd2+1:extStart]

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
    if(inFile[1:2] == ":"):
         proc = subprocess.Popen(("cygpath", "-m", inFile), stdout=subprocess.PIPE)
         out,err = proc.communicate()
    else:
        proc = subprocess.Popen(("cygpath", "-m", os.path.abspath(inFile)), stdout=subprocess.PIPE)
        out,err = proc.communicate()
    return out.rstrip()

def copyLogs(inFile, ignoreUnalloc, list):
  name = imageName(inFile)
  if ignoreUnalloc:
   name +="-u"
  if list:
    name+="-l"
  logDir = os.path.join("..","build","test","qa-functional","work","userdir0","var","log")
  shutil.copytree(logDir,os.path.join(outDir,name,"logs"))

def testFile(image, rebuild, ignoreUnalloc, list):
  if imageType(image) != ImgType.UNKNOWN:
    testAddImageIngest(image, ignoreUnalloc, list)
    copyLogs(image, ignoreUnalloc, list)
    if rebuild:
      clearGoldDir(image, ignoreUnalloc, list)
      copyTestToGold(image, ignoreUnalloc, list)
      copyReportToGold(image, ignoreUnalloc, list)
    else:
      testCompareToGold(image, ignoreUnalloc, list)
      testCompareReports(image, ignoreUnalloc, list)
      
def usage():
  usage = "\
  Usage: ./regression.py [-f FILE] [OPTIONS] \n\n\
  Run the RegressionTest.java file, and compare the result with a gold standard \n\n\
  When the -f flag is set, this script only tests the image given by FILE.\n\
  By default, it tests every image in ./input/\n\n\
  An indexed NSRL database is expected at ./input/nsrl.txt-md5.idx,\n\
  and an indexed notable hash database at ./input/notablehashes.txt-md5.idx\n\
  In addition, any keywords to search for must be in ./input/notablekeywords.xml\n\n\
  When the -l flag is set, the script looks for a config.xml file of the given name\n\
  where images are stored. The above input files may be outsources to a different folder\n\
  via the config file. For usage notes please see the example \"config.xml\" in\n\
  the /script folder.\
    Options:\n\n\
    -r, --rebuild\t\tRebuild the gold standards from the test results for each image.\n\n\
    -i, --ignore\t\tIgnores the ./input directory when searching for files. ONLY use in combinatin with a config file.\n\n\
    -u, --unallocated\t\tIgnores unallocated space when ingesting. Faster, but less accurate results."
    
  return usage

def main():
  rebuild = False
  single = False
  ignoreInput = False
  ignoreUnalloc = False
  list = False
  test = True
  argi = 1
  Config = None   #file pointed to by --list
  imgListB = []   #list of legal images from config
  cwd = wgetcwd()
  while argi < len(sys.argv):
      arg = sys.argv[argi]
      if arg == "-f" and argi+1 < len(sys.argv): #check for single
          single = True
          test = False
          argi+=1
          image = sys.argv[argi]
          print "Running on single image: " + image
      elif arg == "-l" or arg == "--list":    #check for config file
            list = True
            
            argi+=1
            #check for file in ./
            if(os.path.isfile(os.path.join("./", sys.argv[argi]))):
                 Config = parse(os.path.join("./", sys.argv[argi]))
            #else check if it is a specified path
            elif (os.path.exists(wabspath(sys.argv[argi]))):
                Config = parse(sys.argv[argi])
            else:
                print sys.argv[argi]
                print wabspath(sys.argv[argi])
                markError("Ran with " + arg +" but no such file exists", arg)
      elif (arg  == "--rebuild") or (arg == "-r"):  #check for rebuild flag
          rebuild = True
          print "Running in REBUILD mode"
      elif (arg == "-u") or (arg == "--unallocated"):    #check for ignore unallocated space flag
          ignoreUnalloc = True
          print "Ignoring unallocated space"
      elif (arg == "--ignore") or (arg == "-i"):
          ignoreInput = True
          print "Ignoring /script/input directory"
      else:
          test = False
          print usage()
      argi+=1
  if single:
    testFile(image, rebuild, ignoreUnalloc, list)
  if list:
    listImages = []
    errors = 0
    global inDir
    out = Config.getElementsByTagName("indir")[0].getAttribute("value").encode() #there should only be one indir element in the config
    inDir = out
    for element in Config.getElementsByTagName("image"):
        elem = element.getAttribute("value").encode()
        proc2 = subprocess.Popen(("cygpath", "-u", elem), stdout=subprocess.PIPE)
        out2,err = proc2.communicate()
        out2 = out2.rstrip()
        if(os.path.exists(out2) and os.path.isfile(out2)):
            listImages.append(elem)
        else:
            print out2 + " is not a valid path or is not an image"
            errors+=1
    print "Illegal files specified: " + str(errors)
    for image in listImages:
        testFile(image, rebuild, ignoreUnalloc, list)
    if not ignoreInput:
        global inDir
        inDir = os.path.join(cwd, "input")
        test = True
  if test:
    for inFile in os.listdir(inDir):
      testFile(os.path.join(inDir,inFile), rebuild, ignoreUnalloc, list)

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
