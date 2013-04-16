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
from xml.dom.minidom import parse, parseString
import Emailer

def compile(errore, attachli, parsedin):
	global redo
	global tryredo
	global failedbool
	global errorem
	errorem = errore
	global attachl
	attachl = attachli
	global passed
	global parsed
	parsed = parsedin
	passed = True
	tryredo = False
	redo = True
	while(redo):
		passed = True
		if(passed):
			gitPull("sleuthkit")
		if(passed):
			vsBuild()
		if(passed):
			gitPull("autopsy")
		if(passed):
			antBuild("datamodel", False)
		if(passed):
			antBuild("autopsy", True)
		if(passed):
			redo = False
		else:
			print("Compile Failed")
			time.sleep(3600)
	attachl = []
	errorem = "The test standard didn't match the gold standard.\n"
	failedbool = False
	if(tryredo):
		errorem += "Rebuilt properly.\n"
		Emailer.send_email(parsed, errorem, attachl, True)
		attachl = []
		errorem = "The test standard didn't match the gold standard.\n"	
		passed = True
		
#Pulls from git
def gitPull(TskOrAutopsy):
	global SYS
	global errorem
	global attachl
	ccwd = ""
	gppth = Emailer.make_local_path("..", "GitPullOutput" + TskOrAutopsy + ".txt")
	attachl.append(gppth)
	gpout = open(gppth, 'a')
	toPull = "http://www.github.com/sleuthkit/" + TskOrAutopsy
	call = ["git", "pull", toPull]
	if TskOrAutopsy == "sleuthkit":
		ccwd = os.path.join("..", "..", "..", "sleuthkit")
	else:
		ccwd = os.path.join("..", "..")
	subprocess.call(call, stdout=gpout, cwd=ccwd)
	gpout.close()
	

#Builds TSK as a win32 applicatiion
def vsBuild():
	global redo
	global tryredo
	global passed
	global parsed
	#Please ensure that the current working directory is $autopsy/testing/script
	vs = []
	vs.append("/cygdrive/c/windows/microsoft.NET/framework/v4.0.30319/MSBuild.exe")
	vs.append(os.path.join("..", "..", "..","sleuthkit", "win32", "Tsk-win.sln"))
	vs.append("/p:configuration=release")
	vs.append("/p:platform=win32")
	vs.append("/t:clean")
	vs.append("/t:rebuild")
	print(vs)
	VSpth = Emailer.make_local_path("..", "VSOutput.txt")
	VSout = open(VSpth, 'a')
	subprocess.call(vs, stdout=VSout)
	VSout.close()
	chk = os.path.join("..", "..", "..","sleuthkit", "win32", "Release", "libtsk_jni.dll")
	try:
		open(chk)
	except IOError as e:
		global errorem
		global attachl
		if(not tryredo):
			errorem += "LIBTSK C++ failed to build.\n"
			attachl.append(VSpth)
			Emailer.send_email(parsed, errorem, attachl, False)
		tryredo = True
		passed = False
		redo = True
		
	
 
#Builds Autopsy or the Datamodel
def antBuild(which, Build):
	global redo
	global passed
	global tryredo
	global parsed
	directory = os.path.join("..", "..")
	ant = []
	if which == "datamodel":
		directory = os.path.join("..", "..", "..", "sleuthkit", "bindings", "java")
	ant.append("ant")
	ant.append("-f")
	ant.append(directory)
	ant.append("clean")
	if(Build):
		ant.append("build")
	else:
		ant.append("dist")
	antpth = Emailer.make_local_path("..", "ant" + which + "Output.txt")
	antout = open(antpth, 'a')
	succd = subprocess.call(ant, stdout=antout)
	antout.close()
	global errorem
	global attachl
	if which == "datamodel":
		chk = os.path.join("..", "..", "..","sleuthkit",  "bindings", "java", "dist", "TSK_DataModel.jar")
		try:
			open(chk)
		except IOError as e:
			if(not tryredo):
				errorem += "DataModel Java build failed.\n"
				attachl.append(antpth)
				Emailer.send_email(parsed, errorem, attachl, False)
			passed = False
			tryredo = True
	elif (succd != 0 and (not tryredo)):
		errorem += "Autopsy build failed.\n"
		attachl.append(antpth)
		Emailer.send_email(parsed, errorem, attachl, False)
		tryredo = True
	elif (succd != 0):
		passed = False


def main():
	errore = ""
	attachli = []
	config_file = ""
	arg = sys.argv.pop(0)
	arg = sys.argv.pop(0)
	config_file = arg
	parsedin = parse(config_file)
	compile(errore, attachli, parsedin)
	
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