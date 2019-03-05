#!/usr/bin/env python3
#
# Copyright (c) 2015,Thibault Saunier <tsaunier@gnome.org>
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

import os
import time
from . import loggable
import subprocess


class VirtualFrameBufferServer(loggable.Loggable):
    def __init__(self, options):
        loggable.Loggable.__init__(self)
        self.options = options

    def start(self):
        raise NotImplementedError

    def stop(self):
        raise NotImplementedError


class Xvfb(VirtualFrameBufferServer):

    """ Class to run xvfb in a process."""

    def __init__(self, options):
        VirtualFrameBufferServer.__init__(self, options)
        self.display_id = ":27"
        self._process = None
        self._logsfile = None
        self._command = "Xvfb %s -screen 0 1920x1080x24" % self.display_id

    def _check_is_up(self, timeout=3, assume_true=True):
        """ Check if the xvfb is up, running a simple test based on xset. """
        start = time.time()
        while True:
            try:
                os.environ["DISPLAY"] = self.display_id
                subprocess.check_output(["xset", "q"],
                                        stderr=self._logsfile)
                print(("DISPLAY set to %s" % self.display_id))
                return True
            except subprocess.CalledProcessError:
                pass
            except FileNotFoundError:
                if assume_true:
                    print('WARNING: xset not preset on the system,'
                        ' just wait for %s seconds and hope for the best.'
                        ' (this is what xvfb-run itself does anyway.)' % timeout)
                    time.sleep(timeout)
                return assume_true

            if time.time() - start > timeout:
                return False

            time.sleep(1)

    def start(self):
        """ Start xvfb in a subprocess """
        self._logsfile = open(os.path.join(self.options.logsdir,
                                           "xvfb.logs"), 'w+')
        if self._check_is_up(assume_true=False):
            print("xvfb already running")
            return (True, None)

        print("Starting xvfb")
        try:
            self.debug("Launching xvfb: %s (logs in %s)", self._command, self._logsfile)
            self._process = subprocess.Popen(self._command.split(" "),
                                             stderr=self._logsfile,
                                             stdout=self._logsfile)
            self.debug("Launched xvfb")

            # Dirty way to avoid eating to much CPU...
            # good enough for us anyway.
            time.sleep(1)

            if self._check_is_up():
                print("Xvfb tarted")
                return (True, None)
            else:
                print("Failed starting xvfb")
                self._process.terminate()
                self._process = None
        except Exception as ex:
            return (False, "Could not launch %s %s\n"
                    "Make sure Xvfb is installed" % (self._command, ex))

    def stop(self):
        """ Stop the xvfb subprocess if running. """
        if self._process:
            self._process.terminate()
            self._process = None
            self.debug("xvfb stopped")


def get_virual_frame_buffer_server(options):
    """
    Return a VirtualFrameBufferServer
    """
    return Xvfb(options)
