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
from regression_utils import *

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

# TODO: Double check this purpose statement
# Folder name for gold standard database testing
AUTOPSY_TEST_CASE = "AutopsyTestCase"

# TODO: Double check this purpose statement
# The filename of the log to store error messages
COMMON_LOG = "AutopsyErrors.txt"

Day = 0

#----------------------#
#        Main          #
#----------------------#
def main():
    """Parse the command-line arguments, create the configuration, and run the tests."""
    args = Args()
    parse_result = args.parse()
    test_config = TestConfiguration(args)
    # The arguments were given wrong:
    if not parse_result:
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
    TestRunner.run_tests(test_config)


class TestRunner(object):
    """A collection of functions to run the regression tests."""

    def run_tests(test_config):
        """Run the tests specified by the main TestConfiguration.

        Executes the AutopsyIngest for each image and dispatches the results based on
        the mode (rebuild or testing)
        """
        test_data_list = [ TestData(image, test_config) for image in test_config.images ]

        Reports.html_add_images(test_config.html_log, test_config.images)

        logres =[]
        for test_data in test_data_list:
            Errors.clear_print_logs()
            Errors.set_testing_phase(test_data.image)
            if not (test_config.args.rebuild or os.path.exists(test_data.gold_archive)):
                msg = "Gold standard doesn't exist, skipping image:"
                Errors.print_error(msg)
                Errors.print_error(test_data.gold_archive)
                continue
            TestRunner._run_autopsy_ingest(test_data)

            if test_config.args.rebuild:
                TestRunner.rebuild(test_data)
            else:
                logres.append(TestRunner._run_test(test_data))
            test_data.printout = Errors.printout
            test_data.printerror = Errors.printerror

        Reports.write_html_foot(test_config.html_log)
        # TODO: move this elsewhere
        if (len(logres)>0):
            for lm in logres:
                for ln in lm:
                    Errors.add_email_msg(ln)

        # TODO: possibly worth putting this in a sub method
        if all([ test_data.overall_passed for test_data in test_data_list ]):
            Errors.add_email_msg("All images passed.\n")
        else:
            msg = "The following images failed:\n"
            for test_data in test_data_list:
                if not test_data.overall_passed:
                    msg += "\t" + test_data.image + "\n"
            Errors.add_email_msg(msg)
            html = open(test_config.html_log)
            Errors.add_email_attachment(html.name)
            html.close()

        if test_config.email_enabled:
            Emailer.send_email(test_config.mail_to, test_config.mail_server,
            test_config.mail_subject, Errors.email_body, Errors.email_attachs)

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

        # Dump the database before we diff or use it for rebuild
        TskDbDiff.dump_output_db(test_data)

        # merges logs into a single log for later diff / rebuild
        copy_logs(test_data)
        Logs.generate_log_data(test_data)

        TestRunner._handle_solr(test_data)
        TestRunner._handle_exception(test_data)

    #TODO: figure out return type of _run_test (logres)
    def _run_test(test_data):
        """Compare the results of the output to the gold standard.

        Args:
            test_data: the TestData

        Returns:
            logres?
        """
        TestRunner._extract_gold(test_data)

        # Look for core exceptions
        # @@@ Should be moved to TestResultsDiffer, but it didn't know about logres -- need to look into that
        logres = Logs.search_common_log("TskCoreException", test_data)

        TestResultsDiffer.run_diff(test_data)
        test_data.overall_passed = (test_data.html_report_passed and
        test_data.errors_diff_passed and test_data.sorted_data_passed and
        test_data.db_dump_passed and test_data.db_diff_results.passed)

        Reports.generate_reports(test_data)
        if(not test_data.overall_passed):
            Errors.add_email_attachment(test_data.common_log_path)
        return logres

    def _extract_gold(test_data):
        """Extract gold archive file to output/gold/tmp/

        Args:
            test_data: the TestData
        """
        extrctr = zipfile.ZipFile(test_data.gold_archive, 'r', compression=zipfile.ZIP_DEFLATED)
        extrctr.extractall(test_data.main_config.gold)
        extrctr.close
        time.sleep(2)

    def _handle_solr(test_data):
        """Clean up SOLR index if in keep mode (-k).

        Args:
            test_data: the TestData
        """
        if not test_data.main_config.args.keep:
            if clear_dir(test_data.solr_index):
                print_report([], "DELETE SOLR INDEX", "Solr index deleted.")
        else:
            print_report([], "KEEP SOLR INDEX", "Solr index has been kept.")

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
        # Errors to print
        errors = []
        # Delete the current gold standards
        gold_dir = test_config.img_gold
        clear_dir(test_config.img_gold)
        tmpdir = make_path(gold_dir, test_data.image_name)
        dbinpth = test_data.get_db_path(DBType.OUTPUT)
        dboutpth = make_path(tmpdir, DB_FILENAME)
        dataoutpth = make_path(tmpdir, test_data.image_name + "SortedData.txt")
        dbdumpinpth = test_data.get_db_dump_path(DBType.OUTPUT)
        dbdumpoutpth = make_path(tmpdir, test_data.image_name + "DBDump.txt")
        if not os.path.exists(test_config.img_gold):
            os.makedirs(test_config.img_gold)
        if not os.path.exists(tmpdir):
            os.makedirs(tmpdir)
        try:
            copy_file(dbinpth, dboutpth)
            if file_exists(test_data.get_sorted_data_path(DBType.OUTPUT)):
                copy_file(test_data.get_sorted_data_path(DBType.OUTPUT), dataoutpth)
            copy_file(dbdumpinpth, dbdumpoutpth)
            error_pth = make_path(tmpdir, test_data.image_name+"SortedErrors.txt")
            copy_file(test_data.sorted_log, error_pth)
        except Exception as e:
            Errors.print_error(str(e))
            print(str(e))
            print(traceback.format_exc())
        # Rebuild the HTML report
        output_html_report_dir = test_data.get_html_report_path(DBType.OUTPUT)
        gold_html_report_dir = make_path(tmpdir, "Report")

        try:
            copy_dir(output_html_report_dir, gold_html_report_dir)
        except FileNotFoundException as e:
            errors.append(e.error())
        except Exception as e:
            errors.append("Error: Unknown fatal error when rebuilding the gold html report.")
            errors.append(str(e) + "\n")
            print(traceback.format_exc())
        oldcwd = os.getcwd()
        zpdir = gold_dir
        os.chdir(zpdir)
        os.chdir("..")
        img_gold = "tmp"
        img_archive = make_path(test_data.image_name+"-archive.zip")
        comprssr = zipfile.ZipFile(img_archive, 'w',compression=zipfile.ZIP_DEFLATED)
        TestRunner.zipdir(img_gold, comprssr)
        comprssr.close()
        os.chdir(oldcwd)
        del_dir(test_config.img_gold)
        okay = "Sucessfully rebuilt all gold standards."
        print_report(errors, "REBUILDING", okay)

    def zipdir(path, zip):
        for root, dirs, files in os.walk(path):
            for file in files:
                zip.write(os.path.join(root, file))

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
        os.makedirs(test_data.output_path)
        test_data.ant = ["ant"]
        test_data.ant.append("-v")
        test_data.ant.append("-f")
    #   case.ant.append(case.build_path)
        test_data.ant.append(os.path.join("..","..","Testing","build.xml"))
        test_data.ant.append("regression-test")
        test_data.ant.append("-l")
        test_data.ant.append(test_data.antlog_dir)
        test_data.ant.append("-Dimg_path=" + test_data.image_file)
        test_data.ant.append("-Dknown_bad_path=" + test_config.known_bad_path)
        test_data.ant.append("-Dkeyword_path=" + test_config.keyword_path)
        test_data.ant.append("-Dnsrl_path=" + test_config.nsrl_path)
        test_data.ant.append("-Dgold_path=" + test_config.gold)
        test_data.ant.append("-Dout_path=" +
        make_local_path(test_data.output_path))
        test_data.ant.append("-Dignore_unalloc=" + "%s" % test_config.args.unallocated)
        test_data.ant.append("-Dtest.timeout=" + str(test_config.timeout))

        Errors.print_out("Ingesting Image:\n" + test_data.image_file + "\n")
        Errors.print_out("CMD: " + " ".join(test_data.ant))
        Errors.print_out("Starting test...\n")
        antoutpth = make_local_path(test_data.main_config.output_dir, "antRunOutput.txt")
        antout = open(antoutpth, "a")
        if SYS is OS.CYGWIN:
            subprocess.call(test_data.ant, stdout=subprocess.PIPE)
        elif SYS is OS.WIN:
            theproc = subprocess.Popen(test_data.ant, shell = True, stdout=subprocess.PIPE)
            theproc.communicate()
        antout.close()


class TestData(object):
    """Container for the input and output of a single image.

    Represents data for the test of a single image, including path to the image,
    database paths, etc.

    Attributes:
        main_config: the global TestConfiguration
        ant: a listof_String, the ant command for this TestData
        image_file: a pathto_Image, the image for this TestData
        image: a String, the image file's name
        image_name: a String, the image file's name with a trailing (0)
        output_path: pathto_Dir, the output directory for this TestData
        autopsy_data_file: a pathto_File, the IMAGE_NAMEAutopsy_data.txt file
        warning_log: a pathto_File, the AutopsyLogs.txt file
        antlog_dir: a pathto_File, the antlog.txt file
        test_dbdump: a pathto_File, the database dump, IMAGENAMEDump.txt
        common_log_path: a pathto_File, the IMAGE_NAMECOMMON_LOG file
        sorted_log: a pathto_File, the IMAGENAMESortedErrors.txt file
        reports_dir: a pathto_Dir, the AutopsyTestCase/Reports folder
        gold_data_dir: a pathto_Dir, the gold standard directory
        gold_archive: a pathto_File, the gold standard archive
        logs_dir: a pathto_Dir, the location where autopsy logs are stored
        solr_index: a pathto_Dir, the locatino of the solr index
        db_diff_results: a DiffResults, the results of the database comparison
        html_report_passed: a boolean, did the HTML report diff pass?
        errors_diff_passed: a boolean, did the error diff pass?
        db_dump_passed: a boolean, did the db dump diff pass?
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
        # TODO: This 0 should be be refactored out, but it will require rebuilding and changing of outputs.
        self.image = get_image_name(self.image_file)
        self.image_name = self.image + "(0)"
        # Directory structure and files
        self.output_path = make_path(self.main_config.output_dir, self.image_name)
        self.autopsy_data_file = make_path(self.output_path, self.image_name + "Autopsy_data.txt")
        self.warning_log = make_local_path(self.output_path, "AutopsyLogs.txt")
        self.antlog_dir = make_local_path(self.output_path, "antlog.txt")
        self.test_dbdump = make_path(self.output_path, self.image_name +
        "DBDump.txt")
        self.common_log_path = make_local_path(self.output_path, self.image_name + COMMON_LOG)
        self.sorted_log = make_local_path(self.output_path, self.image_name + "SortedErrors.txt")
        self.reports_dir = make_path(self.output_path, AUTOPSY_TEST_CASE, "Reports")
        self.gold_data_dir = make_path(self.main_config.img_gold, self.image_name)
        self.gold_archive = make_path(self.main_config.gold,
        self.image_name + "-archive.zip")
        self.logs_dir = make_path(self.output_path, "logs")
        self.solr_index = make_path(self.output_path, AUTOPSY_TEST_CASE,
        "ModuleOutput", "KeywordSearch")
        # Results and Info
        self.db_diff_results = None
        self.html_report_passed = False
        self.errors_diff_passed = False
        self.sorted_data_passed = False
        self.db_dump_passed = False
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
                html_path = make_path(self.reports_dir, fs)
                if os.path.isdir(html_path):
                    break
            return make_path(html_path, os.listdir(html_path)[0])

    def get_sorted_data_path(self, file_type):
        """Get the path to the SortedData file that corresponds to the given DBType.

        Args:
            file_type: the DBType of the path to be generated
        """
        return self._get_path_to_file(file_type, "SortedData.txt")

    def get_sorted_errors_path(self, file_type):
        """Get the path to the SortedErrors file that correspodns to the given
        DBType.

        Args:
            file_type: the DBType of the path to be generated
        """
        return self._get_path_to_file(file_type, "SortedErrors.txt")

    def get_db_dump_path(self, file_type):
        """Get the path to the DBDump file that corresponds to the given DBType.

        Args:
            file_type: the DBType of the path to be generated
        """
        return self._get_path_to_file(file_type, "DBDump.txt")

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
    """

    def __init__(self, args):
        """Inits TestConfiguration and loads a config file if available.

        Args:
            args: an Args, the command line arguments.
        """
        self.args = args
        # Paths:
        self.output_dir = ""
        self.input_dir = make_local_path("..","input")
        self.gold = make_path("..", "output", "gold")
        self.img_gold = make_path(self.gold, 'tmp')
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
        # Email info
        self.email_enabled = False
        self.mail_server = ""
        self.mail_to = ""
        self.mail_subject = ""
        # Set the timeout to something huge
        # The entire tester should not timeout before this number in ms
        # However it only seems to take about half this time
        # And it's very buggy, so we're being careful
        self.timeout = 24 * 60 * 60 * 1000 * 1000

        if not self.args.single:
            self._load_config_file(self.args.config_file)
        else:
            self.images.append(self.args.single_file)
        self._init_logs()
        #self._init_imgs()
        #self._init_build_info()


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
            if parsed_config.getElementsByTagName("indir"):
                self.input_dir = parsed.getElementsByTagName("indir")[0].getAttribute("value").encode().decode("utf_8")
            if parsed_config.getElementsByTagName("global_csv"):
                self.global_csv = parsed.getElementsByTagName("global_csv")[0].getAttribute("value").encode().decode("utf_8")
                self.global_csv = make_local_path(self.global_csv)
            if parsed_config.getElementsByTagName("golddir"):
                self.gold = parsed.getElementsByTagName("golddir")[0].getAttribute("value").encode().decode("utf_8")
                self.img_gold = make_path(self.gold, 'tmp')

            self._init_imgs(parsed_config)
            self._init_build_info(parsed_config)
            self._init_email_info(parsed_config)

        except Exception as e:
            msg = "There was an error running with the configuration file.\n"
            msg += "\t" + str(e)
            Errors.add_email_msg(msg)
            logging.critical(traceback.format_exc())
            print(traceback.format_exc())

    def _init_logs(self):
        """Setup output folder, logs, and reporting infrastructure."""
        if(not dir_exists(make_path("..", "output", "results"))):
            os.makedirs(make_path("..", "output", "results",))
        self.output_dir = make_path("..", "output", "results", time.strftime("%Y.%m.%d-%H.%M.%S"))
        os.makedirs(self.output_dir)
        self.csv = make_local_path(self.output_dir, "CSV.txt")
        self.html_log = make_path(self.output_dir, "AutopsyTestCase.html")
        log_name = self.output_dir + "\\regression.log"
        logging.basicConfig(filename=log_name, level=logging.DEBUG)

    def _init_build_info(self, parsed_config):
        """Initializes paths that point to information necessary to run the AutopsyIngest."""
        build_elements = parsed_config.getElementsByTagName("build")
        if build_elements:
            build_element = build_elements[0]
            build_path = build_element.getAttribute("value").encode().decode("utf_8")
            self.build_path = build_path

    def _init_imgs(self, parsed_config):
        """Initialize the list of images to run tests on."""
        for element in parsed_config.getElementsByTagName("image"):
            value = element.getAttribute("value").encode().decode("utf_8")
            print ("Image in Config File: " + value)
            if file_exists(value):
                self.images.append(value)
            else:
                msg = "File: " + value + " doesn't exist"
                Errors.print_error(msg)
                Errors.add_email_msg(msg)
        image_count = len(self.images)

        # Sanity check to see if there are obvious gold images that we are not testing
        gold_count = 0
        for file in os.listdir(self.gold):
            if not(file == 'tmp'):
                gold_count+=1

        if (image_count > gold_count):
            print("******Alert: There are more input images than gold standards, some images will not be properly tested.\n")
        elif (image_count < gold_count):
            print("******Alert: There are more gold standards than input images, this will not check all gold Standards.\n")

    def _init_email_info(self, parsed_config):
        """Initializes email information dictionary"""
        email_elements = parsed_config.getElementsByTagName("email")
        if email_elements:
            mail_to = email_elements[0]
            self.mail_to = mail_to.getAttribute("value").encode().decode("utf_8")
        mail_server_elements = parsed_config.getElementsByTagName("mail_server")
        if mail_server_elements:
            mail_from = mail_server_elements[0]
            self.mail_server = mail_from.getAttribute("value").encode().decode("utf_8")
        subject_elements = parsed_config.getElementsByTagName("subject")
        if subject_elements:
            subject = subject_elements[0]
            self.mail_subject = subject.getAttribute("value").encode().decode("utf_8")
        if self.mail_server and self.mail_to:
            self.email_enabled = True



class TskDbDiff(object):
    """Represents the differences between the gold and output databases.

    Contains methods to compare two databases.

    Attributes:
        gold_artifacts:
        autopsy_artifacts:
        gold_attributes:
        autopsy_attributes:
        gold_objects:
        autopsy_objects:
        artifact_comparison:
        attribute_comparision:
        report_errors: a listof_listof_String, the error messages that will be
        printed to screen in the run_diff method
        passed: a boolean, did the diff pass?
        autopsy_db_file:
        gold_db_file:
    """
    def __init__(self, output_db_path, gold_db_path):
        """Constructor for TskDbDiff.

        Args:
            output_db_path: a pathto_File, the output database
            gold_db_path: a pathto_File, the gold database
        """
        self.gold_artifacts = []
        self.autopsy_artifacts = []
        self.gold_attributes = 0
        self.autopsy_attributes = 0
        self.gold_objects = 0
        self.autopsy_objects = 0
        self.artifact_comparison = []
        self.attribute_comparison = []
        self.report_errors = []
        self.autopsy_db_file = output_db_path
        self.gold_db_file = gold_db_path

    def _get_artifacts(self, cursor):
        """Get a list of artifacts from the given SQLCursor.

        Args:
            cursor: SQLCursor - the cursor to execute on

        Returns:
            listof_Artifact - the artifacts found by the query
        """
        cursor.execute("SELECT COUNT(*) FROM blackboard_artifact_types")
        length = cursor.fetchone()[0] + 1
        artifacts = []
        for type_id in range(1, length):
            cursor.execute("SELECT COUNT(*) FROM blackboard_artifacts WHERE artifact_type_id=%d" % type_id)
            artifacts.append(cursor.fetchone()[0])
        return artifacts

    def _count_attributes(self, cursor):
        """Count the attributes from the given SQLCursor.

        Args:
            cursor: SQLCursor - the cursor to execute on

        Returns:
            Nat - the number of attributes found by the query
        """
        cursor.execute("SELECT COUNT(*) FROM blackboard_attributes")
        return cursor.fetchone()[0]

    def _count_objects(self, cursor):
        """Count the objects from the given SQLCursor.

        Args:
            cursor: SQLCursor - the cursor to execute on

        Returns:
            Nat - the number of objects found by the query
        """
        cursor.execute("SELECT COUNT(*) FROM tsk_objects")
        return cursor.fetchone()[0]

    def _compare_bb_artifacts(self):
        """Compares the blackboard artifact counts of two databases.

        Returns:
            True if the artifacts are the same, false otherwise.
        """
        exceptions = []
        passed = True
        try:
            if self.gold_artifacts != self.autopsy_artifacts:
                msg = "There was a difference in the number of artifacts.\n"
                Errors.add_email_msg(msg)
            rner = len(self.gold_artifacts)
            for type_id in range(1, rner):
                if self.gold_artifacts[type_id] != self.autopsy_artifacts[type_id]:
                    error = str("Artifact counts do not match for type id %d. " % type_id)
                    error += str("Gold: %d, Test: %d" %
                                (self.gold_artifacts[type_id],
                                 self.autopsy_artifacts[type_id]))
                    exceptions.append(error)
                    passed = False
            self.report_errors.append(exceptions)
            return passed
        except Exception as e:
            Errors.print_error(str(e))
            exceptions.append("Error: Unable to compare blackboard_artifacts.\n")
            self.report_errors.append(exceptions)
            return False

    def _compare_bb_attributes(self):
        """Compares the blackboard attribute counts of two databases.

        Updates this TskDbDiff's report_errors with the error messages from the
        attribute diff

        Returns:
            True is the attributes are the same, False otherwise.
        """
        exceptions = []
        passed = True
        try:
            if self.gold_attributes != self.autopsy_attributes:
                error = "Attribute counts do not match. "
                error += str("Gold: %d, Test: %d" % (self.gold_attributes, self.autopsy_attributes))
                exceptions.append(error)
                msg = "There was a difference in the number of attributes.\n"
                Errors.add_email_msg(msg)
                passed = False
            self.report_errors.append(exceptions)
            return passed
        except Exception as e:
            exceptions.append("Error: Unable to compare blackboard_attributes.\n")
            self.report_errors.append(exceptions)
            return False

    def _compare_tsk_objects(self):
        """Compares the TSK object counts of two databases."""
        exceptions = []
        passed = True
        try:
            if self.gold_objects != self.autopsy_objects:
                error = "TSK Object counts do not match. "
                error += str("Gold: %d, Test: %d" % (self.gold_objects, self.autopsy_objects))
                exceptions.append(error)
                msg ="There was a difference between the tsk object counts.\n"
                Errors.add_email_msg(msg)
                passed = False
            self.report_errors.append(exceptions)
            return passed
        except Exception as e:
            exceptions.append("Error: Unable to compare tsk_objects.\n")
            self.report_errors.append(exceptions)
            return False

    def _get_basic_counts(self, autopsy_cur, gold_cur):
        """Count the items necessary to compare the databases.

        Gets the counts of objects, artifacts, and attributes in the Gold
        and Ouput databases and updates this TskDbDiff's attributes
        accordingly

        Args:
            autopsy_cur: SQLCursor - the cursor for the output database
            gold_cur:    SQLCursor - the cursor for the gold database
        """
        try:
            # Objects
            self.gold_objects = self._count_objects(gold_cur)
            self.autopsy_objects = self._count_objects(autopsy_cur)
            # Artifacts
            self.gold_artifacts = self._get_artifacts(gold_cur)
            self.autopsy_artifacts = self._get_artifacts(autopsy_cur)
            # Attributes
            self.gold_attributes = self._count_attributes(gold_cur)
            self.autopsy_attributes = self._count_attributes(autopsy_cur)
        except Exception as e:
            Errors.print_error("Way out:" + str(e))

    def run_diff(self):
        """Basic test between output and gold databases.

        Compares only counts of objects and blackboard items.
        Note: SQLITE needs unix style pathing
        """
        # Check to make sure both db files exist
        if not file_exists(self.autopsy_db_file):
            Errors.print_error("Error: TskDbDiff file does not exist at:")
            Errors.print_error(self.autopsy_db_file + "\n")
            return
        if not file_exists(self.gold_db_file):
            Errors.print_error("Error: Gold database file does not exist at:")
            Errors.print_error(self.gold_db_file + "\n")
            return

        # Get connections and cursors to output / gold databases
        autopsy_con = sqlite3.connect(self.autopsy_db_file)
        autopsy_cur = autopsy_con.cursor()
        gold_con = sqlite3.connect(self.gold_db_file)
        gold_cur = gold_con.cursor()

        # Get Counts of objects, artifacts, and attributes
        self._get_basic_counts(autopsy_cur, gold_cur)

        # We're done with the databases, close up the connections
        autopsy_con.close()
        gold_con.close()

        # Compare counts
        objects_passed = self._compare_tsk_objects()
        artifacts_passed = self._compare_bb_artifacts()
        attributes_passed = self._compare_bb_attributes()

        self.passed = objects_passed and artifacts_passed and attributes_passed

        self.artifact_comparison = self.report_errors[1]
        self.attribute_comparison = self.report_errors[2]

        okay = "All counts match."
        print_report(self.report_errors[0], "COMPARE TSK OBJECTS", okay)
        print_report(self.report_errors[1], "COMPARE ARTIFACTS", okay)
        print_report(self.report_errors[2], "COMPARE ATTRIBUTES", okay)

        return DiffResults(self)

    def _dump_output_db_bb(autopsy_con, db_file, data_file, sorted_data_file):
        """Dumps sorted text results to the given output location.

        Smart method that deals with a blackboard comparison to avoid issues
        with different IDs based on when artifacts were created.

        Args:
            autopsy_con: a SQLConn to the autopsy database.
            db_file: a pathto_File, the output database.
            data_file: a pathto_File, the dump file to write to
            sorted_data_file: a pathto_File, the sorted dump file to write to
        """
        autopsy_cur2 = autopsy_con.cursor()
        # Get the list of all artifacts
        # @@@ Could add a SORT by parent_path in here since that is how we are going to later sort it.
        autopsy_cur2.execute("SELECT tsk_files.parent_path, tsk_files.name, blackboard_artifact_types.display_name, blackboard_artifacts.artifact_id FROM blackboard_artifact_types INNER JOIN blackboard_artifacts ON blackboard_artifact_types.artifact_type_id = blackboard_artifacts.artifact_type_id INNER JOIN tsk_files ON tsk_files.obj_id = blackboard_artifacts.obj_id")
        database_log = codecs.open(data_file, "wb", "utf_8")
        rw = autopsy_cur2.fetchone()
        appnd = False
        counter = 0
        artifact_count = 0
        artifact_fail = 0
        # Cycle through artifacts
        try:
            while (rw != None):
                # File Name and artifact type
                if(rw[0] != None):
                    database_log.write(rw[0] + rw[1] + ' <artifact type="' + rw[2] + '" > ')
                else:
                    database_log.write(rw[1] + ' <artifact type="' + rw[2] + '" > ')

                # Get attributes for this artifact
                autopsy_cur1 = autopsy_con.cursor()
                looptry = True
                artifact_count += 1
                try:
                    key = ""
                    key = str(rw[3])
                    key = key,
                    autopsy_cur1.execute("SELECT blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double FROM blackboard_attributes INNER JOIN blackboard_attribute_types ON blackboard_attributes.attribute_type_id = blackboard_attribute_types.attribute_type_id WHERE artifact_id =? ORDER BY blackboard_attributes.source, blackboard_attribute_types.display_name, blackboard_attributes.value_type, blackboard_attributes.value_text, blackboard_attributes.value_int32, blackboard_attributes.value_int64, blackboard_attributes.value_double", key)
                    attributes = autopsy_cur1.fetchall()
                except Exception as e:
                    Errors.print_error(str(e))
                    Errors.print_error(str(rw[3]))
                    msg ="Attributes in artifact id (in output DB)# " + str(rw[3]) + " encountered an error: " + str(e) +" .\n"
                    Errors.add_email_msg(msg)
                    looptry = False
                    print(artifact_fail)
                    artifact_fail += 1
                    print(artifact_fail)
                    database_log.write('Error Extracting Attributes');

                # Print attributes
                if(looptry == True):
                    src = attributes[0][0]
                    for attr in attributes:
                        val = 3 + attr[2]
                        numvals = 0
                        for x in range(3, 6):
                            if(attr[x] != None):
                                numvals += 1
                        if(numvals > 1):
                            msg = "There were too many values for attribute type: " + attr[1] + " for artifact with id #" + str(rw[3]) + ".\n"
                            Errors.add_email_msg(msg)
                            Errors.print_error(msg)
                            if(not appnd):
                                Errors.add_email_attachment(db_file)
                                appnd = True
                        if(not attr[0] == src):
                            msg ="There were inconsistent sources for artifact with id #" + str(rw[3]) + ".\n"
                            Errors.add_email_msg(msg)
                            Errors.print_error(msg)
                            if(not appnd):
                                Errors.add_email_attachment(db_file)
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
                                Errors.print_error("Inner exception" + outp)
                        except Exception as e:
                                Errors.print_error(str(e))
                        database_log.write('" />')
                database_log.write(' <artifact/>\n')
                rw = autopsy_cur2.fetchone()

            # Now sort the file
            srtcmdlst = ["sort", data_file, "-o", sorted_data_file]
            subprocess.call(srtcmdlst)
            print(artifact_fail)
            if(artifact_fail > 0):
                msg ="There were " + str(artifact_count) + " artifacts and " + str(artifact_fail) + " threw an exception while loading.\n"
                Errors.add_email_msg(msg)
        except Exception as e:
            Errors.print_error('outer exception: ' + str(e))

    def _dump_output_db_nonbb(test_data):
        """Dumps a database to a text file.

        Does not dump the artifact and attributes.

        Args:
            test_data: the TestData that corresponds with this dump.
        """
        # Make a copy of the DB
        autopsy_db_file = test_data.get_db_path(DBType.OUTPUT)
        backup_db_file = test_data.get_db_path(DBType.BACKUP)
        copy_file(autopsy_db_file, backup_db_file)
        autopsy_con = sqlite3.connect(backup_db_file)

        # Delete the blackboard tables
        autopsy_con.execute("DROP TABLE blackboard_artifacts")
        autopsy_con.execute("DROP TABLE blackboard_attributes")
        dump_file = test_data.test_dbdump
        database_log = codecs.open(dump_file, "wb", "utf_8")
        dump_list = autopsy_con.iterdump()
        try:
            for line in dump_list:
                try:
                    database_log.write(line + "\n")
                except Exception as e:
                    Errors.print_error("dump_output_db_nonbb: Inner dump Exception:" + str(e))
        except Exception as e:
            Errors.print_error("dump_output_db_nonbb: Outer dump Exception:" + str(e))


    def dump_output_db(test_data):
        """Dumps the given database to text files for later comparison.

        Args:
            test_data: the TestData that corresponds to this dump.
        """
        autopsy_db_file = test_data.get_db_path(DBType.OUTPUT)
        autopsy_con = sqlite3.connect(autopsy_db_file)
        autopsy_cur = autopsy_con.cursor()
        # Try to query the databases. Ignore any exceptions, the function will
        # return an error later on if these do fail
        TskDbDiff._dump_output_db_bb(autopsy_con, autopsy_db_file,
        test_data.autopsy_data_file,
        test_data.get_sorted_data_path(DBType.OUTPUT))
        TskDbDiff._dump_output_db_nonbb(test_data)
        autopsy_con.close()

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

            # Diff the gold and output databases
            output_db_path = test_data.get_db_path(DBType.OUTPUT)
            gold_db_path = test_data.get_db_path(DBType.GOLD)
            db_diff = TskDbDiff(output_db_path, gold_db_path)
            test_data.db_diff_results = db_diff.run_diff()

            # Compare Exceptions
            # replace is a fucntion that replaces strings of digits with 'd'
            # this is needed so dates and times will not cause the diff to fail
            replace = lambda file: re.sub(re.compile("\d"), "d", file)
            output_errors = test_data.get_sorted_errors_path(DBType.OUTPUT)
            gold_errors = test_data.get_sorted_errors_path(DBType.GOLD)
            passed = TestResultsDiffer._compare_text(output_errors, gold_errors,
            replace)
            test_data.errors_diff_passed = passed

            # Compare smart blackboard results
            output_data = test_data.get_sorted_data_path(DBType.OUTPUT)
            gold_data = test_data.get_sorted_data_path(DBType.GOLD)
            passed = TestResultsDiffer._compare_text(output_data, gold_data)
            test_data.sorted_data_passed = passed

            # Compare the rest of the database (non-BB)
            output_dump = test_data.get_db_dump_path(DBType.OUTPUT)
            gold_dump = test_data.get_db_dump_path(DBType.GOLD)
            passed = TestResultsDiffer._compare_text(output_dump, gold_dump)
            test_data.db_dump_passed = passed

            # Compare html output
            gold_report_path = test_data.get_html_report_path(DBType.GOLD)
            output_report_path = test_data.get_html_report_path(DBType.OUTPUT)
            passed = TestResultsDiffer._html_report_diff(gold_report_path,
            output_report_path)
            test_data.html_report_passed = passed

            # Clean up tmp folder
            del_dir(test_data.gold_data_dir)

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
            dffcmdlst = ["diff", output_file, gold_file]
            subprocess.call(dffcmdlst, stdout = diff_file)
            Errors.add_email_attachment(diff_path)
            msg = "There was a difference in "
            msg += os.path.basename(output_file) + ".\n"
            Errors.add_email_msg(msg)
            Errors.print_error(msg)
            return False
        else:
            return True

    def _html_report_diff(gold_report_path, output_report_path):
        """Compare the output and gold html reports.

        Args:
            gold_report_path: a pathto_Dir, the gold HTML report directory
            output_report_path: a pathto_Dir, the output HTML report directory

        Returns:
            true, if the reports match, false otherwise.
        """
        try:
            gold_html_files = get_files_by_ext(gold_report_path, ".html")
            output_html_files = get_files_by_ext(output_report_path, ".html")

            #ensure both reports have the same number of files and are in the same order
            if(len(gold_html_files) != len(output_html_files)):
                msg = "The reports did not have the same number or files."
                msg += "One of the reports may have been corrupted."
                Errors.print_error(msg)
            else:
                gold_html_files.sort()
                output_html_files.sort()

            total = {"Gold": 0, "New": 0}
            for gold, output in zip(gold_html_files, output_html_files):
                count = TestResultsDiffer._compare_report_files(gold, output)
                total["Gold"] += count[0]
                total["New"] += count[1]

            okay = "The test report matches the gold report."
            errors=["Gold report had " + str(total["Gold"]) +" errors", "New report had " + str(total["New"]) + " errors."]
            print_report(errors, "REPORT COMPARISON", okay)

            if total["Gold"] == total["New"]:
                return True
            else:
                Errors.print_error("The reports did not match each other.\n " + errors[0] +" and the " + errors[1])
                return False
        except DirNotFoundException as e:
            e.print_error()
            return False
        except Exception as e:
            Errors.print_error("Error: Unknown fatal error comparing reports.")
            Errors.print_error(str(e) + "\n")
            logging.critical(traceback.format_exc())
            return False

    def _compare_report_files(a_path, b_path):
        """Compares the two specified report html files.

        Args:
            a_path: a pathto_File, the first html report file
            b_path: a pathto_File, the second html report file

        Returns:
            a tuple of (Nat, Nat), which represent the length of each
            unordered list in the html report files, or (0, 0) if the
            lenghts are the same.
        """
        a_file = open(a_path)
        b_file = open(b_path)
        a = a_file.read()
        b = b_file.read()
        a = a[a.find("<ul>"):]
        b = b[b.find("<ul>"):]

        a_list = TestResultsDiffer._split(a, 50)
        b_list = TestResultsDiffer._split(b, 50)
        if not len(a_list) == len(b_list):
            ex = (len(a_list), len(b_list))
            return ex
        else:
            return (0, 0)

    # Split a string into an array of string of the given size
    def _split(input, size):
        return [input[start:start+size] for start in range(0, len(input), size)]


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

    def _generate_html(test_data):
        """Generate the HTML log file."""
        # If the file doesn't exist yet, this is the first test_config to run for
        # this test, so we need to make the start of the html log
        html_log = test_data.main_config.html_log
        if not file_exists(html_log):
            Reports.write_html_head()
        try:
            html = open(html_log, "a")
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
            info += "<tr><td>TSK Objects Count:</td>"
            info += "<td>" + str(test_data.db_diff_results.output_objs) + "</td></tr>"
            info += "<tr><td>Artifacts Count:</td>"
            info += "<td>" + str(test_data.db_diff_results.output_artifacts)+ "</td></tr>"
            info += "<tr><td>Attributes Count:</td>"
            info += "<td>" + str(test_data.db_diff_results.output_attrs) + "</td></tr>"
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
            html.close()
        except Exception as e:
            Errors.print_error("Error: Unknown fatal error when creating HTML log at:")
            Errors.print_error(html_log)
            Errors.print_error(str(e) + "\n")
            logging.critical(traceback.format_exc())

    def write_html_head(html_log):
        """Write the top of the HTML log file.

        Args:
            html_log: a pathto_File, the global HTML log
        """
        print(html_log)
        html = open(str(html_log), "a")
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
        html.close()

    def write_html_foot(html_log):
        """Write the bottom of the HTML log file.

        Args:
            html_log: a pathto_File, the global HTML log
        """
        html = open(html_log, "a")
        head = "</body></html>"
        html.write(head)
        html.close()

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
        html = open(html_log, "a")
        links = []
        for full_name in full_image_names:
            name = get_image_name(full_name)
            links.append("<a href='#" + name + "(0)'>" + name + "</a>")
        html.write("<p align='center'>" + (" | ".join(links)) + "</p>")
        html.close()

    def _generate_csv(csv_path, test_data):
        """Generate the CSV log file"""
        try:
            # If the CSV file hasn't already been generated, this is the
            # first run, and we need to add the column names
            if not file_exists(csv_path):
                Reports.csv_header(csv_path)
            # Now add on the fields to a new row
            csv = open(csv_path, "a")

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
            vars.append( str(test_data.db_diff_results.output_objs) )
            vars.append( str(test_data.db_diff_results.output_artifacts) )
            vars.append( str(test_data.db_diff_results.output_objs) )
            vars.append( make_local_path("gold", test_data.image_name, DB_FILENAME) )
            vars.append( test_data.db_diff_results.get_artifact_comparison() )
            vars.append( test_data.db_diff_results.get_attribute_comparison() )
            vars.append( make_local_path("gold", test_data.image_name, "standard.html") )
            vars.append( str(test_data.html_report_passed) )
            vars.append( test_data.ant_to_string() )
            # Join it together with a ", "
            output = "|".join(vars)
            output += "\n"
            # Write to the log!
            csv.write(output)
            csv.close()
        except Exception as e:
            Errors.print_error("Error: Unknown fatal error when creating CSV file at:")
            Errors.print_error(csv_path)
            Errors.print_error(str(e) + "\n")
            print(traceback.format_exc())
            logging.critical(traceback.format_exc())

    def csv_header(csv_path):
        """Generate the CSV column names."""
        csv = open(csv_path, "w")
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
            Errors.print_error("Error: Unknown fatal error when filling test_config data.")
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
            common_log.write("--------------------------------------------------\n")
            common_log.write(test_data.image_name + "\n")
            common_log.write("--------------------------------------------------\n")
            rep_path = make_local_path(test_data.main_config.output_dir)
            rep_path = rep_path.replace("\\\\", "\\")
            for file in os.listdir(logs_path):
                log = codecs.open(make_path(logs_path, file), "r", "utf_8")
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
            srtcmdlst = ["sort", test_data.common_log_path, "-o", test_data.sorted_log]
            subprocess.call(srtcmdlst)
        except Exception as e:
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
        except Exception as e:
            Errors.print_error("Error: Unable to open autopsy.log.0.")
            Errors.print_error(str(e) + "\n")
            logging.warning(traceback.format_exc())
        # Start date must look like: "Jul 16, 2012 12:57:53 PM"
        # End date must look like: "Mon Jul 16 13:02:42 2012"
        # *** If logging time format ever changes this will break ***
        start = datetime.datetime.strptime(test_data.start_date, "%b %d, %Y %I:%M:%S %p")
        end = datetime.datetime.strptime(test_data.end_date, "%a %b %d %H:%M:%S %Y")
        test_data.total_test_time = str(end - start)

        try:
            # Set Autopsy version, heap space, ingest time, and service times

            version_line = search_logs("INFO: Application name: Autopsy, version:", test_data)[0]
            test_data.autopsy_version = get_word_at(version_line, 5).rstrip(",")

            test_data.heap_space = search_logs("Heap memory usage:", test_data)[0].rstrip().split(": ")[1]

            ingest_line = search_logs("Ingest (including enqueue)", test_data)[0]
            test_data.total_ingest_time = get_word_at(ingest_line, 6).rstrip()

            message_line = search_log_set("autopsy", "Ingest messages count:", test_data)[0]
            test_data.ingest_messages = int(message_line.rstrip().split(": ")[2])

            files_line = search_log_set("autopsy", "Indexed files count:", test_data)[0]
            test_data.indexed_files = int(files_line.rstrip().split(": ")[2])

            chunks_line = search_log_set("autopsy", "Indexed file chunks count:", test_data)[0]
            test_data.indexed_chunks = int(chunks_line.rstrip().split(": ")[2])
        except Exception as e:
            Errors.print_error("Error: Unable to find the required information to fill test_config data.")
            Errors.print_error(str(e) + "\n")
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
        except Exception as e:
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
        log_dir = os.path.join("..", "..", "Testing","build","test","qa-functional","work","userdir0","var","log")
        shutil.copytree(log_dir, test_data.logs_dir)
    except Exception as e:
        printerror(test_data,"Error: Failed to copy the logs.")
        printerror(test_data,str(e) + "\n")
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
        email_body: a String, the body of the report email
        email_msg_prefix: a String, the prefix for lines added to the email
        email_attchs: a listof_pathto_File, the files to be attached to the
        report email
    """
    printout = []
    printerror = []
    email_body = ""
    email_msg_prefix = "Configuration"
    email_attachs = []

    def set_testing_phase(image_name):
        """Change the email message prefix to be the given testing phase.

        Args:
            image_name: a String, representing the current image being tested
        """
        Errors.email_msg_prefix = image_name

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

    def add_email_msg(msg):
        """Add the given message to the body of the report email.

        Args:
            msg: a String, the message to be added to the email
        """
        Errors.email_body += Errors.email_msg_prefix + ":" + msg

    def add_email_attachment(path):
        """Add the given file to be an attachment for the report email

        Args:
            file: a pathto_File, the file to add
        """
        Errors.email_attachs.append(path)


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
        """Get the command line arguments and parse them."""
        nxtproc = []
        nxtproc.append("python3")
        nxtproc.append(sys.argv.pop(0))
        while sys.argv:
            arg = sys.argv.pop(0)
            nxtproc.append(arg)
            if(arg == "-f"):
                #try: @@@ Commented out until a more specific except statement is added
                arg = sys.argv.pop(0)
                print("Running on a single file:")
                print(path_fix(arg) + "\n")
                self.single = True
                self.single_file = path_fix(arg)
                #except:
                #   print("Error: No single file given.\n")
            #       return False
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
        for line in log:
            if string in line:
                results.append(line)
        log.close()
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
            for line in log:
                if string in line:
                    results.append(line)
            log.close()
    return results


def clear_dir(dir):
    """Clears all files from a directory and remakes it.

    Args:
        dir: a pathto_Dir, the directory to clear
    """
    try:
        if dir_exists(dir):
            shutil.rmtree(dir)
        os.makedirs(dir)
        return True;
    except Exception as e:
        printerror(test_data,"Error: Cannot clear the given directory:")
        printerror(test_data,dir + "\n")
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
        printerror(test_data,"Error: Cannot delete the given directory:")
        printerror(test_data,dir + "\n")
        return False;

def copy_file(ffrom, to):
    """Copies a given file from "ffrom" to "to".

    Args:
        ffrom: a pathto_File, the origin path
        to: a pathto_File, the destination path
    """
    try :
        shutil.copy(ffrom, to)
    except Exception as e:
        print(str(e))
        print(traceback.format_exc())

def copy_dir(ffrom, to):
    """Copies a directory file from "ffrom" to "to".

    Args:
        ffrom: a pathto_Dir, the origin path
        to: a pathto_Dir, the destination path
    """
    try :
        if not os.path.isdir(ffrom):
            raise FileNotFoundException(ffrom)
        shutil.copytree(ffrom, to)
    except:
        raise FileNotFoundException(to)

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
