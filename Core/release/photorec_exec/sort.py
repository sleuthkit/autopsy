#!/usr/bin/env python
import os
import os.path
import shutil
import sys

source = sys.argv[1]
destination = sys.argv[2]

while not os.path.exists(source):
    source = raw_input('Enter a valid source directory\n')
while not os.path.exists(destination):
    destination = raw_input('Enter a valid destination directory\n')

for root, dirs, files in os.walk(source, topdown=False):
    for file in files:
        extension = os.path.splitext(file)[1][1:].upper()
	destinationPath = os.path.join(destination,extension)
  	
	if not os.path.exists(destinationPath):
            os.mkdir(destinationPath)
	if os.path.exists(os.path.join(destinationPath,file)):
            print 'WARNING: this file was not copied :' + os.path.join(root,file)
	else:
	    shutil.copy2(os.path.join(root,file), destinationPath)
