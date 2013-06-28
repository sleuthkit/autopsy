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
import re
import zipfile
import zlib
import Emailer
import srcupdater

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
# - Information pertaining to each test is in TestAutopsy (named test_case)
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
		self.fr = False
	
	def parse(self):
		global nxtproc 
		nxtproc = []
		nxtproc.append("python3")
		nxtproc.append(sys.argv.pop(0))
		while sys.argv:
			arg = sys.argv.pop(0)
			nxtproc.append(arg)
			if(arg == "-f"):
				try:
					arg = sys.argv.pop(0)
					print("Running on a single file:")
					print(path_fix(arg) + "\n")
					self.single = True
					self.single_file = path_fix(arg)
				except:
					print("Error: No single file given.\n")
					return False
			elif(arg == "-r" or arg == "--rebuild"):
				print("Running in rebuild mode.\n")
				self.rebuild = True
			elif(arg == "-l" or arg == "--list"):
				try:
					arg = sys.argv.pop(0)
					nxtproc.append(arg)
					print("Running from configuration file:")
					print(arg + "\n")
					self.list = True
					self.config_file = arg
				except:
					print("Error: No configuration file given.\n")
					return False
			elif(arg == "-u" or arg == "--unallocated"):
			   print("Ignoring unallocated space.\n")
			   self.unallocated = True
			elif(arg == "-k" or arg == "--keep"):
				print("Keeping the Solr index.\n")
				self.keep = True
			elif(arg == "-v" or arg == "--verbose"):
				print("Running in verbose mode:")
				print("Printing all thrown exceptions.\n")
				self.verbose = True
			elif(arg == "-e" or arg == "--exception"):
				try:
					arg = sys.argv.pop(0)
					nxtproc.append(arg)
					print("Running in exception mode: ")
					print("Printing all exceptions with the string '" + arg + "'\n")
					self.exception = True
					self.exception_string = arg
				except:
					print("Error: No exception string given.")
			elif arg == "-h" or arg == "--help":
				print(usage())
				return False
			elif arg == "-fr" or arg == "--forcerun":
				print("Not downloading new images")
				self.fr = True
			else:
				print(usage())
				return False
		# Return the args were sucessfully parsed
		return True
#-----------------------------------------------------#
# Holds all global variables for each individual test #
#-----------------------------------------------------#
class TestAutopsy:
	def __init__(self, args):
		self.args = args
		# Paths:
		self.output_dir = ""
		self.input_dir = Emailer.make_local_path("..","input")
		self.gold = Emailer.make_path("..", "output", "gold")
		self.img_gold = Emailer.make_path(self.gold, 'tmp')
		self.gold_parse = ""
		self.img_gold_parse = ""
		self.common_log = "AutopsyErrors.txt"
		self.test_db_file = "autopsy.db"
		self.Img_Test_Folder = "AutopsyTestCase"
		# Logs:
		self.csv = ""
		self.global_csv = ""
		self.html_log = ""
		# Ant info:
		self.known_bad_path = ""
		self.keyword_path = ""
		self.nsrl_path = ""
		self.build_path = ""
		# test_case info
		self.autopsy_version = ""
		self.ingest_messages = 0
		self.indexed_files = 0
		self.indexed_chunks = 0
		# Infinite Testing info
		timer = 0
		self.images = []
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
		# Error tracking
		self.printerror = []
		self.printout = []
		
		# Set the timeout to something huge
		# The entire tester should not timeout before this number in ms
		# However it only seems to take about half this time
		# And it's very buggy, so we're being careful
		self.timeout = 24 * 60 * 60 * 1000 * 1000
		self.ant = []
	   
#---------------------------------------------------------#
# Holds all database information from querying autopsy.db #
#  and standard.db. Initialized when the autopsy.db file  #
#		  is compared to the gold standard.			      #
#---------------------------------------------------------#
class Database:
	def __init__(self, case):
		self.gold_artifacts = []
		self.autopsy_artifacts = []
		self.gold_attributes = 0
		self.autopsy_attributes = 0
		self.gold_objects = 0
		self.autopsy_objects = 0
		self.artifact_comparison = []
		self.attribute_comparison = []
		self.test_data = case
		
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
			global imgfail
			imgfail = True
			return "; ".join(self.artifact_comparison)
		
	def get_attribute_comparison(self):
		if not self.attribute_comparison:
			return "All counts matched"
		global failedbool
		global errorem
		failedbool = True
		global imgfail
		imgfail = True
		list = []
		for error in self.attribute_comparison:
			list.append(error)
		return ";".join(list)
		
	def _generate_autopsy_artifacts(self):
		if not self.autopsy_artifacts:
			autopsy_db_file = Emailer.make_path(test_case.output_dir, self.test_data.image_name,
										  test_case.Img_Test_Folder, test_case.test_db_file)
			autopsy_con = sqlite3.connect(autopsy_db_file)
			autopsy_cur = autopsy_con.cursor()
			autopsy_cur.execute("SELECT COUNT(*) FROM blackboard_artifact_types")
			length = autopsy_cur.fetchone()[0] + 1
			for type_id in range(1, length):
				autopsy_cur.execute("SELECT COUNT(*) FROM blackboard_artifacts WHERE artifact_type_id=%d" % type_id)
				self.autopsy_artifacts.append(autopsy_cur.fetchone()[0])		
	
	def _generate_autopsy_attributes(self):
		if self.autopsy_attributes == 0:
			autopsy_db_file = Emailer.make_path(test_case.output_dir, self.test_data.image_name,
										  test_case.Img_Test_Folder, test_case.test_db_file)
			autopsy_con = sqlite3.connect(autopsy_db_file)
			autopsy_cur = autopsy_con.cursor()
			autopsy_cur.execute("SELECT COUNT(*) FROM blackboard_attributes")
			autopsy_attributes = autopsy_cur.fetchone()[0]
			self.autopsy_attributes = autopsy_attributes

        # Counts number of objects and saves them into database.
        # @@@ Should have better name (count_output_objects) and does not need to connect again. Should be storing connection in Database
        # See also for _generate_autopsy_attributes
	def _generate_autopsy_objects(self):
		if self.autopsy_objects == 0:
			autopsy_db_file = Emailer.make_path(test_case.output_dir, self.test_data.image_name,
										  test_case.Img_Test_Folder, test_case.test_db_file)
			autopsy_con = sqlite3.connect(autopsy_db_file)
			autopsy_cur = autopsy_con.cursor()
			autopsy_cur.execute("SELECT COUNT(*) FROM tsk_objects")
			autopsy_objects = autopsy_cur.fetchone()[0]
			self.autopsy_objects = autopsy_objects
		
        # @@@ see _generate_autopsy_objects comment about better name, saving connections, etc. Or could have design where connection
        # is passed in so that we do not need separate methods for gold and output.
	def _generate_gold_artifacts(self):
		if not self.gold_artifacts:
			gold_db_file = Emailer.make_path(test_case.img_gold, self.test_data.image_name, test_case.test_db_file)
			if(not Emailer.file_exists(gold_db_file)):
				gold_db_file = Emailer.make_path(test_case.img_gold_parse, self.test_data.image_name, test_case.test_db_file)
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
				
	def _generate_gold_attributes(self):
		if self.gold_attributes == 0:
			gold_db_file = Emailer.make_path(test_case.img_gold, self.test_data.image_name, test_case.test_db_file)
			if(not Emailer.file_exists(gold_db_file)):
				gold_db_file = Emailer.make_path(test_case.img_gold_parse, self.test_data.image_name, test_case.test_db_file)
			gold_con = sqlite3.connect(gold_db_file)
			gold_cur = gold_con.cursor()
			gold_cur.execute("SELECT COUNT(*) FROM blackboard_attributes")
			self.gold_attributes = gold_cur.fetchone()[0]

	def _generate_gold_objects(self):
		if self.gold_objects == 0:
			gold_db_file = Emailer.make_path(test_case.img_gold, self.test_data.image_name, test_case.test_db_file)
			if(not Emailer.file_exists(gold_db_file)):
				gold_db_file = Emailer.make_path(test_case.img_gold_parse, self.test_data.image_name, test_case.test_db_file)
			gold_con = sqlite3.connect(gold_db_file)
			gold_cur = gold_con.cursor()
			gold_cur.execute("SELECT COUNT(*) FROM tsk_objects")
			self.gold_objects = gold_cur.fetchone()[0]
			
        # smart method that deals with blackboard comparison to avoid issues with different IDs based on when artifacts were created.
        # Dumps sorted text results to output location stored in test_data. 
        # autopsy_db_file: Output database file
	def _retrieve_data(autopsy_con, autopsy_db_file, test_data):
		autopsy_cur2 = autopsy_con.cursor()
		global errorem
		global attachl
		global failedbool
		autopsy_cur2.execute("SELECT tsk_files.parent_path, tsk_files.name, blackboard_artifact_types.display_name, blackboard_artifacts.artifact_id FROM blackboard_artifact_types INNER JOIN blackboard_artifacts ON blackboard_artifact_types.artifact_type_id = blackboard_artifacts.artifact_type_id INNER JOIN tsk_files ON tsk_files.obj_id = blackboard_artifacts.obj_id")
		database_log = codecs.open(test_data.autopsy_data_file, "wb", "utf_8")
		rw = autopsy_cur2.fetchone()
		appnd = False
		counter = 0
		try:
			while (rw != None):
				if(rw[0] != None):
					database_log.write(rw[0] + rw[1] + ' <artifact type="' + rw[2] + '" > ')
				else:
					database_log.write(rw[1] + ' <artifact type="' + rw[2] + '" > ')
				autopsy_cur1 = autopsy_con.cursor()
				looptry = True
				test_data.artifact_count += 1
				try:
					key = ""
					key = str(rw[3])
					key = key,
					autopsy_cur1.execute("SELECT blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double FROM blackboard_attributes INNER JOIN blackboard_attribute_types ON blackboard_attributes.attribute_type_id = blackboard_attribute_types.attribute_type_id WHERE artifact_id =? ORDER BY blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double", key)
					attributes = autopsy_cur1.fetchall()
				except Exception as e:
					printerror(test_data, str(e))
					printerror(test_data, str(rw[3]))
					print(test_data.image_name)
					errorem += test_data.image_name + ":Artifact with id#" + str(rw[3]) + " encountered an error.\n"
					looptry = False
					print(test_data.artifact_fail)
					test_data.artifact_fail += 1
					print(test_data.artifact_fail)
				if(looptry == True):
					src = attributes[0][0]
					for attr in attributes:
						val = 3 + attr[2]
						numvals = 0
						for x in range(3, 6):
							if(attr[x] != None):
								numvals += 1
						if(numvals > 1):
							errorem += test_data.image_name + ":There were too many values for attribute type: " + attr[1] + " for artifact with id #" + str(rw[3]) + ".\n"
							printerror(test_data, "There were too many values for attribute type: " + attr[1] + " for artifact with id #" + str(rw[3]) + " for image " + test_data.image_name + ".")
							failedbool = True
							if(not appnd):
								attachl.append(autopsy_db_file)
								appnd = True
						if(not attr[0] == src):
							errorem += test_data.image_name + ":There were inconsistents sources for artifact with id #" + str(rw[3]) + ".\n"
							printerror(test_data, "There were inconsistents sources for artifact with id #" + str(rw[3]) + " for image " + test_data.image_name + ".")
							failedbool = True
							if(not appnd):
								attachl.append(autopsy_db_file)
								appnd = True
						try:
							database_log.write('<attribute source="' + attr[0] + '" type="' + attr[1] + '" value="')
							inpval = attr[val]
							if((type(inpval) != 'unicode') or (type(inpval) != 'str')):
								inpval = str(inpval)
							patrn = re.compile("[\n\0\a\b\r\f\e]")
							inpval = re.sub(patrn, ' ', inpval)
							try:
								database_log.write(inpval)
							except Exception as e:
								printerror(test_data, "Inner exception" + outp)
						except Exception as e:
								printerror(test_data, str(e))
						database_log.write('" />')
				database_log.write(' <artifact/>\n')
				rw = autopsy_cur2.fetchone()
			srtcmdlst = ["sort", test_data.autopsy_data_file, "-o", test_data.sorted_data_file]
			subprocess.call(srtcmdlst)
			print(test_data.artifact_fail)
			if(test_data.artifact_fail > 0):
				errorem += test_data.image_name + ":There were " + str(test_data.artifact_count) + " artifacts and " + str(test_data.artifact_fail) + " threw an exception while loading.\n"
		except Exception as e:
			printerror(test_data, 'outer exception: ' + str(e))
			
        # Dumps a database (minus the artifact and attributes) to a text file. 
        # 
	def _dbDump(test_data):
		autopsy_db_file = Emailer.make_path(test_case.output_dir, test_data.image_name,
										  test_case.Img_Test_Folder, test_case.test_db_file)
		backup_db_file = Emailer.make_path(test_case.output_dir, test_data.image_name,
										  test_case.Img_Test_Folder, "autopsy_backup.db")
		copy_file(autopsy_db_file,backup_db_file)
		autopsy_con = sqlite3.connect(backup_db_file)
		autopsy_con.execute("DROP TABLE blackboard_artifacts")
		autopsy_con.execute("DROP TABLE blackboard_attributes")
		dump_file = Emailer.make_path(test_case.output_dir, test_data.image_name, test_data.image_name + "Dump.txt")
		database_log = codecs.open(dump_file, "wb", "utf_8")
		dump_list = autopsy_con.iterdump()
		try:
			for line in dump_list:
				try:
					database_log.write(line + "\n")
				except Exception as e:
					printerror(test_data, "Inner dump Exception:" + str(e))
		except Exception as e:
			printerror(test_data, "Outer dump Exception:" + str(e))
			
	# Basic test between output and gold databases. Compares only counts of objects and blackboard items
        # 
        # test_data: TestData object for current test
        # database: Database object to use for comparison
        # @@@ This is currently being called statically with databaes being passed in.  Could be called as database.compare... and not pass database in. 
        # @@@ Rename
	def compare_to_gold_db(test_data, database):
		# SQLITE needs unix style pathing

                # Get connection to output database from current run
		autopsy_db_file = Emailer.make_path(test_case.output_dir, test_data.image_name,
										  test_case.Img_Test_Folder, test_case.test_db_file)
		autopsy_con = sqlite3.connect(autopsy_db_file)
		autopsy_cur = autopsy_con.cursor()

                # Get connection to gold DB and count artifacts, etc.
		gold_db_file = Emailer.make_path(test_case.img_gold, test_data.image_name, test_case.test_db_file)
		if(not Emailer.file_exists(gold_db_file)):
			gold_db_file = Emailer.make_path(test_case.img_gold_parse, test_data.image_name, test_case.test_db_file)
		try:
			database._generate_gold_objects()
			database._generate_gold_artifacts()
			database._generate_gold_attributes()
		except Exception as e:
			printerror(test_data, "Way out:" + str(e))
		# This is where we return if a file doesn't exist, because we don't want to
		# compare faulty databases, but we do however want to try to run all queries
		# regardless of the other database
		if not Emailer.file_exists(autopsy_db_file):
			printerror(test_data, "Error: Database file does not exist at:")
			printerror(test_data, autopsy_db_file + "\n")
			return
		if not Emailer.file_exists(gold_db_file):
			printerror(test_data, "Error: Gold database file does not exist at:")
			printerror(test_data, gold_db_file + "\n")
			return

		# compare size of bb artifacts, attributes, and tsk objects
		gold_con = sqlite3.connect(gold_db_file)
		gold_cur = gold_con.cursor()
		
		exceptions = []
		
		autopsy_db_file = Emailer.make_path(test_case.output_dir, test_data.image_name,
											  test_case.Img_Test_Folder, test_case.test_db_file)
                # Connect again and count things
		autopsy_con = sqlite3.connect(autopsy_db_file)
		try:
			database._generate_autopsy_objects()
			database._generate_autopsy_artifacts()
			database._generate_autopsy_attributes()
		except Exception as e:
			printerror(test_data, "Way out:" + str(e))

                # Compare counts
		exceptions.append(Database._compare_tsk_objects(test_data, database))
		exceptions.append(Database._count_bb_artifacts(test_data, database))
		exceptions.append(Database._compare_bb_attributes(test_data, database))
		
		database.artifact_comparison = exceptions[1]
		database.attribute_comparison = exceptions[2]
		
		okay = "All counts match."
		print_report(test_data, exceptions[0], "COMPARE TSK OBJECTS", okay)
		print_report(test_data, exceptions[1], "COMPARE ARTIFACTS", okay)
		print_report(test_data, exceptions[2], "COMPARE ATTRIBUTES", okay)
			
        # Dumps the given database to text files for later comparison
        # @@@ Rename and follow-on methods
	def get_Data(test_data):
		autopsy_db_file = Emailer.make_path(test_case.output_dir, test_data.image_name,
										  test_case.Img_Test_Folder, test_case.test_db_file)
		autopsy_con = sqlite3.connect(autopsy_db_file)
		autopsy_cur = autopsy_con.cursor()
		# Try to query the databases. Ignore any exceptions, the function will
		# return an error later on if these do fail
		Database._retrieve_data(autopsy_con,autopsy_db_file, test_data)
		Database._dbDump(test_data)
		
	# Compares the blackboard artifact counts of two databases
	# given the two database cursors
	def _count_bb_artifacts(test_data, database):
		exceptions = []
		try:
			global failedbool
			global errorem
			if database.gold_artifacts != database.autopsy_artifacts:
				failedbool = True
				global imgfail
				imgfail = True
				errorem += test_data.image + ":There was a TestDifference in the number of artifacts.\n"
			rner = len(database.gold_artifacts)
			for type_id in range(1, rner):
				if database.gold_artifacts[type_id] != database.autopsy_artifacts[type_id]:
					error = str("Artifact counts do not match for type id %d. " % type_id)
					error += str("Gold: %d, Test: %d" %
								(database.gold_artifacts[type_id],
								 database.autopsy_artifacts[type_id]))
					exceptions.append(error)
			return exceptions
		except Exception as e:
			printerror(test_data, str(e))
			exceptions.append("Error: Unable to compare blackboard_artifacts.\n")
			return exceptions

	# Compares the blackboard atribute counts of two databases
	# given the two database cursors
	def _compare_bb_attributes(test_data, database):
		exceptions = []
		try:
			if database.gold_attributes != database.autopsy_attributes:
				error = "Attribute counts do not match. "
				error += str("Gold: %d, Test: %d" % (database.gold_attributes, database.autopsy_attributes))
				exceptions.append(error)
				global failedbool
				global errorem
				failedbool = True
				global imgfail
				imgfail = True
				errorem += test_data.image + ":There was a TestDifference in the number of attributes.\n"
				return exceptions
		except Exception as e:
			exceptions.append("Error: Unable to compare blackboard_attributes.\n")
			return exceptions

	# Compares the tsk object counts of two databases
	# given the two database cursors
	def _compare_tsk_objects(test_data,database):
		exceptions = []
		try:
			if database.gold_objects != database.autopsy_objects:
				error = "TSK Object counts do not match. "
				error += str("Gold: %d, Test: %d" % (database.gold_objects, database.autopsy_objects))
				exceptions.append(error)
				global failedbool
				global errorem
				failedbool = True
				global imgfail
				imgfail = True
				errorem += test_data.image + ":There was a TestDifference between the tsk object counts.\n"
				return exceptions
		except Exception as e:
			exceptions.append("Error: Unable to compare tsk_objects.\n")
			return exceptions

#-------------------------------------------------#
#	  Functions relating to comparing outputs	  #
#-------------------------------------------------#	  
class TestResultsDiffer:
	#
	def run_diff(test_data):
		TestResultsDiffer._compare_errors(test_data)
                
                # Compare smart blackboard stuff results
		gold_nm = "SortedData"
		TestResultsDiffer._compare_data(test_data.sorted_data_file, gold_nm, test_data)

                # Compare the rest of the database (non-BB)
		gold_nm = "DBDump"
		TestResultsDiffer._compare_data(test_data.test_dbdump, gold_nm, test_data)

                # Compare html output
		TestResultsDiffer._compare_to_gold_html(test_data)
		
        # Compares database dump files.  
        # aut: output text file
        # gld: gold text file
        # test_data: Test being performed
        # @@@ Could be renamed to be more generic about testing text files.
	def _compare_data(aut, gld,test_data):
		gold_dir = Emailer.make_path(test_case.img_gold, test_data.image_name, test_data.image_name + gld + ".txt")
		if(not Emailer.file_exists(gold_dir)):
				gold_dir = Emailer.make_path(test_case.img_gold_parse,  test_data.image_name, test_data.image_name + gld + ".txt")
		if(not Emailer.file_exists(aut)):
			return
		srtd_data = codecs.open(aut, "r", "utf_8")
		gold_data = codecs.open(gold_dir, "r", "utf_8")
		gold_dat = gold_data.read()
		srtd_dat = srtd_data.read()
		if (not(gold_dat == srtd_dat)):
			diff_dir = Emailer.make_local_path(test_case.output_dir, test_data.image_name, test_data.image_name+gld+"-Diff.txt")
			diff_file = codecs.open(diff_dir, "wb", "utf_8") 
			dffcmdlst = ["diff", test_data.sorted_data_file, gold_dir]
			subprocess.call(dffcmdlst, stdout = diff_file)
			global attachl
			global errorem
			global failedbool
			attachl.append(diff_dir)
			errorem += test_data.image_name + ":There was a database TestDifference in the file " + gld + ".\n"
			printerror(test_data, "There was a TestDifference in the Database data for " + test_data.image_name + " for the file " + gld + ".\n")
			failedbool = True
			global imgfail
			imgfail = True

        # Compare merged error log files
	def _compare_errors(test_data):
		gold_dir = Emailer.make_path(test_case.img_gold,  test_data.image_name, test_data.image_name + "SortedErrors.txt")
		if(not Emailer.file_exists(gold_dir)):
				gold_dir = Emailer.make_path(test_case.img_gold_parse, test_data.image_name, test_data.image_name + "SortedErrors.txt")
		common_log = codecs.open(test_data.sorted_log, "r", "utf_8")
		gold_log = codecs.open(gold_dir, "r", "utf_8")
		gold_dat = gold_log.read()
		common_dat = common_log.read()
		patrn = re.compile("\d")
		if (not((re.sub(patrn, 'd', gold_dat)) == (re.sub(patrn, 'd', common_dat)))):
			diff_dir = Emailer.make_local_path(test_case.output_dir, test_data.image_name, test_data.image_name+"AutopsyErrors-Diff.txt")
			diff_file = open(diff_dir, "w") 
			dffcmdlst = ["diff", test_data.sorted_log, gold_dir]
			subprocess.call(dffcmdlst, stdout = diff_file)
			global attachl
			global errorem
			global failedbool
			attachl.append(test_data.sorted_log)
			attachl.append(diff_dir)
			errorem += test_data.image_name + ":There was a TestDifference in the exceptions Log.\n"
			printerror(test_data, "Exceptions didn't match.\n")
			failedbool = True
			global imgfail
			imgfail = True
			
	# Compare the html report file made by
	# the regression test against the gold standard html report
	def _compare_to_gold_html(test_data):
		gold_html_file = Emailer.make_path(test_case.img_gold, test_data.image_name, "Report", "index.html")
		if(not Emailer.file_exists(gold_html_file)):
			gold_html_file = Emailer.make_path(test_case.img_gold_parse, test_data.image_name, "Report", "index.html")
		htmlfolder = ""
		for fs in os.listdir(Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports")):
			if os.path.isdir(Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports", fs)):
				htmlfolder = fs
		autopsy_html_path = Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports", htmlfolder, "HTML Report")
		
		
		try:
			autopsy_html_file = get_file_in_dir(autopsy_html_path, "index.html")
			if not Emailer.file_exists(gold_html_file):
				printerror(test_data, "Error: No gold html report exists at:")
				printerror(test_data, gold_html_file + "\n")
				return
			if not Emailer.file_exists(autopsy_html_file):
				printerror(test_data, "Error: No test_case html report exists at:")
				printerror(test_data, autopsy_html_file + "\n")
				return
			#Find all gold .html files belonging to this test_case
			ListGoldHTML = []
			for fs in os.listdir(Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports", htmlfolder)):
				if(fs.endswith(".html")):
					ListGoldHTML.append(Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports", htmlfolder, fs))
			#Find all new .html files belonging to this test_case
			ListNewHTML = []
			if(os.path.exists(Emailer.make_path(test_case.img_gold, test_data.image_name))):
				for fs in os.listdir(Emailer.make_path(test_case.img_gold, test_data.image_name)):
					if (fs.endswith(".html")):
						ListNewHTML.append(Emailer.make_path(test_case.img_gold, test_data.image_name, fs))
			if(not test_case.img_gold_parse == "" or test_case.img_gold == test_case.img_gold_parse):
				if(Emailer.file_exists(Emailer.make_path(test_case.img_gold_parse, test_data.image_name))):
					for fs in os.listdir(Emailer.make_path(test_case.img_gold_parse,test_data.image_name)):
						if (fs.endswith(".html")):
							ListNewHTML.append(Emailer.make_path(test_case.img_gold_parse, test_data.image_name, fs))
			#ensure both reports have the same number of files and are in the same order
			if(len(ListGoldHTML) != len(ListNewHTML)):
				printerror(test_data, "The reports did not have the same number of files. One of the reports may have been corrupted")
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
			print_report(test_data, errors, "REPORT COMPARISON", okay)
			if total["Gold"] == total["New"]:
				test_data.report_passed = True
			else:
				printerror(test_data, "The reports did not match each other.\n " + errors[0] +" and the " + errors[1])
		except FileNotFoundException as e:
			e.print_error()
		except DirNotFoundException as e:
			e.print_error()
		except Exception as e:
			printerror(test_data, "Error: Unknown fatal error comparing reports.")
			printerror(test_data, str(e) + "\n")
			logging.critical(traceback.format_exc())
		
class TestData:
	def __init__(self):
		self.image = ""
		self.image_file = ""
		self.image_name = ""
		self.sorted_log = ""
		self.warning_log = ""
		self.autopsy_data_file = ""
		self.sorted_data_file = ""
		self.common_log_path = ""
		self.antlog_dir = ""
		self.test_dbdump = ""
		self.total_test_time = ""
		self.start_date = ""
		self.end_date = ""
		self.total_ingest_time = ""
		self.artifact_count = 0
		self.artifact_fail = 0
		self.heap_space = ""
		self.service_times = ""
		self.report_passed = False
		# Error tracking
		self.printerror = []
		self.printout = []
	def reset(self):
		self.image = ""
		self.image_file = ""
		self.image_name = ""
		self.sorted_log = ""
		self.warning_log = ""
		self.autopsy_data_file = ""
		self.sorted_data_file = ""
		self.common_log_path = ""
		self.antlog_dir = ""
		self.test_dbdump = ""
		self.total_test_time = ""
		self.start_date = ""
		self.end_date = ""
		self.total_ingest_time = ""
		self.artifact_count = 0
		self.artifact_fail = 0
		self.heap_space = ""
		self.service_times = ""
		# Error tracking
		self.printerror = []
		self.printout = []
			
class Reports:
	def generate_reports(csv_path, database, test_data):
		Reports._generate_html(database, test_data)
		if test_case.global_csv:
			Reports._generate_csv(test_case.global_csv, database, test_data)
		else:
			Reports._generate_csv(csv_path, database, test_data)
		
	# Generates the HTML log file
	def _generate_html(database, test_data):
		# If the file doesn't exist yet, this is the first test_case to run for
		# this test, so we need to make the start of the html log
		global imgfail
		if not Emailer.file_exists(test_case.html_log):
			Reports.write_html_head()
		try:
			global html
			html = open(test_case.html_log, "a")
			# The image title
			title = "<h1><a name='" + test_data.image_name + "'>" + test_data.image_name + " \
						<span>tested on <strong>" + socket.gethostname() + "</strong></span></a></h1>\
					 <h2 align='center'>\
					 <a href='#" + test_data.image_name + "-errors'>Errors and Warnings</a> |\
					 <a href='#" + test_data.image_name + "-info'>Information</a> |\
					 <a href='#" + test_data.image_name + "-general'>General Output</a> |\
					 <a href='#" + test_data.image_name + "-logs'>Logs</a>\
					 </h2>"
			# The script errors found
			if imgfail:
				ids = 'errors1'
			else:
				ids = 'errors'
			errors = "<div id='" + ids + "'>\
					  <h2><a name='" + test_data.image_name + "-errors'>Errors and Warnings</a></h2>\
					  <hr color='#FF0000'>"
			# For each error we have logged in the test_case
			for error in test_data.printerror:
				# Replace < and > to avoid any html display errors
				errors += "<p>" + error.replace("<", "&lt").replace(">", "&gt") + "</p>"
				# If there is a \n, we probably want a <br /> in the html
				if "\n" in error:
					errors += "<br />"
			errors += "</div>"
			
			# Links to the logs
			logs = "<div id='logs'>\
					<h2><a name='" + test_data.image_name + "-logs'>Logs</a></h2>\
					<hr color='#282828'>"
			logs_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "logs")
			for file in os.listdir(logs_path):
				logs += "<p><a href='file:\\" + Emailer.make_path(logs_path, file) + "' target='_blank'>" + file + "</a></p>"
			logs += "</div>"
			
			# All the testing information
			info = "<div id='info'>\
					<h2><a name='" + test_data.image_name + "-info'>Information</a></h2>\
					<hr color='#282828'>\
					<table cellspacing='5px'>"
			# The individual elements
			info += "<tr><td>Image Path:</td>"
			info += "<td>" + test_data.image_file + "</td></tr>"
			info += "<tr><td>Image Name:</td>"
			info += "<td>" + test_data.image_name + "</td></tr>"
			info += "<tr><td>test_case Output Directory:</td>"
			info += "<td>" + test_case.output_dir + "</td></tr>"
			info += "<tr><td>Autopsy Version:</td>"
			info += "<td>" + test_case.autopsy_version + "</td></tr>"
			info += "<tr><td>Heap Space:</td>"
			info += "<td>" + test_data.heap_space + "</td></tr>"
			info += "<tr><td>Test Start Date:</td>"
			info += "<td>" + test_data.start_date + "</td></tr>"
			info += "<tr><td>Test End Date:</td>"
			info += "<td>" + test_data.end_date + "</td></tr>"
			info += "<tr><td>Total Test Time:</td>"
			info += "<td>" + test_data.total_test_time + "</td></tr>"
			info += "<tr><td>Total Ingest Time:</td>"
			info += "<td>" + test_data.total_ingest_time + "</td></tr>"
			info += "<tr><td>Exceptions Count:</td>"
			info += "<td>" + str(len(get_exceptions(test_data))) + "</td></tr>"
			info += "<tr><td>Autopsy OutOfMemoryExceptions:</td>"
			info += "<td>" + str(len(search_logs("OutOfMemoryException", test_data))) + "</td></tr>"
			info += "<tr><td>Autopsy OutOfMemoryErrors:</td>"
			info += "<td>" + str(len(search_logs("OutOfMemoryError", test_data))) + "</td></tr>"
			info += "<tr><td>Tika OutOfMemoryErrors/Exceptions:</td>"
			info += "<td>" + str(Reports._get_num_memory_errors("tika", test_data)) + "</td></tr>"
			info += "<tr><td>Solr OutOfMemoryErrors/Exceptions:</td>"
			info += "<td>" + str(Reports._get_num_memory_errors("solr", test_data)) + "</td></tr>"
			info += "<tr><td>TskCoreExceptions:</td>"
			info += "<td>" + str(len(search_log_set("autopsy", "TskCoreException", test_data))) + "</td></tr>"
			info += "<tr><td>TskDataExceptions:</td>"
			info += "<td>" + str(len(search_log_set("autopsy", "TskDataException", test_data))) + "</td></tr>"
			info += "<tr><td>Ingest Messages Count:</td>"
			info += "<td>" + str(test_case.ingest_messages) + "</td></tr>"
			info += "<tr><td>Indexed Files Count:</td>"
			info += "<td>" + str(test_case.indexed_files) + "</td></tr>"
			info += "<tr><td>Indexed File Chunks Count:</td>"
			info += "<td>" + str(test_case.indexed_chunks) + "</td></tr>"
			info += "<tr><td>Out Of Disk Space:\
							 <p style='font-size: 11px;'>(will skew other test results)</p></td>"
			info += "<td>" + str(len(search_log_set("autopsy", "Stopping ingest due to low disk space on disk", test_data))) + "</td></tr>"
			info += "<tr><td>TSK Objects Count:</td>"
			info += "<td>" + str(database.autopsy_objects) + "</td></tr>"
			info += "<tr><td>Artifacts Count:</td>"
			info += "<td>" + str(database.get_artifacts_count()) + "</td></tr>"
			info += "<tr><td>Attributes Count:</td>"
			info += "<td>" + str(database.autopsy_attributes) + "</td></tr>"
			info += "</table>\
					 </div>"
			# For all the general print statements in the test_case
			output = "<div id='general'>\
					  <h2><a name='" + test_data.image_name + "-general'>General Output</a></h2>\
					  <hr color='#282828'>"
			# For each printout in the test_case's list
			for out in test_data.printout:
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
			printerror(test_data, "Error: Unknown fatal error when creating HTML log at:")
			printerror(test_data, test_case.html_log)
			printerror(test_data, str(e) + "\n")
			logging.critical(traceback.format_exc())

	# Writed the top of the HTML log file
	def write_html_head():
		print(test_case.html_log)
		html = open(str(test_case.html_log), "a")
		head = "<html>\
				<head>\
				<title>AutopsyTesttest_case Output</title>\
				</head>\
				<style type='text/css'>\
				body { font-family: 'Courier New'; font-size: 12px; }\
				h1 { background: #444; margin: 0px auto; padding: 0px; color: #FFF; border: 1px solid #000; font-family: Tahoma; text-align: center; }\
				h1 span { font-size: 12px; font-weight: 100; }\
				h2 { font-family: Tahoma; padding: 0px; margin: 0px; }\
				hr { width: 100%; height: 1px; border: none; margin-top: 10px; margin-bottom: 10px; }\
				#errors { background: #CCCCCC; border: 1px solid #282828; color: #282828; padding: 10px; margin: 20px; }\
				#errors1 { background: #CC0000; border: 1px solid #282828; color: #282828; padding: 10px; margin: 20px; }\
				#info { background: #CCCCCC; border: 1px solid #282828; color: #282828; padding: 10px; margin: 20px; }\
				#general { background: #CCCCCC; border: 1px solid #282828; color: #282828; padding: 10px; margin: 20px; }\
				#logs { background: #CCCCCC; border: 1px solid #282828; color: #282828; padding: 10px; margin: 20px; }\
				#errors p, #info p, #general p, #logs p { pading: 0px; margin: 0px; margin-left: 5px; }\
				#info table td { color: ##282828; font-size: 12px; min-width: 225px; }\
				#logs a { color: ##282828; }\
				</style>\
				<body>"
		html.write(head)
		html.close()

	# Writed the bottom of the HTML log file
	def write_html_foot():
		html = open(test_case.html_log, "a")
		head = "</body></html>"
		html.write(head)
		html.close()

	# Adds all the image names to the HTML log for easy access
	def html_add_images(full_image_names):
		# If the file doesn't exist yet, this is the first test_case to run for
		# this test, so we need to make the start of the html log
		if not Emailer.file_exists(test_case.html_log):
			Reports.write_html_head()
		html = open(test_case.html_log, "a")
		links = []
		for full_name in full_image_names:
			name = test_case.get_image_name(full_name)
			links.append("<a href='#" + name + "(0)'>" + name + "</a>")
		html.write("<p align='center'>" + (" | ".join(links)) + "</p>")

	# Generate the CSV log file
	def _generate_csv(csv_path, database, test_data):
		try:
			# If the CSV file hasn't already been generated, this is the
			# first run, and we need to add the column names
			if not Emailer.file_exists(csv_path):
				Reports.csv_header(csv_path)
			# Now add on the fields to a new row
			csv = open(csv_path, "a")
			
			# Variables that need to be written
			vars = []
			vars.append( test_data.image_file )
			vars.append( test_data.image_name )
			vars.append( test_case.output_dir )
			vars.append( socket.gethostname() )
			vars.append( test_case.autopsy_version )
			vars.append( test_data.heap_space )
			vars.append( test_data.start_date )
			vars.append( test_data.end_date )
			vars.append( test_data.total_test_time )
			vars.append( test_data.total_ingest_time )
			vars.append( test_data.service_times )
			vars.append( str(len(get_exceptions(test_data))) )
			vars.append( str(Reports._get_num_memory_errors("autopsy", test_data)) )
			vars.append( str(Reports._get_num_memory_errors("tika", test_data)) )
			vars.append( str(Reports._get_num_memory_errors("solr", test_data)) )
			vars.append( str(len(search_log_set("autopsy", "TskCoreException", test_data))) )
			vars.append( str(len(search_log_set("autopsy", "TskDataException", test_data))) )
			vars.append( str(test_case.ingest_messages) )
			vars.append( str(test_case.indexed_files) )
			vars.append( str(test_case.indexed_chunks) )
			vars.append( str(len(search_log_set("autopsy", "Stopping ingest due to low disk space on disk", test_data))) )
			vars.append( str(database.autopsy_objects) )
			vars.append( str(database.get_artifacts_count()) )
			vars.append( str(database.autopsy_attributes) )
			vars.append( Emailer.make_local_path("gold", test_data.image_name, test_case.test_db_file) )
			vars.append( database.get_artifact_comparison() )
			vars.append( database.get_attribute_comparison() )
			vars.append( Emailer.make_local_path("gold", test_data.image_name, "standard.html") )
			vars.append( str(test_data.report_passed) )
			vars.append( test_case.ant_to_string() )
			# Join it together with a ", "
			output = "|".join(vars)
			output += "\n"
			# Write to the log!
			csv.write(output)
			csv.close()
		except Exception as e:
			printerror(test_data, "Error: Unknown fatal error when creating CSV file at:")
			printerror(test_data, csv_path)
			printerror(test_data, str(e) + "\n")
			print(traceback.format_exc())
			logging.critical(traceback.format_exc())

	# Generates the CSV header (column names)
	def csv_header(csv_path):
		csv = open(csv_path, "w")
		titles = []
		titles.append("Image Path")
		titles.append("Image Name")
		titles.append("Output test_case Directory")
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

	# Returns the number of OutOfMemoryErrors and OutOfMemoryExceptions
	# for a certain type of log
	def _get_num_memory_errors(type, test_data):
		return (len(search_log_set(type, "OutOfMemoryError", test_data)) + 
				len(search_log_set(type, "OutOfMemoryException", test_data)))

class Logs:
	def generate_log_data(test_data):
		Logs._generate_common_log(test_data)
		try:
			Logs._fill_test_case_data(test_data)
		except Exception as e:
			printerror(test_data, "Error: Unknown fatal error when filling test_case data.")
			printerror(test_data, str(e) + "\n")
			logging.critical(traceback.format_exc())
		# If running in verbose mode (-v)
		if test_case.args.verbose:
			errors = Logs._report_all_errors()
			okay = "No warnings or errors in any log files."
			print_report(test_data, errors, "VERBOSE", okay)
	# Generate the "common log": a log of all exceptions and warnings
	# from each log file generated by Autopsy
	def _generate_common_log(test_data):
		try:
			logs_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "logs")
			common_log = codecs.open(test_case.common_log_path, "w", "utf_8")
			warning_log = codecs.open(test_data.warning_log, "w", "utf_8")
			common_log.write("--------------------------------------------------\n")
			common_log.write(test_data.image_name + "\n")
			common_log.write("--------------------------------------------------\n")
			rep_path = Emailer.make_local_path(test_case.output_dir)
			rep_path = rep_path.replace("\\\\", "\\")
			for file in os.listdir(logs_path):
				log = codecs.open(Emailer.make_path(logs_path, file), "r", "utf_8")
				for line in log:
					line = line.replace(rep_path, "test_data")
					if line.startswith("Exception"):
						common_log.write(file +": " +  line)
					elif line.startswith("Error"):
						common_log.write(file +": " +  line)
					elif line.startswith("SEVERE"):
						common_log.write(file +":" +  line)
					else:
						warning_log.write(file +": " +  line)
				log.close()
			common_log.write("\n")
			common_log.close()
			print(test_data.sorted_log)
			srtcmdlst = ["sort", test_case.common_log_path, "-o", test_data.sorted_log]
			subprocess.call(srtcmdlst)
		except Exception as e:
			printerror(test_data, "Error: Unable to generate the common log.")
			printerror(test_data, str(e) + "\n")
			printerror(test_data, traceback.format_exc())
			logging.critical(traceback.format_exc())

	# Fill in the global test_case's variables that require the log files
	def _fill_test_case_data(test_data):
		try:
			# Open autopsy.log.0
			log_path = Emailer.make_path(test_case.output_dir, test_data.image_name, "logs", "autopsy.log.0")
			log = open(log_path)
			
			# Set the test_case starting time based off the first line of autopsy.log.0
			# *** If logging time format ever changes this will break ***
			test_data.start_date = log.readline().split(" org.")[0]
		
			# Set the test_case ending time based off the "create" time (when the file was copied)
			test_data.end_date = time.ctime(os.path.getmtime(log_path))
		except Exception as e:
			printerror(test_data, "Error: Unable to open autopsy.log.0.")
			printerror(test_data, str(e) + "\n")
			logging.warning(traceback.format_exc())
		# Set the test_case total test time
		# Start date must look like: "Jul 16, 2012 12:57:53 PM"
		# End date must look like: "Mon Jul 16 13:02:42 2012"
		# *** If logging time format ever changes this will break ***
		start = datetime.datetime.strptime(test_data.start_date, "%b %d, %Y %I:%M:%S %p")
		end = datetime.datetime.strptime(test_data.end_date, "%a %b %d %H:%M:%S %Y")
		test_data.total_test_time = str(end - start)

		try:
			# Set Autopsy version, heap space, ingest time, and service times
			
			version_line = search_logs("INFO: Application name: Autopsy, version:", test_data)[0]
			test_case.autopsy_version = Emailer.get_word_at(version_line, 5).rstrip(",")
			
			test_data.heap_space = search_logs("Heap memory usage:", test_data)[0].rstrip().split(": ")[1]
			
			ingest_line = search_logs("Ingest (including enqueue)", test_data)[0]
			test_data.total_ingest_time = Emailer.get_word_at(ingest_line, 6).rstrip()
			
			message_line = search_log_set("autopsy", "Ingest messages count:", test_data)[0]
			test_case.ingest_messages = int(message_line.rstrip().split(": ")[2])
			
			files_line = search_log_set("autopsy", "Indexed files count:", test_data)[0]
			test_case.indexed_files = int(files_line.rstrip().split(": ")[2])
			
			chunks_line = search_log_set("autopsy", "Indexed file chunks count:", test_data)[0]
			test_case.indexed_chunks = int(chunks_line.rstrip().split(": ")[2])
		except Exception as e:
			printerror(test_data, "Error: Unable to find the required information to fill test_case data.")
			printerror(test_data, str(e) + "\n")
			logging.critical(traceback.format_exc())
			print(traceback.format_exc())
		try:
			service_lines = search_log("autopsy.log.0", "to process()", test_data)
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
			test_data.service_times = "; ".join(service_list)
		except Exception as e:
			printerror(test_data, "Error: Unknown fatal error when finding service times.")
			printerror(test_data, str(e) + "\n")
			logging.critical(traceback.format_exc())
	
	# Returns all the errors found in the common log in a list
	def _report_all_errors():
		try:
			return get_warnings() + get_exceptions()
		except Exception as e:
			printerror(test_data, "Error: Unknown fatal error when reporting all errors.")
			printerror(test_data, str(e) + "\n")
			logging.warning(traceback.format_exc())
	# Searches the common log for any instances of a specific string.
	def search_common_log(string, test_data):
		results = []
		log = codecs.open(test_case.common_log_path, "r", "utf_8")
		for line in log:
			if string in line:
				results.append(line)
		log.close()
		return results

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
		
# Search through all the known log files for a specific string.
# Returns a list of all lines with that string
def search_logs(string, test_data):
	logs_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		log = codecs.open(Emailer.make_path(logs_path, file), "r", "utf_8")
		for line in log:
			if string in line:
				results.append(line)
		log.close()
	return results
	
# Searches the given log for the given string
# Returns a list of all lines with that string
def search_log(log, string, test_data):
	logs_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "logs", log)
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
def search_log_set(type, string, test_data):
	logs_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		if type in file:
			log = codecs.open(Emailer.make_path(logs_path, file), "r", "utf_8")
			for line in log:
				if string in line:
					results.append(line)
			log.close()
	return results
		
# Print a report for the given errors with the report name as name
# and if no errors are found, print the okay message
def print_report(test_data, errors, name, okay):
	if errors:
		printerror(test_data, "--------< " + name + " >----------")
		for error in errors:
			printerror(test_data, str(error))
		printerror(test_data, "--------< / " + name + " >--------\n")
	else:
		printout(test_data, "-----------------------------------------------------------------")
		printout(test_data, "< " + name + " - " + okay + " />")
		printout(test_data, "-----------------------------------------------------------------\n")

# Used instead of the print command when printing out an error
def printerror(test_data, string):
	print(string)
	test_data.printerror.append(string)

# Used instead of the print command when printing out anything besides errors
def printout(test_data, string):
	print(string)
	test_data.printout.append(string)

#----------------------------------#
#		 Helper functions		   #
#----------------------------------#
# Returns a list of all the exceptions listed in all the autopsy logs
def get_exceptions(test_data):
	exceptions = []
	logs_path = Emailer.make_path(test_case.output_dir, test_data.image_name, "logs")
	results = []
	for file in os.listdir(logs_path):
		if "autopsy.log" in file:
			log = codecs.open(Emailer.make_path(logs_path, file), "r", "utf_8")
			ex = re.compile("\SException")
			er = re.compile("\SError")
			for line in log:
				if ex.search(line) or er.search(line):
					exceptions.append(line)
			log.close()
	return exceptions
	
# Returns a list of all the warnings listed in the common log
def get_warnings(test_data):
	warnings = []
	common_log = codecs.open(test_data.warning_log, "r", "utf_8")
	for line in common_log:
		if "warning" in line.lower():
			warnings.append(line)
	common_log.close()
	return warnings

def copy_logs(test_data):
	try:
		log_dir = os.path.join("..", "..", "Testing","build","test","qa-functional","work","userdir0","var","log")
		shutil.copytree(log_dir, Emailer.make_local_path(test_case.output_dir, test_data.image_name, "logs"))
	except Exception as e:
		printerror(test_data,"Error: Failed to copy the logs.")
		printerror(test_data,str(e) + "\n")
		logging.warning(traceback.format_exc())
# Clears all the files from a directory and remakes it
def clear_dir(dir):
	try:
		if Emailer.dir_exists(dir):
			shutil.rmtree(dir)
		os.makedirs(dir)
		return True;
	except Exception as e:
		printerror(test_data,"Error: Cannot clear the given directory:")
		printerror(test_data,dir + "\n")
		print(str(e))
		return False;

def del_dir(dir):
	try:
		if Emailer.dir_exists(dir):
			shutil.rmtree(dir)
		return True;
	except:
		printerror(test_data,"Error: Cannot delete the given directory:")
		printerror(test_data,dir + "\n")
		return False;
#Copies a given file from "ffrom" to "to"
def copy_file(ffrom, to):
	try :
		shutil.copy(ffrom, to)
	except Exception as e:
		print(str(e))
		print(traceback.format_exc())

# Copies a directory file from "ffrom" to "to"
def copy_dir(ffrom, to):
	try :
		if not os.path.isdir(ffrom):
			raise FileNotFoundException(ffrom)
		shutil.copytree(ffrom, to)
	except:
		raise FileNotFoundException(to)
# Returns the first file in the given directory with the given extension
def get_file_in_dir(dir, ext):
	try:
		for file in os.listdir(dir):
			if file.endswith(ext):
				return Emailer.make_path(dir, file)
		# If nothing has been found, raise an exception
		raise FileNotFoundException(dir)
	except:
		raise DirNotFoundException(dir)
		
def find_file_in_dir(dir, name, ext):
	try: 
		for file in os.listdir(dir):
			if file.startswith(name):
				if file.endswith(ext):
					return Emailer.make_path(dir, file)
		raise FileNotFoundException(dir)
	except:
		raise DirNotFoundException(dir)
		
def setDay():
	global Day
	Day = int(strftime("%d", localtime()))
		
def getLastDay():
	return Day
		
def getDay():
	return int(strftime("%d", localtime()))
		
def newDay():
	return getLastDay() != getDay()

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
  -fr			Will not try download gold standard images
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
		printerror(test_data,"Error: File could not be found at:")
		printerror(test_data,self.file + "\n")
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
		printerror(test_data, "Error: Directory could not be found at:")
		printerror(test_data, self.dir + "\n")
	def error(self):
		error = "Error: Directory could not be found at:\n" + self.dir + "\n"
		return error

#############################
#	Main Testing Functions  #
#############################
class Test_Runner:

	#Executes the tests, makes continuous testing easier 
        # Identifies the tests to run and runs the tests
	def execute_test():
		global parsed
		global errorem
		global failedbool
		global html
		global attachl

                # Setup TestData object
		if(not Emailer.dir_exists(Emailer.make_path("..", "output", "results"))):
			os.makedirs(Emailer.make_path("..", "output", "results",))
		test_case.output_dir = Emailer.make_path("..", "output", "results", time.strftime("%Y.%m.%d-%H.%M.%S"))
		os.makedirs(test_case.output_dir)
		test_case.csv = Emailer.make_local_path(test_case.output_dir, "CSV.txt")
		test_case.html_log = Emailer.make_path(test_case.output_dir, "AutopsyTestCase.html")
		test_data = TestData()
		log_name = test_case.output_dir + "\\regression.log"
		logging.basicConfig(filename=log_name, level=logging.DEBUG)


                #Identify tests to run and populate test_case with list
		# If user wants to do a single file and a list (contradictory?)
		if test_case.args.single and test_case.args.list:
			printerror(test_data, "Error: Cannot run both from config file and on a single file.")
			return
		# If working from a configuration file
		if test_case.args.list:
		   if not Emailer.file_exists(test_case.args.config_file):
			   printerror(test_data, "Error: Configuration file does not exist at:")
			   printerror(test_data, test_case.args.config_file)
			   return
		   Test_Runner._fill_case_data(test_case.args.config_file,test_data)
		# Else if working on a single file
		elif test_case.args.single:
		   if not Emailer.file_exists(test_case.args.single_file):
			   printerror(test_data, "Error: Image file does not exist at:")
			   printerror(test_data, test_case.args.single_file)
			   return
		   Test_case.images.append(test_case.args.single_file)

		# If user has not selected a single file, and does not want to ignore
		#  the input directory, continue on to parsing ../input
		if (not test_case.args.single) and (not test_case.args.ignore) and (not test_case.args.list):
		   test_case.args.config_file = "config.xml"
		   if not Emailer.file_exists(test_case.args.config_file):
			   printerror(test_data, "Error: Configuration file does not exist at:")
			   printerror(test_data, test_case.args.config_file)
			   return
		   Test_Runner._fill_case_data(test_case.args.config_file, test_data)

                   # Cycle through images in test_case and run tests
		logres =[]
		for img in test_case.images:  
			if Emailer.file_exists(img):
				logres.append(Test_Runner._run_ingest(str(img), 0, test_data))
			else:
				printerror(test_data, "Warning: Image file listed in configuration does not exist:")
				printerror(value + "\n")
			test_data.reset()
		Reports.write_html_foot()
		html.close()
		if (len(logres)>0):
			failedbool = True
			imgfail = True
			passFail = False
			for lm in logres:
				for ln in lm:
					errorem += ln
		html.close()
		if failedbool:
			passFail = False
			errorem += "The test output didn't match the gold standard.\n"
			errorem += "Autopsy test failed.\n"
			attachl.insert(0, html.name)
		else:
			errorem += "Autopsy test passed.\n"
			passFail = True
			attachl = []
		Emailer.send_email(parsed, errorem, attachl, passFail)
			
	# Iterates through an XML configuration file to find all given elements.  Populates global test_case object with tests to run	
        # config_file: Path to the config file
        # test_data: TestData object (@@@ Only being passed in for print messages)
	def _fill_case_data(config_file, test_data):
		try:
			global parsed
			global errorem
			global attachl
			count = 0
			parsed = parse(config_file)
			logres = []
			test_case
			counts = {}
			if parsed.getElementsByTagName("indir"):
				test_case.input_dir = parsed.getElementsByTagName("indir")[0].getAttribute("value").encode().decode("utf_8")
			if parsed.getElementsByTagName("global_csv"):
				test_case.global_csv = parsed.getElementsByTagName("global_csv")[0].getAttribute("value").encode().decode("utf_8")
				test_case.global_csv = Emailer.make_local_path(test_case.global_csv)
			if parsed.getElementsByTagName("golddir"):
				test_case.gold_parse = parsed.getElementsByTagName("golddir")[0].getAttribute("value").encode().decode("utf_8")
				test_case.img_gold_parse = Emailer.make_path(test_case.gold_parse, 'tmp')
			else:
				test_case.gold_parse = test_case.gold
				test_case.img_gold_parse = Emailer.make_path(test_case.gold_parse, 'tmp')
			# Generate the top navbar of the HTML for easy access to all images
			values = []
			for element in parsed.getElementsByTagName("image"):
				value = element.getAttribute("value").encode().decode("utf_8")
				if Emailer.file_exists(value):
					test_case.images.append(value)
				else:
					printout(test_data, "File: " + value + " doesn't exist")
			count = len(values)
			archives = Emailer.make_path(test_case.gold, "..")
			arcount = 0
			for file in os.listdir(archives):
				if not(file == 'tmp'):
					arcount+=1
			if (count > arcount):
				print("******Alert: There are more input images than gold standards, some images will not be properly tested.\n")
			elif not (arcount == count):
				print("******Alert: There are more gold standards than input images, this will not check all gold Standards.\n")
			Reports.html_add_images(values)
		except Exception as e:
			printerror(test_data, "Error: There was an error running with the configuration file.")
			printerror(test_data, str(e) + "\n")
			logging.critical(traceback.format_exc())
			print(traceback.format_exc())

        # Run autopsy to generate output file and do comparision
        # image_file: Path to image (@@@ We should be able to remove this since TestData should have this)
        # count: Number of times this image has been tested (@@@ we think we can remove this)
        # test_data: TestData object
	def _run_ingest(image_file, count, test_data):
		global parsed
		global imgfail
		global failedbool
		imgfail = False
		if image_type(image_file) == IMGTYPE.UNKNOWN:
			printerror(test_data, "Error: Image type is unrecognized:")
			printerror(test_data, image_file + "\n")
			return
			

		# Set the test_case to work for this test
		test_data.image_file = image_file
		test_data.image_name = test_case.get_image_name(image_file) + "(" + str(count) + ")"
		test_data.autopsy_data_file = Emailer.make_path(test_case.output_dir, test_data.image_name, test_data.image_name + "Autopsy_data.txt")
		test_data.sorted_data_file = Emailer.make_path(test_case.output_dir, test_data.image_name, "Sorted_Autopsy_data.txt")
		test_data.warning_log = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "AutopsyLogs.txt")
		test_data.antlog_dir = Emailer.make_local_path(test_case.output_dir, test_data.image_name, "antlog.txt")
		test_data.test_dbdump = Emailer.make_path(test_case.output_dir, test_data.image_name,
											  test_data.image_name + "Dump.txt")
		test_data.image = test_case.get_image_name(image_file)
		if(test_case.args.list):
			element = parsed.getElementsByTagName("build")
			if(len(element)<=0):
				toval = Emailer.make_path("..", "build.xml")
			else:
				element = element[0]
				toval = element.getAttribute("value").encode().decode("utf_8")
				if(toval==None):
					toval = Emailer.make_path("..", "build.xml")
		else:
			toval = Emailer.make_path("..", "build.xml")
		test_case.build_path = toval	
		test_case.known_bad_path = Emailer.make_path(test_case.input_dir, "notablehashes.txt-md5.idx")
		test_case.keyword_path = Emailer.make_path(test_case.input_dir, "notablekeywords.xml")
		test_case.nsrl_path = Emailer.make_path(test_case.input_dir, "nsrl.txt-md5.idx")
		logging.debug("--------------------")
		logging.debug(test_data.image_name)
		logging.debug("--------------------")
		Test_Runner._run_ant(test_data)
		time.sleep(2) # Give everything a second to process


                # Autopsy has finished running, we will now process the results
		test_case.common_log_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, test_data.image_name+test_case.common_log)
		
                # get data for the follow on db diff
		Database.get_Data(test_data)

                # merges logs into a single log for later diff
		copy_logs(test_data)
		test_data.sorted_log = Emailer.make_local_path(test_case.output_dir, test_data.image_name, test_data.image_name + "SortedErrors.txt")
		Logs.generate_log_data(test_data)

                # Look for existance of core exceptions (@@@ Should be moved to differ class)
		logres = Logs.search_common_log("TskCoreException", test_data)

		# Cleanup SOLR: If NOT keeping Solr index (-k)
		if not test_case.args.keep:
			solr_index = Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "ModuleOutput", "KeywordSearch")
			if clear_dir(solr_index):
				print_report(test_data, [], "DELETE SOLR INDEX", "Solr index deleted.")
		elif test_case.args.keep:
			print_report(test_data, [], "KEEP SOLR INDEX", "Solr index has been kept.")

		# If running in exception mode, print exceptions to log
		if test_case.args.exception:
			exceptions = search_logs(test_case.args.exception_string, test_data)
			okay = "No warnings or exceptions found containing text '" + test_case.args.exception_string + "'."
			print_report(test_data, exceptions, "EXCEPTION", okay)

                 
		database = Database(test_data)

		# Now either diff or rebuild
		if not test_case.args.rebuild:
			Test_Runner._run_test(image_file, database, test_data)
		# If running in rebuild mode (-r)
		else:
			Test_Runner.rebuild(test_data)

                # @@@ COnsider if we want to do this for a rebuild. 
		# Make the CSV log and the html log viewer
		Reports.generate_reports(test_case.csv, database, test_data)
		# Reset the test_case and return the tests sucessfully finished
		clear_dir(Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "ModuleOutput", "keywordsearch"))
		if(failedbool):
			attachl.append(test_case.common_log_path)
		test_case.reset()
		return logres
		
	# Compares results for a single test.  Autopsy has already been run.
        # This should all be moved into TestDiffer.run_diff()
        # image_file: Path to image (@@@ Shoudl go away in favor of test_data)
        # database: Database object created based on test_data 
        # test_data: TestData object
	def _run_test(image_file, database, test_data):
		try:
			gold_path = test_case.gold
                        # Tmp location to extract ZIP file into
			img_gold = Emailer.make_path(test_case.gold, "tmp", test_data.image_name)

                        # Open gold archive file
			img_archive = Emailer.make_path("..", "output", "gold", test_data.image_name+"-archive.zip")
			if(not Emailer.file_exists(img_archive)):
				img_archive = Emailer.make_path(test_case.gold_parse, test_data.image_name+"-archive.zip")
				gold_path = test_case.gold_parse
				img_gold = Emailer.make_path(gold_path, "tmp", test_data.image_name)
			extrctr = zipfile.ZipFile(img_archive, 'r', compression=zipfile.ZIP_DEFLATED)
			extrctr.extractall(gold_path)
			extrctr.close
			time.sleep(2)

                        # Compare database count to gold (@@@ This should all be done as part of TestDiffer.run_diff())
			Database.compare_to_gold_db(test_data, database)

                        # Compare other data to gold
			TestResultsDiffer.run_diff(test_data)
			del_dir(img_gold)
		except Exception as e:
			printerror(test_data, "Tests failed due to an error, try rebuilding or creating gold standards.\n")
			printerror(test_data, str(e) + "\n")
			print(traceback.format_exc())
		
	# Rebuilds the gold standards by copying the test-generated database
	# and html report files into the gold directory. Autopsy has already been run
	def rebuild(test_data):
		# Errors to print
		errors = []
		if(test_case.gold_parse == "" ):
			test_case.gold_parse = test_case.gold
			test_case.img_gold_parse = test_case.img_gold
		# Delete the current gold standards
		gold_dir = test_case.img_gold_parse
		clear_dir(test_case.img_gold_parse)
		tmpdir = Emailer.make_path(gold_dir, test_data.image_name)
		dbinpth = Emailer.make_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, test_case.test_db_file)
		dboutpth = Emailer.make_path(tmpdir, test_case.test_db_file)
		dataoutpth = Emailer.make_path(tmpdir, test_data.image_name + "SortedData.txt")
		dbdumpinpth = test_data.test_dbdump
		dbdumpoutpth = Emailer.make_path(tmpdir, test_data.image_name + "DBDump.txt")
		if not os.path.exists(test_case.img_gold_parse):
			os.makedirs(test_case.img_gold_parse)
		if not os.path.exists(gold_dir):
			os.makedirs(gold_dir)
		if not os.path.exists(tmpdir):
			os.makedirs(tmpdir)
		try:
			copy_file(dbinpth, dboutpth)
			if Emailer.file_exists(test_data.sorted_data_file):
				copy_file(test_data.sorted_data_file, dataoutpth)
			copy_file(dbdumpinpth, dbdumpoutpth)
			error_pth = Emailer.make_path(tmpdir, test_data.image_name+"SortedErrors.txt")
			copy_file(test_data.sorted_log, error_pth)
		except Exception as e:
			printerror(test_data, str(e))
			print(str(e))
			print(traceback.format_exc())
		# Rebuild the HTML report
		htmlfolder = ""
		for fs in os.listdir(os.path.join(os.getcwd(),test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports")):
			if os.path.isdir(os.path.join(os.getcwd(), test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports", fs)):
				htmlfolder = fs
		autopsy_html_path = Emailer.make_local_path(test_case.output_dir, test_data.image_name, test_case.Img_Test_Folder, "Reports", htmlfolder)
		
		html_path = Emailer.make_path(test_case.output_dir, test_data.image_name,
									 test_case.Img_Test_Folder, "Reports")
		try:
			if not os.path.exists(Emailer.make_path(tmpdir, htmlfolder)):
				os.makedirs(Emailer.make_path(tmpdir, htmlfolder))
			for file in os.listdir(autopsy_html_path):
				html_to = Emailer.make_path(tmpdir, file.replace("HTML Report", "Report"))
				copy_dir(get_file_in_dir(autopsy_html_path, file), html_to)
		except FileNotFoundException as e:
			errors.append(e.error)
		except Exception as e:
			errors.append("Error: Unknown fatal error when rebuilding the gold html report.")
			errors.append(str(e) + "\n")
			print(traceback.format_exc())
		oldcwd = os.getcwd()
		zpdir = gold_dir
		os.chdir(zpdir)
		os.chdir("..")
		img_gold = "tmp"
		img_archive = Emailer.make_path(test_data.image_name+"-archive.zip")
		comprssr = zipfile.ZipFile(img_archive, 'w',compression=zipfile.ZIP_DEFLATED)
		Test_Runner.zipdir(img_gold, comprssr)
		comprssr.close()
		os.chdir(oldcwd)
		del_dir(test_case.img_gold_parse)
		okay = "Sucessfully rebuilt all gold standards."
		print_report(test_data, errors, "REBUILDING", okay)

	def zipdir(path, zip):
		for root, dirs, files in os.walk(path):
			for file in files:
				zip.write(os.path.join(root, file))

	# Tests Autopsy with RegressionTest.java by by running
	# the build.xml file through ant
	def _run_ant(test_data):
		# Set up the directories
		test_case_path = os.path.join(test_case.output_dir, test_data.image_name)
		if Emailer.dir_exists(test_case_path):
			shutil.rmtree(test_case_path)
		os.makedirs(test_case_path)
		test_case.ant = ["ant"]
		test_case.ant.append("-v")
		test_case.ant.append("-f")
	#	case.ant.append(case.build_path)
		test_case.ant.append(os.path.join("..","..","Testing","build.xml"))
		test_case.ant.append("regression-test")
		test_case.ant.append("-l")
		test_case.ant.append(test_data.antlog_dir)
		test_case.ant.append("-Dimg_path=" + test_data.image_file)
		test_case.ant.append("-Dknown_bad_path=" + test_case.known_bad_path)
		test_case.ant.append("-Dkeyword_path=" + test_case.keyword_path)
		test_case.ant.append("-Dnsrl_path=" + test_case.nsrl_path)
		test_case.ant.append("-Dgold_path=" + Emailer.make_path(test_case.gold))
		test_case.ant.append("-Dout_path=" + Emailer.make_local_path(test_case.output_dir, test_data.image_name))
		test_case.ant.append("-Dignore_unalloc=" + "%s" % test_case.args.unallocated)
		test_case.ant.append("-Dtest.timeout=" + str(test_case.timeout))
		
		printout(test_data, "Ingesting Image:\n" + test_data.image_file + "\n")
		printout(test_data, "CMD: " + " ".join(test_case.ant))
		printout(test_data, "Starting test...\n")
		antoutpth = Emailer.make_local_path(test_case.output_dir, "antRunOutput.txt")
		antout = open(antoutpth, "a")
		if SYS is OS.CYGWIN:
			subprocess.call(test_case.ant, stdout=subprocess.PIPE)
		elif SYS is OS.WIN:
			theproc = subprocess.Popen(test_case.ant, shell = True, stdout=subprocess.PIPE)
			theproc.communicate()
		antout.close()
		
#----------------------#
#		 Main		   #
#----------------------#
def main():
	# Global variables
	global failedbool
	global inform
	global fl
	global test_case
	global errorem
	global attachl
	global daycount
	global redo
	global passed
	daycount = 0
	failedbool = False
	redo = False
	errorem = ""
	args = Args()
	test_case = TestAutopsy(args)
	attachl = []
	passed = False
	# The arguments were given wrong:
	if not args.parse():
		case.reset()
		return
	if(not args.fr):
		antin = ["ant"]
		antin.append("-f")
		antin.append(os.path.join("..","..","build.xml"))
		antin.append("test-download-imgs")
		if SYS is OS.CYGWIN:
			subprocess.call(antin)
		elif SYS is OS.WIN:
			theproc = subprocess.Popen(antin, shell = True, stdout=subprocess.PIPE)
			theproc.communicate()
	# Otherwise test away!
	Test_Runner.execute_test()

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