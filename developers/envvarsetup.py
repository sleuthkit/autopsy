import os
import sys

if len(sys.argv) < 2:
    print("Usage: envarsetup.py [full path to parent directory of sleuthkit, autopsy, etc.]")
    exit(1)

'''
The following 7 lines can be configured to the specified paths (if different) on your system.
open_jdk_64_home is the 64 bit jdk and is the assumed default
source_base_path is the directory containing all necessary repos (i.e. autopsy, sleuthkit, etc.)
open_jdk_32_home and postgres_32_home are only necessary if building binaries
'''
source_base_path = sys.argv[1]
open_jdk_64_home = "C:\\Program Files\\ojdkbuild\\java-1.8.0-openjdk-1.8.0.222-1"
postgres_home = "C:\\Program Files\\PostgreSQL\\9.5"
ant_home = "C:\\Program Files\\NetBeans 8.2\\extide\\ant"
open_jdk_32_home = "C:\\Program Files (x86)\\ojdkbuild\\java-1.8.0-openjdk-1.8.0.222-1"
postgres_32_home = "C:\\Program Files (x86)\\PostgreSQL\\9.5"
open_jdk_home = open_jdk_64_home


sep = '\\'
path_env_sep = ";"


def set_var(env_var, env_val):
    print("Setting {0} to {1}".format(env_var,env_val))
    os.system("SETX {0} \"{1}\" /M".format(env_var,env_val))


def add_path(paths):
    print("Adding to path: {0}".format(paths))
    # work around for command prompt to access PATH via %PATH%
    cmd = "cmd.exe /k SETX PATH \"%PATH%;{0}\" /M & exit".format(path_env_sep.join(paths))
    os.system(cmd)


set_var("JAVA_HOME", open_jdk_home)
set_var("JRE_HOME", open_jdk_home + sep + "jre")
set_var("JDK_HOME", open_jdk_home)
set_var("LIBEWF_HOME", source_base_path + sep + "libewf_64bit")
set_var("LIBVHDI_HOME", source_base_path + sep + "libvhdi_64bit")
set_var("LIBVMDK_HOME", source_base_path + sep + "libvmdk_64bit" + sep + "libvmdk")
set_var("POSTGRESQL_HOME_64", postgres_home)
set_var("TSK_HOME", source_base_path + sep + "sleuthkit")

set_var("JDK_HOME_64", open_jdk_64_home)
set_var("JRE_HOME_64", open_jdk_64_home + sep + "jre")

set_var("JDK_HOME_32", open_jdk_32_home)
set_var("JRE_HOME_32", open_jdk_32_home + sep + "jre")

set_var("POSTGRESQL_HOME_32", postgres_32_home)

add_path([postgres_home + sep + "bin", ant_home])