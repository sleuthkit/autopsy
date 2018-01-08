#
# Autopsy Forensic Browser
#
# Copyright 2012-2013 Basis Technology Corp.
# Contact: carrier <at> sleuthkit <dot> org
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


#######################
# This script exists to help us determine update the library
# versions appropriately. See this page for version details.
#
#    http://wiki.sleuthkit.org/index.php?title=Autopsy_3_Module_Versions
#
# The basic idea is that this script uses javadoc/jdiff to
# compare the current state of the source code to the last
# tag and identifies if APIs were removed, added, etc.
#
# When run from the Autopsy build script, this script will:
#  - Clone Autopsy and checkout to the previous release tag
#    as found in the NEWS.txt file
#  - Auto-discover all modules and packages
#  - Run jdiff, comparing the current and previous modules
#  - Use jdiff's output to determine if each module
#     a) has no changes
#     b) has backwards compatible changes
#     c) has backwards incompatible changes
#  - Based off it's compatibility, updates each module's
#     a) Major version
#     b) Specification version
#     c) Implementation version
#  - Updates the dependencies on each module depending on the
#    updated version numbers
#
# Optionally, when run from the command line, one can provide the
# desired tag to compare the current version to, the directory for
# the current version of Autopsy, and whether to automatically
# update the version numbers and dependencies.
# ------------------------------------------------------------

import errno
import os
import shutil
import stat
import subprocess
import sys
import traceback
from os import remove, close
from shutil import move
from tempfile import mkstemp
from xml.dom.minidom import parse, parseString

# Jdiff return codes. Described in more detail further on
NO_CHANGES = 100
COMPATIBLE = 101
NON_COMPATIBLE = 102
ERROR = 1

# Set this to true when developing the script.  It does not delete
# the cloned repo each time - making it much faster to run.
TESTING = False

# An Autopsy module object
class Module:
    # Initialize it with a name, return code, and version numbers
    def __init__(self, name=None, ret=None, versions=None):
        self.name = name
        self.ret = ret
        self.versions = versions
    # As a string, the module should be it's name
    def __str__(self):
        return self.name
    def __repr__(self):
        return self.name
    # When compared to another module, the two are equal if the names are the same
    def __cmp__(self, other):
        if isinstance(other, Module):
            if self.name == other.name:
                return 0
            elif self.name < other.name:
                return -1
            else:
                return 1
        return 1
    def __eq__(self, other):
        if isinstance(other, Module):
            if self.name == other.name:
                return True
        return False
    def set_name(self, name):
        self.name = name
    def set_ret(self, ret):
        self.ret = ret
    def set_versions(self, versions):
        self.versions = versions
    def spec(self):
        return self.versions[0]
    def impl(self):
        return self.versions[1]
    def release(self):
        return self.versions[2]

# Representation of the Specification version number
class Spec:
    # Initialize specification number, where num is a string like x.y
    def __init__(self, num):
        self.third = None
        spec_nums = num.split(".")
        if len(spec_nums) == 3:
            self.final = spec_nums[2]
            self.third = int(self.final)
        l, r = spec_nums[0], spec_nums[1]

        self.left = int(l)
        self.right = int(r)

    def __str__(self):
        return self.get()
    def __cmp__(self, other):
        if isinstance(other, Spec):
            if self.left == other.left:
                if self.right == other.right:
                    return 0
                if self.right < other.right:
                    return -1
                return 1
            if self.left < other.left:
                return -1
            return 1
        elif isinstance(other, str):
            l, r = other.split(".")
            if self.left == int(l):
                if self.right == int(r):
                    return 0
                if self.right < int(r):
                    return -1
                return 1
            if self.left < int(l):
                return -1
            return 1
        return -1

    def incrementIncompat(self):
        return str(self.left + 1) + ".0"
    def incrementCompat(self):
        return str(self.left) + "." + str(self.right + 1)
    def get(self):
        spec_str = str(self.left) + "." + str(self.right)
        if self.third is not None:
            spec_str += "." + str(self.final)
        return spec_str
    def set(self, num):
        if isinstance(num, str):
            l, r = num.split(".")
            self.left = int(l)
            self.right = int(r)
        elif isinstance(num, Spec):
            self.left = num.left
            self.right = num.right
        return self

# ================================ #
#     Core Functions               #
# ================================ #

# Given a list of modules and the names for each version, compare
# the generated jdiff XML for each module and output the jdiff
# JavaDocs.
#
# modules: the list of all modules both versions have in common
# apiname_tag: the api name of the previous version, most likely the tag
# apiname_cur: the api name of the current version, most likely "Current"
#
# returns the exit code from the modified jdiff.jar
#   return code 1   = error in jdiff
#   return code 100 = no changes
#   return code 101 = compatible changes
#   return code 102 = incompatible changes
def compare_xml(module, apiname_tag, apiname_cur):
    global docdir
    make_dir(docdir)
    null_file = fix_path(os.path.abspath("./thirdparty/jdiff/v-custom/lib/Null.java"))
    jdiff = fix_path(os.path.abspath("./thirdparty/jdiff/v-custom/jdiff.jar"))
    oldapi = fix_path("build/jdiff-xml/" + apiname_tag + "-" + module.name)
    newapi = fix_path("build/jdiff-xml/" + apiname_cur + "-" + module.name)
    docs = fix_path(docdir + "/" + module.name)
    # Comments are strange. They look for a file with additional user comments in a
    # directory like docs/user_comments_for_xyz. The problem being that xyz is the
    # path to the new/old api. So xyz turns into multiple directories for us.
    # i.e. user_comments_for_build/jdiff-xml/[tag name]-[module name]_to_build/jdiff-xml
    comments = fix_path(docs + "/user_comments_for_build")
    jdiff_com = fix_path(comments + "/jdiff-xml")
    tag_comments = fix_path(jdiff_com + "/" + apiname_tag + "-" + module.name + "_to_build")
    jdiff_tag_com = fix_path(tag_comments + "/jdiff-xml")

    if not os.path.exists(jdiff):
        print("JDIFF doesn't exist.")

    make_dir(docs)
    make_dir(comments)
    make_dir(jdiff_com)
    make_dir(tag_comments)
    make_dir(jdiff_tag_com)
    make_dir("jdiff-logs")
    log = open("jdiff-logs/COMPARE-" + module.name + ".log", "w")
    cmd =   ["javadoc",
            "-doclet", "jdiff.JDiff",
            "-docletpath", jdiff,
            "-d", docs,
            "-oldapi", oldapi,
            "-newapi", newapi,
            "-script",
            null_file]
    try:
        jdiff = subprocess.Popen(cmd, stdout=log, stderr=log)
        jdiff.wait()
        code = jdiff.returncode
    except Exception:
        printt("Error executing javadoc. Exiting...")
        exit(1)
    log.close()

    print("Compared XML for " + module.name)
    if code == NO_CHANGES:
        print("  No API changes")
    elif code == COMPATIBLE:
        print("  API Changes are backwards compatible")
    elif code == NON_COMPATIBLE:
        print("  API Changes are not backwards compatible")
    else:
        print("  *Error in XML, most likely an empty module")
    sys.stdout.flush()
    return code

# Generate the jdiff xml for the given module
#   path: path to the autopsy source
#   module: Module object
#   name: api name for jdiff
def gen_xml(path, modules, name):
    for module in modules:
        # If its the regression test, the source is in the "test" dir
        if module.name == "Testing":
            src = os.path.join(path, module.name, "test", "qa-functional", "src")
        else:
            src = os.path.join(path, module.name, "src")
        # xerces = os.path.abspath("./lib/xerces.jar")
        xml_out = fix_path(os.path.abspath("./build/jdiff-xml/" + name + "-" + module.name))
        jdiff = fix_path(os.path.abspath("./thirdparty/jdiff/v-custom/jdiff.jar"))
        make_dir("build/jdiff-xml")
        make_dir("jdiff-logs")
        log = open("jdiff-logs/GEN_XML-" + name + "-" + module.name + ".log", "w")
        cmd =   ["javadoc",
                "-doclet", "jdiff.JDiff",
                "-docletpath", jdiff,       # ;" + xerces, <-- previous problems required this
                "-apiname", xml_out,        # leaving it in just in case it's needed once again
                "-sourcepath", fix_path(src)]
        cmd = cmd + get_packages(src)
        try:
            jdiff = subprocess.Popen(cmd, stdout=log, stderr=log)
            jdiff.wait()
        except Exception:
            printt("Error executing javadoc. Exiting...")
            exit(1)
        log.close()
        print("Generated XML for " + name + " " + module.name)
        sys.stdout.flush()

# Find all the modules in the given path
def find_modules(path):
    modules = []
    # Step into each folder in the given path and
    # see if it has manifest.mf - if so, it's a module
    for dir in os.listdir(path):
        directory = os.path.join(path, dir)
        if os.path.isdir(directory):
            for file in os.listdir(directory):
                if file == "manifest.mf":
                    modules.append(Module(dir, None, None))
    return modules

# Detects the differences between the source and tag modules
def module_diff(source_modules, tag_modules):
    added_modules   = [x for x in source_modules if x not in tag_modules]
    removed_modules = [x for x in tag_modules if x not in source_modules]
    similar_modules = [x for x in source_modules if x in tag_modules]

    added_modules   = (added_modules if added_modules else [])
    removed_modules = (removed_modules if removed_modules else [])
    similar_modules = (similar_modules if similar_modules else [])
    return similar_modules, added_modules, removed_modules

# Reads the previous tag from NEWS.txt
def get_tag(sourcepath):
    news = open(sourcepath + "/NEWS.txt", "r")
    second_instance = False
    for line in news:
        if "----------------" in line:
            if second_instance:
                ver = line.split("VERSION ")[1]
                ver = ver.split(" -")[0]
                return ("autopsy-" + ver).strip()
            else:
                second_instance = True
                continue
    news.close()


# ========================================== #
#      Dependency Functions                  #
# ========================================== #

# Write a new XML file, copying all the lines from projectxml
# and replacing the specification version for the code-name-base base
# with the supplied specification version spec
def set_dep_spec(projectxml, base, spec):
    print("    Updating Specification version..")
    orig = open(projectxml, "r")
    f, abs_path = mkstemp()
    new_file = open(abs_path, "w")
    found_base = False
    spacing = "                        "
    sopen = "<specification-version>"
    sclose = "</specification-version>\n"
    for line in orig:
        if base in line:
            found_base = True
        if found_base and sopen in line:
            update = spacing + sopen + str(spec) + sclose
            new_file.write(update)
        else:
            new_file.write(line)
    new_file.close()
    close(f)
    orig.close()
    remove(projectxml)
    move(abs_path, projectxml)

# Write a new XML file, copying all the lines from projectxml
# and replacing the release version for the code-name-base base
# with the supplied release version
def set_dep_release(projectxml, base, release):
    print("    Updating Release version..")
    orig = open(projectxml, "r")
    f, abs_path = mkstemp()
    new_file = open(abs_path, "w")
    found_base = False
    spacing = "                        "
    ropen = "<release-version>"
    rclose = "</release-version>\n"
    for line in orig:
        if base in line:
            found_base = True
        if found_base and ropen in line:
            update = spacing + ropen + str(release) + rclose
            new_file.write(update)
        else:
            new_file.write(line)
    new_file.close()
    close(f)
    orig.close()
    remove(projectxml)
    move(abs_path, projectxml)

# Return the dependency versions in the XML dependency node
def get_dep_versions(dep):
    run_dependency = dep.getElementsByTagName("run-dependency")[0]
    release_version = run_dependency.getElementsByTagName("release-version")
    if release_version:
        release_version = getTagText(release_version[0].childNodes)
    specification_version = run_dependency.getElementsByTagName("specification-version")
    if specification_version:
        specification_version = getTagText(specification_version[0].childNodes)
    return int(release_version), Spec(specification_version)

# Given a code-name-base, see if it corresponds with any of our modules
def get_module_from_base(modules, code_name_base):
    for module in modules:
        if "org.sleuthkit.autopsy." + module.name.lower() == code_name_base:
            return module
    return None # If it didn't match one of our modules

# Check the text between two XML tags
def getTagText(nodelist):
    for node in nodelist:
        if node.nodeType == node.TEXT_NODE:
            return node.data

# Check the projectxml for a dependency on any module in modules
def check_for_dependencies(projectxml, modules):
    dom = parse(projectxml)
    dep_list = dom.getElementsByTagName("dependency")
    for dep in dep_list:
        code_name_base = dep.getElementsByTagName("code-name-base")[0]
        code_name_base = getTagText(code_name_base.childNodes)
        module = get_module_from_base(modules, code_name_base)
        if module:
            print("  Found dependency on " + module.name)
            release, spec = get_dep_versions(dep)
            if release != module.release() and module.release() is not None:
                set_dep_release(projectxml, code_name_base, module.release())
            else: print("    Release version is correct")
            if spec != module.spec() and module.spec() is not None:
                set_dep_spec(projectxml, code_name_base, module.spec())
            else: print("    Specification version is correct")

# Given the module and the source directory, return
# the paths to the manifest and project properties files
def get_dependency_file(module, source):
    projectxml = os.path.join(source, module.name, "nbproject", "project.xml")
    if os.path.isfile(projectxml):
        return projectxml

# Verify/Update the dependencies for each module, basing the dependency
# version number off the versions in each module
def update_dependencies(modules, source):
    for module in modules:
        print("Checking the dependencies for " + module.name + "...")
        projectxml = get_dependency_file(module, source)
        if projectxml == None:
            print("  Error finding project xml file")
        else:
            other = [x for x in modules]
            check_for_dependencies(projectxml, other)
        sys.stdout.flush()

# ======================================== #
#      Versioning Functions                #
# ======================================== #

# Return the specification version in the given project.properties/manifest.mf file
def get_specification(project, manifest):
    try:
        # Try to find it in the project file
        # it will be there if impl version is set to append automatically
        f = open(project, 'r')
        for line in f:
            if "spec.version.base" in line:
                return Spec(line.split("=")[1].strip())
        f.close()
        # If not found there, try the manifest file
        f = open(manifest, 'r')
        for line in f:
            if "OpenIDE-Module-Specification-Version:" in line:
                return Spec(line.split(": ")[1].strip())
    except Exception as e:
        print("Error parsing Specification version for")
        print(project)
        print(e)

# Set the specification version in the given project properties file
# but if it can't be found there, set it in the manifest file
def set_specification(project, manifest, num):
    try:
        # First try the project file
        f = open(project, 'r')
        for line in f:
            if "spec.version.base" in line:
                f.close()
                replace(project, line, "spec.version.base=" + str(num) + "\n")
                return
        f.close()
        # If it's not there, try the manifest file
        f = open(manifest, 'r')
        for line in f:
            if "OpenIDE-Module-Specification-Version:" in line:
                f.close()
                replace(manifest, line, "OpenIDE-Module-Specification-Version: " + str(num) + "\n")
                return
        # Otherwise we're out of luck
        print("  Error finding the Specification version to update")
        print("  " + manifest)
        f.close()
    except:
        print("  Error incrementing Specification version for")
        print("  " + project)

# Return the implementation version in the given manifest.mf file
def get_implementation(manifest):
    try:
        f = open(manifest, 'r')
        for line in f:
            if "OpenIDE-Module-Implementation-Version" in line:
                return int(line.split(": ")[1].strip())
        f.close()
    except:
        print("Error parsing Implementation version for")
        print(manifest)

# Set the implementation version in the given manifest file
def set_implementation(manifest, num):
    try:
        f = open(manifest, 'r')
        for line in f:
            if "OpenIDE-Module-Implementation-Version" in line:
                f.close()
                replace(manifest, line, "OpenIDE-Module-Implementation-Version: " + str(num) + "\n")
                return
        # If it isn't there, add it
        f.close()
        write_implementation(manifest, num)
    except:
        print("  Error incrementing Implementation version for")
        print("  " + manifest)

# Rewrite the manifest file to include the implementation version
def write_implementation(manifest, num):
    f = open(manifest, "r")
    contents = f.read()
    contents = contents[:-2] + "OpenIDE-Module-Implementation-Version: " + str(num) + "\n\n"
    f.close()
    f = open(manifest, "w")
    f.write(contents)
    f.close()

# Return the release version in the given manifest.mf file
def get_release(manifest):
    try:
        f = open(manifest, 'r')
        for line in f:
            if "OpenIDE-Module:" in line:
                return int(line.split("/")[1].strip())
        f.close()
    except:
        #print("Error parsing Release version for")
        #print(manifest)
        return 0

# Set the release version in the given manifest file
def set_release(manifest, num):
    try:
        f = open(manifest, 'r')
        for line in f:
            if "OpenIDE-Module:" in line:
                f.close()
                index = line.index('/') - len(line) + 1
                newline = line[:index] + str(num)
                replace(manifest, line, newline + "\n")
                return
        print("  Error finding the release version to update")
        print("  " + manifest)
        f.close()
    except:
        print("  Error incrementing release version for")
        print("  " + manifest)

# Given the module and the source directory, return
# the paths to the manifest and project properties files
def get_version_files(module, source):
    manifest = os.path.join(source, module.name, "manifest.mf")
    project = os.path.join(source, module.name, "nbproject", "project.properties")
    if os.path.isfile(manifest) and os.path.isfile(project):
        return manifest, project

# Returns a the current version numbers for the module in source
def get_versions(module, source):
    manifest, project = get_version_files(module, source)
    if manifest == None or project == None:
        print("  Error finding manifeset and project properties files")
        return
    spec = get_specification(project, manifest)
    impl = get_implementation(manifest)
    release =  get_release(manifest)
    return [spec, impl, release]

# Update the version numbers for every module in modules
def update_versions(modules, source):
    for module in modules:
        versions = module.versions
        manifest, project = get_version_files(module, source)
        print("Updating " + module.name + "...")
        if manifest == None or project == None:
            print("  Error finding manifeset and project properties files")
            return
        if module.ret == COMPATIBLE:
            versions = [versions[0].set(versions[0].incrementCompat()), versions[1] + 1, versions[2]]
            set_specification(project, manifest, versions[0])
            set_implementation(manifest, versions[1])
            module.set_versions(versions)
        elif module.ret == NON_COMPATIBLE:
            versions = [versions[0].set(versions[0].incrementIncompat()), versions[1] + 1, versions[2] + 1]
            set_specification(project, manifest, versions[0])
            set_implementation(manifest, versions[1])
            set_release(manifest, versions[2])
            module.set_versions(versions)
        elif module.ret == NO_CHANGES:
            versions = [versions[0], versions[1] + 1, versions[2]]
            set_implementation(manifest, versions[1])
            module.set_versions(versions)
        elif module.ret == None:
            versions = [Spec("1.0"), 1, 1]
            set_specification(project, manifest, versions[0])
            set_implementation(manifest, versions[1])
            set_release(manifest, versions[2])
            module.set_versions(versions)
        sys.stdout.flush()

# Given a list of the added modules, remove the modules
# which have the correct 'new module default' version number
def remove_correct_added(modules):
    correct = [x for x in modules]
    for module in modules:
        if module.spec() == "1.0" or module.spec() == "0.0":
            if module.impl() == 1:
                if module.release() == 1 or module.release() == 0:
                    correct.remove(module)
    return correct

# ==================================== #
#      Helper Functions                #
# ==================================== #

# Replace pattern with subst in given file
def replace(file, pattern, subst):
    #Create temp file
    fh, abs_path = mkstemp()
    new_file = open(abs_path,'w')
    old_file = open(file)
    for line in old_file:
        new_file.write(line.replace(pattern, subst))
    #close temp file
    new_file.close()
    close(fh)
    old_file.close()
    #Remove original file
    remove(file)
    #Move new file
    move(abs_path, file)

# Given a list of modules print the version numbers that need changing
def print_version_updates(modules):
    f = open("gen_version.txt", "a")
    for module in modules:
        versions = module.versions
        if module.ret == COMPATIBLE:
            output = (module.name + ":\n")
            output += ("\tMajor Release:\tNo Change.\n")            
            output += ("\tSpecification:\t" + str(versions[0]) + "\t->\t" + str(versions[0].incrementCompat()) + "\n")
            if versions[1] is None:
                output += ("\tImplementation: Not defined\n")
            else:
                output += ("\tImplementation:\t" + str(versions[1]) + "\t->\t" + str(versions[1] + 1) + "\n")
            output += ("\n")
            print(output)
            sys.stdout.flush()
            f.write(output)
        elif module.ret == NON_COMPATIBLE:
            output = (module.name + ":\n")
            output += ("\tMajor Release:\t" + str(versions[2]) + "\t->\t" + str(versions[2] + 1) + "\n")
            output += ("\tSpecification:\t" + str(versions[0]) + "\t->\t" + str(versions[0].incrementIncompat()) + "\n")
            if versions[1] is None:
                output += ("\tImplementation: Not defined\n")
            else:
                output += ("\tImplementation:\t" + str(versions[1]) + "\t->\t" + str(versions[1] + 1) + "\n")
            output += ("\n")
            print(output)
            sys.stdout.flush()
            f.write(output)
        elif module.ret == ERROR:
            output = (module.name + ":\n")
            output += ("\t*Unable to detect necessary changes\n")
            output += ("\tMajor Release:\t\t" + str(versions[2]) + "\n")
            output += ("\tSpecification:\t" + str(versions[0]) + "\n")
            if versions[1] is None:
                output += ("\tImplementation: Not defined\n")
            else:
                output += ("\tImplementation:\t" + str(versions[1]) + "\n")
            output += ("\n")
            print(output)
            f.write(output)
            sys.stdout.flush()
        elif module.ret == NO_CHANGES:
            output = (module.name + ":\n")
            output += ("\tMajor Release:\tNo Change.\n")
            output += ("\tSpecification:\tNo Change.\n")
            if versions[1] is None:
                output += ("\tImplementation: Not defined\n")
            else:
                output += ("\tImplementation:\t" + str(versions[1]) + "\t->\t" + str(versions[1] + 1) + "\n")
                output += ("\n")
            print(output)
            sys.stdout.flush()
            f.write(output)
        elif module.ret is None:
            output = ("Added " + module.name + ":\n")
            if module.release() != 1 and module.release() != 0:
                output += ("\tMajor Release:\t\t" + str(module.release()) + "\t->\t" + "1\n")
                output += ("\n")
            if module.spec() != "1.0" and module.spec() != "0.0":
                output += ("\tSpecification:\t" + str(module.spec()) + "\t->\t" + "1.0\n")
                output += ("\n")
            if module.impl() != 1:
                output += ("\tImplementation:\t" + str(module.impl()) + "\t->\t" + "1\n")
                output += ("\n")
            print(output)
            sys.stdout.flush()
            f.write(output)
        sys.stdout.flush()
    f.close()

# Changes cygwin paths to Windows
def fix_path(path):
    if "cygdrive" in path:
        new_path = path[11:]
        return "C:/" + new_path
    else:
        return path

# Print a 'title'
def printt(title):
    print("\n" + title)
    lines = ""
    for letter in title:
        lines += "-"
    print(lines)
    sys.stdout.flush()

# Get a list of package names in the given path
# The path is expected to be of the form {base}/module/src
#
# NOTE: We currently only check for packages of the form
#   org.sleuthkit.autopsy.x
# If we add other namespaces for commercial modules we will
# have to add a check here
def get_packages(path):
    packages = []
    package_path = os.path.join(path, "org", "sleuthkit", "autopsy")
    for folder in os.listdir(package_path):
        package_string = "org.sleuthkit.autopsy."
        packages.append(package_string + folder)
    return packages

# Create the given directory, if it doesn't already exist
def make_dir(dir):
    try:
        if not os.path.isdir(dir):
            os.mkdir(dir)
        if os.path.isdir(dir):
            return True
        return False
    except:
        print("Exception thrown when creating directory")
        return False

# Delete the given directory, and make sure it is deleted
def del_dir(dir):
    try:
        if os.path.isdir(dir):
            shutil.rmtree(dir, ignore_errors=False, onerror=handleRemoveReadonly)
            if os.path.isdir(dir):
                return False
            else:
                return True
        return True
    except:
        print("Exception thrown when deleting directory")
        traceback.print_exc()
        return False

# Handle any permisson errors thrown by shutil.rmtree
def handleRemoveReadonly(func, path, exc):
  excvalue = exc[1]
  if func in (os.rmdir, os.remove) and excvalue.errno == errno.EACCES:
      os.chmod(path, stat.S_IRWXU| stat.S_IRWXG| stat.S_IRWXO) # 0777
      func(path)
  else:
      raise

# Run git clone and git checkout for the tag
def do_git(tag, tag_dir):
    try:
        printt("Cloning Autopsy tag " + tag + " into dir " + tag_dir + " (this could take a while)...")
        subprocess.call(["git", "clone", "https://github.com/sleuthkit/autopsy.git", tag_dir],
                        stdout=subprocess.PIPE)
        printt("Checking out tag " + tag + "...")
        subprocess.call(["git", "checkout", tag],
                        stdout=subprocess.PIPE,
                        cwd=tag_dir)
        return True
    except Exception as ex:
        print("Error cloning and checking out Autopsy: ",  sys.exc_info()[0])
        print(str(ex))
        print("The terminal you are using most likely does not recognize git commands.")
        return False

# Get the flags from argv
def args():
    try:
        sys.argv.pop(0)
        while sys.argv:
            arg = sys.argv.pop(0)
            if arg == "-h" or arg == "--help":
                return 1
            elif arg == "-t" or arg == "--tag":
                global tag
                tag = sys.argv.pop(0)
            elif arg == "-s" or arg == "--source":
                global source
                source = sys.argv.pop(0)
            elif arg == "-d" or arg == "--dir":
                global docdir
                docdir = sys.argv.pop(0)
            elif arg == "-a" or arg == "--auto":
                global dry
                dry = False
            else:
                raise Exception()
    except:
        pass

# Print script run info
def printinfo():
    global tag
    global source
    global docdir
    global dry
    printt("Release script information:")
    if source is None:
        source = fix_path(os.path.abspath("."))
    print("Using source directory:\n  " + source)
    if tag is None:
        tag = get_tag(source)
    print("Checking out to tag:\n  " + tag)
    if docdir is None:
        docdir = fix_path(os.path.abspath("./jdiff-javadocs"))
    print("Generating jdiff JavaDocs in:\n  " + docdir)
    if dry is True:
        print("Dry run: will not auto-update version numbers")
    sys.stdout.flush()

# Print the script's usage/help
def usage():
    return \
 """
 USAGE:
   Compares the API of the current Autopsy source code with a previous
   tagged version. By default, it will detect the previous tag from
   the NEWS file and will not update the versions in the source code.

 OPTIONAL FLAGS:
   -t --tag      Specify a previous tag to compare to. 
                 Otherwise the NEWS file will be used. 

   -d --dir      The output directory for the jdiff JavaDocs. If no
                 directory is given, the default is jdiff-javadocs/{module}.

   -s --source   The directory containing Autopsy's source code.

   -a --auto     Automatically update version numbers (not dry).

   -h --help     Prints this usage.
 """

# ==================================== #
#     Main Functionality               #
# ==================================== #

# Where the magic happens
def main():
    global tag; global source; global docdir; global dry
    tag = None; source = None; docdir = None; dry = True
    ret = args()
    if ret:
        print(usage())
        return 0
    printinfo()

    # Check if javadoc and jdiff are present.
    jdiff = fix_path(os.path.abspath("./thirdparty/jdiff/v-custom/jdiff.jar"))
    if(not os.path.isfile(jdiff)):
        printt("jdiff not found. Exiting...")
        return 1
    try:
        subprocess.call(["javadoc"], stdout=open(os.devnull, "w"), stderr=subprocess.STDOUT)
    except Exception:
        printt("javadoc not found in path. Exiting...")
        return 1
    # -----------------------------------------------
    # 1) Clone Autopsy, checkout to given tag/commit
    # 2) Get the modules in the clone and the source
    # 3) Generate the xml comparison
    # -----------------------------------------------
    if (not TESTING) and (not del_dir("./build/" + tag)):
        print("\n\n=========================================")
        print(" Failed to delete previous Autopsy clone.")
        print(" Unable to continue...")
        print("=========================================")
        return 1
    tag_dir = os.path.abspath("./build/" + tag)
    if not do_git(tag, tag_dir):
        return 1
    sys.stdout.flush()
    tag_modules = find_modules(tag_dir)
    source_modules = find_modules(source)

    printt("Generating jdiff XML reports...")
    apiname_tag = tag
    apiname_cur = "current"
    gen_xml(tag_dir, tag_modules, apiname_tag)
    gen_xml(source, source_modules, apiname_cur)

    if not TESTING:
        printt("Deleting cloned Autopsy directory...")
        print("Clone successfully deleted" if del_dir(tag_dir) else "Failed to delete clone")
    sys.stdout.flush()

    # -----------------------------------------------------
    # 1) Seperate modules into added, similar, and removed
    # 2) Compare XML for each module
    # -----------------------------------------------------
    printt("Comparing modules found...")
    similar_modules, added_modules, removed_modules = module_diff(source_modules, tag_modules)
    if added_modules or removed_modules:
        for m in added_modules:
            print("+  Added " + m.name)
            sys.stdout.flush()
        for m in removed_modules:
            print("-  Removed " + m.name)
            sys.stdout.flush()
    else:
        print("No added or removed modules")
        sys.stdout.flush()

    printt("Comparing jdiff outputs...")
    for module in similar_modules:
        module.set_ret(compare_xml(module, apiname_tag, apiname_cur))
    print("Refer to the jdiff-javadocs folder for more details")

    # ------------------------------------------------------------
    # 1) Do versioning
    # 2) Auto-update version numbers in files and the_modules list
    # 3) Auto-update dependencies
    # ------------------------------------------------------------
    printt("Auto-detecting version numbers and changes...")
    for module in added_modules:
        module.set_versions(get_versions(module, source))
    for module in similar_modules:
        module.set_versions(get_versions(module, source))

    added_modules = remove_correct_added(added_modules)
    the_modules = similar_modules + added_modules
    print_version_updates(the_modules)

    if not dry:
        printt("Auto-updating version numbers...")
        update_versions(the_modules, source)
        print("All auto-updates complete")

        printt("Detecting and auto-updating dependencies...")
        update_dependencies(the_modules, source)

    printt("Deleting jdiff XML...")
    xml_dir = os.path.abspath("./build/jdiff-xml")
    print("XML successfully deleted" if del_dir(xml_dir) else "Failed to delete XML")

    print("\n--- Script completed successfully ---")
    return 0

# Start off the script
if __name__ == "__main__":
    sys.exit(main())
