This folder contains the data and scripts required to run regression tests
for Autopsy. There is a 'Testing' folder in the root directory that contains
the Java code that drives Autopsy to perform the tests. 

To run these tests:
- Download the input images by typing 'ant test-download-imgs' in the root Autopsy folder. This will place images in 'test/input'.
- Run 'regression.py' from inside of the 'test/scripts' folder. 
