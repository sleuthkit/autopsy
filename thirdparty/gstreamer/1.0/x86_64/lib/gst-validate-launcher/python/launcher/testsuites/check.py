# -*- Mode: Python -*- vi:si:et:sw=4:sts=4:ts=4:syntax=python
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

"""
GStreamer unit tests
"""

TEST_MANAGER = "check"

KNOWN_NOT_LEAKY = r'^check.gst-devtools.*|^check.gstreamer.*|^check-gst-plugins-base|^check.gst-plugins-ugly|^check.gst-plugins-good'


def setup_tests(test_manager, options):
    if options.gst_check_leak_trace_testnames == 'known-not-leaky':
        options.gst_check_leak_trace_testnames = KNOWN_NOT_LEAKY
    test_manager.register_tests()
    return True
