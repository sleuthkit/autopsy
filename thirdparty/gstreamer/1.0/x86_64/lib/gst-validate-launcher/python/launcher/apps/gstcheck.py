#!/usr/bin/env python3
#
# Copyright (c) 2016,Thibault Saunier <thibault.saunier@osg.samsung.com>
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
import argparse
import os
import re
import pickle
import platform
import shutil
import threading
import concurrent.futures as conc

from launcher import config
from launcher.utils import printc, Colors


class MesonTest(Test):

    def __init__(self, name, options, reporter, test, child_env=None):
        ref_env = os.environ.copy()
        if child_env is None:
            child_env = {}
        else:
            ref_env.update(child_env)

        if not isinstance(test.env, dict):
            test.env = test.env.get_env(ref_env)
        child_env.update(test.env)
        if len(test.extra_paths) > 0:
            child_env['PATH'] = child_env['PATH'] + \
                ';'.join([''] + test.extra_paths)
        self.child_env = child_env

        timeout = int(child_env.pop('CK_DEFAULT_TIMEOUT', test.timeout))

        Test.__init__(self, test.fname[0], name, options,
                      reporter, timeout=timeout, hard_timeout=timeout,
                      is_parallel=test.is_parallel)

        self.mesontest = test

    def build_arguments(self):
        self.add_arguments(*self.mesontest.fname[1:])
        self.add_arguments(*self.mesontest.cmd_args)

    def get_subproc_env(self):
        env = os.environ.copy()
        env.update(self.child_env)
        # No reason to fork since we are launching
        # each test individually
        env['CK_FORK'] = 'no'
        self.add_env_variable('CK_FORK', 'no')
        for var, val in self.child_env.items():
            self.add_env_variable(var, val)

        return env


class MesonTestsManager(TestsManager):
    name = "mesontest"
    arggroup = None

    def __init__(self):
        super().__init__()
        self.rebuilt = None

    def add_options(self, parser):
        if self.arggroup:
            return

        arggroup = MesonTestsManager.arggroup = parser.add_argument_group(
            "meson tests specific options and behaviours")
        arggroup.add_argument("--meson-build-dir",
                            action="append",
                            dest='meson_build_dirs',
                            default=[],
                            help="defines the paths to look for GstValidate tools.")
        arggroup.add_argument("--meson-no-rebuild",
                            action="store_true",
                            default=False,
                            help="Whether to avoid to rebuild tests before running them.")

    def get_meson_tests(self):
        if not self.options.meson_build_dirs:
            self.options.meson_build_dirs = [config.BUILDDIR]
        mesontests = []
        for i, bdir in enumerate(self.options.meson_build_dirs):
            bdir = os.path.abspath(bdir)
            datafile = os.path.join(
                bdir, 'meson-private/meson_test_setup.dat')

            if not os.path.isfile(datafile):
                self.error("%s does not exists, can't use meson test launcher",
                           datafile)
                continue

            with open(datafile, 'rb') as f:
                tests = pickle.load(f)
                mesontests.extend(tests)

        return mesontests

    def rebuild(self, all=False):
        if not self.options.meson_build_dirs:
            self.options.meson_build_dirs = [config.BUILDDIR]
        if self.options.meson_no_rebuild:
            return True

        if self.rebuilt is not None:
            return self.rebuilt

        for bdir in self.options.meson_build_dirs:
            if not os.path.isfile(os.path.join(bdir, 'build.ninja')):
                printc("Only ninja backend is supported to rebuilt tests before running them.\n",
                       Colors.OKBLUE)
                self.rebuilt = True
                return True

            ninja = shutil.which('ninja')
            if not ninja:
                ninja = shutil.which('ninja-build')
            if not ninja:
                printc("Can't find ninja, can't rebuild test.\n", Colors.FAIL)
                self.rebuilt = False
                return False

            print("-> Rebuilding %s.\n" % bdir)
            try:
                subprocess.check_call([ninja, '-C', bdir])
            except subprocess.CalledProcessError:
                self.rebuilt = False
                return False

        self.rebuilt = True
        return True

    def run_tests(self, starting_test_num, total_num_tests):
        if not self.rebuild():
            self.error("Rebuilding FAILED!")
            return Result.FAILED

        return TestsManager.run_tests(self, starting_test_num, total_num_tests)

    def get_test_name(self, test):
        name = test.name.replace('/', '.')
        if test.suite:
            name = '.'.join(test.suite) + '.' + name

        return name.replace('..', '.').replace(' ', '-')

    def list_tests(self):
        if self.tests:
            return self.tests

        mesontests = self.get_meson_tests()
        for test in mesontests:
            self.add_test(MesonTest(self.get_test_name(test),
                                    self.options, self.reporter, test))

        return self.tests


class GstCheckTestsManager(MesonTestsManager):
    name = "check"

    def __init__(self):
        MesonTestsManager.__init__(self)
        self.tests_info = {}

    def init(self):
        return True

    def check_binary_ts(self, binary):
        try:
            last_touched = os.stat(binary).st_mtime
            test_info = self.tests_info.get(binary)
            if not test_info:
                return last_touched, []
            elif test_info[0] == 0:
                return True
            elif test_info[0] == last_touched:
                return True
        except FileNotFoundError:
            return None

        return last_touched, []

    def _list_gst_check_tests(self, test, recurse=False):
        binary = test.fname[0]

        self.tests_info[binary] = self.check_binary_ts(binary)

        tmpenv = os.environ.copy()
        tmpenv['GST_DEBUG'] = "0"
        pe = subprocess.Popen([binary, '--list-tests'],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              env=tmpenv)

        output = pe.communicate()[0].decode()
        if pe.returncode != 0:
            self.debug("%s not able to list tests" % binary)
            return
        for t in output.split("\n"):
            test_name = re.findall(r'(?<=^Test: )\w+$', t)
            if len(test_name) == 1:
                self.tests_info[binary][1].append(test_name[0])

    def load_tests_info(self):
        dumpfile = os.path.join(self.options.privatedir, self.name + '.dat')
        try:
            with open(dumpfile, 'rb') as f:
                self.tests_info = pickle.load(f)
        except FileNotFoundError:
            self.tests_info = {}

    def save_tests_info(self):
        dumpfile = os.path.join(self.options.privatedir, self.name + '.dat')
        with open(dumpfile, 'wb') as f:
            pickle.dump(self.tests_info, f)

    def add_options(self, parser):
        super().add_options(parser)
        arggroup = parser.add_argument_group("gstcheck specific options")
        arggroup.add_argument("--gst-check-leak-trace-testnames",
                            default=None,
                            help="A regex to specifying testsnames of the test"
                              "to run with the leak tracer activated, if 'known-not-leaky'"
                              " is specified, the testsuite will automatically activate"
                              " leak tracers on tests known to be not leaky.")
        arggroup.add_argument("--gst-check-leak-options",
                            default=None,
                            help="Leak tracer options")

    def get_child_env(self, testname, check_name=None):
        child_env = {}
        if check_name:
            child_env['GST_CHECKS'] = check_name

        if self.options.gst_check_leak_trace_testnames:
            if re.findall(self.options.gst_check_leak_trace_testnames, testname):
                leak_tracer = "leaks"
                if self.options.gst_check_leak_options:
                    leak_tracer += "(%s)" % self.options.gst_check_leak_options
                tracers = set(os.environ.get('GST_TRACERS', '').split(';')) | set([leak_tracer])
                child_env['GST_TRACERS'] = ';'.join(tracers)

        return child_env

    def register_tests(self):
        if self.tests:
            return self.tests

        self.rebuild(all=True)
        self.load_tests_info()
        mesontests = self.get_meson_tests()
        to_inspect = []
        for test in mesontests:
            binary = test.fname[0]
            test_info = self.check_binary_ts(binary)
            if test_info is True:
                continue
            elif test_info is None:
                test_info = self.check_binary_ts(binary)
                if test_info is None:
                    raise RuntimeError("Test binary %s does not exist"
                                       " even after a full rebuild" % binary)

            with open(binary, 'rb') as f:
                if b"gstcheck" not in f.read():
                    self.tests_info[binary] = [0, []]
                    continue
            to_inspect.append(test)

        if to_inspect:
            executor = conc.ThreadPoolExecutor(
                max_workers=self.options.num_jobs)
            tmp = []
            for test in to_inspect:
                tmp.append(executor.submit(self._list_gst_check_tests, test))

            for e in tmp:
                e.result()

        for test in mesontests:
            gst_tests = self.tests_info[test.fname[0]][1]
            if not gst_tests:
                name = self.get_test_name(test)
                child_env = self.get_child_env(name)
                self.add_test(MesonTest(name, self.options, self.reporter, test,
                                        child_env))
            else:
                for ltest in gst_tests:
                    name = self.get_test_name(test) + '.' + ltest
                    child_env = self.get_child_env(name, ltest)
                    self.add_test(MesonTest(name, self.options, self.reporter, test,
                                            child_env))
        self.save_tests_info()
        return self.tests
