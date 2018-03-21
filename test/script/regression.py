#!/usr/bin/python
# -*- coding: utf_8 -*-

# Autopsy Forensic Browser
#
# Copyright 2013 Basis Technology Corp.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from tskdbdiff import TskDbDiff, TskDbDiffException, PGSettings
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
import re
import zipfile
import zlib
from regression_utils import *
import shutil
import ntpath
import glob
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
# this_is_a_variable    this_is_a_function()    ThisIsAClass
#
#


# Data Definitions:
#
# pathto_X:     A path to type X.
# ConfigFile:   An XML file formatted according to the template in myconfig.xml
# ParsedConfig: A dom object that represents a ConfigFile
# SQLCursor:    A cursor recieved from a connection to an SQL database
# Nat:          A Natural Number
# Image:        An image
#

# Enumeration of database types used for the simplification of generating database paths
DBType = enum('OUTPUT', 'GOLD', 'BACKUP')

# Common filename of the output and gold databases (although they are in different directories
DB_FILENAME = "autopsy.db"

# Backup database filename
BACKUP_DB_FILENAME = "autopsy_backup.db"

# Folder name for gold standard database testing
AUTOPSY_TEST_CASE = "AutopsyTestCase"

Day = 0

# HTML file name for links of output directories
OUTPUT_DIR_LINK_FILE="output_dir_link.html"

def usage():
    print ("-f PATH single file")
    print ("-r rebuild")
    print ("-b run both compare and rebuild")
    print ("-l PATH path to config file")
    print ("-u Ignore unallocated space")
    print ("-k Do not delete SOLR index")
    print ("-o PATH path to output folder for Diff files")
    print ("-v verbose mode")
    print ("-e ARG Enable exception mode with given string")
    print ("-h help")

#----------------------#
#        Main          #
#----------------------#
def main():
    """Parse the command-line arguments, create the configuration, and run the tests."""
    args = Args()
    parse_result = args.parse()
    # The arguments were given wrong:
    if not parse_result:
        Errors.print_error("The arguments were given wrong")
        exit(1)

    test_config = TestConfiguration(args)
    case_type = test_config.userCaseType.lower()
    if case_type.startswith('multi'):
        TestRunner.run_tests(test_config, True)
    elif case_type.startswith('single'):
        TestRunner.run_tests(test_config, False)
    elif case_type.startswith('both'):
        TestRunner.run_tests(test_config, False)
        TestRunner.run_tests(test_config, True)
    else:
        Errors.print_error("Invalid case type inputed. Please use 'Multi-user, Single-user or Both for case type'.")
        exit(1)
    exit(0)


class TestRunner(object):
    """A collection of functions to run the regression tests."""

    def run_tests(test_config, isMultiUser):
        """Run the tests specified by the main TestConfiguration.

        Executes the AutopsyIngest for each image and dispatches the results based on
        the mode (rebuild or testing)
        """

        if isMultiUser:
            test_config.testUserCase='multi'
        else:
            test_config.testUserCase='single'

        test_config._init_logs()

        #  get list of test images to process
        test_data_list = [ TestData(image, test_config) for image in test_config.images ]

        Reports.html_add_images(test_config.html_log, test_config.images)

        # Test each image
        gold_exists = False
        logres =[]
        for test_data in test_data_list:
            Errors.clear_print_logs()
            if not (test_config.args.rebuild or os.path.exists(test_data.gold_archive)):
                msg = "Gold standard doesn't exist, skipping image:"
                Errors.print_error(msg)
                Errors.print_error(test_data.gold_archive)
                continue
                
            # At least one test has gold
            gold_exists = True


            # Analyze the given image
            TestRunner._run_autopsy_ingest(test_data)

            # Generate HTML report
            Reports.write_html_foot(test_config.html_log)

            # Either copy the data or compare the data or both
            if test_config.args.rebuild:
                TestRunner.rebuild(test_data)
            elif test_config.args.both:
                logres.append(TestRunner._compare_results(test_data))
                TestRunner.rebuild(test_data)
            else:
                logres.append(TestRunner._compare_results(test_data))

            test_data.printout = Errors.printout
            test_data.printerror = Errors.printerror
            # give solr process time to die.
            time.sleep(10)
            TestRunner._cleanup(test_data)

        if not gold_exists:
            Errors.print_error("No image had any gold; Regression did not run")
            exit(1)

        if not (test_config.args.rebuild or all([ test_data.overall_passed for test_data in test_data_list ])):
            html = open(test_config.html_log)
            Errors.add_errors_out(html.name)
            html.close()
            sys.exit(1)

    def _run_autopsy_ingest(test_data):
        """Run Autopsy ingest for the image in the given TestData.

        Also generates the necessary logs for rebuilding or diff.

        Args:
            test_data: the TestData to run the ingest on.
        """
        if image_type(test_data.image_file) == IMGTYPE.UNKNOWN:
            Errors.print_error("Error: Image type is unrecognized:")
            Errors.print_error(test_data.image_file + "\n")
            return

        logging.debug("--------------------")
        logging.debug(test_data.image_name)
        logging.debug("--------------------")
        TestRunner._run_ant(test_data)
        time.sleep(2) # Give everything a second to process

        # exit if any build errors are found in antlog.txt
        antlog = 'antlog.txt'
        logs_path = test_data.logs_dir
        for ant_line in codecs.open(os.path.join(logs_path, os.pardir, antlog)):
            ant_ignoreCase = ant_line.lower()
            if ant_line.startswith("BUILD FAILED") or "fatal error" in ant_ignoreCase or "crashed" in ant_ignoreCase:
                Errors.print_error("Autopsy test failed. Please check the build log antlog.txt for details.")
                sys.exit(1)
        # exit if a single-user case and the local .db file was not created 
        if not file_exists(test_data.get_db_path(DBType.OUTPUT)) and not test_data.isMultiUser:
            Errors.print_error("Autopsy did not run properly; No .db file was created")
            sys.exit(1)
        try:
            # Dump the database before we diff or use it for rebuild
            db_file = test_data.get_db_path(DBType.OUTPUT)
            TskDbDiff.dump_output_db(db_file, test_data.get_db_dump_path(DBType.OUTPUT),
            test_data.get_sorted_data_path(DBType.OUTPUT), test_data.isMultiUser, test_data.pgSettings)
        except sqlite3.OperationalError as e:
            Errors.print_error("Ingest did not run properly.\nMake sure no other instances of Autopsy are open and try again." + str(e))
            sys.exit(1)

        # merges logs into a single log for later diff / rebuild
        copy_logs(test_data)
        Logs.generate_log_data(test_data)

        TestRunner._handle_solr(test_data)
        TestRunner._handle_exception(test_data)

    #TODO: figure out return type of _compare_results(logres)
    def _compare_results(test_data):
        """Compare the results of the output to the gold standard.

        Args:
            test_data: the TestData

        Returns:
            logres?
        """

        # Setup the gold file
        TestRunner._setup_gold(test_data)

        # Look for core exceptions
        # @@@ Should be moved to TestResultsDiffer, but it didn't know about logres -- need to look into that
        logres = Logs.search_common_log("TskCoreException", test_data)

        # Compare output with gold and display results
        TestResultsDiffer.run_diff(test_data)
        print("Html report passed: ", test_data.html_report_passed)
        print("Errors diff passed: ", test_data.errors_diff_passed)
        print("DB diff passed: ", test_data.db_diff_passed)

        # run time test only for the specific jenkins test
        if test_data.main_config.timing:
            print("Run time test passed: ", test_data.run_time_passed)
            test_data.overall_passed = (test_data.html_report_passed and
            test_data.errors_diff_passed and test_data.db_diff_passed)
        # otherwise, do the usual
        else:
            test_data.overall_passed = (test_data.html_report_passed and
            test_data.errors_diff_passed and test_data.db_diff_passed)

        Reports.generate_reports(test_data)
        if(not test_data.overall_passed):
            diffFiles = [ f for f in os.listdir(test_data.output_path) if os.path.isfile(os.path.join(test_data.output_path,f)) ]
            for f in diffFiles:
               if f.endswith("Diff.txt"):
                  Errors.add_errors_out(os.path.join(test_data.output_path, f))
            Errors.add_errors_out(test_data.common_log_path)
            # Diff files are copied to user-specified folder for every ingested image test_data_list.
            if test_data.main_config.args.copy_diff_files:
                TestRunner._copy_diff_files(test_data)
        return logres


    def _setup_gold(test_data):
        """Extract gold archive file to output/gold/
        and then copies gold txt files to the same location.

        Args:
            test_data: the TestData
        """
        extrctr = zipfile.ZipFile(test_data.gold_archive, 'r', compression=zipfile.ZIP_DEFLATED)
        extrctr.extractall(test_data.main_config.gold)
        extrctr.close
        time.sleep(2)

        gold_dir = test_data.main_config.gold
        for file in os.listdir(gold_dir):
            if file.startswith(test_data.image_name) and file.endswith(".txt"):
                src = os.path.join(gold_dir, file)
                dst = os.path.join(gold_dir, test_data.image_name)
                shutil.copy(src, dst)
        time.sleep(2)

    def _handle_solr(test_data):
        """Clean up SOLR index if not in keep mode (-k).

        Args:
            test_data: the TestData
        """
        if not test_data.main_config.args.keep:
            if clear_dir(test_data.solr_index):
                print_report([], "DELETE SOLR INDEX", "Solr index deleted.")
        else:
            print_report([], "KEEP SOLR INDEX", "Solr index has been kept.")

    def _copy_diff_files(test_data):
        """Copies the Diff-txt files from the output directory to a specified location
        Args:
            test_data: the TestData
        """
        copied = False

        for file in glob.glob(test_data.output_path + "/*-Diff.txt"):
            # Eg. copies HTML-Report-Diff.txt to <Image-name>-HTML-Report-Diff.txt
            shutil.copy(file, test_data.main_config.args.diff_files_output_folder +
                        "/" + test_data.image + "-" + os.path.basename(file))
            copied = True
        if not copied:
            print_report([], "NO DIFF FILES COPIED FROM " + test_data.output_path, "")
        else:
            print_report([], "DIFF OUTPUT COPIED TO " + test_data.main_config.args.diff_files_output_folder, "")

    def _handle_exception(test_data):
        """If running in exception mode, print exceptions to log.

        Args:
            test_data: the TestData
        """
        if test_data.main_config.args.exception:
            exceptions = search_logs(test_data.main_config.args.exception_string, test_data)
            okay = ("No warnings or exceptions found containing text '" +
            test_data.main_config.args.exception_string + "'.")
            print_report(exceptions, "EXCEPTION", okay)

    def rebuild(test_data):
        """Rebuild the gold standard with the given TestData.

        Copies the test-generated database and html report files into the gold directory.
        """
        test_config = test_data.main_config
        image_name = test_data.image_name
        errors = []

        gold_dir = test_config.gold
        image_dir = make_path(gold_dir, image_name)
        clear_dir(image_dir)

        dbinpth = test_data.get_db_path(DBType.OUTPUT)
        dboutpth = make_path(image_dir, DB_FILENAME)
        dataoutpth = make_path(gold_dir, image_name + "-BlackboardDump.txt")
        dbdumpinpth = test_data.get_db_dump_path(DBType.OUTPUT)
        dbdumpoutpth = make_path(gold_dir, image_name + "-DBDump.txt")
        time_pth = make_path(gold_dir, image_name + "-Time.txt")
        error_pth = make_path(gold_dir, image_name + "-Exceptions.txt")

        # Copy files to gold
        try:
            if not test_data.isMultiUser: # This find the local .db file and copy it for single-user case. Multi-user case doesn't have a local db file. 
                shutil.copy(dbinpth, dboutpth)
            if file_exists(test_data.get_sorted_data_path(DBType.OUTPUT)):
                shutil.copy(test_data.get_sorted_data_path(DBType.OUTPUT), dataoutpth)
            shutil.copy(dbdumpinpth, dbdumpoutpth)          
            shutil.copy(test_data.common_log_path, error_pth)
        except IOError as e:
            Errors.print_error(str(e))
            print(str(e))
            print(traceback.format_exc())

        # Rebuild the HTML report
        output_html_report_dir = test_data.get_html_report_path(DBType.OUTPUT)
        gold_html_report_dir = make_path(image_dir, "Report")

        try:
            shutil.copytree(output_html_report_dir, gold_html_report_dir)
        except OSError as e:
            errors.append(e.error())
        except Exception as e:
            errors.append("Error: Unknown fatal error when rebuilding the gold html report.")
            errors.append(str(e) + "\n")
            print(traceback.format_exc())

        # Rebuild the Run time report
        if(test_data.main_config.timing):
            file = open(time_pth, "w")
            file.writelines(test_data.total_ingest_time)
            file.close()

        # Create the zip for the image
        img_archive = make_path(gold_dir, image_name + "-archive.zip")
        comprssr = zipfile.ZipFile(img_archive, 'w', compression=zipfile.ZIP_DEFLATED)
        TestRunner.zipdir(image_dir, comprssr)
        comprssr.close()
        del_dir(image_dir)
        okay = "Successfully rebuilt all gold standards."
        print_report(errors, "REBUILDING", okay)

    def zipdir(path, zip):
        fix_path = path.replace("\\","/")
        for root, dirs, files in os.walk(fix_path):
            for file in files:
                relpath = os.path.relpath(os.path.join(root, file), os.path.join(fix_path, '..'))
                zip.write(os.path.join(root, file), relpath)

    def _run_ant(test_data):
        """Construct and run the ant build command for the given TestData.

        Tests Autopsy by calling RegressionTest.java via the ant build file.

        Args:
            test_data: the TestData
        """
        test_config = test_data.main_config
        # Set up the directories
        if dir_exists(test_data.output_path):
            shutil.rmtree(test_data.output_path)
        os.makedirs(make_os_path(_platform, test_data.output_path))
        test_data.ant = ["ant"]
        test_data.ant.append("-v")
        test_data.ant.append("-f")
        test_data.ant.append(make_local_path(test_data.main_config.build_path, "build.xml"))
        test_data.ant.append("regression-test")
        test_data.ant.append("-l")
        test_data.ant.append(test_data.antlog_dir)
        test_data.ant.append("-Dimg_path=" + test_data.image_file)
        test_data.ant.append("-Dknown_bad_path=" + test_config.known_bad_path)
        test_data.ant.append("-Dkeyword_path=" + test_config.keyword_path)
        test_data.ant.append("-Dnsrl_path=" + test_config.nsrl_path)
        test_data.ant.append("-Dgold_path=" + test_config.gold)
        if (re.match('^[\w]:', test_data.output_path) == None and not test_data.output_path.startswith("\\\\")) or test_data.output_path.startswith('/'):
            test_data.ant.append("-Dout_path=" + make_local_path(test_data.output_path))
        else:
            test_data.ant.append("-Dout_path=" + test_data.output_path)
        test_data.ant.append("-Dignore_unalloc=" + "%s" % test_config.args.unallocated)
        test_data.ant.append("-Dtest.timeout=" + str(test_config.timeout))
        #multi-user settings
        test_data.ant.append("-DdbHost=" + test_config.dbHost)
        test_data.ant.append("-DdbPort=" + str(test_config.dbPort))
        test_data.ant.append("-DdbUserName=" + test_config.dbUserName)
        test_data.ant.append("-DdbPassword=" + test_config.dbPassword)
        test_data.ant.append("-DsolrHost=" + test_config.solrHost)
        test_data.ant.append("-DsolrPort=" + str(test_config.solrPort))
        test_data.ant.append("-DmessageServiceHost=" + test_config.messageServiceHost)
        test_data.ant.append("-DmessageServicePort=" + str(test_config.messageServicePort))
        if test_data.isMultiUser:
            test_data.ant.append("-DisMultiUser=true")
        # Note: test_data has autopys_version attribute, but we couldn't see it from here. It's set after run ingest.
        autopsyVersionPath = os.path.join("..", "..", "nbproject", "project.properties")

        autopsyVersion = search_properties("app.version", autopsyVersionPath)
        if len(autopsyVersion) == 0:
            print("Couldn't get the autopsy version from: " + autopsyVersionPath)
            sys.exit(1)

        # if need autopsyPlatform setup
        if len(test_data.main_config.autopsyPlatform) > 0:
            test_data.ant.append("-Dnbplatform.Autopsy_" + autopsyVersion + ".netbeans.dest.dir=" + test_data.main_config.autopsyPlatform)
            test_data.ant.append("-Dnbplatform.default.harness.dir=" + test_data.main_config.autopsyPlatform + "/harness")
            test_data.ant.append("-Dnbplatform.Autopsy_" + autopsyVersion + ".harness.dir=" + test_data.main_config.autopsyPlatform + "/harness")
 
        Errors.print_out("Ingesting Image:\n" + test_data.image_file + "\n")
        Errors.print_out("CMD: " + " ".join(test_data.ant))
        Errors.print_out("Starting test...\n")
        if (re.match('^[\w]:', test_data.main_config.output_dir) == None and not test_data.main_config.output_dir.startswith("\\\\")) or test_data.main_config.output_dir.startswith('/'):
            antoutpth = make_local_path(test_data.main_config.output_dir, "antRunOutput.txt")
        else:
            antoutpth = test_data.main_config.output_dir + "\\antRunOutput.txt"
        antout = open(antoutpth, "a")
        if SYS is OS.CYGWIN:
            subprocess.call(test_data.ant, stdout=subprocess.PIPE)
        elif SYS is OS.WIN:
            theproc = subprocess.Popen(test_data.ant, shell = True, stdout=subprocess.PIPE)
            theproc.communicate()
        antout.close()

    def _cleanup(test_data):
        """
        Delete the additional files.
        :param test_data:
        :return:
        """
        try:
            os.remove(test_data.get_sorted_data_path(DBType.OUTPUT))
        except:
            pass
        try:
            os.remove(test_data.get_sorted_errors_path(DBType.OUTPUT))
        except:
            pass
        try:
            os.remove(test_data.get_db_dump_path(DBType.OUTPUT))
        except:
            pass
        try:
            os.remove(test_data.get_run_time_path(DBType.OUTPUT))
        except:
            pass



class TestData(object):
    """Container for the input and output of a single image.

    Represents data for the test of a single image, including path to the image,
    database paths, etc.

    Attributes:
        main_config: the global TestConfiguration
        ant: a listof_String, the ant command for this TestData
        image_file: a pathto_Image, the image for this TestData
        image: a String, the image file's name
        image_name: a String, the image file's name
        output_path: pathto_Dir, the output directory for this TestData
        autopsy_data_file: a pathto_File, the IMAGE_NAMEAutopsy_data.txt file
        warning_log: a pathto_File, the AutopsyLogs.txt file
        antlog_dir: a pathto_File, the antlog.txt file
        test_dbdump: a pathto_File, the database dump, IMAGENAMEDump.txt
        common_log_path: a pathto_File, the IMAGE_NAMECOMMON_LOG file
        reports_dir: a pathto_Dir, the AutopsyTestCase/Reports folder
        gold_data_dir: a pathto_Dir, the gold standard directory
        gold_archive: a pathto_File, the gold standard archive
        logs_dir: a pathto_Dir, the location where autopsy logs are stored
        solr_index: a pathto_Dir, the locatino of the solr index
        html_report_passed: a boolean, did the HTML report diff pass?
        errors_diff_passed: a boolean, did the error diff pass?
        db_diff_passed: a boolean, did the db diff pass?
        run_time_passed: a boolean, did the run time test pass?
        overall_passed: a boolean, did the test pass?
        total_test_time: a String representation of the test duration
        start_date: a String representation of this TestData's start date
        end_date: a String representation of the TestData's end date
        total_ingest_time: a String representation of the total ingest time
        artifact_count: a Nat, the number of artifacts
        artifact_fail: a Nat, the number of artifact failures
        heap_space: a String representation of TODO
        service_times: a String representation of TODO
        autopsy_version: a String, the version of autopsy that was run
        ingest_messages: a Nat, the number of ingest messages
        indexed_files: a Nat, the number of files indexed during the ingest
        indexed_chunks: a Nat, the number of chunks indexed during the ingest
        printerror: a listof_String, the error messages printed during this TestData's test
        printout: a listof_String, the messages pritned during this TestData's test
    """

    def __init__(self, image, main_config):
        """Init this TestData with it's image and the test configuration.

        Args:
            image: the Image to be tested.
            main_config: the global TestConfiguration.
        """
        # Configuration Data
        self.main_config = main_config
        self.ant = []
        self.image_file = str(image)
        self.image = get_image_name(self.image_file)
        self.image_name = self.image
        # userCaseType
        self.isMultiUser = True if self.main_config.testUserCase == "multi" else False
        # Directory structure and files
        self.output_path = make_path(self.main_config.output_dir, self.image_name)
        self.autopsy_data_file = make_path(self.output_path, self.image_name + "Autopsy_data.txt")
        self.warning_log = make_path(self.output_path, "AutopsyLogs.txt")
        self.antlog_dir = make_path(self.output_path, "antlog.txt")
        self.test_dbdump = make_path(self.output_path, self.image_name +
        "-DBDump.txt")
        self.common_log_path = make_path(self.output_path, self.image_name + "-Exceptions.txt")
        if self.isMultiUser:
            self.reports_dir = make_path(self.output_path, AUTOPSY_TEST_CASE, socket.gethostname(), "Reports")
            self.solr_index = make_path(self.output_path, AUTOPSY_TEST_CASE, socket.gethostname(), "ModuleOutput", "KeywordSearch")
        else:
            self.reports_dir = make_path(self.output_path, AUTOPSY_TEST_CASE, "Reports")
            self.solr_index = make_path(self.output_path, AUTOPSY_TEST_CASE, "ModuleOutput", "KeywordSearch")
        self.gold_data_dir = make_path(self.main_config.gold, self.image_name)
        self.gold_archive = make_path(self.main_config.gold,
        self.image_name + "-archive.zip")
        self.logs_dir = make_path(self.output_path, "logs")
        # Results and Info
        self.html_report_passed = False
        self.errors_diff_passed = False
        self.db_diff_passed = False
        self.run_time_passed = False
        self.overall_passed = False
        # Ingest info
        self.total_test_time = ""
        self.start_date = ""
        self.end_date = ""
        self.total_ingest_time = ""
        self.artifact_count = 0
        self.artifact_fail = 0
        self.heap_space = ""
        self.service_times = ""
        self.autopsy_version = ""
        self.ingest_messages = 0
        self.indexed_files = 0
        self.indexed_chunks = 0
        # Error tracking
        self.printerror = []
        self.printout = []
        # autopsyPlatform
        self.autopsyPlatform = str(self.main_config.autopsyPlatform)
        # postgreSQL db connection data settings
        self.pgSettings = PGSettings(self.main_config.dbHost, self.main_config.dbPort, self.main_config.dbUserName, self.main_config.dbPassword)

    def ant_to_string(self):
        string = ""
        for arg in self.ant:
            string += (arg + " ")
        return string

    def get_db_path(self, db_type):
        """Get the path to the database file that corresponds to the given DBType.

        Args:
            DBType: the DBType of the path to be generated.
        """
        if(db_type == DBType.GOLD):
            db_path = make_path(self.gold_data_dir, DB_FILENAME)
        elif(db_type == DBType.OUTPUT):
            if self.isMultiUser:
                case_path = make_path(self.main_config.output_dir, self.image_name, AUTOPSY_TEST_CASE, "AutopsyTestCase.aut")
                parsed = parse(case_path)
                db_path = parsed.getElementsByTagName("CaseDatabase")[0].firstChild.data
            else:
                db_path = make_path(self.main_config.output_dir, self.image_name, AUTOPSY_TEST_CASE, DB_FILENAME)
        else:
            db_path = make_path(self.main_config.output_dir, self.image_name, AUTOPSY_TEST_CASE, BACKUP_DB_FILENAME)
        return db_path

    def get_html_report_path(self, html_type):
        """Get the path to the HTML Report folder that corresponds to the given DBType.

        Args:
            DBType: the DBType of the path to be generated.
        """
        if(html_type == DBType.GOLD):
            return make_path(self.gold_data_dir, "Report")
        else:
            # Autopsy creates an HTML report folder in the form AutopsyTestCase DATE-TIME
            # It's impossible to get the exact time the folder was created, but the folder
            # we are looking for is the only one in the self.reports_dir folder
            html_path = ""
            for fs in os.listdir(self.reports_dir):
                if "HTML Report" in fs:
                    html_path = make_path(self.reports_dir, fs)
                    break
            return html_path

    def get_sorted_data_path(self, file_type):
        """Get the path to the BlackboardDump file that corresponds to the given DBType.

        Args:
            file_type: the DBType of the path to be generated
        """
        return self._get_path_to_file(file_type, "-BlackboardDump.txt")

    def get_sorted_errors_path(self, file_type):
        """Get the path to the Exceptions (SortedErrors) file that corresponds to the given
        DBType.

        Args:
            file_type: the DBType of the path to be generated
        """
        return self._get_path_to_file(file_type, "-Exceptions.txt")

    def get_db_dump_path(self, file_type):
        """Get the path to the DBDump file that corresponds to the given DBType.

        Args:
            file_type: the DBType of the path to be generated
        """
        return self._get_path_to_file(file_type, "-DBDump.txt")

    def get_run_time_path(self, file_type):
        """Get the path to the run time storage file."
        """
        return self._get_path_to_file(file_type, "-Time.txt")

    def _get_path_to_file(self, file_type, file_name):
        """Get the path to the specified file with the specified type.

        Args:
            file_type: the DBType of the path to be generated
            file_name: a String, the filename of the path to be generated
        """
        full_filename = self.image_name + file_name
        if(file_type == DBType.GOLD):
            return make_path(self.gold_data_dir, full_filename)
        else:
            return make_path(self.output_path, full_filename)


class TestConfiguration(object):
    """Container for test configuration data.

    The Master Test Configuration. Encapsulates consolidated high level input from
    config XML file and command-line arguments.

    Attributes:
        args: an Args, the command line arguments
        output_dir: a pathto_Dir, the output directory
        input_dir: a pathto_Dir,  the input directory
        gold: a pathto_Dir, the gold directory
        img_gold: a pathto_Dir, the temp directory where gold images are unzipped to
        csv: a pathto_File, the local csv file
        global_csv: a pathto_File, the global csv file
        html_log: a pathto_File
        known_bad_path:
        keyword_path:
        nsrl_path:
        build_path: a pathto_File, the ant build file which runs the tests
        autopsy_version:
        ingest_messages: a Nat, number of ingest messages
        indexed_files: a Nat, the number of indexed files
        indexed_chunks: a Nat, the number of indexed chunks
        timer:
        images: a listof_Image, the images to be tested
        timeout: a Nat, the amount of time before killing the test
        ant: a listof_String, the ant command to run the tests
        jenkins: a boolean, is this test running through a Jenkins job?
        timing: are we doing a running time test?
    """

    def __init__(self, args):
        """Inits TestConfiguration and loads a config file if available.

        Args:
            args: an Args, the command line arguments.
        """
        self.args = args
        # Default output parent dir
        self.output_parent_dir = make_path("..", "output", "results")
        self.output_dir = "" 
        self.singleUser_outdir = ""
        self.input_dir = make_local_path("..","input")
        self.gold = ""
        self.singleUser_gold = make_path("..", "output", "gold", "single_user")
        # Logs:
        self.csv = ""
        self.global_csv = ""
        self.html_log = ""
        # Ant info:
        self.known_bad_path = make_path(self.input_dir, "notablehashes.txt-md5.idx")
        self.keyword_path = make_path(self.input_dir, "notablekeywords.xml")
        self.nsrl_path = make_path(self.input_dir, "nsrl.txt-md5.idx")
        self.build_path = make_path("..", "build.xml") 
        # Infinite Testing info
        timer = 0
        self.images = []
        self.jenkins = False
        self.timing = False
        # Set the timeout to something huge
        # The entire tester should not timeout before this number in ms
        # However it only seems to take about half this time
        # And it's very buggy, so we're being careful
        self.timeout = 24 * 60 * 60 * 1000 * 1000
        self.autopsyPlatform = ""

        # Multi-user setting:
        self.dbHost = ""
        self.dbPort = ""
        self.dbUserName = ""
        self.dbPassword = ""
        self.solrHost = ""
        self.solrPort = ""
        self.messageServiceHost = ""
        self.messageServicePort = ""
        self.userCaseType = "Both"
        self.multiUser_gold = make_path("..", "output", "gold", "multi_user")
        self.multiUser_outdir = ""

        # Test runner user case:
        self.testUserCase = ""
        if not self.args.single:
            self._load_config_file(self.args.config_file)
        else:
            self.images.append(self.args.single_file)

    def _load_config_file(self, config_file):
        """Updates this TestConfiguration's attributes from the config file.

        Initializes this TestConfiguration by iterating through the XML config file
        command-line argument. Populates self.images and optional email configuration

        Args:
            config_file: ConfigFile - the configuration file to load
        """
        try:
            count = 0
            parsed_config = parse(config_file)
            logres = []
            counts = {}
            if parsed_config.getElementsByTagName("userCaseType"):
                self.userCaseType = parsed_config.getElementsByTagName("userCaseType")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("indir"):
                self.input_dir = parsed_config.getElementsByTagName("indir")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("singleUser_outdir"):
                self.singleUser_outdir = parsed_config.getElementsByTagName("singleUser_outdir")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("singleUser_golddir"):
                self.singleUser_gold = parsed_config.getElementsByTagName("singleUser_golddir")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("timing"):
                self.timing = ("True" == parsed_config.getElementsByTagName("timing")[0].getAttribute("value").encode().decode("utf_8"))
            if parsed_config.getElementsByTagName("autopsyPlatform"):
                self.autopsyPlatform = parsed_config.getElementsByTagName("autopsyPlatform")[0].getAttribute("value").encode().decode("utf_8")
            # Multi-user settings
            if parsed_config.getElementsByTagName("multiUser_golddir"):
                self.multiUser_gold = parsed_config.getElementsByTagName("multiUser_golddir")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("dbHost"):
                self.dbHost = parsed_config.getElementsByTagName("dbHost")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("dbPort"):
                self.dbPort = parsed_config.getElementsByTagName("dbPort")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("dbUserName"):
                self.dbUserName = parsed_config.getElementsByTagName("dbUserName")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("dbPassword"):
                self.dbPassword = parsed_config.getElementsByTagName("dbPassword")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("solrHost"):
                self.solrHost = parsed_config.getElementsByTagName("solrHost")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("solrPort"):
                self.solrPort = parsed_config.getElementsByTagName("solrPort")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("messageServiceHost"):
                self.messageServiceHost = parsed_config.getElementsByTagName("messageServiceHost")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("messageServicePort"):
                self.messageServicePort = parsed_config.getElementsByTagName("messageServicePort")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("multiUser_outdir"):
                self.multiUser_outdir = parsed_config.getElementsByTagName("multiUser_outdir")[0].getAttribute("value").encode().decode("utf_8")
            self._init_imgs(parsed_config)
            self._init_build_info(parsed_config)

        except IOError as e:
            msg = "There was an error loading the configuration file.\n"
            msg += "\t" + str(e)
            logging.critical(traceback.format_exc())
            print(traceback.format_exc())

        if self.userCaseType.lower().startswith("multi") or self.userCaseType.lower().startswith("both"):
            if not self.dbHost.strip() or not self.dbPort.strip() or not self.dbUserName.strip() or not self.dbPassword.strip():
                Errors.print_error("Please provide database connection information via configuration file. ")
                sys.exit(1)
            if not self.solrHost.strip() or not self.solrPort.strip():
                Errors.print_error("Please provide solr host name and port number via configuration file. ")
                sys.exit(1)
            if not self.messageServiceHost.strip() or not self.messageServicePort.strip():
                Errors.print_error("Please provide ActiveMQ host name and port number via configuration file. ")
                sys.exit(1)
            if not self.multiUser_outdir.strip():
                Errors.print_error("Please provide a shared output directory for multi-user test. ")
                sys.exit(1)

    def _init_logs(self):
        """Setup output folder, logs, and reporting infrastructure."""
        if self.testUserCase == "multi":
            self.output_parent_dir = self.multiUser_outdir
            self.gold = self.multiUser_gold
        else:
            self.output_parent_dir = self.singleUser_outdir
            self.gold = self.singleUser_gold

        if not dir_exists(self.output_parent_dir):
            print(_platform)
            print(self.output_parent_dir)
            os.makedirs(make_os_path(_platform, self.output_parent_dir))
        self.global_csv = make_path(os.path.join(self.output_parent_dir, "Global_CSV.log"))
        self.output_dir = make_path(self.output_parent_dir, time.strftime("%Y.%m.%d-%H.%M.%S"))

        os.makedirs(self.output_dir)

        #write the output_dir to a html file

        linkFile = open(os.path.join(self.args.diff_files_output_folder, OUTPUT_DIR_LINK_FILE), "a")
        index = self.output_dir.find("\\")
        linkStr = "<a href=\"file://"
        linkOutputDir =  self.output_dir[index+2:].replace("//", "/").replace("\\\\", "\\")
        if index == 0:
            linkStr = linkStr + linkOutputDir
        else:
            linkStr = linkStr + socket.gethostname() + "\\" + linkOutputDir
        if self.testUserCase == "multi":
            linkStr = linkStr + "\">Enterprise Viking Tests</a>"
        else:
            linkStr = linkStr + "\">Standalone Viking Tests</a>"
        linkFile.write(linkStr + "\n")
        linkFile.close()
 
        self.csv = make_path(self.output_dir, "CSV.txt")
        self.html_log = make_path(self.output_dir, "AutopsyTestCase.html")
        log_name = ''
        if SYS is OS.CYGWIN and ((re.match('^[\w]:', self.output_dir) != None and self.output_dir.startswith("\\\\")) or not self.output_dir.startswith('/')):
            a = ["cygpath", "-u", self.output_dir]
            cygpath_output_dir = subprocess.check_output(a).decode('utf-8')
            log_name = cygpath_output_dir.rstrip() + "/regression.log"
        else:
            log_name = self.output_dir + "\\regression.log"
        logging.basicConfig(filename=log_name, level=logging.DEBUG)

        # Sanity check to see if there are obvious gold images that we are not testing
        if not dir_exists(self.gold):
            print(self.gold)
            Errors.print_error("Gold folder does not exist")
            sys.exit(1)
        gold_count = 0
        for file in os.listdir(self.gold):
            if not(file == 'tmp'):
                gold_count+=1

        image_count = len(self.images)
        if (image_count > gold_count):
            print("******Alert: There are more input images than gold standards, some images will not be properly tested.\n")
        elif (image_count < gold_count):
            print("******Alert: There are more gold standards than input images, this will not check all gold Standards.\n")

    def _init_build_info(self, parsed_config):
        """Initializes paths that point to information necessary to run the AutopsyIngest."""
        build_elements = parsed_config.getElementsByTagName("build")
        if build_elements:
            build_element = build_elements[0]
            build_path = build_element.getAttribute("value").encode().decode("utf_8")
            self.build_path = build_path

    def _init_imgs(self, parsed_config):
        """Initialize the list of images to run tests on. Logical file set also included."""
        for element in parsed_config.getElementsByTagName("image"):
            value = element.getAttribute("value").encode().decode("utf_8")
            print ("Image in Config File: " + value)
            if file_exists(value) or dir_exists(value):
                self.images.append(value)
            else:
                msg = "File: " + value + " doesn't exist"
                Errors.print_error(msg)

#-------------------------------------------------#
#     Functions relating to comparing outputs     #
#-------------------------------------------------#
class TestResultsDiffer(object):
    """Compares results for a single test."""

    def run_diff(test_data):
        """Compares results for a single test.

        Args:
            test_data: the TestData to use.
            databaseDiff: TskDbDiff object created based off test_data
        """
        try:
            output_db = test_data.get_db_path(DBType.OUTPUT)
            gold_db = test_data.get_db_path(DBType.GOLD)
            output_dir = test_data.output_path
            gold_bb_dump = test_data.get_sorted_data_path(DBType.GOLD)
            gold_dump = test_data.get_db_dump_path(DBType.GOLD)
            test_data.db_diff_passed = all(TskDbDiff(output_db, gold_db, output_dir=output_dir, gold_bb_dump=gold_bb_dump,
            gold_dump=gold_dump, isMultiUser=test_data.isMultiUser, pgSettings=test_data.pgSettings).run_diff())

            # Compare Exceptions
            # replace is a fucntion that replaces strings of digits with 'd'
            # this is needed so dates and times will not cause the diff to fail
            replace = lambda file: re.sub(re.compile("\d"), "d", file)
            output_errors = test_data.get_sorted_errors_path(DBType.OUTPUT)
            gold_errors = test_data.get_sorted_errors_path(DBType.GOLD)
            passed = TestResultsDiffer._compare_text(output_errors, gold_errors,
            replace)
            test_data.errors_diff_passed = passed

            # Compare html output
            gold_report_path = test_data.get_html_report_path(DBType.GOLD)
            output_report_path = test_data.get_html_report_path(DBType.OUTPUT)
            passed = TestResultsDiffer._html_report_diff(test_data)
            test_data.html_report_passed = passed

            # Compare time outputs
            if test_data.main_config.timing:
                old_time_path = test_data.get_run_time_path(DBType.GOLD)
                passed = TestResultsDiffer._run_time_diff(test_data, old_time_path)
                test_data.run_time_passed = passed

            # Clean up tmp folder
            del_dir(test_data.gold_data_dir)

        except sqlite3.OperationalError as e:
            Errors.print_error("Tests failed while running the diff:\n")
            Errors.print_error(str(e))
        except TskDbDiffException as e:
            Errors.print_error(str(e))
        except Exception as e:
            Errors.print_error("Tests failed due to an error, try rebuilding or creating gold standards.\n")
            Errors.print_error(str(e) + "\n")
            print(traceback.format_exc())

    def _compare_text(output_file, gold_file, process=None):
        """Compare two text files.

        Args:
            output_file: a pathto_File, the output text file
            gold_file: a pathto_File, the input text file
            pre-process: (optional) a function of String -> String that will be
            called on each input file before the diff, if specified.
        """
        if(not file_exists(output_file)):
            return False
        output_data = codecs.open(output_file, "r", "utf_8").read()
        gold_data = codecs.open(gold_file, "r", "utf_8").read()

        if process is not None:
            output_data = process(output_data)
            gold_data = process(gold_data)

        if (not(gold_data == output_data)):
            diff_path = os.path.splitext(os.path.basename(output_file))[0]
            diff_path += "-Diff.txt"
            diff_file = codecs.open(diff_path, "wb", "utf_8")

            # Gold needs to be passed in before output.
            dffcmdlst = ["diff", gold_file, output_file]
            subprocess.call(dffcmdlst, stdout = diff_file)
            Errors.add_errors_out(diff_path)

            # create file path for gold files inside report output folder. In case of diff, both gold and current run
            # Exception.txt files are available in the report output folder. Prefix Gold- is added to the filename.
            gold_file_in_output_dir = output_file[:output_file.rfind("\\")] + "\\Gold-" + output_file[output_file.rfind("\\")+1:]
            shutil.copy(gold_file, gold_file_in_output_dir)

            return False
        else:
            return True

    def _html_report_diff(test_data):
        """Compare the output and gold html reports. Diff util is used for this purpose.
        Diff -r -N -x <non-textual files> --ignore-matching-lines <regex> <folder-location-1> <folder-location-2>
        is executed.
        Diff is recursively used to scan through the HTML report directories. Modify the <regex> to suit the needs.
        Currently, the regex is set to match certain lines found on index.html and summary.html, and skip (read ignore)
        them.
        Diff returns 0 when there is no difference, 1 when there is difference, and 2 when there is trouble (trouble not
        defined in the official documentation).

        Args:
            test_data TestData object which contains initialized report_paths.

        Returns:
            true, if the reports match, false otherwise.
        """
        gold_report_path = test_data.get_html_report_path(DBType.GOLD)
        output_report_path = test_data.get_html_report_path(DBType.OUTPUT)
        try:
            # Ensure gold is passed before output 
            (subprocess.check_output(["diff", '-r', '-N', '-x', '*.png', '-x', '*.ico', '--ignore-matching-lines',
                                      'HTML Report Generated on \|Autopsy Report for case \|Case:\|Case Number:'
                                      '\|Examiner:', gold_report_path, output_report_path]))
            print_report("", "REPORT COMPARISON", "The test html reports matched the gold reports")
            return True
        except subprocess.CalledProcessError as e:
            if e.returncode == 1:
                Errors.print_error("Error Code: 1\nThe HTML reports did not match.")
                diff_file = codecs.open(test_data.output_path + "\HTML-Report-Diff.txt", "wb", "utf_8")
                diff_file.write(str(e.output.decode("utf-8")))
                return False
            if e.returncode == 2:
                Errors.print_error("Error Code: 2\nTrouble executing the Diff Utility.")
                diff_file = codecs.open(test_data.output_path + "\HTML-Report-Diff.txt", "wb", "utf_8")
                diff_file.write(str(e.output.decode("utf-8")))
                return False
        except OSError as e:
            e.print_error()
            return False
        except Exception as e:
            Errors.print_error("Error: Unknown fatal error comparing reports.")
            Errors.print_error(str(e) + "\n")
            logging.critical(traceback.format_exc())
            return False

    def _run_time_diff(test_data, old_time_path):
        """ Compare run times for this run, and the run previous.

        Args:
            test_data: the TestData
            old_time_path: path to the log containing the run time from a previous test
        """
        # read in time
        file = open(old_time_path, "r")
        line = file.readline()
        oldtime = int(line[:line.find("ms")].replace(',', ''))
        file.close()

        # If we don't have a previous run time bail out here to
        # avoid dividing by zero below.
        if oldtime == 0:
            return True

        newtime = test_data.total_ingest_time

        # write newtime to the file inside the report dir.
        file = open(test_data.get_run_time_path(DBType.OUTPUT), "w")
        file.writelines(newtime)
        file.close()

        newtime = int(newtime[:newtime.find("ms")].replace(',', ''))

        # run the test, 5% tolerance
        if oldtime * 1.05 >=  newtime: # new run was faster
            return True
        else: # old run was faster
            print("The last run took: " + str(oldtime))
            print("This run took: " + str(newtime))
            diff = ((newtime / oldtime) * 100) - 100
            diff = str(diff)[:str(diff).find('.') + 3]
            print("This run took " + diff + "% longer to run than the last run.")
            return False

        
class Reports(object):
    def generate_reports(test_data):
        """Generate the reports for a single test

        Args:
            test_data: the TestData
        """
        Reports._generate_html(test_data)
        if test_data.main_config.global_csv:
            Reports._generate_csv(test_data.main_config.global_csv, test_data)
        else:
            Reports._generate_csv(test_data.main_config.csv, test_data)

        if test_data.main_config.timing:
            Reports._write_time(test_data)

    def _generate_html(test_data):
        """Generate the HTML log file."""
        # If the file doesn't exist yet, this is the first test_config to run for
        # this test, so we need to make the start of the html log
        html_log = test_data.main_config.html_log
        if not file_exists(html_log):
            Reports.write_html_head()
        with open(html_log, "a") as html:
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
            if not test_data.overall_passed:
                ids = 'errors1'
            else:
                ids = 'errors'
            errors = "<div id='" + ids + "'>\
                      <h2><a name='" + test_data.image_name + "-errors'>Errors and Warnings</a></h2>\
                      <hr color='#FF0000'>"
            # For each error we have logged in the test_config
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
            logs_path = test_data.logs_dir
            for file in os.listdir(logs_path):
                logs += "<p><a href='file:\\" + make_path(logs_path, file) + "' target='_blank'>" + file + "</a></p>"
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
            info += "<tr><td>test_config Output Directory:</td>"
            info += "<td>" + test_data.main_config.output_dir + "</td></tr>"
            info += "<tr><td>Autopsy Version:</td>"
            info += "<td>" + test_data.autopsy_version + "</td></tr>"
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
            info += "<td>" + str(test_data.ingest_messages) + "</td></tr>"
            info += "<tr><td>Indexed Files Count:</td>"
            info += "<td>" + str(test_data.indexed_files) + "</td></tr>"
            info += "<tr><td>Indexed File Chunks Count:</td>"
            info += "<td>" + str(test_data.indexed_chunks) + "</td></tr>"
            info += "<tr><td>Out Of Disk Space:\
                             <p style='font-size: 11px;'>(will skew other test results)</p></td>"
            info += "<td>" + str(len(search_log_set("autopsy", "Stopping ingest due to low disk space on disk", test_data))) + "</td></tr>"
            info += "</table>\
                    </div>"
            # For all the general print statements in the test_config
            output = "<div id='general'>\
                      <h2><a name='" + test_data.image_name + "-general'>General Output</a></h2>\
                      <hr color='#282828'>"
            # For each printout in the test_config's list
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

    def write_html_head(html_log):
        """Write the top of the HTML log file.

        Args:
            html_log: a pathto_File, the global HTML log
        """
        with open(str(html_log), "a") as html:
            head = "<html>\
                    <head>\
                    <title>AutopsyTesttest_config Output</title>\
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

    def write_html_foot(html_log):
        """Write the bottom of the HTML log file.

        Args:
            html_log: a pathto_File, the global HTML log
        """
        with open(html_log, "a") as html:
            foot = "</body></html>"
            html.write(foot)

    def html_add_images(html_log, full_image_names):
        """Add all the image names to the HTML log.

        Args:
            full_image_names: a listof_String, each representing an image name
            html_log: a pathto_File, the global HTML log
        """
        # If the file doesn't exist yet, this is the first test_config to run for
        # this test, so we need to make the start of the html log
        if not file_exists(html_log):
            Reports.write_html_head(html_log)
        with open(html_log, "a") as html:
            links = []
            for full_name in full_image_names:
                name = get_image_name(full_name)
                links.append("<a href='#" + name + "'>" + name + "</a>")
            html.write("<p align='center'>" + (" | ".join(links)) + "</p>")

    def _generate_csv(csv_path, test_data):
        """Generate the CSV log file"""
        # If the CSV file hasn't already been generated, this is the
        # first run, and we need to add the column names
        if not file_exists(csv_path):
            Reports.csv_header(csv_path)
        # Now add on the fields to a new row
        with open(csv_path, "a") as csv:
            # Variables that need to be written
            vars = []
            vars.append( test_data.image_file )
            vars.append( test_data.image_name )
            vars.append( test_data.main_config.output_dir )
            vars.append( socket.gethostname() )
            vars.append( test_data.autopsy_version )
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
            vars.append( str(test_data.ingest_messages) )
            vars.append( str(test_data.indexed_files) )
            vars.append( str(test_data.indexed_chunks) )
            vars.append( str(len(search_log_set("autopsy", "Stopping ingest due to low disk space on disk", test_data))) )
            vars.append( make_local_path("gold", test_data.image_name, DB_FILENAME) )
            vars.append( make_local_path("gold", test_data.image_name, "standard.html") )
            vars.append( str(test_data.html_report_passed) )
            vars.append( test_data.ant_to_string() )
            # Join it together with a ", "
            output = "|".join(vars)
            output += "\n"
            # Write to the log!
            csv.write(output)

    def csv_header(csv_path):
        """Generate the CSV column names."""
        with open(csv_path, "w") as csv:
            titles = []
            titles.append("Image Path")
            titles.append("Image Name")
            titles.append("Output test_config Directory")
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
            titles.append("Gold Database Name")
            titles.append("Gold Report Name")
            titles.append("Report Comparison")
            titles.append("Ant Command Line")
            output = "|".join(titles)
            output += "\n"
            csv.write(output)

    def _write_time(test_data):
        """Write out the time ingest took. For jenkins purposes.
        Copies the _time.txt file the the input dir.

        Args:
            test_data: the TestData
        """
        filename = test_data.image + "_time.txt"
        filepath = make_path(test_data.output_path, filename)
        new_file = open(filepath, "w")
        new_file.write(test_data.total_ingest_time)
        new_file.close()
        shutil.copy(new_file.name, make_path(test_data.main_config.input_dir, filename))

    def _get_num_memory_errors(type, test_data):
        """Get the number of OutOfMemory errors and Exceptions.

        Args:
            type: a String representing the type of log to check.
            test_data: the TestData to examine.
        """
        return (len(search_log_set(type, "OutOfMemoryError", test_data)) +
                len(search_log_set(type, "OutOfMemoryException", test_data)))


class Logs(object):

    def generate_log_data(test_data):
        """Find and handle relevent data from the Autopsy logs.

        Args:
            test_data: the TestData whose logs to examine
        """
        Logs._generate_common_log(test_data)
        try:
            Logs._fill_ingest_data(test_data)
        except Exception as e:
            Errors.print_error("Error when filling test_config data.")
            Errors.print_error(str(e) + "\n")
            logging.critical(traceback.format_exc())
        # If running in verbose mode (-v)
        if test_data.main_config.args.verbose:
            errors = Logs._report_all_errors()
            okay = "No warnings or errors in any log files."
            print_report(errors, "VERBOSE", okay)

    def _generate_common_log(test_data):
        """Generate the common log, the log of all exceptions and warnings from
        each log file generated by Autopsy.

        Args:
            test_data: the TestData to generate a log for
        """
        try:
            logs_path = test_data.logs_dir
            common_log = codecs.open(test_data.common_log_path, "w", "utf_8")
            warning_log = codecs.open(test_data.warning_log, "w", "utf_8")
            rep_path = make_local_path(test_data.main_config.output_dir)
            rep_path = rep_path.replace("\\\\", "\\")
            for file in os.listdir(logs_path):
                log = codecs.open(make_path(logs_path, file), "r", "utf_8")
                try:
                    for line in log:
                        line = line.replace(rep_path, "test_data")
                        if line.startswith("SEVERE"):
                            common_log.write(file +": " +  line)
                except UnicodeDecodeError as e:
                    pass
                log.close()
            common_log.write("\n")
            common_log.close()
            srtcmdlst = ["sort", test_data.common_log_path, "-o", test_data.common_log_path]
            subprocess.call(srtcmdlst)
        except (OSError, IOError) as e:
            Errors.print_error("Error: Unable to generate the common log.")
            Errors.print_error(str(e) + "\n")
            Errors.print_error(traceback.format_exc())
            logging.critical(traceback.format_exc())

    def _fill_ingest_data(test_data):
        """Fill the TestDatas variables that require the log files.

        Args:
            test_data: the TestData to modify
        """
        try:
            # Open autopsy.log.0
            log_path = make_path(test_data.logs_dir, "autopsy.log.0")
            log = open(log_path)

            # Set the TestData start time based off the first line of autopsy.log.0
            # *** If logging time format ever changes this will break ***
            test_data.start_date = log.readline().split(" org.")[0]
            # Set the test_data ending time based off the "create" time (when the file was copied)
            test_data.end_date = time.ctime(os.path.getmtime(log_path))
        except IOError as e:
            Errors.print_error("Error: Unable to open autopsy.log.0.")
            Errors.print_error(str(e) + "\n")
            logging.warning(traceback.format_exc())
        # Start date must look like: ""
        # End date must look like: "Mon Jul 16 13:02:42 2012"
        # *** If logging time format ever changes this will break ***
        start = datetime.datetime.strptime(test_data.start_date, "%Y-%m-%d %H:%M:%S.%f")
        end = datetime.datetime.strptime(test_data.end_date, "%a %b %d %H:%M:%S %Y")
        test_data.total_test_time = str(end - start)

        try:
            # Set Autopsy version, heap space, ingest time, and service times

            version_line = search_logs("INFO: Application name: Autopsy, version:", test_data)[0]
            test_data.autopsy_version = get_word_at(version_line, 5).rstrip(",")
            test_data.heap_space = search_logs("Heap memory usage:", test_data)[0].rstrip().split(": ")[1]
            ingest_line = search_logs("Ingest (including enqueue)", test_data)[0]
            test_data.total_ingest_time = get_word_at(ingest_line, 6).rstrip()
            message_line_count = find_msg_in_log_set("Ingest messages count:", test_data)
            test_data.indexed_files = message_line_count

            files_line_count = find_msg_in_log_set("Indexed files count:", test_data)
            test_data.indexed_files = files_line_count

            chunks_line_count = find_msg_in_log_set("Indexed file chunks count:", test_data)
            test_data.indexed_chunks = chunks_line_count

        except (OSError, IOError) as e:
            Errors.print_error("Error: Unable to find the required information to fill test_config data.")
            Errors.print_error(str(e) + "\n")
            logging.critical(traceback.format_exc())
            print(traceback.format_exc())
        try:
            service_lines = find_msg_in_log("autopsy.log.0", "to process()", test_data)
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
        except (OSError, IOError) as e:
            Errors.print_error("Error: Unknown fatal error when finding service times.")
            Errors.print_error(str(e) + "\n")
            logging.critical(traceback.format_exc())

    def _report_all_errors():
        """Generate a list of all the errors found in the common log.

        Returns:
            a listof_String, the errors found in the common log
        """
        try:
            return get_warnings() + get_exceptions()
        except (OSError, IOError) as e:
            Errors.print_error("Error: Unknown fatal error when reporting all errors.")
            Errors.print_error(str(e) + "\n")
            logging.warning(traceback.format_exc())

    def search_common_log(string, test_data):
        """Search the common log for any instances of a given string.

        Args:
            string: the String to search for.
            test_data: the TestData that holds the log to search.

        Returns:
            a listof_String, all the lines that the string is found on
        """
        results = []
        log = codecs.open(test_data.common_log_path, "r", "utf_8")
        for line in log:
            if string in line:
                results.append(line)
        log.close()
        return results


def print_report(errors, name, okay):
    """Print a report with the specified information.

    Args:
        errors: a listof_String, the errors to report.
        name: a String, the name of the report.
        okay: the String to print when there are no errors.
    """
    if errors:
        Errors.print_error("--------< " + name + " >----------")
        for error in errors:
            Errors.print_error(str(error))
        Errors.print_error("--------< / " + name + " >--------\n")
    else:
        Errors.print_out("-----------------------------------------------------------------")
        Errors.print_out("< " + name + " - " + okay + " />")
        Errors.print_out("-----------------------------------------------------------------\n")


def get_exceptions(test_data):
    """Get a list of the exceptions in the autopsy logs.

    Args:
        test_data: the TestData to use to find the exceptions.
    Returns:
        a listof_String, the exceptions found in the logs.
    """
    exceptions = []
    logs_path = test_data.logs_dir
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


def get_warnings(test_data):
    """Get a list of the warnings listed in the common log.

    Args:
        test_data: the TestData to use to find the warnings

    Returns:
        listof_String, the warnings found.
    """
    warnings = []
    common_log = codecs.open(test_data.warning_log, "r", "utf_8")
    for line in common_log:
        if "warning" in line.lower():
            warnings.append(line)
    common_log.close()
    return warnings


def copy_logs(test_data):
    """Copy the Autopsy generated logs to output directory.

    Args:
        test_data: the TestData whose logs will be copied
    """
    try:
        # copy logs from autopsy case's Log folder
        if test_data.isMultiUser:
            log_dir = os.path.join(test_data.output_path, AUTOPSY_TEST_CASE, socket.gethostname(), "Log")
        else:
            log_dir = os.path.join(test_data.output_path, AUTOPSY_TEST_CASE, "Log")
        shutil.copytree(log_dir, test_data.logs_dir)

        # copy logs from userdir0/var/log
        log_dir = os.path.join(test_data.main_config.build_path,"build","test","qa-functional","work","userdir0","var","log/")
        for log in os.listdir(log_dir):
            if log.find("log"):
                new_name = log_dir + "userdir0." + log
                log = log_dir + log
                shutil.move(log, new_name)
                shutil.copy(new_name, test_data.logs_dir)
                shutil.move(new_name, log)
    except OSError as e:
        print_error(test_data,"Error: Failed to copy the logs.")
        print_error(test_data,str(e) + "\n")
        logging.warning(traceback.format_exc())


def setDay():
    global Day
    Day = int(strftime("%d", localtime()))


def getLastDay():
    return Day


def getDay():
    return int(strftime("%d", localtime()))


def newDay():
    return getLastDay() != getDay()

#------------------------------------------------------------#
# Exception classes to manage "acceptable" thrown exceptions #
#         versus unexpected and fatal exceptions            #
#------------------------------------------------------------#


class FileNotFoundException(Exception):
    """
    If a file cannot be found by one of the helper functions,
    they will throw a FileNotFoundException unless the purpose
    is to return False.
    """
    def __init__(self, file):
        self.file = file
        self.strerror = "FileNotFoundException: " + file

    def print_error(self):
        Errors.print_error("Error: File could not be found at:")
        Errors.print_error(self.file + "\n")

    def error(self):
        error = "Error: File could not be found at:\n" + self.file + "\n"
        return error


class DirNotFoundException(Exception):
    """
    If a directory cannot be found by a helper function,
    it will throw this exception
    """
    def __init__(self, dir):
        self.dir = dir
        self.strerror = "DirNotFoundException: " + dir

    def print_error(self):
        Errors.print_error("Error: Directory could not be found at:")
        Errors.print_error(self.dir + "\n")

    def error(self):
        error = "Error: Directory could not be found at:\n" + self.dir + "\n"
        return error


class Errors:
    """A class used to manage error reporting.

    Attributes:
        printout: a listof_String, the non-error messages that were printed
        printerror: a listof_String, the error messages that were printed
        email_attchs: a listof_pathto_File, the files to be attached to the
        report email
    """
    printout = []
    printerror = []
    errors_out = []

    def print_out(msg):
        """Print out an informational message.

        Args:
            msg: a String, the message to be printed
        """
        print(msg)
        Errors.printout.append(msg)

    def print_error(msg):
        """Print out an error message.

        Args:
            msg: a String, the error message to be printed.
        """
        print(msg)
        Errors.printerror.append(msg)

    def clear_print_logs():
        """Reset the image-specific attributes of the Errors class."""
        Errors.printout = []
        Errors.printerror = []

    def add_errors_out(path):
        """Add the given file to be an attachment for the report email

        Args:
            file: a pathto_File, the file to add
        """
        Errors.errors_out.append(path)


class DiffResults(object):
    """Container for the results of the database diff tests.

    Stores artifact, object, and attribute counts and comparisons generated by
    TskDbDiff.

    Attributes:
        gold_attrs: a Nat, the number of gold attributes
        output_attrs: a Nat, the number of output attributes
        gold_objs: a Nat, the number of gold objects
        output_objs: a Nat, the number of output objects
        artifact_comp: a listof_String, describing the differences
        attribute_comp: a listof_String, describing the differences
        passed: a boolean, did the diff pass?
    """
    def __init__(self, tsk_diff):
        """Inits a DiffResults

        Args:
            tsk_diff: a TskDBDiff
        """
        self.gold_attrs = tsk_diff.gold_attributes
        self.output_attrs = tsk_diff.autopsy_attributes
        self.gold_objs = tsk_diff.gold_objects
        self.output_objs = tsk_diff.autopsy_objects
        self.artifact_comp = tsk_diff.artifact_comparison
        self.attribute_comp = tsk_diff.attribute_comparison
        self.gold_artifacts = len(tsk_diff.gold_artifacts)
        self.output_artifacts = len(tsk_diff.autopsy_artifacts)
        self.passed = tsk_diff.passed

    def get_artifact_comparison(self):
        if not self.artifact_comp:
            return "All counts matched"
        else:
            return "; ".join(self.artifact_comp)

    def get_attribute_comparison(self):
        if not self.attribute_comp:
            return "All counts matched"
        list = []
        for error in self.attribute_comp:
            list.append(error)
        return ";".join(list)


#-------------------------------------------------------------#
# Parses argv and stores booleans to match command line input #
#-------------------------------------------------------------#
class Args(object):
    """A container for command line options and arguments.

    Attributes:
        single: a boolean indicating whether to run in single file mode
        single_file: an Image to run the test on
        rebuild: a boolean indicating whether to run in rebuild mode
        list: a boolean indicating a config file was specified
        unallocated: a boolean indicating unallocated space should be ignored
        ignore: a boolean indicating the input directory should be ingnored
        keep: a boolean indicating whether to keep the SOLR index
        verbose: a boolean indicating whether verbose output should be printed
        exeception: a boolean indicating whether errors containing exception
                    exception_string should be printed
        exception_sring: a String representing and exception name
        fr: a boolean indicating whether gold standard images will be downloaded
    """
    def __init__(self):
        self.single = False
        self.single_file = ""
        self.rebuild = False
        self.both = False
        self.list = False
        self.config_file = ""
        self.unallocated = False
        self.ignore = False
        self.keep = False
        self.verbose = False
        self.exception = False
        self.exception_string = ""
        self.copy_diff_files = False
        self.diff_files_output_folder = ""

    def parse(self):
        """Get the command line arguments and parse them."""
        nxtproc = []
        nxtproc.append("python3")
        nxtproc.append(sys.argv.pop(0))
        while sys.argv:
            arg = sys.argv.pop(0)
            nxtproc.append(arg)
            if(arg == "-f"):
                arg = sys.argv.pop(0)
                print("Running on a single file:")
                print(path_fix(arg) + "\n")
                self.single = True
                self.single_file = path_fix(arg)
            elif(arg == "-r" or arg == "--rebuild"):
                print("Running in rebuild mode.\n")
                self.rebuild = True
            elif(arg == "-b" or arg == "--both"):
                print("Comparing then creating gold")
                self.both = True
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
                usage()
                return False
            elif arg == "-o" or arg == "--output":
                try:
                    arg = sys.argv.pop(0)
                    if not dir_exists(arg):
                        print("Invalid output folder given.\n")
                        return False
                    nxtproc.append(arg)
                    self.copy_diff_files = True
                    self.diff_files_output_folder = arg
                except:
                    print("Error: No output folder given.\n")
                    return False
            else:
                print(usage())
                return False
        # Return the args were sucessfully parsed
        return self._sanity_check()

    def _sanity_check(self):
        """Check to make sure there are no conflicting arguments and the
        specified files exist.

        Returns:
            False if there are conflicting arguments or a specified file does
            not exist, True otherwise
        """
        if self.single and self.list:
            print("Cannot run both from config file and on a single file.")
            return False
        if self.list:
           if not file_exists(self.config_file):
               print("Configuration file does not exist at:",
               self.config_file)
               return False
        elif self.single:
           if not file_exists(self.single_file):
               msg = "Image file does not exist at: " + self.single_file
               return False
        if (not self.single) and (not self.ignore) and (not self.list):
           self.config_file = "config.xml"
           if not file_exists(self.config_file):
               msg = "Configuration file does not exist at: " + self.config_file
               return False

        return True

####
# Helper Functions
####


def search_logs(string, test_data):
    """Search through all the known log files for a given string.

    Args:
        string: the String to search for.
        test_data: the TestData that holds the logs to search.

    Returns:
        a listof_String, the lines that contained the given String.
    """
    logs_path = test_data.logs_dir
    results = []
    for file in os.listdir(logs_path):
        log = codecs.open(make_path(logs_path, file), "r", "utf_8")
        try:
            for line in log:
                if string in line:
                    results.append(line)
            log.close()
        except UnicodeDecodeError:
            pass
    return results


def search_log(log, string, test_data):
    """Search the given log for any instances of a given string.

    Args:
        log: a pathto_File, the log to search in
        string: the String to search for.
        test_data: the TestData that holds the log to search.

    Returns:
        a listof_String, all the lines that the string is found on
    """
    logs_path = make_path(test_data.logs_dir, log)
    try:
        results = []
        log = codecs.open(logs_path, "r", "utf_8")
        try:
            for line in log:
                if string in line:
                    results.append(line)
            log.close()
        except UnicodeDecodeError:
            pass
        if results:
            return results
    except:
        raise FileNotFoundException(logs_path)

# Search through all the the logs of the given type
# Types include autopsy, tika, and solr


def search_log_set(type, string, test_data):
    """Search through all logs to the given type for the given string.

    Args:
        type: the type of log to search in.
        string: the String to search for.
        test_data: the TestData containing the logs to search.

    Returns:
        a listof_String, the lines on which the String was found.
    """
    logs_path = test_data.logs_dir
    results = []
    for file in os.listdir(logs_path):
        if type in file:
            log = codecs.open(make_path(logs_path, file), "r", "utf_8")
            try:
                for line in log:
                    if string in line:
                        results.append(line)
                log.close()
            except UnicodeDecodeError:
                pass
    return results


def find_msg_in_log_set(string, test_data):
   """Count how many strings of a certain type are in a log set.

   Args:
      string: the String to search for.
      test_data: the TestData containing the logs to search.
   Returns:
      an int, the number of occurances of the string type.
   """
   count = 0
   try:
      line = search_log_set("autopsy", string, test_data)[0]
      count = int(line.rstrip().split(": ")[2])
   except (Exception) as e:
      # there weren't any matching messages found
      pass
   return count


def find_msg_in_log(log, string, test_data):
   """Get the strings of a certain type that are in a log.

   Args:
      string: the String to search for.
      test_data: the TestData containing the log to search.
   Returns:
      a listof_String, the lines on which the String was found.
   """
   lines = []
   try:
      lines = search_log("autopsy.log.0", string, test_data)[0]
   except (Exception) as e:
      # there weren't any matching messages found
      pass
   return lines


def clear_dir(dir):
    """Clears all files from a directory and remakes it.

    Args:
        dir: a pathto_Dir, the directory to clear
    """
    try:
        if dir_exists(dir):
            shutil.rmtree(dir)
        os.makedirs(make_os_path(_platform, dir))
        return True;
    except OSError as e:
        print_error(test_data,"Error: Cannot clear the given directory:")
        print_error(test_data,dir + "\n")
        print(str(e))
        return False;


def del_dir(dir):
    """Delete the given directory.

    Args:
        dir: a pathto_Dir, the directory to delete
    """
    try:
        if dir_exists(dir):
            shutil.rmtree(dir)
        return True;
    except:
        print_error(test_data,"Error: Cannot delete the given directory:")
        print_error(test_data,dir + "\n")
        return False;


def get_file_in_dir(dir, ext):
    """Returns the first file in the given directory with the given extension.

    Args:
        dir: a pathto_Dir, the directory to search
        ext: a String, the extension to search for

    Returns:
        pathto_File, the file that was found
    """
    try:
        for file in os.listdir(dir):
            if file.endswith(ext):
                return make_path(dir, file)
        # If nothing has been found, raise an exception
        raise FileNotFoundException(dir)
    except:
        raise DirNotFoundException(dir)


def find_file_in_dir(dir, name, ext):
    """Find the file with the given name in the given directory.

    Args:
        dir: a pathto_Dir, the directory to search
        name: a String, the basename of the file to search for
        ext: a String, the extension of the file to search for
    """
    try:
        for file in os.listdir(dir):
            if file.startswith(name):
                if file.endswith(ext):
                    return make_path(dir, file)
        raise FileNotFoundException(dir)
    except:
        raise DirNotFoundException(dir)

def search_properties(string, properties_file):
    """Find a property value.

    Args:
        string: the String to search for.
        properties_file: the properties file to search.

    Returns:
        a string, the value for the given String.
    """
    result = ""
    pf = codecs.open(properties_file, "r", "utf-8")
    try:
        for line in pf:
            if string in line:
                result = line.split('=')[1].rstrip('\n\r ')
                break
        pf.close()
    except:
        print_error("Couldn't find property:" + string + " from: " + properties_file)
        sys.exit(1)
    return result


class OS:
  LINUX, MAC, WIN, CYGWIN = range(4)

if __name__ == "__main__":

    if sys.hexversion < 0x03000000:
        print("Python 3 required")
        sys.exit(1)

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
        sys.exit(1)
