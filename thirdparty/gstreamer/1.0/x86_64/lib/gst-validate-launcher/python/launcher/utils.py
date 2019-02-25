#!/usr/bin/env python3
#
# Copyright (c) 2013,Thibault Saunier <thibault.saunier@collabora.com>
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this program; if not, write to the
# Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
# Boston, MA 02110-1301, USA.
""" Some utilies. """

try:
    import config
except ImportError:
    from . import config

import os
import platform
import re
import shutil
import shlex
import signal
import subprocess
import sys
import tempfile
import time
import urllib.request
import urllib.error
import urllib.parse

from .loggable import Loggable
from operator import itemgetter
from xml.etree import ElementTree


GST_SECOND = int(1000000000)
DEFAULT_TIMEOUT = 30
DEFAULT_MAIN_DIR = os.path.join(os.path.expanduser("~"), "gst-validate")
DEFAULT_GST_QA_ASSETS = os.path.join(
    DEFAULT_MAIN_DIR, "gst-integration-testsuites")
DISCOVERER_COMMAND = "gst-discoverer-1.0"
# Use to set the duration from which a test is considered as being 'long'
LONG_TEST = 40


class Result(object):
    NOT_RUN = "Not run"
    FAILED = "Failed"
    TIMEOUT = "Timeout"
    PASSED = "Passed"
    SKIPPED = "Skipped"
    KNOWN_ERROR = "Known error"


class Protocols(object):
    HTTP = "http"
    FILE = "file"
    HLS = "hls"
    DASH = "dash"
    RTSP = "rtsp"

    @staticmethod
    def needs_clock_sync(protocol):
        if protocol in [Protocols.HLS, Protocols.DASH]:
            return True

        return False


def supports_ansi_colors():
    platform = sys.platform
    supported_platform = platform != 'win32' or 'ANSICON' in os.environ
    # isatty is not always implemented, #6223.
    is_a_tty = hasattr(sys.stdout, 'isatty') and sys.stdout.isatty()
    if not supported_platform or not is_a_tty:
        return False
    return True


class Colors(object):
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'


def desactivate_colors():
    Colors.HEADER = ''
    Colors.OKBLUE = ''
    Colors.OKGREEN = ''
    Colors.WARNING = ''
    Colors.FAIL = ''
    Colors.ENDC = ''

if not supports_ansi_colors():
    desactivate_colors()


def mkdir(directory):
    try:
        os.makedirs(directory)
    except os.error:
        pass


def which(name, extra_path=None):
    exts = [_f for _f in os.environ.get('PATHEXT', '').split(os.pathsep) if _f]
    path = os.environ.get('PATH', '')
    if extra_path:
        path = extra_path + os.pathsep + path
    if not path:
        return []

    for p in path.split(os.pathsep):
        p = os.path.join(p, name)
        if os.access(p, os.X_OK):
            return p
        for e in exts:
            pext = p + e
            if os.access(pext, os.X_OK):
                return pext
    return None


def get_color_for_result(result):
    if result is Result.FAILED:
        color = Colors.FAIL
    elif result is Result.TIMEOUT:
        color = Colors.WARNING
    elif result is Result.PASSED:
        color = Colors.OKGREEN
    else:
        color = Colors.OKBLUE

    return color


def printc(message, color="", title=False, title_char=''):
    if title or title_char:
        length = 0
        for l in message.split("\n"):
            if len(l) > length:
                length = len(l)
        if length == 0:
            length = len(message)

        if title is True:
            message = length * "=" + "\n" + str(message) + "\n" + length * '='
        else:
            message = str(message) + "\n" + length * title_char

    if hasattr(message, "result") and color == '':
        color = get_color_for_result(message.result)

    sys.stdout.write(color + str(message) + Colors.ENDC + "\n")
    sys.stdout.flush()


def launch_command(command, color=None, fails=False):
    printc(command, Colors.OKGREEN, True)
    res = os.system(command)
    if res != 0 and fails is True:
        raise subprocess.CalledProcessError(res, "%s failed" % command)


def path2url(path):
    return urllib.parse.urljoin('file:', urllib.request.pathname2url(path))


def is_windows():
    platname = platform.system().lower()
    return platname == 'windows' or 'mingw' in platname


def url2path(url):
    path = urllib.parse.urlparse(url).path
    if "win32" in sys.platform:
        if path[0] == '/':
            return path[1:]  # We need to remove the first '/' on windows
    path = urllib.parse.unquote(path)
    return path


def isuri(string):
    url = urllib.parse.urlparse(string)
    if url.scheme != "" and url.scheme != "":
        return True

    return False


def touch(fname, times=None):
    with open(fname, 'a'):
        os.utime(fname, times)


def get_subclasses(klass, env):
    subclasses = []
    for symb in env.items():
        try:
            if issubclass(symb[1], klass) and not symb[1] is klass:
                subclasses.append(symb[1])
        except TypeError:
            pass

    return subclasses


def TIME_ARGS(time):
    return "%u:%02u:%02u.%09u" % (time / (GST_SECOND * 60 * 60),
                                  (time / (GST_SECOND * 60)) % 60,
                                  (time / GST_SECOND) % 60,
                                  time % GST_SECOND)


def look_for_file_in_source_dir(subdir, name):
    root_dir = os.path.abspath(os.path.dirname(
        os.path.join(os.path.dirname(os.path.abspath(__file__)))))
    p = os.path.join(root_dir, subdir, name)
    if os.path.exists(p):
        return p

    return None


# Returns the path $top_src_dir/@subdir/@name if running from source, or
# $DATADIR/gstreamer-1.0/validate/@name if not
def get_data_file(subdir, name):
    # Are we running from sources?
    p = look_for_file_in_source_dir(subdir, name)
    if p:
        return p

    # Look in system data dirs
    p = os.path.join(config.DATADIR, 'gstreamer-1.0', 'validate', name)
    if os.path.exists(p):
        return p

    return None

#
# Some utilities to parse gst-validate output   #
#


def gsttime_from_tuple(stime):
    return int((int(stime[0]) * 3600 + int(stime[1]) * 60 + int(stime[2])) * GST_SECOND + int(stime[3]))

timeregex = re.compile(r'(?P<_0>.+):(?P<_1>.+):(?P<_2>.+)\.(?P<_3>.+)')


def parse_gsttimeargs(time):
    stime = list(map(itemgetter(1), sorted(
        timeregex.match(time).groupdict().items())))
    return int((int(stime[0]) * 3600 + int(stime[1]) * 60 + int(stime[2])) * GST_SECOND + int(stime[3]))


def get_duration(media_file):

    duration = 0
    res = ''
    try:
        res = subprocess.check_output(
            [DISCOVERER_COMMAND, media_file]).decode()
    except subprocess.CalledProcessError:
        # gst-media-check returns !0 if seeking is not possible, we do not care
        # in that case.
        pass

    for l in res.split('\n'):
        if "Duration: " in l:
            duration = parse_gsttimeargs(l.replace("Duration: ", ""))
            break

    return duration


def get_scenarios():
    GST_VALIDATE_COMMAND = "gst-validate-1.0"
    os.system("%s --scenarios-defs-output-file %s" % (GST_VALIDATE_COMMAND,
                                                      ))


class BackTraceGenerator(Loggable):
    __instance = None
    _command_line_regex = re.compile(r'Command Line: (.*)\n')
    _timestamp_regex = re.compile(r'Timestamp: .*\((\d*)s ago\)')
    _pid_regex = re.compile(r'PID: (\d+) \(.*\)')

    def __init__(self):
        Loggable.__init__(self)

        self.in_flatpak = os.path.exists("/usr/manifest.json")
        if self.in_flatpak:
            coredumpctl = ['flatpak-spawn', '--host', 'coredumpctl']
        else:
            coredumpctl = ['coredumpctl']

        try:
            subprocess.check_output(coredumpctl)
            self.coredumpctl = coredumpctl
        except Exception as e:
            self.warning(e)
            self.coredumpctl = None
        self.gdb = shutil.which('gdb')

    @classmethod
    def get_default(cls):
        if not cls.__instance:
            cls.__instance = BackTraceGenerator()

        return cls.__instance

    def get_trace(self, test):
        if not test.process.returncode:
            return self.get_trace_on_running_process(test)

        if self.coredumpctl:
            return self.get_trace_from_systemd(test)

        self.debug("coredumpctl not present, and it is the only"
                   " supported way to get backtraces for now.")
        return None

    def get_trace_on_running_process(self, test):
        if not self.gdb:
            return "Can not generate stack trace as `gdb` is not" \
                "installed."

        gdb = ['gdb', '-ex', 't a a bt', '-batch',
               '-p', str(test.process.pid)]

        try:
            return subprocess.check_output(
                gdb, stderr=subprocess.STDOUT, timeout=30).decode()
        except Exception as e:
            return "Could not run `gdb` on process (pid: %d):\n%s" % (
                test.process.pid, e)

    def get_trace_from_systemd(self, test):
        for ntry in range(10):
            if ntry != 0:
                # Loopping, it means we conceder the logs might not be ready
                # yet.
                time.sleep(1)

            if not self.in_flatpak:
                coredumpctl = self.coredumpctl + ['info', str(test.process.pid)]
            else:
                newer_than = time.strftime("%a %Y-%m-%d %H:%M:%S %Z", time.localtime(test._starting_time))
                coredumpctl = self.coredumpctl + ['info', os.path.basename(test.command[0]),
                    '--since', newer_than]

            try:
                info = subprocess.check_output(coredumpctl, stderr=subprocess.STDOUT)
            except subprocess.CalledProcessError:
                # The trace might not be ready yet
                time.sleep(1)
                continue

            info = info.decode()
            try:
                pid = self._pid_regex.findall(info)[0]
            except IndexError:
                self.debug("Backtrace could not be found yet, trying harder.")
                continue

            command_line = BackTraceGenerator._command_line_regex.findall(info)[0]
            if shlex.split(command_line)[0] != test.application:
                self.debug("PID: %s -- executable %s != test application: %s" % (
                    pid, command_line[0], test.application))
                # The trace might not be ready yet
                continue

            if not BackTraceGenerator._timestamp_regex.findall(info):
                self.debug("Timestamp %s is more than 1min old",
                           re.findall(r'Timestamp: .*', info))
                # The trace might not be ready yet
                continue

            bt_all = None
            if self.gdb:
                try:
                    with tempfile.NamedTemporaryFile() as stderr:
                        coredump = subprocess.check_output(self.coredumpctl + ['dump', pid],
                            stderr=stderr)

                    with tempfile.NamedTemporaryFile() as tf:
                        tf.write(coredump)
                        tf.flush()
                        gdb = ['gdb', '-ex', 't a a bt', '-ex', 'quit',
                            test.application, tf.name]
                        bt_all = subprocess.check_output(
                            gdb, stderr=subprocess.STDOUT).decode()

                        info += "\nThread apply all bt:\n\n%s" % (
                            bt_all.replace('\n', '\n' + 15 * ' '))
                except Exception as e:
                    self.error("Could not get backtrace from gdb: %s" % e)

            return info

        return None


def check_bugs_resolution(bugs_definitions):
    bugz = {}
    regexes = {}
    for regex, bugs in bugs_definitions:
        if isinstance(bugs, str):
            bugs = [bugs]

        for bug in bugs:
            url = urllib.parse.urlparse(bug)

            if "bugzilla" not in url.netloc:
                printc("  + %s \n   --> bug: %s\n   --> Status: Not a bugzilla report\n" % (regex, bug),
                       Colors.WARNING)
                continue

            query = urllib.parse.parse_qs(url.query)
            _id = query.get('id')
            if not _id:
                printc("  + '%s' -- Can't check bug '%s'\n" %
                       (regex, bug), Colors.WARNING)
                continue

            if isinstance(_id, list):
                _id = _id[0]

            regexes[_id] = (regex, bug)
            url_parts = tuple(list(url)[:3] + ['', '', ''])
            ids = bugz.get(url_parts, [])
            ids.append(_id)
            bugz[url_parts] = ids

    res = True
    for url_parts, ids in bugz.items():
        url_parts = list(url_parts)
        query = {'id': ','.join(ids)}
        query['ctype'] = 'xml'
        url_parts[4] = urllib.parse.urlencode(query)
        try:
            res = urllib.request.urlopen(urllib.parse.urlunparse(url_parts))
        except Exception as e:
            printc("  + Could not properly check bugs status for: %s (%s)\n"
                   % (urllib.parse.urlunparse(url_parts), e), Colors.FAIL)
            continue

        root = ElementTree.fromstring(res.read())
        bugs = root.findall('./bug')

        if len(bugs) != len(ids):
            printc("  + Could not properly check bugs status on server %s\n" %
                   urllib.parse.urlunparse(url_parts), Colors.FAIL)
            continue

        for bugelem in bugs:
            status = bugelem.findtext('./bug_status')
            bugid = bugelem.findtext('./bug_id')
            regex, bug = regexes[bugid]
            desc = bugelem.findtext('./short_desc')

            if not status:
                printc("  + %s \n   --> bug: %s\n   --> Status: UNKNOWN\n" % (regex, bug),
                       Colors.WARNING)
                continue

            if not status.lower() in ['new', 'verified']:
                printc("  + %s \n   --> bug: #%s: '%s'\n   ==> Bug CLOSED already (status: %s)\n" % (
                       regex, bugid, desc, status), Colors.WARNING)

                res = False

            printc("  + %s \n   --> bug: #%s: '%s'\n   --> Status: %s\n" % (
                   regex, bugid, desc, status), Colors.OKGREEN)

    return res


def kill_subprocess(owner, process, timeout):
    if process is None:
        return

    stime = time.time()
    res = process.poll()
    waittime = 0.05
    while res is None:
        try:
            owner.debug("Subprocess is still alive, sending KILL signal")
            if is_windows():
                subprocess.call(
                    ['taskkill', '/F', '/T', '/PID', str(process.pid)])
            else:
                process.send_signal(signal.SIGKILL)
            time.sleep(waittime)
            waittime *= 2
        except OSError:
            pass
        if time.time() - stime > DEFAULT_TIMEOUT:
            raise RuntimeError("Could not kill subprocess after %s second"
                               " Something is really wrong, => EXITING"
                               % DEFAULT_TIMEOUT)
        res = process.poll()

    return res
