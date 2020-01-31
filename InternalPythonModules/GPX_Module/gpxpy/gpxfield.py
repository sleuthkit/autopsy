# -*- coding: utf-8 -*-

# Copyright 2014 Tomo Krajina
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import inspect as mod_inspect
import datetime as mod_datetime

from . import utils as mod_utils


class GPXFieldTypeConverter:
    def __init__(self, from_string, to_string):
        self.from_string = from_string
        self.to_string = to_string


def parse_time(string):
    from . import gpx as mod_gpx
    if not string:
        return None
    if 'T' in string:
        string = string.replace('T', ' ')
    if 'Z' in string:
        string = string.replace('Z', '')
    for date_format in mod_gpx.DATE_FORMATS:
        try:
            return mod_datetime.datetime.strptime(string, date_format)
        except ValueError as e:
            pass
    raise GPXException('Invalid time: %s' % string)


# ----------------------------------------------------------------------------------------------------
# Type converters used to convert from/to the string in the XML:
# ----------------------------------------------------------------------------------------------------


class FloatConverter:
    def __init__(self):
        self.from_string = lambda string : None if string is None else float(string.strip())
        self.to_string =   lambda flt    : str(flt)


class IntConverter:
    def __init__(self):
        self.from_string = lambda string : None if string is None else int(string.strip())
        self.to_string =   lambda flt    : str(flt)


class TimeConverter:
    def from_string(self, string):
        from . import gpx as mod_gpx
        if not string:
            return None
        if 'T' in string:
            string = string.replace('T', ' ')
        if 'Z' in string:
            string = string.replace('Z', '')
        for date_format in mod_gpx.DATE_FORMATS:
            try:
                return mod_datetime.datetime.strptime(string, date_format)
            except ValueError as e:
                pass
        return None
    def to_string(self, time):
        from . import gpx as mod_gpx
        return time.strftime(mod_gpx.DATE_FORMAT) if time else None


INT_TYPE = IntConverter()
FLOAT_TYPE = FloatConverter()
TIME_TYPE = TimeConverter()


# ----------------------------------------------------------------------------------------------------
# Field converters:
# ----------------------------------------------------------------------------------------------------


class AbstractGPXField:
    def __init__(self, attribute_field=None, is_list=None):
        self.attribute_field = attribute_field
        self.is_list = is_list
        self.attribute = False

    def from_xml(self, parser, node, version):
        raise Exception('Not implemented')

    def to_xml(self, value, version):
        raise Exception('Not implemented')


class GPXField(AbstractGPXField):
    """
    Used for to (de)serialize fields with simple field<->xml_tag mapping.
    """
    def __init__(self, name, tag=None, attribute=None, type=None, possible=None, mandatory=None):
        AbstractGPXField.__init__(self)
        self.name = name
        if tag and attribute:
            raise GPXException('Only tag *or* attribute may be given!')
        if attribute:
            self.tag = None
            self.attribute = name if attribute is True else attribute
        elif tag:
            self.tag = name if tag is True else tag
            self.attribute = None
        else:
            self.tag = name
            self.attribute = None
        self.type_converter = type
        self.possible = possible
        self.mandatory = mandatory

    def from_xml(self, parser, node, version):
        if self.attribute:
            result = parser.get_node_attribute(node, self.attribute)
        else:
            __node = parser.get_first_child(node, self.tag)
            result = parser.get_node_data(__node)

        if result is None:
            if self.mandatory:
                from . import gpx as mod_gpx
                raise mod_gpx.GPXException('%s is mandatory in %s' % (self.name, self.tag))
            return None

        if self.type_converter:
            try:
                result = self.type_converter.from_string(result)
            except Exception as e:
                from . import gpx as mod_gpx
                raise mod_gpx.GPXException('Invalid value for <%s>... %s (%s)' % (self.tag, result, e))

        if self.possible:
            if not (result in self.possible):
                from . import gpx as mod_gpx
                raise mod_gpx.GPXException('Invalid value "%s", possible: %s' % (result, self.possible))

        return result

    def to_xml(self, value, version):
        if not value:
            return ''

        if self.attribute:
            return '%s="%s"' % (self.attribute, mod_utils.make_str(value))
        else:
            if self.type_converter:
                value = self.type_converter.to_string(value)
            if isinstance(self.tag, list) or isinstance(self.tag, tuple):
                raise Exception('Not yet implemented')
            return mod_utils.to_xml(self.tag, content=value, escape=True)


class GPXComplexField(AbstractGPXField):
    def __init__(self, name, classs, tag=None, is_list=None):
        AbstractGPXField.__init__(self, is_list=is_list)
        self.name = name
        self.tag = tag or name
        self.classs = classs

    def from_xml(self, parser, node, version):
        if self.is_list:
            result = []
            for child_node in parser.get_children(node):
                if parser.get_node_name(child_node) == self.tag:
                    result.append(gpx_fields_from_xml(self.classs, parser, child_node, version))
            return result
        else:
            field_node = parser.get_first_child(node, self.tag)
            if field_node is None:
                return None
            return gpx_fields_from_xml(self.classs, parser, field_node, version)

    def to_xml(self, value, version):
        if self.is_list:
            result = ''
            for obj in value:
                result += gpx_fields_to_xml(obj, self.tag, version)
            return result
        else:
            return gpx_fields_to_xml(value, self.tag, version)


class GPXEmailField(AbstractGPXField):
    """
    Converts GPX1.1 email tag group from/to string.
    """
    def __init__(self, name, tag=None):
        self.attribute = False
        self.is_list = False
        self.name = name
        self.tag = tag or name

    def from_xml(self, parser, node, version):
        email_node = parser.get_first_child(node, self.tag)

        if email_node is None:
            return None

        email_id = parser.get_node_attribute(email_node, 'id')
        email_domain = parser.get_node_attribute(email_node, 'domain')

        return '%s@%s' % (email_id, email_domain)

    def to_xml(self, value, version):
        if not value:
            return ''

        if '@' in value:
            pos = value.find('@')
            email_id = value[:pos]
            email_domain = value[pos+1:]
        else:
            email_id = value
            email_domain = 'unknown'

        return '\n<%s id="%s" domain="%s" />' % (self.tag, email_id, email_domain)


class GPXExtensionsField(AbstractGPXField):
    """
    GPX1.1 extensions <extensions>...</extensions> key-value type.
    """
    def __init__(self, name, tag=None):
        self.attribute = False
        self.name = name
        self.is_list = False
        self.tag = tag or 'extensions'

    def from_xml(self, parser, node, version):
        result = {}

        if node is None:
            return result

        extensions_node = parser.get_first_child(node, self.tag)

        if extensions_node is None:
            return result

        children = parser.get_children(extensions_node)
        if children is None:
            return result

        for child in children:
            result[parser.get_node_name(child)] = parser.get_node_data(child)

        return result

    def to_xml(self, value, version):
        if value is None or not value:
            return ''

        result = '\n<' + self.tag + '>'
        for ext_key, ext_value in value.items():
            result += mod_utils.to_xml(ext_key, content=ext_value)
        result += '</' + self.tag + '>'

        return result


# ----------------------------------------------------------------------------------------------------
# Utility methods:
# ----------------------------------------------------------------------------------------------------


def gpx_fields_to_xml(instance, tag, version, custom_attributes=None):
    fields = instance.gpx_10_fields
    if version == '1.1':
        fields = instance.gpx_11_fields

    tag_open = bool(tag)
    body = ''
    if tag:
        body = '\n<' + tag
        if custom_attributes:
            for key, value in custom_attributes.items():
                body += ' %s="%s"' % (key, mod_utils.make_str(value))

    for gpx_field in fields:
        if isinstance(gpx_field, str):
            if tag_open:
                body += '>'
                tag_open = False
            if gpx_field[0] == '/':
                body += '<%s>' % gpx_field
            else:
                body += '\n<%s' % gpx_field
                tag_open = True
        else:
            value = getattr(instance, gpx_field.name)
            if gpx_field.attribute:
                body += ' ' + gpx_field.to_xml(value, version)
            elif value:
                if tag_open:
                    body += '>'
                    tag_open = False
                xml_value = gpx_field.to_xml(value, version)
                if xml_value:
                    body += xml_value

    if tag:
        if tag_open:
            body += '>'
        body += '</' + tag + '>'

    return body


def gpx_fields_from_xml(class_or_instance, parser, node, version):
    if mod_inspect.isclass(class_or_instance):
        result = class_or_instance()
    else:
        result = class_or_instance

    fields = result.gpx_10_fields
    if version == '1.1':
        fields = result.gpx_11_fields

    node_path = [ node ]

    for gpx_field in fields:
        current_node = node_path[-1]
        if isinstance (gpx_field, str):
            if gpx_field.startswith('/'):
                node_path.pop()
            else:
                if current_node is None:
                    node_path.append(None)
                else:
                    node_path.append(parser.get_first_child(current_node, gpx_field))
        else:
            if current_node is not None:
                value = gpx_field.from_xml(parser, current_node, version)
                setattr(result, gpx_field.name, value)
            elif gpx_field.attribute:
                value = gpx_field.from_xml(parser, node, version)
                setattr(result, gpx_field.name, value)

    return result


def gpx_check_slots_and_default_values(classs):
    """
    Will fill the default values for this class. Instances will inherit those
    values so we don't need to fill default values for every instance.

    This method will also fill the attribute gpx_field_names with a list of
    gpx field names. This can be used
    """
    fields = classs.gpx_10_fields + classs.gpx_11_fields

    gpx_field_names = []

    instance = classs()

    try:
        attributes = list(filter(lambda x : x[0] != '_', dir(instance)))
        attributes = list(filter(lambda x : not callable(getattr(instance, x)), attributes))
        attributes = list(filter(lambda x : not x.startswith('gpx_'), attributes))
    except Exception as e:
        raise Exception('Error reading attributes for %s: %s' % (classs.__name__, e))

    attributes.sort()
    slots = list(classs.__slots__)
    slots.sort()

    if attributes != slots:
        raise Exception('Attributes for %s is\n%s but should be\n%s' % (classs.__name__, attributes, slots))

    for field in fields:
        if not isinstance(field, str):
            if field.is_list:
                value = []
            else:
                value = None
            try:
                actual_value = getattr(instance, field.name)
            except:
                raise Exception('%s has no attribute %s' % (classs.__name__, field.name))
            if value != actual_value:
                raise Exception('Invalid default value %s.%s is %s but should be %s'
                                % (classs.__name__, field.name, actual_value, value))
            #print('%s.%s -> %s' % (classs, field.name, value))
            if not field.name in gpx_field_names:
                gpx_field_names.append(field.name)

    gpx_field_names = tuple(gpx_field_names)
    if not hasattr(classs, '__slots__') or not classs.__slots__ or classs.__slots__ != gpx_field_names:
        try: slots = classs.__slots__
        except Exception as e: slots = '[Unknown:%s]' % e
        raise Exception('%s __slots__ invalid, found %s, but should be %s' % (classs, slots, gpx_field_names))
