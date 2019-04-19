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

""" Test Reporters implementation. """

import os
import re
import time
import codecs
import datetime
import tempfile
from .loggable import Loggable
from xml.sax import saxutils
from .utils import Result, printc, Colors

UNICODE_STRINGS = (type(str()) == type(str()))  # noqa


class UnknownResult(Exception):
    pass


CONTROL_CHARACTERS = re.compile(r"[\000-\010\013\014\016-\037]")


def xml_safe(value):
    """Replaces invalid XML characters with '?'."""
    return CONTROL_CHARACTERS.sub('?', value)


def escape_cdata(cdata):
    """Escape a string for an XML CDATA section."""
    return xml_safe(cdata).replace(']]>', ']]>]]&gt;<![CDATA[')


class Reporter(Loggable):
    name = 'simple'

    def __init__(self, options):
        Loggable.__init__(self)

        self.options = options
        self._start_time = 0
        self.stats = {'timeout': 0,
                      'failures': 0,
                      'passed': 0,
                      'skipped': 0
                      }
        self.results = []

    def init_timer(self):
        """Initialize a timer before starting tests."""
        self._start_time = time.time()

    def set_failed(self, test):
        if test.result == Result.SKIPPED:
            self.stats["skipped"] += 1
        else:
            self.stats["failures"] += 1

    def set_passed(self, test):
        self.stats["passed"] += 1

    def add_results(self, test):
        self.debug("%s", test)
        if test.result == Result.PASSED:
            self.set_passed(test)
        elif test.result == Result.FAILED or \
                test.result == Result.TIMEOUT or \
                test.result == Result.SKIPPED:
            self.set_failed(test)
        else:
            raise UnknownResult("%s" % test.result)

    def after_test(self, test):
        if test not in self.results:
            self.results.append(test)

        self.add_results(test)

    def final_report(self):
        print("\n")
        printc("Final Report:", title=True)
        sortedresults = sorted(self.results, key=lambda test: test.classname)
        for test in sorted(sortedresults, key=lambda test: test.result):
            printc(test)
            if test.result != Result.PASSED:
                print("\n")

        print("\n")
        lenstat = (len("Statistics") + 1)
        printc("Statistics:\n%s" % (lenstat * "-"), Colors.OKBLUE)
        if self._start_time > 0:
            printc("\n%sTotal time spent: %s seconds\n" %
                ((lenstat * " "), datetime.timedelta(
                    seconds=(time.time() - self._start_time))),
                Colors.OKBLUE)
        printc("%sPassed: %d" %
               (lenstat * " ", self.stats["passed"]), Colors.OKGREEN)
        printc("%sFailed: %d" %
               (lenstat * " ", self.stats["failures"]), Colors.FAIL)
        printc("%s%s" %
               (lenstat * " ", (len("Failed: 0")) * "-"), Colors.OKBLUE)

        total = self.stats["failures"] + self.stats["passed"]
        color = Colors.WARNING
        if total == self.stats["passed"]:
            color = Colors.OKGREEN
        elif total == self.stats["failures"]:
            color = Colors.FAIL

        printc("%sTotal: %d" % (lenstat * " ", total), color)

        return self.stats["failures"]


class XunitReporter(Reporter):

    """This reporter provides test results in the standard XUnit XML format."""
    name = 'xunit'
    encoding = 'UTF-8'

    def __init__(self, options):
        super(XunitReporter, self).__init__(options)

        self._createTmpFile()

    def final_report(self):
        self.report()
        return super(XunitReporter, self).final_report()

    def _get_captured(self, test):
        captured = ""
        if not self.options.redirect_logs:
            value = test.get_log_content()
            if value:
                captured += '<system-out><![CDATA[%s' % \
                    escape_cdata(value)
            for extralog in test.extra_logfiles:
                captured += "\n\n===== %s =====\n\n" % escape_cdata(
                    os.path.basename(extralog))
                value = test.get_extra_log_content(extralog)
                captured += escape_cdata(value)

            captured += "]]></system-out>"

        return captured

    def _quoteattr(self, attr):
        """Escape an XML attribute. Value can be unicode."""
        attr = xml_safe(attr)
        if isinstance(attr, str) and not UNICODE_STRINGS:
            attr = attr.encode(self.encoding)
        return saxutils.quoteattr(attr)

    def report(self):
        """Writes an Xunit-formatted XML file

        The file includes a report of test errors and failures.

        """
        self.debug("Writing XML file to: %s", self.options.xunit_file)
        xml_file = codecs.open(self.options.xunit_file, 'w',
                               self.encoding, 'replace')

        self.stats['encoding'] = self.encoding
        self.stats['total'] = (self.stats['timeout'] + self.stats['failures'] +
                               self.stats['passed'] + self.stats['skipped'])

        xml_file.write('<?xml version="1.0" encoding="%(encoding)s"?>'
                       '<testsuite name="gst-validate-launcher" tests="%(total)d" '
                       'errors="%(timeout)d" failures="%(failures)d" '
                       'skipped="%(skipped)d">' % self.stats)

        tmp_xml_file = codecs.open(self.tmp_xml_file.name, 'r',
                                   self.encoding, 'replace')

        for l in tmp_xml_file:
            xml_file.write(l)

        xml_file.write('</testsuite>')
        xml_file.close()
        tmp_xml_file.close()
        os.remove(self.tmp_xml_file.name)

        self._createTmpFile()

    def _createTmpFile(self):
        self.tmp_xml_file = tempfile.NamedTemporaryFile(delete=False)
        self.tmp_xml_file.close()

    def set_failed(self, test):
        """Add failure output to Xunit report.
        """
        super().set_failed(test)

        stack_trace = ''
        if test.stack_trace:
            stack_trace = '<![CDATA[%s]]>' % (escape_cdata(test.stack_trace))
        xml_file = codecs.open(self.tmp_xml_file.name, 'a',
                               self.encoding, 'replace')
        xml_file.write(self._forceUnicode(
            '<testcase classname=%(cls)s name=%(name)s time="%(taken).3f">'
            '<error type=%(errtype)s message=%(message)s>%(stacktrace)s'
            '</error>%(systemout)s</testcase>' %
            {'cls': self._quoteattr(test.get_classname()),
             'name': self._quoteattr(test.get_name()),
             'taken': test.time_taken,
             'stacktrace': stack_trace,
             'errtype': self._quoteattr(test.result),
             'message': self._quoteattr(test.message),
             'systemout': self._get_captured(test),
             }))
        xml_file.close()

    def set_passed(self, test):
        """Add success output to Xunit report.
        """
        self.stats['passed'] += 1

        xml_file = codecs.open(self.tmp_xml_file.name, 'a',
                               self.encoding, 'replace')
        xml_file.write(self._forceUnicode(
            '<testcase classname=%(cls)s name=%(name)s '
            'time="%(taken).3f">%(systemout)s</testcase>' %
            {'cls': self._quoteattr(test.get_classname()),
             'name': self._quoteattr(test.get_name()),
             'taken': test.time_taken,
             'systemout': self._get_captured(test),
             }))
        xml_file.close()

    def _forceUnicode(self, s):
        if not UNICODE_STRINGS:
            if isinstance(s, str):
                s = s.decode(self.encoding, 'replace')
        return s
