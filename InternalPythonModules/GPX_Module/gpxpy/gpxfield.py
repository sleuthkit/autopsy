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
import re as mod_re
import copy as mod_copy

from . import utils as mod_utils


class GPXFieldTypeConverter:
    def __init__(self, from_string, to_string):
        self.from_string = from_string
        self.to_string = to_string


RE_TIMESTAMP = mod_re.compile(
    r'^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})[T ]([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2})'
    r'(\.[0-9]{1,8})?(Z|[+-−][0-9]{2}:?(?:[0-9]{2})?)?$')


class SimpleTZ(mod_datetime.tzinfo):
    __slots__ = ('offset',)

    def __init__(self, s=None):
        self.offset = 0
        if s and len(s) >= 2:
            if s[0] in ('−', '-'):
                mult = -1
                s = s[1:]
            else:
                if s[0] == '+':
                    s = s[1:]
                mult = 1
            hour = int(s[:2]) if s[:2].isdigit() else 0
            if len(s) >= 4:
                minute = int(s[-2:]) if s[-2:].isdigit() else 0
            else:
                minute = 0
            self.offset = mult * (hour * 60 + minute)

    def utcoffset(self, dt):
        return mod_datetime.timedelta(minutes=self.offset)

    def dst(self, dt):
        return mod_datetime.timedelta(0)

    def tzname(self, dt):
        if self.offset == 0:
            return 'Z'
        return '{:02}:{:02}'.format(self.offset // 60, self.offset % 60)

    def __repr__(self):
        return 'SimpleTZ("{}")'.format(self.tzname(None))

    def __eq__(self, other):
        return self.offset == other.offset


def parse_time(string):
    from . import gpx as mod_gpx
    if not string:
        return None
    m = RE_TIMESTAMP.match(string)
    if m:
        dt = [int(m.group(i)) for i in range(1, 7)]
        if m.group(7):
            f = m.group(7)[1:7]
            dt.append(int(f + "0" * (6 - len(f))))
        else:
            dt.append(0)
        dt.append(SimpleTZ(m.group(8)))
        return mod_datetime.datetime(*dt)
    raise mod_gpx.GPXException('Invalid time: {0}'.format(string))


def format_time(time):
    offset = time.utcoffset()
    if not offset or offset == 0:
        tz = 'Z'
    else:
        tz = time.strftime('%z')
    if time.microsecond:
        ms = time.strftime('.%f')
    else:
        ms = ''
    return ''.join((time.strftime('%Y-%m-%dT%H:%M:%S'), ms, tz))



# ----------------------------------------------------------------------------------------------------
# Type converters used to convert from/to the string in the XML:
# ----------------------------------------------------------------------------------------------------


class FloatConverter:
    def __init__(self):
        self.from_string = lambda string : None if string is None else float(string.strip())
        self.to_string =   lambda flt    : mod_utils.make_str(flt)


class IntConverter:
    def __init__(self):
        self.from_string = lambda string: None if string is None else int(string.strip())
        self.to_string = lambda flt: str(flt)


class TimeConverter:
    def from_string(self, string):
        try:
            return parse_time(string)
        except:
            return None

    def to_string(self, time):
        return format_time(time) if time else None


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

    def from_xml(self, node, version):
        raise Exception('Not implemented')

    def to_xml(self, value, version, nsmap):
        raise Exception('Not implemented')


class GPXField(AbstractGPXField):
    """
    Used for to (de)serialize fields with simple field<->xml_tag mapping.
    """
    def __init__(self, name, tag=None, attribute=None, type=None,
                 possible=None, mandatory=None):
        AbstractGPXField.__init__(self)
        self.name = name
        if tag and attribute:
            from . import gpx as mod_gpx
            raise mod_gpx.GPXException('Only tag *or* attribute may be given!')
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

    def from_xml(self, node, version):
        if self.attribute:
            if node is not None:
                result = node.get(self.attribute)
        else:
            __node = node.find(self.tag)
            if __node is not None:
                result = __node.text
            else:
                result = None
        if result is None:
            if self.mandatory:
                from . import gpx as mod_gpx
                raise mod_gpx.GPXException('{0} is mandatory in {1} (got {2})'.format(self.name, self.tag, result))
            return None

        if self.type_converter:
            try:
                result = self.type_converter.from_string(result)
            except Exception as e:
                from . import gpx as mod_gpx
                raise mod_gpx.GPXException('Invalid value for <{0}>... {1} ({2})'.format(self.tag, result, e))

        if self.possible:
            if not (result in self.possible):
                from . import gpx as mod_gpx
                raise mod_gpx.GPXException('Invalid value "{0}", possible: {1}'.format(result, self.possible))

        return result

    def to_xml(self, value, version, nsmap=None, prettyprint=True, indent=''):
        if value is None:
            return ''
        if not prettyprint:
            indent = ''
        if self.attribute:
            return '{0}="{1}"'.format(self.attribute, mod_utils.make_str(value))
        elif self.type_converter:
            value = self.type_converter.to_string(value)
        return mod_utils.to_xml(self.tag, content=value, escape=True,
                                prettyprint=prettyprint, indent=indent)


class GPXComplexField(AbstractGPXField):
    def __init__(self, name, classs, tag=None, is_list=None):
        AbstractGPXField.__init__(self, is_list=is_list)
        self.name = name
        self.tag = tag or name
        self.classs = classs

    def from_xml(self, node, version):
        if self.is_list:
            result = []
            for child in node:
                if child.tag == self.tag:
                    result.append(gpx_fields_from_xml(self.classs, child,
                                                      version))
            return result
        else:
            field_node = node.find(self.tag)
            if field_node is None:
                return None
            return gpx_fields_from_xml(self.classs, field_node, version)

    def to_xml(self, value, version, nsmap=None, prettyprint=True, indent=''):
        if not prettyprint:
            indent = ''
        if self.is_list:
            result = []
            for obj in value:
                result.append(gpx_fields_to_xml(obj, self.tag, version,
                                                nsmap=nsmap,
                                                prettyprint=prettyprint,
                                                indent=indent))
            return ''.join(result)
        else:
            return gpx_fields_to_xml(value, self.tag, version,
                                     prettyprint=prettyprint, indent=indent)


class GPXEmailField(AbstractGPXField):
    """
    Converts GPX1.1 email tag group from/to string.
    """
    def __init__(self, name, tag=None):
        AbstractGPXField.__init__(self, is_list=False)
        self.name = name
        self.tag = tag or name

    def from_xml(self, node, version):
        """
        Extract email address.

        Args:
            node: ETree node with child node containing self.tag
            version: str of the gpx output version "1.0" or "1.1"

        Returns:
            A string containing the email address.
        """
        email_node = node.find(self.tag)
        if email_node is None:
            return ''

        email_id = email_node.get('id')
        email_domain = email_node.get('domain')
        return '{0}@{1}'.format(email_id, email_domain)

    def to_xml(self, value, version, nsmap=None, prettyprint=True, indent=''):
        """
        Write email address to XML

        Args:
            value: str representing an email address
            version: str of the gpx output version "1.0" or "1.1"

        Returns:
            None if value is empty or str of XML representation of the
            address. Representation starts with a \n.
        """
        if not value:
            return ''

        if not prettyprint:
            indent = ''

        if '@' in value:
            pos = value.find('@')
            email_id = value[:pos]
            email_domain = value[pos+1:]
        else:
            email_id = value
            email_domain = 'unknown'

        return ('\n' + indent +
                '<{0} id="{1}" domain="{2}" />'.format(self.tag,
                                                       email_id, email_domain))


class GPXExtensionsField(AbstractGPXField):
    """
    GPX1.1 extensions <extensions>...</extensions> key-value type.
    """
    def __init__(self, name, tag=None, is_list=True):
        AbstractGPXField.__init__(self, is_list=is_list)
        self.name = name
        self.tag = tag or 'extensions'

    def from_xml(self, node, version):
        """
        Build a list of extension Elements.

        Args:
            node: Element at the root of the extensions
            version: unused, only 1.1 supports extensions

        Returns:
            a list of Element objects
        """
        result = []
        extensions_node = node.find(self.tag)
        if extensions_node is None:
            return result
        for child in extensions_node:
            result.append(mod_copy.deepcopy(child))
        return result

    def _resolve_prefix(self, qname, nsmap):
        """
        Convert a tag from Clark notation into prefix notation.

        Convert a tag from Clark notation using the nsmap into a
        prefixed tag. If the tag isn't in Clark notation, return the
        qname back. Converts {namespace}tag -> prefix:tag
        
        Args:
            qname: string with the fully qualified name in Clark notation
            nsmap: a dict of prefix, namespace pairs

        Returns:
            string of the tag ready to be serialized.
        """
        if nsmap is not None and '}' in qname:
            uri, _, localname = qname.partition("}")
            uri = uri.lstrip("{")
            qname = uri + ':' + localname
            for prefix, namespace in nsmap.items():
                if uri == namespace:
                    qname = prefix + ':' + localname
                    break
        return qname

    def _ETree_to_xml(self, node, nsmap=None, prettyprint=True, indent=''):
        """
        Serialize ETree element and all subelements.

        Creates a string of the ETree and all children. The prefixes are
        resolved through the nsmap for easier to read XML.

        Args:
            node: ETree with the extension data
            version: string of GPX version, must be 1.1
            nsmap: dict of prefixes and URIs
            prettyprint: boolean, when true, indent line
            indent: string prepended to tag, usually 2 spaces per level

        Returns:
            string with all the prefixed tags and data for the node
            and its children as XML.

        """
        if not prettyprint:
            indent = ''

        # Build element tag and text
        result = []
        prefixedname = self._resolve_prefix(node.tag, nsmap)
        result.append('\n' + indent + '<' + prefixedname)
        for attrib, value in node.attrib.items():
            attrib = self._resolve_prefix(attrib, nsmap)
            result.append(' {0}="{1}"'.format(attrib, value))
        result.append('>')
        if node.text is not None:
             result.append(node.text.strip())


        # Build subelement nodes
        for child in node:
            result.append(self._ETree_to_xml(child, nsmap,
                                             prettyprint=prettyprint,
                                             indent=indent+'  '))

        # Add tail and close tag
        tail = node.tail
        if tail is not None:
            tail = tail.strip()
        else:
            tail = ''
        if len(node) > 0:
            result.append('\n' + indent)
        result.append('</' + prefixedname + '>' + tail)

        return ''.join(result)

    def to_xml(self, value, version, nsmap=None, prettyprint=True, indent=''):
        """
        Serialize list of ETree.

        Creates a string of all the ETrees in the list. The prefixes are
        resolved through the nsmap for easier to read XML.

        Args:
            value: list of ETrees with the extension data
            version: string of GPX version, must be 1.1
            nsmap: dict of prefixes and URIs
            prettyprint: boolean, when true, indent line
            indent: string prepended to tag, usually 2 spaces per level

        Returns:
            string with all the prefixed tags and data for each node
            as XML.

        """
        if not prettyprint:
            indent = ''
        if not value or version != "1.1":
            return ''
        result = []
        result.append('\n' + indent + '<' + self.tag + '>')
        for extension in value:
            result.append(self._ETree_to_xml(extension, nsmap,
                                             prettyprint=prettyprint,
                                             indent=indent+'  '))
        result.append('\n' + indent + '</' + self.tag + '>')
        return ''.join(result)

# ----------------------------------------------------------------------------------------------------
# Utility methods:
# ----------------------------------------------------------------------------------------------------

def _check_dependents(gpx_object, fieldname):
    """
    Check for data in subelements.

    Fieldname takes the form of 'tag:dep1:dep2:dep3' for an arbitrary
    number of dependents. If all the gpx_object.dep attributes are
    empty, return a sentinel value to suppress serialization of all
    subelements.

    Args:
        gpx_object: GPXField object to check for data
        fieldname: string with tag and dependents delimited with ':'

    Returns:
        Two strings. The first is a sentinel value, '/' + tag, if all
        the subelements are empty and an empty string otherwise. The
        second is the bare tag name.
    """
    if ':' in fieldname:
        children = fieldname.split(':')
        field = children.pop(0)
        for child in children:
            if getattr(gpx_object, child.lstrip('@')):
                return '', field # Child has data
        return '/' + field, field # No child has data
    return '', fieldname # No children

def gpx_fields_to_xml(instance, tag, version, custom_attributes=None,
                      nsmap=None, prettyprint=True, indent=''):
    if not prettyprint:
        indent = ''
    fields = instance.gpx_10_fields
    if version == '1.1':
        fields = instance.gpx_11_fields

    tag_open = bool(tag)
    body = []
    if tag:
        body.append('\n' + indent + '<' + tag)
        if tag == 'gpx':  # write nsmap in root node
            body.append(' xmlns="{0}"'.format(nsmap['defaultns']))
            namespaces = set(nsmap.keys())
            namespaces.remove('defaultns')
            for prefix in sorted(namespaces):
                body.append(
                    ' xmlns:{0}="{1}"'.format(prefix, nsmap[prefix])
                )
        if custom_attributes:
            # Make sure to_xml() always return attributes in the same order:
            for key in sorted(custom_attributes.keys()):
                body.append(' {0}="{1}"'.format(key, mod_utils.make_str(custom_attributes[key])))
    suppressuntil = ''
    for gpx_field in fields:
        # strings indicate non-data container tags with subelements
        if isinstance(gpx_field, str):
            # Suppress empty tags
            if suppressuntil:
                if suppressuntil == gpx_field:
                    suppressuntil = ''
            else:
                suppressuntil, gpx_field = _check_dependents(instance,
                                                             gpx_field)
                if not suppressuntil:
                    if tag_open:
                        body.append('>')
                        tag_open = False
                    if gpx_field[0] == '/':
                        body.append('\n' + indent + '<{0}>'.format(gpx_field))
                        if prettyprint and len(indent) > 1:
                            indent = indent[:-2]
                    else:
                        if prettyprint:
                            indent += '  '
                        body.append('\n' + indent + '<{0}'.format(gpx_field))
                        tag_open = True
        elif not suppressuntil:
            value = getattr(instance, gpx_field.name)
            if gpx_field.attribute:
                body.append(' ' + gpx_field.to_xml(value, version, nsmap,
                                                   prettyprint=prettyprint,
                                                   indent=indent + '  '))
            elif value is not None:
                if tag_open:
                    body.append('>')
                    tag_open = False
                xml_value = gpx_field.to_xml(value, version, nsmap,
                                             prettyprint=prettyprint,
                                             indent=indent + '  ')
                if xml_value:
                    body.append(xml_value)

    if tag:
        if tag_open:
            body.append('>')
        body.append('\n' + indent + '</' + tag + '>')

    return ''.join(body)


def gpx_fields_from_xml(class_or_instance, node, version):
    if mod_inspect.isclass(class_or_instance):
        result = class_or_instance()
    else:
        result = class_or_instance

    fields = result.gpx_10_fields
    if version == '1.1':
        fields = result.gpx_11_fields

    node_path = [node]

    for gpx_field in fields:
        current_node = node_path[-1]
        if isinstance(gpx_field, str):
            gpx_field = gpx_field.partition(':')[0]
            if gpx_field.startswith('/'):
                node_path.pop()
            else:
                if current_node is None:
                    node_path.append(None)
                else:
                    node_path.append(current_node.find(gpx_field))
        else:
            if current_node is not None:
                value = gpx_field.from_xml(current_node, version)
                setattr(result, gpx_field.name, value)
            elif gpx_field.attribute:
                value = gpx_field.from_xml(node, version)
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
##    if not hasattr(classs, '__slots__') or not classs.__slots__ or classs.__slots__ != gpx_field_names:
##        try: slots = classs.__slots__
##        except Exception as e: slots = '[Unknown:%s]' % e
##        raise Exception('%s __slots__ invalid, found %s, but should be %s' % (classs, slots, gpx_field_names))
