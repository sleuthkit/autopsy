This folder contains the projects you need for building and testing the yarabridge.dll and YaraJNIWrapper.jar.

bin:
Contains the built jar and jarac64.exe.  jarac64.exe is used to by the ingest module to compile the rule files.

yarabridge:
VS project to create the dll that wraps the the libyara library.

YaraJNIWrapper:
Simple jar file that contains the native JNI methods for accessing the yarabridge.dll. 


Steps for building yarabridge, YaraJNIWrapper and YaraWrapperTest.

1. Clone the yara repo at the same level as you have the autopsy repo. https://github.com/VirusTotal/yara
2. Build libyara:
	- Open the project yara/windows/2015/yara.sln
	- Build Release x64.
3. Open the yarabridge project and build Release x64.
	-If you have link issues, make sure you build release x64 in the previous step.
	-This project will automatically copy the built dll into the YaraJNIWrapper src\org\sleuthkit\autopsy\yara folder.
		- This is where is needs to be so that its included into the jar file.
4. Build YaraJNIWrapper
	- Open in netbeans and select Build.
	- Manually move the newly build jar file to the bin folder. After building the jar file can be found in
	  yara/YaraJNIWrapper/dist/
	- Any matching rules will appear on the CL or the output of the project.
5. Test
	- Open the YaraWrapperTest
	- In the Run Properties you need to specify the path to a compiled yara rule file and a file to search. 
	  There are sample files in YaraWrapperTest\resources.
	- If you would like to make your own compiled rule file you can use the yarac tool that can be found
	  in yara/windows/vs2015/Release, if its not there go back to the yara project and build all of the 
	  projects.

Troubleshooting:
- When building libyara make sure that you are building the vs2015 project (There is a vs2017 project too). 
  The paths in the yarabrige package are relative, but assume
  that you are building the release version of the dll with the vs2015 project.
- Don't forget to move the YaraJNIWrapper.jar after you build it. 
