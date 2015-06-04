Place Jython modules HERE in their respective folders. Eg. - 
InternalPythonModules/
				- testModule1/
							- testModule1.py
				- testModule2/
							- testModule2.py
							- testModule21.py
							- testModule22.py
				- testModule3/
							- testModule3.py
Content of this folder is automatically copied to NB installation structure. JythonModuleLoader looks up that location for Jython modules.
NOTE: Empty folders are not copied to the NB installation structure.