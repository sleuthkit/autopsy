#!/usr/bin/python

# Autopsy Forensic Browser
#
# Copyright 2011-2015 Basis Technology Corp.
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

import os
import sys
import time

def remove_file(f):
	"""
	Remove the given file
	"""
	try:
		if os.path.exists(f):
			os.remove(f)
	except OSError:
		print("Unable to delete file: %s", f)
		return 1

def remove_folder(d):
	"""
 	Remove the given directory
	"""	
	try:
		os.rmdir(d)
	except OSError:
		print("Unable to delete folder: %s", d)
		return 1

def cleanup(days, path):
	"""
	Remove files from given directory that are older than 
	the number of given days, then remove empty directories
	"""	
	error = 0
	now = time.time()
	for root, dirs, files in os.walk(path, topdown=False):
		for f in files:
			f = os.path.join(root, f)
			if os.stat(f).st_mtime <= now - days * 86400:
				error = remove_file(f)			
		for d in dirs:
			d = os.path.join(root, d)
			if not os.listdir(d):
				error = remove_folder(d)
	return error


def usage():
    print("USAGE:\n\tpython " + sys.argv[0] + " <number of days> <valid path>")
    exit(1)

if __name__ == "__main__":
	if len(sys.argv) != 3:
		print("Invalid number of arguments")
		usage()
	try:
		days = int(sys.argv[1])
	except:
		print("Invalid number of days.")
		usage()
	path = sys.argv[2]
	if not os.path.exists(path):
		print("Invalid path.")
		usage()
	error = cleanup(days, path)
	exit(error)