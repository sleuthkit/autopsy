This folder contains the data and scripts required to run regression tests
for Autopsy. There is a 'Testing' folder in the root directory that contains
the Java code that drives Autopsy to perform the tests. 

To run these tests:
- You will need python3.  We run this from within Cygwin.
- Download the input images by typing 'ant test-download-imgs' in the root Autopsy folder.
  This will place images in 'test/input'.
- Run 'python3 regression.py' from inside of the 'test/scripts' folder.
- Alternatively, run 'python3 regression.py -l [CONFIGFILE] to run the tests on a specified
  list of images using a configuration file. See config.xml in the 'test/scripts' folder to
  see configuration file formatting.
- Run 'python3 regression.py -h' to see other options. 
