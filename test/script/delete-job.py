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

def delete_files(days, path):
	now = time.time()
	for root, dirs, files in os.walk(path, topdown=False):
		for f in files:
			f = os.path.join(path, f)
			if os.stat(f).st_mtime <= now - days * 86400:
				try:
					if os.path.exists(f):
						os.remove(f)
					except IOError:
						print("Unable to delete folder: %s", d)
						exit(1)
		for d in dirs:
			d = os.path.join(root, d)
			if not os.listdir(d):
				try:
					os.rmdir(d)
				except IOError:
					print("Unable to delete folder: %s", d)
					exit(1)

	exit(0)

def usage():
    print("USAGE:\npython delete-job.py <number of days> <valid path>")
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
	delete_files(days, path)