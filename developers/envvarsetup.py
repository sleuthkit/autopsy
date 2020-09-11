import os
import sys
from os import path
from pathlib import PureWindowsPath

# taken from https://stackoverflow.com/questions/2946746/python-checking-if-a-user-has-administrator-privileges?rq=1
def isUserAdmin():
    try:
        # only windows users with admin privileges can read the C:\windows\temp
        temp = os.listdir(os.sep.join([os.environ.get('SystemRoot','C:\\windows'),'temp']))
        return True
    except:
        return False

if not isUserAdmin():
    print("This script must be run with administrative privileges")
    exit(1)

usage_message = "Usage: envarsetup.py [full path to parent directory of sleuthkit, autopsy, etc.]"

if len(sys.argv) < 2:
    print(usage_message)
    exit(1)

source_base_path = sys.argv[1]
if (not path.exists(source_base_path)):
    print("path: \"{0}\" does not exist".format(source_base_path))
    print(usage_message)
    exit(1)

'''
The following 6 lines can be configured to the specified paths (if different) on your system.
open_jdk_64_home is the 64 bit jdk and is the assumed default
source_base_path is the directory containing all necessary repos (i.e. autopsy, sleuthkit, etc.)
open_jdk_32_home and postgres_32_home are only necessary if building binaries
'''
open_jdk_64_home = "C:\\Program Files\\ojdkbuild\\java-1.8.0-openjdk-1.8.0.222-1"
postgres_home = "C:\\Program Files\\PostgreSQL\\9.5"
ant_home = "C:\\Program Files\\NetBeans 8.2\\extide\\ant"
open_jdk_32_home = "C:\\Program Files (x86)\\ojdkbuild\\java-1.8.0-openjdk-1.8.0.222-1"
postgres_32_home = "C:\\Program Files (x86)\\PostgreSQL\\9.5"
open_jdk_home = open_jdk_64_home


def path_join(*args):
    return str(PureWindowsPath(*args))

def set_var(env_var, env_val):
    print("Setting {0} to {1}".format(env_var,env_val))
    os.system("SETX {0} \"{1}\" /M".format(env_var,env_val))


def add_path(paths):
    print("Adding to path: {0}".format(paths))
    # insert paths at the beginning
    paths.insert(0, "%PATH%")
    # work around for command prompt to access PATH via %PATH%
    cmd = "cmd.exe /k SETX PATH \"{0}\" /M & exit".format(";".join(paths))
    os.system(cmd)


set_var("JAVA_HOME", open_jdk_home)
set_var("JRE_HOME", path_join(open_jdk_home, "jre"))
set_var("JDK_HOME", open_jdk_home)
set_var("POSTGRESQL_HOME_64", postgres_home)
set_var("TSK_HOME", path_join(source_base_path, "sleuthkit"))

set_var("JDK_HOME_64", open_jdk_64_home)
set_var("JRE_HOME_64", path_join(open_jdk_64_home, "jre"))

set_var("JDK_HOME_32", open_jdk_32_home)
set_var("JRE_HOME_32", path_join(open_jdk_32_home, "jre"))

set_var("POSTGRESQL_HOME_32", postgres_32_home)

add_path([path_join(postgres_home, "bin"), ant_home])