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
from regression_utils import *

def compile(errore, attachli, parsedin, branch):
    global to
    global server
    global subj
    global email_enabled 
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
            gitPull("sleuthkit", branch)
        if(passed):
            vsBuild()
            print("TSK") 
        if(passed):
            gitPull("autopsy", branch)
        if(passed):
            antBuild("datamodel", False, branch)
            print("DataModel")
        if(passed):
            antBuild("autopsy", True, branch)
            print("Aut")
        if(passed):
            redo = False
        else:
            print("Compile Failed")
            time.sleep(3600)
    attachl = []
    errorem = "The test standard didn't match the gold standard.\n"
    failedbool = False
    if(tryredo):
        errorem = ""
        errorem += "Rebuilt properly.\n"
        if email_enabled: 
            Emailer.send_email(to, server, subj, errorem, attachl)
        attachl = []
        passed = True

#Pulls from git
def gitPull(TskOrAutopsy, branch):
    global SYS
    global errorem
    global attachl
    ccwd = ""
    gppth = make_local_path("..", "GitPullOutput" + TskOrAutopsy + ".txt")
    attachl.append(gppth)
    gpout = open(gppth, 'a')
    if TskOrAutopsy == "sleuthkit":
        ccwd = os.path.join("..", "..", "..", "sleuthkit")
    else:
        ccwd = os.path.join("..", "..")
    print("Resetting " + TskOrAutopsy)
    call = ["git", "reset", "--hard"]
    subprocess.call(call, stdout=sys.stdout, cwd=ccwd)
    print("Checking out " + branch)
    call = ["git", "checkout", branch]
    subprocess.call(call, stdout=sys.stdout, cwd=ccwd)
    toPull = "https://www.github.com/sleuthkit/" + TskOrAutopsy
    call = ["git", "pull", toPull, branch]
    if TskOrAutopsy == "sleuthkit":
        ccwd = os.path.join("..", "..", "..", "sleuthkit")
    else:
        ccwd = os.path.join("..", "..")
    subprocess.call(call, stdout=sys.stdout, cwd=ccwd)
    gpout.close()

#Builds TSK as a win32 applicatiion
def vsBuild():
    global redo
    global tryredo
    global passed
    global parsed
    #Please ensure that the current working directory is $autopsy/testing/script
    oldpath = os.getcwd()
    os.chdir(os.path.join("..", "..", "..","sleuthkit", "win32"))
    vs = []
    vs.append("/cygdrive/c/windows/microsoft.NET/framework/v4.0.30319/MSBuild.exe")
    vs.append(os.path.join("Tsk-win.sln"))
    vs.append("/p:configuration=release")
    vs.append("/p:platform=x64")
    vs.append("/t:clean")
    vs.append("/t:rebuild")
    print(vs)
    VSpth = make_local_path("..", "VSOutput.txt")
    VSout = open(VSpth, 'a')
    subprocess.call(vs, stdout=VSout)
    VSout.close()
    os.chdir(oldpath)
    chk = os.path.join("..", "..", "..","sleuthkit", "win32", "x64", "Release", "libtsk_jni.dll")
    if not os.path.exists(chk):
        print("path doesn't exist")
        global errorem
        global attachl
        global email_enabled
        if(not tryredo):
            errorem += "LIBTSK C++ failed to build.\n"
            attachl.append(VSpth)
            if email_enabled: 
                Emailer.send_email(parsed, errorem, attachl, False)
        tryredo = True
        passed = False
        redo = True

#Builds Autopsy or the Datamodel
def antBuild(which, Build, branch):
    print("building: ", which)
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
    antpth = make_local_path("..", "ant" + which + "Output.txt")
    antout = open(antpth, 'a')
    succd = subprocess.call(ant, stdout=antout)
    antout.close()
    global errorem
    global attachl
    global email_enabled
    global to
    global subj
    global server
    if which == "datamodel":
        chk = os.path.join("..", "..", "..","sleuthkit",  "bindings", "java", "dist", "TSK_DataModel.jar")
        try:
            open(chk)
        except IOError as e:
            if(not tryredo):
                errorem += "DataModel Java build failed. on branch " + branch + "\n"
                attachl.append(antpth)
                if email_enabled: 
                    Emailer.send_email(to, server, subj, errorem, attachl)
            passed = False
            tryredo = True
    elif (succd != 0 and (not tryredo)):
        errorem += "Autopsy build failed on branch " + branch + ".\n"
        attachl.append(antpth)
        Emailer.send_email(to, server, subj, errorem, attachl)
        tryredo = True
    elif (succd != 0):
        passed = False


def main():
    global email_enabled
    global to
    global server
    global subj 
    errore = ""
    attachli = []
    config_file = ""
    arg = sys.argv.pop(0)
    arg = sys.argv.pop(0)
    config_file = arg
    arg = sys.argv.pop(0)
    branch = arg
    parsedin = parse(config_file)
    try:
        to = parsedin.getElementsByTagName("email")[0].getAttribute("value").encode().decode("utf_8")
        server = parsedin.getElementsByTagName("mail_server")[0].getAttribute("value").encode().decode("utf_8")
        subj = parsedin.getElementsByTagName("subject")[0].getAttribute("value").encode().decode("utf_8")
    except Exception:
        email_enabled = False
    # email_enabled = (to is not None) and (server is not None) and (subj is not None) 
    email_enabled = False 
    compile(errore, attachli, parsedin, branch)

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

