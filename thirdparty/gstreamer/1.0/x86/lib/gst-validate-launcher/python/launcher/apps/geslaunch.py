#!/usr/bin/env python2
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

import os
import sys
import urllib.parse
import subprocess
from launcher import utils
from urllib.parse import unquote
import xml.etree.ElementTree as ET
from launcher.baseclasses import GstValidateTest, TestsManager, ScenarioManager, MediaFormatCombination, \
    MediaDescriptor, GstValidateEncodingTestInterface

GES_DURATION_TOLERANCE = utils.GST_SECOND / 2

GES_LAUNCH_COMMAND = "ges-launch-1.0"
if "win32" in sys.platform:
    GES_LAUNCH_COMMAND += ".exe"


GES_ENCODING_TARGET_COMBINATIONS = [
    MediaFormatCombination("ogg", "vorbis", "theora"),
    MediaFormatCombination("ogg", "opus", "theora"),
    MediaFormatCombination("webm", "vorbis", "vp8"),
    MediaFormatCombination("webm", "opus", "vp8"),
    MediaFormatCombination("mp4", "aac", "h264"),
    MediaFormatCombination("mp4", "ac3", "h264"),
    MediaFormatCombination("quicktime", "aac", "jpeg"),
    MediaFormatCombination("mkv", "opus", "h264"),
    MediaFormatCombination("mkv", "vorbis", "h264"),
    MediaFormatCombination("mkv", "opus", "jpeg"),
    MediaFormatCombination("mkv", "vorbis", "jpeg")
]


def quote_uri(uri):
    """
    Encode a URI/path according to RFC 2396, without touching the file:/// part.
    """
    # Split off the "file:///" part, if present.
    parts = urllib.parse.urlsplit(uri, allow_fragments=False)
    # Make absolutely sure the string is unquoted before quoting again!
    raw_path = unquote(parts.path)
    return utils.path2url(raw_path)


class XgesProjectDescriptor(MediaDescriptor):
    def __init__(self, uri):
        super(XgesProjectDescriptor, self).__init__()

        self._uri = uri
        self._xml_path = utils.url2path(uri)
        self._root = ET.parse(self._xml_path)
        self._duration = None

    def get_media_filepath(self):
        return self._xml_path

    def get_path(self):
        return self._xml_path

    def get_caps(self):
        raise NotImplemented

    def get_uri(self):
        return self._uri

    def get_duration(self):
        if self._duration:
            return self._duration

        for l in self._root.iter():
            if l.tag == "timeline":
                self._duration=int(l.attrib['metadatas'].split("duration=(guint64)")[1].split(" ")[0].split(";")[0])
                break

        if not self._duration:
            self.error("%s does not have duration! (setting 2mins)" % self._uri)
            self._duration = 2 * 60

        return self._duration

    def get_protocol(self):
        return Protocols.FILE

    def is_seekable(self):
        return True

    def is_image(self):
        return False

    def get_num_tracks(self, track_type):
        num_tracks = 0
        for l in self._root.iter():
            if l.tag == "track":
                if track_type in l.attrib["caps"]:
                    num_tracks += 1
        return num_tracks


class GESTest(GstValidateTest):
    def __init__(self, classname, options, reporter, project, scenario=None,
                 combination=None, expected_failures=None):

        super(GESTest, self).__init__(GES_LAUNCH_COMMAND, classname, options, reporter,
                                      scenario=scenario)

        self.project = project

    def set_sample_paths(self):
        if not self.options.paths:
            if self.options.disable_recurse:
                return
            paths = [os.path.dirname(self.project.get_media_filepath())]
        else:
            paths = self.options.paths

        if not isinstance(paths, list):
            paths = [paths]

        for path in paths:
            # We always want paths separator to be cut with '/' for ges-launch
            path = path.replace("\\", "/")
            if not self.options.disable_recurse:
                self.add_arguments("--ges-sample-path-recurse", quote_uri(path))
            else:
                self.add_arguments("--ges-sample-paths", quote_uri(path))

    def build_arguments(self):
        GstValidateTest.build_arguments(self)

        if self.options.mute:
            self.add_arguments("--mute")

        self.set_sample_paths()
        self.add_arguments("-l", self.project.get_uri())


class GESPlaybackTest(GESTest):
    def __init__(self, classname, options, reporter, project, scenario):
        super(GESPlaybackTest, self).__init__(classname, options, reporter,
                                      project, scenario=scenario)

    def get_current_value(self):
        return self.get_current_position()


class GESRenderTest(GESTest, GstValidateEncodingTestInterface):
    def __init__(self, classname, options, reporter, project, combination):
        GESTest.__init__(self, classname, options, reporter, project)

        GstValidateEncodingTestInterface.__init__(self, combination, self.project)

    def build_arguments(self):
        GESTest.build_arguments(self)
        self._set_rendering_info()

    def _set_rendering_info(self):
        self.dest_file = path = os.path.join(self.options.dest,
                                             self.classname.replace(".render.", os.sep).
                                             replace(".", os.sep))
        utils.mkdir(os.path.dirname(urllib.parse.urlsplit(self.dest_file).path))
        if not utils.isuri(self.dest_file):
            self.dest_file = utils.path2url(self.dest_file)

        profile = self.get_profile()
        self.add_arguments("-f", profile, "-o", self.dest_file)

    def check_results(self):
        if self.result in [Result.PASSED, Result.NOT_RUN] and self.scenario is None:
            if self.process.returncode != 0:
                return super().check_results()

            res, msg = self.check_encoded_file()
            self.set_result(res, msg)
        else:
            if self.result == utils.Result.TIMEOUT:
                missing_eos = False
                try:
                    if utils.get_duration(self.dest_file) == self.project.get_duration():
                        missing_eos = True
                except Exception as e:
                    pass

                if missing_eos is True:
                    self.set_result(utils.Result.TIMEOUT, "The rendered file had right duration, MISSING EOS?\n",
                                    "failure")
            else:
                GstValidateTest.check_results(self)

    def get_current_value(self):
        size = self.get_current_size()
        if size is None:
            return self.get_current_position()

        return size


class GESTestsManager(TestsManager):
    name = "ges"

    _scenarios = ScenarioManager()

    def __init__(self):
        super(GESTestsManager, self).__init__()

    def init(self):
        try:
            if "--set-scenario=" in subprocess.check_output([GES_LAUNCH_COMMAND, "--help"]).decode():

                return True
            else:
                self.warning("Can not use ges-launch, it seems not to be compiled against"
                             " gst-validate")
        except subprocess.CalledProcessError as e:
            self.warning("Can not use ges-launch: %s" % e)
        except OSError as e:
            self.warning("Can not use ges-launch: %s" % e)

    def add_options(self, parser):
        group = parser.add_argument_group("GStreamer Editing Services specific option"
                            " and behaviours",
                            description="""
The GStreamer Editing Services launcher will be usable only if GES has been compiled against GstValidate
You can simply run scenarios specifying project as args. For example the following will run all available
and activated scenarios on project.xges:

    $gst-validate-launcher ges /some/ges/project.xges


Available options:""")
        group.add_argument("-P", "--projects-paths", dest="projects_paths",
                         default=os.path.join(utils.DEFAULT_GST_QA_ASSETS,
                                              "ges",
                                              "ges-projects"),
                         help="Paths in which to look for moved medias")
        group.add_argument("-r", "--disable-recurse-paths", dest="disable_recurse",
                         default=False, action="store_true",
                         help="Whether to recurse into paths to find medias")

    def set_settings(self, options, args, reporter):
        TestsManager.set_settings(self, options, args, reporter)
        self._scenarios.config = self.options

        try:
            os.makedirs(utils.url2path(options.dest)[0])
        except OSError:
            pass

    def list_tests(self):
        return self.tests

    def register_defaults(self, project_paths=None):
        projects = list()
        if not self.args:
            if project_paths == None:
                path = self.options.projects_paths
            else:
                path = project_paths

            for root, dirs, files in os.walk(path):
                for f in files:
                    if not f.endswith(".xges"):
                        continue
                    projects.append(utils.path2url(os.path.join(path, root, f)))
        else:
            for proj_uri in self.args:
                if not utils.isuri(proj_uri):
                    proj_uri = utils.path2url(proj_uri)

                if os.path.exists(proj_uri):
                    projects.append(proj_uri)

        if self.options.long_limit != 0:
            scenarios = ["none",
                         "scrub_forward_seeking",
                         "scrub_backward_seeking"]
        else:
            scenarios = ["play_15s",
                         "scrub_forward_seeking_full",
                         "scrub_backward_seeking_full"]
        for proj_uri in projects:
            # First playback casses
            project = XgesProjectDescriptor(proj_uri)
            for scenario_name in scenarios:
                scenario = self._scenarios.get_scenario(scenario_name)
                if scenario is None:
                    continue

                if scenario.get_min_media_duration() >= (project.get_duration() / utils.GST_SECOND):
                    continue

                classname = "playback.%s.%s" % (scenario.name,
                                                    os.path.basename(proj_uri).replace(".xges", ""))
                self.add_test(GESPlaybackTest(classname,
                                              self.options,
                                              self.reporter,
                                              project,
                                              scenario=scenario)
                                  )

            # And now rendering casses
            for comb in GES_ENCODING_TARGET_COMBINATIONS:
                classname = "render.%s.%s" % (str(comb).replace(' ', '_'),
                                                  os.path.splitext(os.path.basename(proj_uri))[0])
                self.add_test(GESRenderTest(classname, self.options,
                                            self.reporter, project,
                                            combination=comb)
                                  )
