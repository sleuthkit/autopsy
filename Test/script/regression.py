#!/usr/bin/python
# -*- coding: utf_8 -*- 
import codecs
import datetime
import logging
import os
import re
import shutil
import socket
import sqlite3
import subprocess
import sys
from sys import platform as _platform
import time
import traceback
import xml
from time import localtime, strftime
from xml.dom.minidom import parse, parseString
import smtplib
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.MIMEBase import MIMEBase
from email import Encoders
import urllib2
import re
import zipfile
import zlib

#
# Please read me...
#
# This is the regression testing Python script.
# It uses an ant command to run build.xml for RegressionTest.java
#
# The code is cleanly sectioned and commented.
# Please follow the current formatting.
# It is a long and potentially confusing script.
#
# Variable, function, and class names are written in Python conventions:
# this_is_a_variable	this_is_a_function()	ThisIsAClass
#
# All variables that are needed throughout the script have been initialized
# in a global class.
# - Command line arguments are in Args (named args)
# - Information pertaining to each test is in TestAutopsy (named case)
# - Queried information from the databases is in Database (named database)
# Feel free to add additional global classes or add to the existing ones,
# but do not overwrite any existing variables as they are used frequently.
#

Day = 0

#-------------------------------------------------------------#
# Parses argv and stores booleans to match command line input #
#-------------------------------------------------------------#
class Args:
	def __init__(self):
		self.single = False
		self.single_file = ""
		self.rebuild = False
		self.list = False
		self.config_file = ""
		self.unallocated = False
		self.ignore = False
		self.keep = False
		self.verbose = False
		self.exception = False
		self.exception_string = ""
		self.contin = False
		self.gold_creation = False
	
	def parse(self):
		global nxtproc 
		nxtproc = []
		nxtproc.append("python")
		nxtproc.append(sys.argv.pop(0))
		while sys.argv:
			arg = sys.argv.pop(0)
			nxtproc.append(arg)
			if(arg == "-f"):
				try:
					arg = sys.argv.pop(0)
					printout("Running on a single file:")
					printout(path_fix(arg) + "\n")
					self.single = True
					self.single_file = path_fix(arg)
				except:
					printerror("Error: No single file given.\n")
					return False
			elif(arg == "-r" or arg == "--rebuild"):
				printout("Running in rebuild mode.\n")
				self.rebuild = True
			elif(arg == "-l" or arg == "--list"):
				try:
					arg = sys.argv.pop(0)
					nxtproc.append(arg)
					printout("Running from configuration file:")
					printout(arg + "\n")
					self.list = True
					self.config_file = arg
				except:
					printerror("Error: No configuration file given.\n")
					return False
			elif(arg == "-u" or arg == "--unallocated"):
			   printout("Ignoring unallocated space.\n")
			   self.unallocated = True
			elif(arg == "-i" or arg == "--ignore"):
				printout("Ignoring the ../input directory.\n")
				self.ignore = True
			elif(arg == "-k" or arg == "--keep"):
				printout("Keeping the Solr index.\n")
				self.keep = True
			elif(arg == "-v" or arg == "--verbose"):
				printout("Running in verbose mode:")
				printout("Printing all thrown exceptions.\n")
				self.verbose = True
			elif(arg == "-e" or arg == "--exception"):
				try:
					arg = sys.argv.pop(0)
					nxtproc.append(arg)
					printout("Running in exception mode: ")
					printout("Printing all exceptions with the string '" + arg + "'\n")
					self.exception = True
					self.exception_string = arg
				except:
					printerror("Error: No exception string given.")
			elif arg == "-h" or arg == "--help":
				printout(usage())
				return False
			elif arg == "-c" or arg == "--continuous":
				printout("Running until interrupted")
				self.contin = True
			elif arg == "-g" or arg == "--gold":
				printout("Creating gold standards")
				self.gold_creation = True
			else:
				printout(usage())
				return False
		# Return the args were sucessfully parsed
		return True



#-----------------------------------------------------#
# Holds all global variables for each individual test #
#-----------------------------------------------------#
class TestAutopsy:
	def __init__(self):
		# Paths:
		self.input_dir = make_local_path("..","input")
		self.output_dir = ""
		self.gold = make_local_path("..", "output", "gold", "tmp")
		# Logs:
		self.antlog_dir = ""
		self.common_log = ""
		self.sorted_log = ""
		self.common_log_path = ""
		self.warning_log = ""
		self.csv = ""
		self.global_csv = ""
		self.html_log = ""
		# Error tracking
		self.printerror = []
		self.printout = []
		self.report_passed = False
		# Image info:
		self.image_file = ""
		self.image_name = ""
		# Ant info:
		self.known_bad_path = ""
		self.keyword_path = ""
		self.nsrl_path = ""
		self.build_path = ""
		# Case info
		self.start_date = ""
		self.end_date = ""
		self.total_test_time = ""
		self.total_ingest_time = ""
		self.autopsy_version = ""
		self.heap_space = ""
		self.service_times = ""
		self.ingest_messages = 0
		self.indexed_files = 0
		self.indexed_chunks = 0
		# Infinite Testing info
		timer = 0
		
		# Set the timeout to something huge
		# The entire tester should not timeout before this number in ms
		# However it only seems to take about half this time
		# And it's very buggy, so we're being careful
		self.timeout = 24 * 60 * 60 * 1000 * 1000
		self.ant = []
	  
	def get_image_name(self, image_file):
		path_end = image_file.rfind("/")
		path_end2 = image_file.rfind("\\")
		ext_start = image_file.rfind(".")
		if(ext_start == -1):
			name = image_file
		if(path_end2 != -1):
			name = image_file[path_end2+1:ext_start]
		elif(ext_start == -1):
			name = image_file[path_end+1:]
		elif(path_end == -1):
			name = image_file[:ext_start]
		elif(path_end!=-1 and ext_start!=-1):
			name = image_file[path_end+1:ext_start]
		else:
			name = image_file[path_end2+1:ext_start]
		return name
		
	def ant_to_string(self):
		string = ""
		for arg in self.ant:
			string += (arg + " ")
		return string	

	 
		
	def reset(self):
		# Logs:
		self.antlog_dir = ""
		# Error tracking
		self.printerror = []
		self.printout = []
		self.report_passed = False
		# Image info:
		self.image_file = ""
		self.image_name = ""
		# Ant info:
		self.known_bad_path = ""
		self.keyword_path = ""
		self.nsrl_path = ""
		# Case info
		self.start_date = ""
		self.end_date = ""
		self.total_test_time = ""
		self.total_ingest_time = ""
		self.heap_space = ""
		self.service_times = ""
		
		# Set the timeout to something huge
		# The entire tester should not timeout before this number in ms
		# However it only seems to take about half this time
		# And it's very buggy, so we're being careful
		self.timeout = 24 * 60 * 60 * 1000 * 1000
		self.ant = []
	   



#---------------------------------------------------------#
# Holds all database information from querying autopsy.db #
#  and standard.db. Initialized when the autopsy.db file  #
#		  is compared to the gold standard.			  #
#---------------------------------------------------------#
class Database:
	def __init__(self):
		self.gold_artifacts = []
		self.autopsy_artifacts = []
		self.gold_attributes = 0
		self.autopsy_attributes = 0
		self.gold_objects = 0
		self.autopsy_objects = 0
		self.artifact_comparison = []
		self.attribute_comparison = []
		
	def clear(self):
		self.gold_artifacts = []
		self.autopsy_artifacts = []
		self.gold_attributes = 0
		self.autopsy_attributes = 0
		self.gold_objects = 0
		self.autopsy_objects = 0
		self.artifact_comparison = []
		self.attribute_comparison = []
		
	def get_artifacts_count(self):
		total = 0
		for nums in self.autopsy_artifacts:
			total += nums
		return total
		
	def get_artifact_comparison(self):
		if not self.artifact_comparison:
			return "All counts matched"
		else:
			global failedbool
			global errorem
			failedbool = True
			errorem += "There was a difference in the number of artifacts for " + case.image + ".\n"
			return "; ".join(self.artifact_comparison)
		
	def get_attribute_comparison(self):
		if not self.attribute_comparison:
			return "All counts matched"
		global failedbool
		global errorem
		failedbool = True
		errorem += "There was a difference in the number of attributes for " + case.image + ".\n"
		list = []
		for error in self.attribute_comparison:
			list.append(error)
		return ";".join(list)
		
	def generate_autopsy_artifacts(self):
		if not self.autopsy_artifacts:
			autopsy_db_file = make_path(case.output_dir, case.image_name,
										  "AutopsyTestCase", "autopsy.db")
			autopsy_con = sqlite3.connect(autopsy_db_file)
			autopsy_cur = autopsy_con.cursor()
			autopsy_cur.execute("SELECT COUNT(*) FROM blackboard_artifact_types")
			length = autopsy_cur.fetchone()[0] + 1
			for type_id in range(1, length):
				autopsy_cur.execute("SELECT COUNT(*) FROM blackboard_artifacts WHERE artifact_type_id=%d" % type_id)
				self.autopsy_artifacts.append(autopsy_cur.fetchone()[0])
			autopsy_cur.execute("SELECT * FROM blackboard_artifacts")
			self.autopsy_artifacts_list = []
			for row in autopsy_cur.fetchall():
				for item in row:
					self.autopsy_artifacts_list.append(item)

				
	def generate_autopsy_attributes(self):
		if self.autopsy_attributes == 0:
			autopsy_db_file = make_path(case.output_dir, case.image_name,
										  "AutopsyTestCase", "autopsy.db")
			autopsy_con = sqlite3.connect(autopsy_db_file)
			autopsy_cur = autopsy_con.cursor()
			autopsy_cur.execute("SELECT COUNT(*) FROM blackboard_attributes")
			autopsy_attributes = autopsy_cur.fetchone()[0]
			self.autopsy_attributes = autopsy_attributes

	def generate_autopsy_objects(self):
		if self.autopsy_objects == 0:
			autopsy_db_file = make_path(case.output_dir, case.image_name,
										  "AutopsyTestCase", "autopsy.db")
			autopsy_con = sqlite3.connect(autopsy_db_file)
			autopsy_cur = autopsy_con.cursor()
			autopsy_cur.execute("SELECT COUNT(*) FROM tsk_objects")
			autopsy_objects = autopsy_cur.fetchone()[0]
			self.autopsy_objects = autopsy_objects
		
	def generate_gold_artifacts(self):
		if not self.gold_artifacts:
			gold_db_file = make_path(case.gold, case.image_name, "autopsy.db")
			gold_con = sqlite3.connect(gold_db_file)
			gold_cur = gold_con.cursor()
			gold_cur.execute("SELECT COUNT(*) FROM blackboard_artifact_types")
			length = gold_cur.fetchone()[0] + 1
			for type_id in range(1, length):
				gold_cur.execute("SELECT COUNT(*) FROM blackboard_artifacts WHERE artifact_type_id=%d" % type_id)
				self.gold_artifacts.append(gold_cur.fetchone()[0])
			gold_cur.execute("SELECT * FROM blackboard_artifacts")
			self.gold_artifacts_list = []
			for row in gold_cur.fetchall():
				for item in row:
					self.gold_artifacts_list.append(item)
				
	def generate_gold_attributes(self):
		if self.gold_attributes == 0:
			gold_db_file = make_path(case.gold, case.image_name, "autopsy.db")
			gold_con = sqlite3.connect(gold_db_file)
			gold_cur = gold_con.cursor()
			gold_cur.execute("SELECT COUNT(*) FROM blackboard_attributes")
			self.gold_attributes = gold_cur.fetchone()[0]

	def generate_gold_objects(self):
		if self.gold_objects == 0:
			gold_db_file = make_path(case.gold, case.image_name, "autopsy.db")
			gold_con = sqlite3.connect(gold_db_file)
			gold_cur = gold_con.cursor()
			gold_cur.execute("SELECT COUNT(*) FROM tsk_objects")
			self.gold_objects = gold_cur.fetchone()[0]



#----------------------------------#
#	  Main testing functions	  #
#----------------------------------#



# Iterates through an XML configuration file to find all given elements		
def run_config_test(config_file):
	try:
		global parsed
		parsed = parse(config_file)
		counts = {}
		if parsed.getElementsByTagName("indir"):
			case.input_dir = parsed.getElementsByTagName("indir")[0].getAttribute("value").encode().decode("utf_8")
		if parsed.getElementsByTagName("global_csv"):
			case.global_csv = parsed.getElementsByTagName("global_csv")[0].getAttribute("value").encode().decode("utf_8")
		
		# Generate the top navbar of the HTML for easy access to all images
		case.global_csv = make_local_path(case.global_csv)
		values = []
		for element in parsed.getElementsByTagName("image"):
			value = element.getAttribute("value").encode().decode("utf_8")
			if file_exists(value):
				values.append(value)
		html_add_images(values)
		images = []
		# Run the test for each file in the configuration
		global args
		if(args.contin):
			#set all times an image has been processed to 0
			for element in parsed.getElementsByTagName("image"):
				value = element.getAttribute("value").encode().decode("utf_8")
				images.append(value)
			#Begin infiniloop
			if(newDay()):
				global daycount
				compile()
				if(daycount > 0):
					print("starting process")
					outputer = open("ScriptLog.txt", "a")
					pid = subprocess.Popen(nxtproc,
					stdout = outputer,
					stderr = outputer)
					sys.exit()
				daycount += 1
			for img in images:
				run_test(img, 0 )
		else:
			for img in values:  
				if file_exists(img):
					run_test(img, 0)
				else:
					printerror("Warning: Image file listed in configuration does not exist:")
					printrttot(value + "\n")
		   
	except Exception as e:
		printerror("Error: There was an error running with the configuration file.")
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())
		
def compile():
	global redo
	global tryredo
	global daycount
	global nxtproc
	global failedbool
	global errorem
	global attachl
	global passed
	passed = True
	tryredo = False
	setDay()
	redo = True
	while(redo):
		passed = True
		if(passed):
			gitPull("sleuthkit")
		if(passed):
			vsBuild()
		if(passed):
			gitPull("autopsy")
		if(passed):
			antBuild("datamodel", False)
		if(passed):
			antBuild("autopsy", True)
		if(passed):
			redo = False
		else:
			print("Compile Failed")
			time.sleep(3600)
	attachl = []
	errorem = "The test standard didn't match the gold standard.\n"
	failedbool = False
	if(tryredo):
		errorem += "Rebuilt properly.\n"
		send_email()
		attachl = []
		errorem = "The test standard didn't match the gold standard.\n"	
		passed = True

# Runs the test on the single given file.
# The path must be guarenteed to be a correct path.
def run_test(image_file, count):
	global parsed
	if image_type(image_file) == IMGTYPE.UNKNOWN:
		printerror("Error: Image type is unrecognized:")
		printerror(image_file + "\n")
		return
		
	# Set the case to work for this test
	case.image_file = image_file
	case.image_name = case.get_image_name(image_file) + "(" + str(count) + ")"
	case.image = case.get_image_name(image_file)
	case.common_log_path = make_local_path(case.output_dir, case.image_name, case.image_name+case.common_log)
	case.warning_log = make_local_path(case.output_dir, case.image_name, "AutopsyLogs.txt")
	case.antlog_dir = make_local_path(case.output_dir, case.image_name, "antlog.txt")
	if(args.list):
		element = parsed.getElementsByTagName("build")
		if(len(element)<=0):
			toval = make_path("..", "build.xml")
		else:
			element = element[0]
			toval = element.getAttribute("value").encode().decode("utf_8")
			if(toval==None):
				toval = make_path("..", "build.xml")
	else:
		toval = make_path("..", "build.xml")
	case.build_path = toval	
	case.known_bad_path = make_path(case.input_dir, "notablehashes.txt-md5.idx")
	case.keyword_path = make_path(case.input_dir, "notablekeywords.xml")
	case.nsrl_path = make_path(case.input_dir, "nsrl.txt-md5.idx")
	
	logging.debug("--------------------")
	logging.debug(case.image_name)
	logging.debug("--------------------")
	run_ant()
	time.sleep(2) # Give everything a second to process
	
	# After the java has ran:
	copy_logs()
	generate_common_log()
	try:
		fill_case_data()
	except Exception as e:
		printerror("Error: Unknown fatal error when filling case data.")
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())
	# If NOT keeping Solr index (-k)
	if not args.keep:
		solr_index = make_local_path(case.output_dir, case.image_name, "AutopsyTestCase", "KeywordSearch")
		if clear_dir(solr_index):
			print_report([], "DELETE SOLR INDEX", "Solr index deleted.")
	elif args.keep:
		print_report([], "KEEP SOLR INDEX", "Solr index has been kept.")
	# If running in verbose mode (-v)
	if args.verbose:
		errors = report_all_errors()
		okay = "No warnings or errors in any log files."
		print_report(errors, "VERBOSE", okay)
	# If running in exception mode (-e)
	if args.exception:
		exceptions = search_logs(args.exception_string)
		okay = "No warnings or exceptions found containing text '" + args.exception_string + "'."
		print_report(exceptions, "EXCEPTION", okay)
		
	# Now test in comparison to the gold standards
	if not args.gold_creation:
		try:
			gold_path = case.gold
			img_gold = make_path(case.gold, case.image_name)
			img_archive = make_local_path("..", "output", "gold", case.image_name+"-archive.zip")
			extrctr = zipfile.ZipFile(img_archive, 'r', compression=zipfile.ZIP_DEFLATED)
			print(os.getcwd())
			print(img_archive)
			print(zipfile.is_zipfile(img_archive))
			extrctr.extractall()
			extrctr.close
			print("Here")
			time.sleep(60)
			compare_to_gold_db()
			compare_to_gold_html()
			compare_errors()
			del_dir(img_gold)
		except:
			print("Tests failed due to an error, try rebuilding or creating gold standards.\n")
	# Make the CSV log and the html log viewer
	generate_csv(case.csv)
	if case.global_csv:
		generate_csv(case.global_csv)
	generate_html()
	# If running in rebuild mode (-r)
	if args.rebuild or args.gold_creation:
		rebuild()
	# Reset the case and return the tests sucessfully finished
	clear_dir(make_path(case.output_dir, case.image_name, "AutopsyTestCase", "ModuleOutput", "keywordsearch"))
	case.reset()
	return True

# Tests Autopsy with RegressionTest.java by by running
# the build.xml file through ant
def run_ant():
	# Set up the directories
	test_case_path = os.path.join(case.output_dir, case.image_name)
	if dir_exists(test_case_path):
		shutil.rmtree(test_case_path)
	os.makedirs(test_case_path)
	case.ant = ["ant"]
	case.ant.append("-v")
	case.ant.append("-f")
#	case.ant.append(case.build_path)
	case.ant.append(os.path.join("..","..","Testing","build.xml"))
	case.ant.append("regression-test")
	case.ant.append("-l")
	case.ant.append(case.antlog_dir)
	case.ant.append("-Dimg_path=" + case.image_file)
	case.ant.append("-Dknown_bad_path=" + case.known_bad_path)
	case.ant.append("-Dkeyword_path=" + case.keyword_path)
	case.ant.append("-Dnsrl_path=" + case.nsrl_path)
	case.ant.append("-Dgold_path=" + make_path(case.gold))
	case.ant.append("-Dout_path=" + make_local_path(case.output_dir, case.image_name))
	case.ant.append("-Dignore_unalloc=" + "%s" % args.unallocated)
	case.ant.append("-Dcontin_mode=" + str(args.contin))
	case.ant.append("-Dtest.timeout=" + str(case.timeout))
	
	printout("Ingesting Image:\n" + case.image_file + "\n")
	printout("CMD: " + " ".join(case.ant))
	printout("Starting test...\n")
	antoutpth = make_local_path(case.output_dir, "antRunOutput.txt")
	antout = open(antoutpth, "a")
	if SYS is OS.CYGWIN:
		subprocess.call(case.ant, stdout=antout)
	elif SYS is OS.WIN:
		theproc = subprocess.Popen(case.ant, shell = True, stdout=subprocess.PIPE)
		theproc.communicate()
	antout.close()
	
# Returns the type of image file, based off extension
class IMGTYPE:
  RAW, ENCASE, SPLIT, UNKNOWN = range(4)
def image_type(image_file):
  ext_start = image_file.rfind(".")
  if (ext_start == -1):
	return IMGTYPE.UNKNOWN
  ext = image_file[ext_start:].lower()
  if (ext == ".img" or ext == ".dd"):
	return IMGTYPE.RAW
  elif (ext == ".e01"):
	return IMGTYPE.ENCASE
  elif (ext == ".aa" or ext == ".001"):
	return IMGTYPE.SPLIT
  else:
	return IMGTYPE.UNKNOWN



#-----------------------------------------------------------#
#	  Functions relating to rebuilding and comparison	  #
#				   of gold standards					   #
#-----------------------------------------------------------#

# Rebuilds the gold standards by copying the test-generated database
# and html report files into the gold directory
def rebuild():
	# Errors to print
	errors = []
	# Delete the current gold standards
	gold_dir = make_path(case.gold, case.image_name)
	clear_dir(gold_dir)
	dbinpth = make_path(case.output_dir, case.image_name, "AutopsyTestCase", "autopsy.db")
	dboutpth = make_path(case.gold, case.image_name, "autopsy.db")
	if not os.path.exists(gold_dir):
		os.makedirs(gold_dir)
	copy_file(dbinpth, dboutpth)
	error_pth = make_path(case.gold, case.image_name, case.image_name+"SortedErrors.txt")
	copy_file(case.sorted_log, error_pth)
	# Rebuild the HTML report
	htmlfolder = ""
	for fs in os.listdir(os.path.join(os.getcwd(),case.output_dir, case.image_name, "AutopsyTestCase", "Reports")):
		if os.path.isdir(os.path.join(os.getcwd(), case.output_dir, case.image_name, "AutopsyTestCase", "Reports", fs)):
			htmlfolder = fs
	autopsy_html_path = make_local_path(case.output_dir, case.image_name, "AutopsyTestCase", "Reports", htmlfolder)
	
	html_path = make_path(case.output_dir, case.image_name,
								 "AutopsyTestCase", "Reports")
	try:
		os.makedirs(os.path.join(case.gold, case.image_name, htmlfolder))
		for file in os.listdir(autopsy_html_path):
			html_to = make_path(case.gold, case.image_name, file.replace("HTML Report", "Report"))
			copy_dir(get_file_in_dir(autopsy_html_path, file), html_to)
	except FileNotFoundException as e:
		errors.append(e.error)
	except Exception as e:
		errors.append("Error: Unknown fatal error when rebuilding the gold html report.")
		errors.append(str(e) + "\n")
	oldcwd = os.getcwd()
	zpdir = make_path(case.gold, "..")
	os.chdir(zpdir)
	img_archive = make_path(case.image_name+"-archive.zip")
	img_gold = make_path("tmp", case.image_name)
	comprssr = zipfile.ZipFile(img_archive, 'w',compression=zipfile.ZIP_DEFLATED)
	zipdir(img_gold, comprssr)
	comprssr.close()
	del_dir(gold_dir)
	os.chdir(oldcwd)
	okay = "Sucessfully rebuilt all gold standards."
	print_report(errors, "REBUILDING", okay)

def zipdir(path, zip):
    for root, dirs, files in os.walk(path):
        for file in files:
            zip.write(os.path.join(root, file))

# Using the global case's variables, compare the database file made by the
# regression test to the gold standard database file
# Initializes the global database, which stores the information retrieved
# from queries while comparing
def compare_to_gold_db():
	# SQLITE needs unix style pathing
	gold_db_file = make_path(case.gold, case.image_name, "autopsy.db")
	autopsy_db_file = make_path(case.output_dir, case.image_name,
									  "AutopsyTestCase", "autopsy.db")
	# Try to query the databases. Ignore any exceptions, the function will
	# return an error later on if these do fail
	database.clear()
	try:
		database.generate_gold_objects()
		database.generate_gold_artifacts()
		database.generate_gold_attributes()
	except:
		pass
	try:
		database.generate_autopsy_objects()
		database.generate_autopsy_artifacts()
		database.generate_autopsy_attributes()
	except:
		pass
	# This is where we return if a file doesn't exist, because we don't want to
	# compare faulty databases, but we do however want to try to run all queries
	# regardless of the other database
	if not file_exists(autopsy_db_file):
		printerror("Error: Database file does not exist at:")
		printerror(autopsy_db_file + "\n")
		return
	if not file_exists(gold_db_file):
		printerror("Error: Gold database file does not exist at:")
		printerror(gold_db_file + "\n")
		return
		
	# compare size of bb artifacts, attributes, and tsk objects
	gold_con = sqlite3.connect(gold_db_file)
	gold_cur = gold_con.cursor()
	autopsy_con = sqlite3.connect(autopsy_db_file)
	autopsy_cur = autopsy_con.cursor()
	
	exceptions = []
	# Testing tsk_objects
	exceptions.append(compare_tsk_objects())
	# Testing blackboard_artifacts
	exceptions.append(compare_bb_artifacts())
	# Testing blackboard_attributes
	exceptions.append(compare_bb_attributes())
	
	database.artifact_comparison = exceptions[1]
	database.attribute_comparison = exceptions[2]
	
	okay = "All counts match."
	print_report(exceptions[0], "COMPARE TSK OBJECTS", okay)
	print_report(exceptions[1], "COMPARE ARTIFACTS", okay)
	print_report(exceptions[2], "COMPARE ATTRIBUTES", okay)
	
# Using the global case's variables, compare the html report file made by
# the regression test against the gold standard html report
def compare_to_gold_html():
	gold_html_file = make_path(case.gold, case.image_name, "Report", "index.html")
	htmlfolder = ""
	for fs in os.listdir(make_path(case.output_dir, case.image_name, "AutopsyTestCase", "Reports")):
		if os.path.isdir(make_path(case.output_dir, case.image_name, "AutopsyTestCase", "Reports", fs)):
			htmlfolder = fs
	autopsy_html_path = make_path(case.output_dir, case.image_name, "AutopsyTestCase", "Reports", htmlfolder, "HTML Report") #, "AutopsyTestCase", "Reports", htmlfolder)
	
	
	try:
		autopsy_html_file = get_file_in_dir(autopsy_html_path, "index.html")
		if not file_exists(gold_html_file):
			printerror("Error: No gold html report exists at:")
			printerror(gold_html_file + "\n")
			return
		if not file_exists(autopsy_html_file):
			printerror("Error: No case html report exists at:")
			printerror(autopsy_html_file + "\n")
			return
		#Find all gold .html files belonging to this case
		ListGoldHTML = []
		for fs in os.listdir(make_path(case.output_dir, case.image_name, "AutopsyTestCase", "Reports", htmlfolder)):
			if(fs.endswith(".html")):
				ListGoldHTML.append(os.path.join(case.output_dir, case.image_name, "AutopsyTestCase", "Reports", htmlfolder, fs))
		#Find all new .html files belonging to this case
		ListNewHTML = []
		for fs in os.listdir(make_path(case.gold, case.image_name)):
			if (fs.endswith(".html")):
				ListNewHTML.append(make_path(case.gold, case.image_name, fs))
		#ensure both reports have the same number of files and are in the same order
		if(len(ListGoldHTML) != len(ListNewHTML)):
			printerror("The reports did not have the same number of files. One of the reports may have been corrupted")
		else:
			ListGoldHTML.sort()
			ListNewHTML.sort()
		  
		total = {"Gold": 0, "New": 0}
		for x in range(0, len(ListGoldHTML)):
			count = compare_report_files(ListGoldHTML[x], ListNewHTML[x])
			total["Gold"]+=count[0]
			total["New"]+=count[1]
		okay = "The test report matches the gold report."
		errors=["Gold report had " + str(total["Gold"]) +" errors", "New report had " + str(total["New"]) + " errors."]
		print_report(errors, "REPORT COMPARISON", okay)
		if total["Gold"] == total["New"]:
			case.report_passed = True
		else:
			printerror("The reports did not match each other.\n " + errors[0] +" and the " + errors[1])
	except FileNotFoundException as e:
		e.print_error()
	except DirNotFoundException as e:
		e.print_error()
	except Exception as e:
		printerror("Error: Unknown fatal error comparing reports.")
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())

# Compares the blackboard artifact counts of two databases
# given the two database cursors
def compare_bb_artifacts():
	exceptions = []
	try:
		for type_id in range(1, 13):
			if database.gold_artifacts != database.autopsy_artifacts:
				error = str("Artifact counts do not match for type id %d. " % type_id)
				error += str("Gold: %d, Test: %d" %
							(database.gold_artifacts[type_id],
							 database.autopsy_artifacts[type_id]))
				exceptions.append(error)
				global failedbool
				global errorem
				failedbool = True
				errorem += "There was a difference in the number of artifacts for " + case.image + ".\n"
				return exceptions
	except Exception as e:
		exceptions.append("Error: Unable to compare blackboard_artifacts.\n")
		return exceptions

# Compares the blackboard atribute counts of two databases
# given the two database cursors
def compare_bb_attributes():
	exceptions = []
	try:
		if database.gold_attributes != database.autopsy_attributes:
			error = "Attribute counts do not match. "
			error += str("Gold: %d, Test: %d" % (database.gold_attributes, database.autopsy_attributes))
			exceptions.append(error)
			global failedbool
			global errorem
			failedbool = True
			errorem += "There was a difference in the number of attributes for " + case.image + ".\n"
			return exceptions
	except Exception as e:
		exceptions.append("Error: Unable to compare blackboard_attributes.\n")
		return exceptions

# Compares the tsk object counts of two databases
# given the two database cursors
def compare_tsk_objects():
	exceptions = []
	try:
		if database.gold_objects != database.autopsy_objects:
			error = "TSK Object counts do not match. "
			error += str("Gold: %d, Test: %d" % (database.gold_objects, database.autopsy_objects))
			exceptions.append(error)
			global failedbool
			global errorem
			failedbool = True
			errorem += "There was a difference between the tsk object counts for " + case.image + " .\n"
			return exceptions
	except Exception as e:
		exceptions.append("Error: Unable to compare tsk_objects.\n")
		return exceptions



#-------------------------------------------------#
#	  Functions relating to error reporting	  #
#-------------------------------------------------#	  

# Generate the "common log": a log of all exceptions and warnings
# from each log file generated by Autopsy
def generate_common_log():
	try:
		logs_path = make_local_path(case.output_dir, case.image_name, "logs")
		common_log = codecs.open(case.common_log_path, "w", "utf_8")
		warning_log = codecs.open(case.warning_log, "w", "utf_8")
		common_log.write("--------------------------------------------------\n")
		common_log.write(case.image_name + "\n")
		common_log.write("--------------------------------------------------\n")
		rep_path = make_local_path(case.output_dir)
		rep_path = rep_path.replace("\\\\", "\\")
		for file in os.listdir(logs_path):
			log = codecs.open(make_path(logs_path, file), "r", "utf_8")
			for line in log:
				line = line.replace(rep_path, "CASE")
				if line.startswith("Exception"):
					common_log.write("From " + file +":\n" +  line + "\n")
				elif line.startswith("WARNING"):
					common_log.write("From " + file +":\n" +  line + "\n")
				elif line.startswith("Error"):
					common_log.write("From " + file +":\n" +  line + "\n")
				elif line.startswith("SEVERE"):
					common_log.write("From " + file +":\n" +  line + "\n")
				else:
					warning_log.write("From " + file +":\n" +  line + "\n")
			log.close()
		common_log.write("\n\n")
		common_log.close()
		case.sorted_log = make_local_path(case.output_dir, case.image_name, case.image_name + "SortedErrors.txt")
		srtcmdlst = ["sort", case.common_log_path, "-o", case.sorted_log]
		subprocess.call(srtcmdlst)
	except Exception as e:
		printerror("Error: Unable to generate the common log.")
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())
		
def	compare_errors():
	gold_dir = make_path(case.gold, case.image_name, case.image_name + "SortedErrors.txt")
	common_log = codecs.open(case.sorted_log, "r", "utf_8")
	gold_log = codecs.open(gold_dir, "r", "utf_8")
	gold_dat = gold_log.read()
	common_dat = common_log.read()
	patrn = re.compile("\d")
	if (not((re.sub(patrn, 'd', gold_dat)) == (re.sub(patrn, 'd', common_dat)))):
		diff_dir = make_local_path(case.output_dir, case.image_name, "ErrorDiff.txt")
		diff_file = open(diff_dir, "w") 
		dffcmdlst = ["diff", case.sorted_log, gold_dir]
		subprocess.call(dffcmdlst, stdout = diff_file)
		global attachl
		global errorem
		global failedbool
		attachl.append(diff_dir)
		errorem += "There was a difference in the exceptions Log.\n"
		print("Exceptions didn't match.\n")
		failedbool = True

# Fill in the global case's variables that require the log files
def fill_case_data():
	try:
		# Open autopsy.log.0
		log_path = make_local_path(case.output_dir, case.image_name, "logs", "autopsy.log.0")
		log = open(log_path)
		
		# Set the case starting time based off the first line of autopsy.log.0
		# *** If logging time format ever changes this will break ***
		case.start_date = log.readline().split(" org.")[0]
	
		# Set the case ending time based off the "create" time (when the file was copied)
		case.end_date = time.ctime(os.path.getmtime(log_path))
	except Exception as e:
		printerror("Error: Unable to open autopsy.log.0.")
		printerror(str(e) + "\n")
		logging.warning(traceback.format_exc())
	# Set the case total test time
	# Start date must look like: "Jul 16, 2012 12:57:53 PM"
	# End date must look like: "Mon Jul 16 13:02:42 2012"
	# *** If logging time format ever changes this will break ***
	start = datetime.datetime.strptime(case.start_date, "%b %d, %Y %I:%M:%S %p")
	end = datetime.datetime.strptime(case.end_date, "%a %b %d %H:%M:%S %Y")
	case.total_test_time = str(end - start)

	try:
		# Set Autopsy version, heap space, ingest time, and service times
		
		version_line = search_logs("INFO: Application name: Autopsy, version:")[0]
		case.autopsy_version = get_word_at(version_line, 5).rstrip(",")
		
		case.heap_space = search_logs("Heap memory usage:")[0].rstrip().split(": ")[1]
		
		ingest_line = search_logs("Ingest (including enqueue)")[0]
		case.total_ingest_time = get_word_at(ingest_line, 6).rstrip()
		
		message_line = search_log_set("autopsy", "Ingest messages count:")[0]
		case.ingest_messages = int(message_line.rstrip().split(": ")[2])
		
		files_line = search_log_set("autopsy", "Indexed files count:")[0]
		case.indexed_files = int(files_line.rstrip().split(": ")[2])
		
		chunks_line = search_log_set("autopsy", "Indexed file chunks count:")[0]
		case.indexed_chunks = int(chunks_line.rstrip().split(": ")[2])
	except Exception as e:
		printerror("Error: Unable to find the required information to fill case data.")
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())
	try:
		service_lines = search_log("autopsy.log.0", "to process()")
		service_list = []
		for line in service_lines:
			words = line.split(" ")
			# Kind of forcing our way into getting this data
			# If this format changes, the tester will break
			i = words.index("secs.")
			times = words[i-4] + " "
			times += words[i-3] + " "
			times += words[i-2] + " "
			times += words[i-1] + " "
			times += words[i]
			service_list.append(times)
		case.service_times = "; ".join(service_list)
	except FileNotFoundException as e:
		e.print_error()
	except Exception as e:
		printerror("Error: Unknown fatal error when finding service times.")
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())
	
# Generate the CSV log file
def generate_csv(csv_path):
	try:
		# If the CSV file hasn't already been generated, this is the
		# first run, and we need to add the column names
		if not file_exists(csv_path):
			csv_header(csv_path)
			
		# Now add on the fields to a new row
		csv = open(csv_path, "a")
		
		# Variables that need to be written
		vars = []
		vars.append( case.image_file )
		vars.append( case.image_name )
		vars.append( case.output_dir )
		vars.append( socket.gethostname() )
		vars.append( case.autopsy_version )
		vars.append( case.heap_space )
		vars.append( case.start_date )
		vars.append( case.end_date )
		vars.append( case.total_test_time )
		vars.append( case.total_ingest_time )
		vars.append( case.service_times )
		vars.append( str(len(get_exceptions())) )
		vars.append( str(get_num_memory_errors("autopsy")) )
		vars.append( str(get_num_memory_errors("tika")) )
		vars.append( str(get_num_memory_errors("solr")) )
		vars.append( str(len(search_log_set("autopsy", "TskCoreException"))) )
		vars.append( str(len(search_log_set("autopsy", "TskDataException"))) )
		vars.append( str(case.ingest_messages) )
		vars.append( str(case.indexed_files) )
		vars.append( str(case.indexed_chunks) )
		vars.append( str(len(search_log_set("autopsy", "Stopping ingest due to low disk space on disk"))) )
		vars.append( str(database.autopsy_objects) )
		vars.append( str(database.get_artifacts_count()) )
		vars.append( str(database.autopsy_attributes) )
		vars.append( make_local_path("gold", case.image_name, "autopsy.db") )
		vars.append( database.get_artifact_comparison() )
		vars.append( database.get_attribute_comparison() )
		vars.append( make_local_path("gold", case.image_name, "standard.html") )
		vars.append( str(case.report_passed) )
		vars.append( case.ant_to_string() )
		
		# Join it together with a ", "
		output = "|".join(vars)
		output += "\n"
		# Write to the log!
		csv.write(output)
		csv.close()
	except Exception as e:
		printerror("Error: Unknown fatal error when creating CSV file at:")
		printerror(csv_path)
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())

# Generates the CSV header (column names)
def csv_header(csv_path):
	csv = open(csv_path, "w")
	titles = []
	titles.append("Image Path")
	titles.append("Image Name")
	titles.append("Output Case Directory")
	titles.append("Host Name")
	titles.append("Autopsy Version")
	titles.append("Heap Space Setting")
	titles.append("Test Start Date")
	titles.append("Test End Date")
	titles.append("Total Test Time")
	titles.append("Total Ingest Time")
	titles.append("Service Times")
	titles.append("Autopsy Exceptions")
	titles.append("Autopsy OutOfMemoryErrors/Exceptions")
	titles.append("Tika OutOfMemoryErrors/Exceptions")
	titles.append("Solr OutOfMemoryErrors/Exceptions")
	titles.append("TskCoreExceptions")
	titles.append("TskDataExceptions")
	titles.append("Ingest Messages Count")
	titles.append("Indexed Files Count")
	titles.append("Indexed File Chunks Count")
	titles.append("Out Of Disk Space")
	titles.append("Tsk Objects Count")
	titles.append("Artifacts Count")
	titles.append("Attributes Count")
	titles.append("Gold Database Name")
	titles.append("Artifacts Comparison")
	titles.append("Attributes Comparison")
	titles.append("Gold Report Name")
	titles.append("Report Comparison")
	titles.append("Ant Command Line")
	output = "|".join(titles)
	output += "\n"
	csv.write(output)
	csv.close()
		
# Returns a list of all the exceptions listed in all the autopsy logs
def get_exceptions():
	exceptions = []
	logs_path = make_local_path(case.output_dir, case.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		if "autopsy.log" in file:
			log = codecs.open(make_path(logs_path, file), "r", "utf_8")
			ex = re.compile("\SException")
			er = re.compile("\SError")
			for line in log:
				if ex.search(line) or er.search(line):
					exceptions.append(line)
			log.close()
	return exceptions
	
# Returns a list of all the warnings listed in the common log
def get_warnings():
	warnings = []
	common_log = codecs.open(case.warning_log, "r", "utf_8")
	for line in common_log:
		if "warning" in line.lower():
			warnings.append(line)
	common_log.close()
	return warnings

# Returns all the errors found in the common log in a list
def report_all_errors():
	try:
		return get_warnings() + get_exceptions()
	except Exception as e:
		printerror("Error: Unknown fatal error when reporting all errors.")
		printerror(str(e) + "\n")
		logging.warning(traceback.format_exc())

# Searched all the known logs for the given regex
# The function expects regex = re.compile(...)
def regex_search_logs(regex):
	logs_path = make_local_path(case.output_dir, case.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		log = codecs.open(make_path(logs_path, file), "r", "utf_8")
		for line in log:
			if regex.search(line):
				results.append(line)
		log.close()
	if results:
		return results

# Search through all the known log files for a specific string.
# Returns a list of all lines with that string
def search_logs(string):
	logs_path = make_local_path(case.output_dir, case.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		log = codecs.open(make_path(logs_path, file), "r", "utf_8")
		for line in log:
			if string in line:
				results.append(line)
		log.close()
	return results
	
# Searches the common log for any instances of a specific string.
def search_common_log(string):
	results = []
	log = codecs.open(case.common_log_path, "r", "utf_8")
	for line in log:
		if string in line:
			results.append(line)
	log.close()
	return results

# Searches the given log for the given string
# Returns a list of all lines with that string
def search_log(log, string):
	logs_path = make_local_path(case.output_dir, case.image_name, "logs", log)
	try:
		results = []
		log = codecs.open(logs_path, "r", "utf_8")
		for line in log:
			if string in line:
				results.append(line)
		log.close()
		if results:
			return results
	except:
		raise FileNotFoundException(logs_path)

# Search through all the the logs of the given type
# Types include autopsy, tika, and solr
def search_log_set(type, string):
	logs_path = make_local_path(case.output_dir, case.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		if type in file:
			log = codecs.open(make_path(logs_path, file), "r", "utf_8")
			for line in log:
				if string in line:
					results.append(line)
			log.close()
	return results
		
# Returns the number of OutOfMemoryErrors and OutOfMemoryExceptions
# for a certain type of log
def get_num_memory_errors(type):
	return (len(search_log_set(type, "OutOfMemoryError")) + 
			len(search_log_set(type, "OutOfMemoryException")))

# Print a report for the given errors with the report name as name
# and if no errors are found, print the okay message
def print_report(errors, name, okay):
	if errors:
		printerror("--------< " + name + " >----------")
		for error in errors:
			printerror(str(error))
		printerror("--------< / " + name + " >--------\n")
	else:
		printout("-----------------------------------------------------------------")
		printout("< " + name + " - " + okay + " />")
		printout("-----------------------------------------------------------------\n")

# Used instead of the print command when printing out an error
def printerror(string):
	print(string)
	case.printerror.append(string)

# Used instead of the print command when printing out anything besides errors
def printout(string):
	print(string)
	case.printout.append(string)

# Generates the HTML log file
def generate_html():
	# If the file doesn't exist yet, this is the first case to run for
	# this test, so we need to make the start of the html log
	if not file_exists(case.html_log):
		write_html_head()
	try:
		global html
		html = open(case.html_log, "a")
		# The image title
		title = "<h1><a name='" + case.image_name + "'>" + case.image_name + " \
					<span>tested on <strong>" + socket.gethostname() + "</strong></span></a></h1>\
				 <h2 align='center'>\
				 <a href='#" + case.image_name + "-errors'>Errors and Warnings</a> |\
				 <a href='#" + case.image_name + "-info'>Information</a> |\
				 <a href='#" + case.image_name + "-general'>General Output</a> |\
				 <a href='#" + case.image_name + "-logs'>Logs</a>\
				 </h2>"
				 
		# The script errors found
		errors = "<div id='errors'>\
				  <h2><a name='" + case.image_name + "-errors'>Errors and Warnings</a></h2>\
				  <hr color='#FF0000'>"
		# For each error we have logged in the case
		for error in case.printerror:
			# Replace < and > to avoid any html display errors
			errors += "<p>" + error.replace("<", "&lt").replace(">", "&gt") + "</p>"
			# If there is a \n, we probably want a <br /> in the html
			if "\n" in error:
				errors += "<br />"
		errors += "</div>"
		
		# Links to the logs
		logs = "<div id='logs'>\
				<h2><a name='" + case.image_name + "-logs'>Logs</a></h2>\
				<hr color='#00a00f'>"
		logs_path = make_local_path(case.output_dir, case.image_name, "logs")
		for file in os.listdir(logs_path):
			logs += "<p><a href='file:\\" + make_path(logs_path, file) + "' target='_blank'>" + file + "</a></p>"
		logs += "</div>"
		
		# All the testing information
		info = "<div id='info'>\
				<h2><a name='" + case.image_name + "-info'>Information</a></h2>\
				<hr color='#0005FF'>\
				<table cellspacing='5px'>"
		# The individual elements
		info += "<tr><td>Image Path:</td>"
		info += "<td>" + case.image_file + "</td></tr>"
		info += "<tr><td>Image Name:</td>"
		info += "<td>" + case.image_name + "</td></tr>"
		info += "<tr><td>Case Output Directory:</td>"
		info += "<td>" + case.output_dir + "</td></tr>"
		info += "<tr><td>Autopsy Version:</td>"
		info += "<td>" + case.autopsy_version + "</td></tr>"
		info += "<tr><td>Heap Space:</td>"
		info += "<td>" + case.heap_space + "</td></tr>"
		info += "<tr><td>Test Start Date:</td>"
		info += "<td>" + case.start_date + "</td></tr>"
		info += "<tr><td>Test End Date:</td>"
		info += "<td>" + case.end_date + "</td></tr>"
		info += "<tr><td>Total Test Time:</td>"
		info += "<td>" + case.total_test_time + "</td></tr>"
		info += "<tr><td>Total Ingest Time:</td>"
		info += "<td>" + case.total_ingest_time + "</td></tr>"
		info += "<tr><td>Exceptions Count:</td>"
		info += "<td>" + str(len(get_exceptions())) + "</td></tr>"
		info += "<tr><td>Autopsy OutOfMemoryExceptions:</td>"
		info += "<td>" + str(len(search_logs("OutOfMemoryException"))) + "</td></tr>"
		info += "<tr><td>Autopsy OutOfMemoryErrors:</td>"
		info += "<td>" + str(len(search_logs("OutOfMemoryError"))) + "</td></tr>"
		info += "<tr><td>Tika OutOfMemoryErrors/Exceptions:</td>"
		info += "<td>" + str(get_num_memory_errors("tika")) + "</td></tr>"
		info += "<tr><td>Solr OutOfMemoryErrors/Exceptions:</td>"
		info += "<td>" + str(get_num_memory_errors("solr")) + "</td></tr>"
		info += "<tr><td>TskCoreExceptions:</td>"
		info += "<td>" + str(len(search_log_set("autopsy", "TskCoreException"))) + "</td></tr>"
		info += "<tr><td>TskDataExceptions:</td>"
		info += "<td>" + str(len(search_log_set("autopsy", "TskDataException"))) + "</td></tr>"
		info += "<tr><td>Ingest Messages Count:</td>"
		info += "<td>" + str(case.ingest_messages) + "</td></tr>"
		info += "<tr><td>Indexed Files Count:</td>"
		info += "<td>" + str(case.indexed_files) + "</td></tr>"
		info += "<tr><td>Indexed File Chunks Count:</td>"
		info += "<td>" + str(case.indexed_chunks) + "</td></tr>"
		info += "<tr><td>Out Of Disk Space:\
						 <p style='font-size: 11px;'>(will skew other test results)</p></td>"
		info += "<td>" + str(len(search_log_set("autopsy", "Stopping ingest due to low disk space on disk"))) + "</td></tr>"
		info += "<tr><td>TSK Objects Count:</td>"
		info += "<td>" + str(database.autopsy_objects) + "</td></tr>"
		info += "<tr><td>Artifacts Count:</td>"
		info += "<td>" + str(database.get_artifacts_count()) + "</td></tr>"
		info += "<tr><td>Attributes Count:</td>"
		info += "<td>" + str(database.autopsy_attributes) + "</td></tr>"
		info += "</table>\
				 </div>"
		# For all the general print statements in the case
		output = "<div id='general'>\
				  <h2><a name='" + case.image_name + "-general'>General Output</a></h2>\
				  <hr color='#282828'>"
		# For each printout in the case's list
		for out in case.printout:
			output += "<p>" + out + "</p>"
			# If there was a \n it probably means we want a <br /> in the html
			if "\n" in out:
				output += "<br />"
		output += "</div>"
		
		html.write(title)
		html.write(errors)
		html.write(info)
		html.write(logs)
		html.write(output)
		html.close()
	except Exception as e:
		printerror("Error: Unknown fatal error when creating HTML log at:")
		printerror(case.html_log)
		printerror(str(e) + "\n")
		logging.critical(traceback.format_exc())

# Writed the top of the HTML log file
def write_html_head():
	html = open(case.html_log, "a")
	head = "<html>\
			<head>\
			<title>AutopsyTestCase Output</title>\
			</head>\
			<style type='text/css'>\
			body { font-family: 'Courier New'; font-size: 12px; }\
			h1 { background: #444; margin: 0px auto; padding: 0px; color: #FFF; border: 1px solid #000; font-family: Tahoma; text-align: center; }\
			h1 span { font-size: 12px; font-weight: 100; }\
			h2 { font-family: Tahoma; padding: 0px; margin: 0px; }\
			hr { width: 100%; height: 1px; border: none; margin-top: 10px; margin-bottom: 10px; }\
			#errors { background: #FFCFCF; border: 1px solid #FF0000; color: #FF0000; padding: 10px; margin: 20px; }\
			#info { background: #D2D3FF; border: 1px solid #0005FF; color: #0005FF; padding: 10px; margin: 20px; }\
			#general { background: #CCCCCC; border: 1px solid #282828; color: #282828; padding: 10px; margin: 20px; }\
			#logs { background: #8cff97; border: 1px solid #00820c; color: #00820c; padding: 10px; margin: 20px; }\
			#errors p, #info p, #general p, #logs p { pading: 0px; margin: 0px; margin-left: 5px; }\
			#info table td { color: #0005FF; font-size: 12px; min-width: 225px; }\
			#logs a { color: #00820c; }\
			</style>\
			<body>"
	html.write(head)
	html.close()

# Writed the bottom of the HTML log file
def write_html_foot():
	html = open(case.html_log, "a")
	head = "</body></html>"
	html.write(head)
	html.close()

# Adds all the image names to the HTML log for easy access
def html_add_images(full_image_names):
	# If the file doesn't exist yet, this is the first case to run for
	# this test, so we need to make the start of the html log
	if not file_exists(case.html_log):
		write_html_head()
	html = open(case.html_log, "a")
	links = []
	for full_name in full_image_names:
		name = case.get_image_name(full_name)
		links.append("<a href='#" + name + "'>" + name + "</a>")
	html.write("<p align='center'>" + (" | ".join(links)) + "</p>")



#----------------------------------#
#		 Helper functions		 #
#----------------------------------#

def setDay():
	global Day
	Day = int(strftime("%d", localtime()))
		
def getLastDay():
	return Day
		
def getDay():
	return int(strftime("%d", localtime()))
		
def newDay():
	return getLastDay() != getDay()

#Pulls from git
def gitPull(TskOrAutopsy):
	global SYS
	global errorem
	global attachl
	ccwd = ""
	gppth = make_local_path(case.output_dir, "GitPullOutput" + TskOrAutopsy + ".txt")
	attachl.append(gppth)
	gpout = open(gppth, 'a')
	toPull = "http://www.github.com/sleuthkit/" + TskOrAutopsy
	call = ["git", "pull", toPull]
	if TskOrAutopsy == "sleuthkit":
		ccwd = os.path.join("..", "..", "..", "sleuthkit")
	else:
		ccwd = os.path.join("..", "..")
	subprocess.call(call, stdout=gpout, cwd=ccwd)
	gpout.close()
	

#Builds TSK as a win32 applicatiion
def vsBuild():
	global redo
	global tryredo
	global passed
	#Please ensure that the current working directory is $autopsy/testing/script
	vs = []
	vs.append("/cygdrive/c/windows/microsoft.NET/framework/v4.0.30319/MSBuild.exe")
	vs.append(os.path.join("..", "..", "..","sleuthkit", "win32", "Tsk-win.sln"))
	vs.append("/p:configuration=release")
	vs.append("/p:platform=win32")
	vs.append("/t:clean")
	vs.append("/t:rebuild")
	print(vs)
	VSpth = make_local_path(case.output_dir, "VSOutput.txt")
	VSout = open(VSpth, 'a')
	subprocess.call(vs, stdout=VSout)
	VSout.close()
	chk = os.path.join("..", "..", "..","sleuthkit", "win32", "Release", "libtsk_jni.dll")
	try:
		open(chk)
	except IOError as e:
		global errorem
		global attachl
		if(not tryredo):
			errorem += "LIBTSK C++ failed to build.\n"
			attachl.append(VSpth)
			send_email()
		tryredo = True
		passed = False
		redo = True
		
	
 
#Builds Autopsy or the Datamodel
def antBuild(which, Build):
	global redo
	global passed
	global tryredo
	directory = os.path.join("..", "..")
	ant = []
	if which == "datamodel":
		directory = os.path.join("..", "..", "..", "sleuthkit", "bindings", "java")
	ant.append("ant")
	ant.append("-f")
	ant.append(directory)
	ant.append("clean")
	if(Build):
		ant.append("build")
	else:
		ant.append("dist")
	antpth = make_local_path(case.output_dir, "ant" + which + "Output.txt")
	antout = open(antpth, 'a')
	succd = subprocess.call(ant, stdout=antout)
	antout.close()
	global errorem
	global attachl
	if which == "datamodel":
		chk = os.path.join("..", "..", "..","sleuthkit",  "bindings", "java", "dist", "TSK_DataModel.jar")
		try:
			open(chk)
		except IOError as e:
			if(not tryredo):
				errorem += "DataModel Java build failed.\n"
				attachl.append(antpth)
				send_email()
			passed = False
			tryredo = True
	elif (succd != 0 and (not tryredo)):
		errorem += "Autopsy build failed.\n"
		attachl.append(antpth)
		send_email()
		tryredo = True
	elif (succd != 0):
		passed = False
		
	
#Watches clock and waits for current ingest to be done

# Verifies a file's existance
def file_exists(file):
	try:
		if os.path.exists(file):
			return os.path.isfile(file)
	except:
		return False
		
# Verifies a directory's existance
def dir_exists(dir):
	try:
		return os.path.exists(dir)
	except:
		return False

# Returns a Windows style path starting with the cwd and
# ending with the list of directories given
def make_local_path(*dirs):
	path = wgetcwd()
	for dir in dirs:
		path += ("\\" + dir)
	return path_fix(path)

# Returns a Windows style path based only off the given directories
def make_path(*dirs):
	path = dirs[0]
	for dir in dirs[1:]:
		path += ("\\" + dir)
	return path_fix(path)
	
# Fix a standard os.path by making it Windows format
def path_fix(path):
	return path.replace("/", "\\")

# Gets the true current working directory instead of Cygwin's
def wgetcwd():
	if SYS is OS.CYGWIN:
		proc = subprocess.Popen(("cygpath", "-m", os.getcwd()), stdout=subprocess.PIPE)
		out,err = proc.communicate()
		return out.rstrip()
	elif SYS is OS.WIN:
		return os.getcwd()

# Copy the log files from Autopsy's default directory
def copy_logs():
	try:
		log_dir = os.path.join("..", "..", "Testing","build","test","qa-functional","work","userdir0","var","log")
		shutil.copytree(log_dir, make_local_path(case.output_dir, case.image_name, "logs"))
	except Exception as e:
		printerror("Error: Failed to copy the logs.")
		printerror(str(e) + "\n")
		logging.warning(traceback.format_exc())
# Clears all the files from a directory and remakes it
def clear_dir(dir):
	try:
		if dir_exists(dir):
			shutil.rmtree(dir)
		os.makedirs(dir)
		return True;
	except:
		printerror("Error: Cannot clear the given directory:")
		printerror(dir + "\n")
		return False;

def del_dir(dir):
	try:
		if dir_exists(dir):
			shutil.rmtree(dir)
		return True;
	except:
		printerror("Error: Cannot delete the given directory:")
		printerror(dir + "\n")
		return False;
# Copies a given file from "ffrom" to "to"
def copy_file(ffrom, to):
	try :
		if not file_exists(ffrom):
			print("hi")
			raise FileNotFoundException(ffrom)
		shutil.copy(ffrom, to)
	except:
		raise FileNotFoundException(to)

# Copies a directory file from "ffrom" to "to"
def copy_dir(ffrom, to):
	#try :
	if not os.path.isdir(ffrom):
		raise FileNotFoundException(ffrom)
	shutil.copytree(ffrom, to)
	#except:
		#raise FileNotFoundException(to)
# Returns the first file in the given directory with the given extension
def get_file_in_dir(dir, ext):
	try:
		for file in os.listdir(dir):
			if file.endswith(ext):
				return make_path(dir, file)
		# If nothing has been found, raise an exception
		raise FileNotFoundException(dir)
	except:
		raise DirNotFoundException(dir)
		
def find_file_in_dir(dir, name, ext):
	try: 
		for file in os.listdir(dir):
			if file.startswith(name):
				if file.endswith(ext):
					return make_path(dir, file)
		raise FileNotFoundException(dir)
	except:
		raise DirNotFoundException(dir)

# Compares file a to file b and any differences are returned
# Only works with report html files, as it searches for the first <ul>
def compare_report_files(a_path, b_path):
	a_file = open(a_path)
	b_file = open(b_path)
	a = a_file.read()
	b = b_file.read()
	a = a[a.find("<ul>"):]
	b = b[b.find("<ul>"):]
	
	a_list = split(a, 50)
	b_list = split(b, 50)
	if not len(a_list) == len(b_list):
		ex = (len(a_list), len(b_list))
		return ex
	else: 
		return (0, 0)
  
# Split a string into an array of string of the given size
def split(input, size):
	return [input[start:start+size] for start in range(0, len(input), size)]

# Returns the nth word in the given string or "" if n is out of bounds
# n starts at 0 for the first word
def get_word_at(string, n):
	words = string.split(" ")
	if len(words) >= n:
		return words[n]
	else:
		return ""

# Returns true if the given file is one of the required input files
# for ingest testing
def required_input_file(name):
	if ((name == "notablehashes.txt-md5.idx") or
	   (name == "notablekeywords.xml") or
	   (name == "nsrl.txt-md5.idx")): 
	   return True
	else:
		return False

		

# Returns the args of the test script
def usage():
	return """
Usage:  ./regression.py [-f FILE] [OPTIONS]

		Run RegressionTest.java, and compare the result with a gold standard.
		By default, the script tests every image in ../input
		When the -f flag is set, this script only tests a single given image.
		When the -l flag is set, the script looks for a configuration file,
		which may outsource to a new input directory and to individual images.
		
		Expected files:
		  An NSRL database at:			../input/nsrl.txt-md5.idx
		  A notable hash database at:	 ../input/notablehashes.txt-md5.idx
		  A notable keyword file at:	  ../input/notablekeywords.xml
		
Options:
  -r			Rebuild the gold standards for the image(s) tested.
  -i			Ignores the ../input directory and all files within it.
  -u			Tells Autopsy not to ingest unallocated space.
  -k			Keeps each image's Solr index instead of deleting it.
  -v			Verbose mode; prints all errors to the screen.
  -e ex		 Prints out all errors containing ex.
  -l cfg		Runs from configuration file cfg.
  -c			Runs in a loop over the configuration file until canceled. Must be used in conjunction with -l
	"""




#------------------------------------------------------------#
# Exception classes to manage "acceptable" thrown exceptions #
#		  versus unexpected and fatal exceptions			#
#------------------------------------------------------------#

# If a file cannot be found by one of the helper functions
# they will throw a FileNotFoundException unless the purpose
# is to return False
class FileNotFoundException(Exception):
	def __init__(self, file):
		self.file = file
		self.strerror = "FileNotFoundException: " + file
		
	def print_error(self):
		printerror("Error: File could not be found at:")
		printerror(self.file + "\n")
	def error(self):
		error = "Error: File could not be found at:\n" + self.file + "\n"
		return error

# If a directory cannot be found by a helper function,
# it will throw this exception
class DirNotFoundException(Exception):
	def __init__(self, dir):
		self.dir = dir
		self.strerror = "DirNotFoundException: " + dir
		
	def print_error(self):
		printerror("Error: Directory could not be found at:")
		printerror(self.dir + "\n")
	def error(self):
		error = "Error: Directory could not be found at:\n" + self.dir + "\n"
		return error




#Executes the tests, makes continuous testing easier 
def execute_test():
	global failedbool
	global html
	global attachl
	case.output_dir = make_path("..", "output", "results", time.strftime("%Y.%m.%d-%H.%M.%S"))
	os.makedirs(case.output_dir)
	case.common_log = "AutopsyErrors.txt"
	case.csv = make_local_path(case.output_dir, "CSV.txt")
	case.html_log = make_local_path(case.output_dir, "AutopsyTestCase.html")
	log_name = case.output_dir + "\\regression.log"
	logging.basicConfig(filename=log_name, level=logging.DEBUG)
	# If user wants to do a single file and a list (contradictory?)
	if args.single and args.list:
		printerror("Error: Cannot run both from config file and on a single file.")
		return
	# If working from a configuration file
	if args.list:
	   if not file_exists(args.config_file):
		   printerror("Error: Configuration file does not exist at:")
		   printerror(args.config_file)
		   return
	   run_config_test(args.config_file)
	# Else if working on a single file
	elif args.single:
	   if not file_exists(args.single_file):
		   printerror("Error: Image file does not exist at:")
		   printerror(args.single_file)
		   return
	   run_test(args.single_file, 0)
	# If user has not selected a single file, and does not want to ignore
	#  the input directory, continue on to parsing ../input
	if (not args.single) and (not args.ignore):
	   count = 0
	   for file in os.listdir(case.input_dir):
			if not(image_type(file) == IMGTYPE.UNKNOWN):
				count +=1
	   archivelist = make_path(case.gold,"..")
	   arcount = 0
	   for file in os.listdir(archivelist):
			if not(file == 'tmp'):
				arcount+=1
	   if count > arcount:
			print("*****Alert: There are still some inconsistencies between the number of gold standards and input files.")
	   for file in os.listdir(case.input_dir):
		   # Make sure it's not a required hash/keyword file or dir
		   if (not required_input_file(file) and
			  not os.path.isdir(make_path(case.input_dir, file))):
			  run_test(make_path(case.input_dir, file), 0)
	write_html_foot()
	html.close()
	logres = search_common_log("TskCoreException")
	if (len(logres)>0):
		failedbool = True
		global errorem
		errorem += "There were Autopsy errors.\n"
		for lm in logres:
			errorem += lm
	html.close()
	if failedbool:
		attachl.append(case.common_log_path)
		attachl.insert(0, html.name)
	else:
		errorem = ""
		errorem += "There were no Errors.\n"
		attachl = []
	if not args.gold_creation:
		send_email()


def send_email():
	global parsed
	global errorem
	global attachl
	global html
	if(not args.list):
		sys.exit()
	element = parsed.getElementsByTagName("email")
	if(len(element)<=0):
		return
	element = element[0]
	toval = element.getAttribute("value").encode().decode("utf_8")
	if(toval==None):
		return
	element = parsed.getElementsByTagName("mail_server")[0]
	serverval = element.getAttribute("value").encode().decode("utf_8")
	# Create the container (outer) email message.
	msg = MIMEMultipart()
	msg['Subject'] = 'Email Test'
	# me == the sender's email address
	# family = the list of all recipients' email addresses
	msg['From'] = 'AutopsyContinuousTest'
	msg['To'] = toval
	msg.preamble = 'This is a test'
	container = MIMEText(errorem, 'plain')
	msg.attach(container)
	Build_email(msg)
	s = smtplib.SMTP(serverval)
	s.sendmail(msg['From'], msg['To'], msg.as_string())
	s.quit()
	
def Build_email(msg):
	global attachl
	for file in attachl:
		part = MIMEBase('application', "octet-stream")
		atach = open(file, "rb")
		attch = atach.read()
		noml = file.split("\\")
		nom = noml[len(noml)-1]
		part.set_payload(attch)
		Encoders.encode_base64(part)
		part.add_header('Content-Disposition', 'attachment; filename="' + nom + '"')
		msg.attach(part)

#----------------------#
#		 Main		 #
#----------------------#
def main():
	# Global variables
	global args
	global case
	global database
	global failedbool
	global inform
	global fl
	global errorem
	global attachl
	global daycount
	global redo
	global passed
	inpvar = raw_input("Your input images may be out of date, do you want to update?(y/n): ")
	if(inpvar.lower() == 'y' or inpvar.lower() == 'yes'):
		antin = ["ant"]
		antin.append("-f")
		antin.append(os.path.join("..","..","build.xml"))
		antin.append("test-download-imgs")
		if SYS is OS.CYGWIN:
			subprocess.call(antin)
		elif SYS is OS.WIN:
			theproc = subprocess.Popen(antin, shell = True, stdout=subprocess.PIPE)
			theproc.communicate()
	daycount = 0
	failedbool = False
	redo = False
	errorem = "The test standard didn't match the gold standard.\n"
	case = TestAutopsy()
	database = Database()
	printout("")
	args = Args()
	attachl = []
	passed = False
	# The arguments were given wrong:
	if not args.parse():
		case.reset()
		pass
	# Otherwise test away!
	else:
		execute_test()
		while args.contin:
			redo = False
			attachl = []
			errorem = "The test standard didn't match the gold standard.\n"
			failedbool = False
			passed = False
			execute_test()
			case = TestAutopsy()


class OS:
  LINUX, MAC, WIN, CYGWIN = range(4)	  
if __name__ == "__main__":
	global SYS
	if _platform == "linux" or _platform == "linux2":
		SYS = OS.LINUX
	elif _platform == "darwin":
		SYS = OS.MAC
	elif _platform == "win32":
		SYS = OS.WIN
	elif _platform == "cygwin":
		SYS = OS.CYGWIN
		
	if SYS is OS.WIN or SYS is OS.CYGWIN:
		main()
	else:
		print("We only support Windows and Cygwin at this time.")
