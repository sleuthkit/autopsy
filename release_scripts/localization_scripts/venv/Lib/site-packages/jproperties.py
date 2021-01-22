# jProperties - Java Property file parser and writer for Python
#
# Copyright (c) 2015, Tilman Blumenbach
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
#
# * Redistributions in binary form must reproduce the above copyright notice,
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
#
# * Neither the name of jProperties nor the names of its
#   contributors may be used to endorse or promote products derived from
#   this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

from __future__ import print_function

import codecs
import functools
import itertools
import os
import re
import sys
import time
from collections import MutableMapping, namedtuple

import six


# This represents a combination of a value and metadata for a property key.
PropertyTuple = namedtuple("PropertyTuple", ["data", "meta"])


def _is_runtime_meta(key):
    """
    Check whether a metadata key refers to "runtime metadata" that should
    not be dumped when writing out property files.

    Such keys are those starting with two underscores, e. g. ``__foo``.

    Handles both unicode and byte metadata keys.
    """
    return (
        (isinstance(key, six.text_type)     and key.startswith(u"__")) or
        (isinstance(key, six.binary_type)   and key.startswith(b"__"))
    )


def _escape_non_ascii(unicode_obj):
    """
    Escape non-printable (or non-ASCII) characters using Java-compatible Unicode escape sequences.

    This function is based on code from the JSON library module shipped with Python 2.7.3
    (json/encoder.py, function py_encode_basestring_ascii), which is Copyright (c) 2001, 2002, 2003,
    2004, 2005, 2006 Python Software Foundation; All Rights Reserved. See the file LICENSE included
    with jProperties for the full license terms. If that file is not available, then please see:
    https://www.python.org/download/releases/2.7.3/license/

    Differences to the aforementioned original version of py_encode_basestring_ascii():
      - Always tries to decode str objects as UTF-8, even if they don't contain any UTF-8
        characters.  This is so that we always return an unicode object.
      - Only processes non-printable or non-ASCII characters. Also _always_ replaces these
        characters with Java-compatible Unicode escape sequences (the original function replaced
        e. g. newlines with "\n" etc.).
      - Does not wrap the resulting string in double quotes (").

    :type unicode_obj: unicode
    :param unicode_obj: The source string containing data to escape.
    :rtype : unicode
    :return: A unicode object. This does not contain any non-ASCII characters anymore.
    """
    def replace(match):
        s = match.group(0)
        n = ord(s)
        if n < 0x10000:
            return u'\\u{0:04x}'.format(n)
        else:
            # surrogate pair
            n -= 0x10000
            s1 = 0xd800 | ((n >> 10) & 0x3ff)
            s2 = 0xdc00 | (n & 0x3ff)
            return u'\\u{0:04x}\\u{1:04x}'.format(s1, s2)

    # Just to be sure: If we get passed a str object, then try to decode it as UTF-8.
    if isinstance(unicode_obj, six.binary_type):
        unicode_obj = unicode_obj.decode('utf-8')

    return re.sub(
        six.text_type(r'[^ -~]'),
        replace,
        unicode_obj
    )


@functools.partial(codecs.register_error, "jproperties.jbackslashreplace")
def _jbackslashreplace_error_handler(err):
    """
    Encoding error handler which replaces invalid characters with Java-compliant Unicode escape sequences.

    :param err: An `:exc:UnicodeEncodeError` instance.
    :return: See https://docs.python.org/2/library/codecs.html?highlight=codecs#codecs.register_error
    """
    if not isinstance(err, UnicodeEncodeError):
        raise err

    return _escape_non_ascii(err.object[err.start:err.end]), err.end



def _escape_str(raw_str, only_leading_spaces=False, escape_non_printing=False, line_breaks_only=False):
    """
    Escape a string so that it can safely be written as a key/value to a property file.

    :type raw_str: unicode
    :param raw_str: The string to escape.
    :param only_leading_spaces: Controls which whitespace characters to escape (other illegal, non-whitespace characters
            are always escaped). If True, then only escape a possibly present single leading space character (this is
             used for the value of a key-value pair). If False, escape all whitespace characters.
    :param escape_non_printing: Whether to escape legal, but non-printable ASCII characters as well.
    :param line_breaks_only: Only escape \r, \n and \f and not characters like : and =. Note: This does not
    invalidate/influence the other parameters like only_leading_spaces -- spaces are always escaped as per
    only_leading_spaces.
    :rtype : unicode
    :return: The escaped string.
    """
    # We NEED an unicode object. It's worth a try.
    if isinstance(raw_str, six.binary_type):
        # consider bringing in chardet...
        raw_str = raw_str.decode("utf-8")
    elif not isinstance(raw_str, six.text_type):
        # Last resort: Convert unknown object to a unicode string.
        # This works nicely for integers etc.
        raw_str = six.text_type(raw_str)

    # Do simple whitespace substitutions.
    trans_dict = {
        ord(u"\r"): u"\\r",
        ord(u"\n"): u"\\n",
        ord(u"\f"): u"\\f"
    }

    # Do we want to be conform to the specs fully?
    if not line_breaks_only:
        # Yes, so escape more possibly ambiguous characters as well.
        trans_dict.update(
            {
                ord(u"#"): u"\\#",
                ord(u"!"): u"\\!",
                ord(u"="): u"\\=",
                ord(u":"): u"\\:",
                ord(u"\\"): u"\\\\",
                ord(u"\t"): u"\\t",
            }
        )

    # All right, now we can actually do the substitutions.
    escaped_str = raw_str.translate(trans_dict)

    # Now escape either all space characters or only a possibly present single space at the beginning.
    if not only_leading_spaces:
        escaped_str = escaped_str.replace(u" ", u"\\ ")
    else:
        escaped_str = re.sub(u"^ ", u"\\\\ ", escaped_str)

    # Do we want to escape non-printing characters as well?
    if escape_non_printing:
        escaped_str = _escape_non_ascii(escaped_str)

    return escaped_str


class PropertyError(Exception):
    """Base exception class for all exceptions raised by this module."""
    pass


class ParseError(PropertyError):
    """
    Raised on parse errors in property files.

    :ivar message: The error message (string).
    :ivar line_number: Number of the line where the error occurred (integer).
    :ivar file_obj: The file object we were reading from when the error occurred (may be None).
    """
    def __init__(self, message, line_number, file_obj=None):
        """
        Create a new ParseError exception.

        :param message: Error message.
        :param line_number: Line number of error.
        :param file_obj: File object we were reading from when the error occurred.
        :return: A new :exc:`.ParseError` object.
        """
        self.message = message
        self.line_number = line_number
        self.file_obj = file_obj

    def __str__(self):
        """
        Get a human-readable string representation of this object.

        :return: Human-readable string representation of this object.
        """
        filename = "<unknown>" if not hasattr(self.file_obj, "filename") else self.file_obj.filename

        return "Parse error in %s:%d: %s" % (
            filename,
            self.line_number,
            self.message
        )


class Properties(MutableMapping, object):
    """
    A parser for Java property files.

    This class implements parsing Java property files as defined here:
    http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html#load(java.io.Reader)
    """
    # Line endings/terminators.
    _EOL = "\r\n"

    # Non-line terminator whitespace.
    _WHITESPACE = " \t\f"

    # Which characters do we treat as whitespace?
    _ALLWHITESPACE = _EOL + _WHITESPACE

    def __init__(self, process_escapes_in_values=True, *args, **kwargs):
        """
        Create a new property file parser.

        :param process_escapes_in_values: If False, do not process escape sequences in values when parsing and try hard
        not to produce any escape sequences when writing, i. e. output strings literally. However, some things like
        leading whitespace and newlines are always escaped (since there is not good way around this).
        :return: A new :class:`.Properties`.
        """
        # For cooperative multiple inheritance.
        # noinspection PyArgumentList
        super(Properties, self).__init__(*args, **kwargs)

        self._process_escapes_in_values = process_escapes_in_values

        # Initialize parser state.
        self.reset()
        # Initialize property data.
        self.clear()

    def __len__(self):
        return len(self._properties)

    def __getitem__(self, item):
        if not isinstance(item, six.string_types):
            raise TypeError("Property keys must be of type str or unicode")

        if item not in self._properties:
            raise KeyError("Key not found")

        return PropertyTuple(
            self._properties[item],
            self._metadata.get(item, {})
        )

    def __setitem__(self, key, value):
        if not isinstance(key, six.string_types):
            raise TypeError("Property keys must be of type str or unicode")

        metadata = None
        if isinstance(value, tuple):
            value, metadata = value

        if not isinstance(value, six.string_types):
            raise TypeError("Property values must be of type str or unicode")

        if metadata is not None and not isinstance(metadata, dict):
            raise TypeError("Metadata needs to be a dictionary")

        self._properties[key] = value
        if metadata is not None:
            self._metadata[key] = metadata

    def __delitem__(self, key):
        if not isinstance(key, six.string_types):
            raise TypeError("Property keys must be of type str or unicode")

        if key not in self._properties:
            raise KeyError("Key not found")

        # Remove the property itself.
        del self._properties[key]

        # Remove its metadata as well.
        if key in self._metadata:
            del self._metadata[key]

        # We also no longer need to remember its key order since the property does not exist anymore.
        try:
            self._key_order.remove(key)
        except ValueError:
            pass

    def __iter__(self):
        return self._properties.__iter__()

    @property
    def properties(self):
        return self._properties

    @properties.setter
    def properties(self, value):
        # noinspection PyAttributeOutsideInit
        self._properties = value

    @properties.deleter
    def properties(self):
        # noinspection PyAttributeOutsideInit
        self._properties = {}

    def getmeta(self, key):
        """
        Get the metadata for a key.

        :param key: The key to get metadata for.
        :return: Metadata for the key (always a dictionary, but empty if there is no metadata).
        """
        return self._metadata.get(key, {})

    def setmeta(self, key, metadata):
        """
        Set the metadata for a key.

        If the key starts with "__", then it is added, but will not be output when the file is written.

        :param key: The key to set the metadata for.
        :param metadata: The metadata to set for the key. Needs to be a dictionary.
        :return: None.
        :raise: TypeError if the metadata is not a dictionary.
        """
        if not isinstance(metadata, dict):
            raise TypeError("Metadata needs to be a dictionary")

        self._metadata[key] = metadata

    def _peek(self):
        """
        Peek at the next character in the input stream.

        This implements a lookahead of one character. Use _getc() to actually read (or skip) the
        next character. Repeated calls to this method return the same character unless _getc() is called
        inbetween.

        :return: Next character in input stream.
        :raise: EOFError on EOF. IOError on other errors.
        """
        if self._lookahead is None:
            # No lookahead yet, need to read a char.
            c = self._source_file.read(1)
            if c == "":
                raise EOFError()

            self._lookahead = c

        return self._lookahead

    def _getc(self):
        """
        Read the next character from the input stream and return it.

        To only peek at the next character, use _peek(). Calling _getc() honors a possibly present lookahead
        and clears it.

        :return: Next character in input stream.
        :raise: EOFError on EOF and IOError on other errors.
        """
        c = self._peek()
        self._lookahead = None

        return c

    def _handle_eol(self):
        """
        Skip a line terminator sequence (on of \r, \n or \r\n) and increment the line number counter.

        This method is a no-op if the next character in the input stream is not \r or \n.

        :return: None.
        :raise: EOFError, IOError.
        """
        c = self._peek()
        if c == "\r":
            # Mac or DOS line ending
            self._line_number += 1
            self._getc()

            try:
                if self._peek() == "\n":
                    # DOS line ending. Skip it.
                    self._getc()
            except EOFError:
                pass
        elif c == "\n":
            # UNIX line ending.
            self._line_number += 1
            self._getc()

    def _skip_whitespace(self, stop_at_eol=False):
        """
        Skip all adjacent whitespace in the input stream.

        Properly increments the line number counter when encountering line terminators.

        :param stop_at_eol: Determines whether to skip line terminators as well.
        :return: None
        :raise: EOFError, IOError
        """
        while True:
            c = self._peek()
            if c not in self._ALLWHITESPACE:
                return

            if c in self._EOL:
                if stop_at_eol:
                    return

                # Increment line count.
                self._handle_eol()
            else:
                # Simply skip this whitespace character.
                self._getc()

    def _skip_natural_line(self):
        """Skip a natural line.

        This simply skips all characters until a line terminator sequence is encountered
        (which is skipped as well).

        :return: The text on the line.
        :raise: IOError.
        """
        line = ""

        try:
            while self._peek() not in self._EOL:
                line += self._getc()

            # Increment line count if needed.
            self._handle_eol()
        except EOFError:
            pass

        return line

    def _parse_comment(self):
        """
        Parse a comment line.

        Usually, comment lines are simply skipped, as expected. However, if the character AFTER the comment line
        marker (i. e. the hash or the colon) is a colon, then the line is parsed for a key-value pair (except that
        line continuation is disabled) and the resulting pair is recorded as metadata for the NEXT, real key-value
        pair.

        :return: None.
        :raise: EOFError and IOError.
        """
        # Skip the comment character (hash or colon).
        self._getc()

        # If the next character is a colon, then this is a metadata comment. If not, then we skip this comment.
        if self._peek() != ":":
            docstr = self._skip_natural_line()
            if self._metadoc and self._prev_key:
                prev_metadata = self._metadata.setdefault(self._prev_key, {})
                prev_metadata.setdefault('_doc', "")
                if docstr.startswith(" "):
                   docstr = docstr[1:]
                prev_metadata['_doc'] += docstr + "\n"
            return

        # Skip the metadata marker (the colon).
        self._getc()

        # Now simply treat this line like a normal key-value pair, but with line continuation disabled.
        key = self._parse_key(True)
        value = self._parse_value(True)

        # Special case: Usually property files allow empty keys. For our purposes (metadata), that's rather stupid,
        # so catch this here.
        if not len(key):
            raise ParseError(
                "Empty key in metadata key-value pair",
                self._line_number,
                self._source_file
            )

        # Good, now we can record the metadata. Also, _parse_value() already took care of the EOL after the
        # comment line. Superb. Spectacular!
        self._next_metadata[key] = value

    def _handle_escape(self, allow_line_continuation=True):
        """Handle escape sequences like \r, \n etc.

        Also handles line continuation.

        :param allow_line_continuation: If True, fold multiple natural lines into a single logical line if the line
            terminator is escaped. If False, then ignore line continuation attempts and drop the backslash used to
            escape the line terminator.
        :return: The evaluated escape sequence (string).
        :raise: EOFError and IOError.
        """
        if self._peek() == "\\":
            self._getc()

        try:
            # NB: We use _peek() here so that _handle_eol() can correctly recognize an escaped line terminator sequence
            #     below.
            escaped_char = self._peek()
        except EOFError:
            # Nothing more to read, stray trailing backslash. Drop it.
            return u""

        if escaped_char in self._EOL:
            # \<newline>
            # Line continuation.
            if allow_line_continuation:
                try:
                    # Skip the line terminator
                    self._handle_eol()
                    # Skip whitespace -- but only until the next EOL.
                    self._skip_whitespace(True)
                except EOFError:
                    pass

            return u""

        # Not an escaped line terminator sequence -- need to manually skip the escaped character (since we
        # are not calling _handle_eol() which would do this for us if it were a line terminator sequence).
        self._getc()

        if escaped_char in "rntf":
            # \r, \n, \t or \f.
            return eval(r"u'\%s'" % escaped_char)

        if escaped_char == "u":
            # Unicode escape: \uXXXX.
            start_linenumber = self._line_number

            try:
                # Read the next four characters which MUST be present and make up the rest of the escape sequence.
                codepoint_hex = u""
                for i in range(4):
                    codepoint_hex += self._getc()

                # Decode the hex string to an int.
                codepoint = int(codepoint_hex, base=16)

                # If this is a high surrogate, we need a low surrogate as well, i. e. we expect that there is
                # an immediately following Unicode escape sequence encoding a low surrogate.
                #
                # See: http://unicodebook.readthedocs.io/unicode_encodings.html#utf-16-surrogate-pairs
                if 0xD800 <= codepoint <= 0xDBFF:
                    codepoint2_hex = u""
                    try:
                        for i in range(6):
                            codepoint2_hex += self._getc()
                    except EOFError:
                        pass

                    if codepoint2_hex[:2] != r"\u" or len(codepoint2_hex) != 6:
                        raise ParseError("High surrogate unicode escape sequence not followed by another "
                                         "(low surrogate) unicode escape sequence.", start_linenumber, self._source_file)

                    codepoint2 = int(codepoint2_hex[2:], base=16)
                    if not (0xDC00 <= codepoint2 <= 0xDFFF):
                        raise ParseError("Low surrogate unicode escape sequence expected after high surrogate "
                                         "escape sequence, but got a non-low-surrogate unicode escape sequence.",
                                         start_linenumber, self._source_file)

                    final_codepoint = 0x10000
                    final_codepoint += (codepoint & 0x03FF) << 10
                    final_codepoint += codepoint2 & 0x03FF

                    codepoint = final_codepoint

                return six.unichr(codepoint)
            except (EOFError, ValueError) as e:
                raise ParseError(str(e), start_linenumber, self._source_file)

        # Else it's an unknown escape sequence. Swallow the backslash.
        return escaped_char

    def _parse_key(self, single_line_only=False):
        """Parse and return the key of a key-value pair, possibly split over multiple natural lines.

        :param single_line_only: True to ignore line continuation, False to allow it.
        :rtype : unicode
        :return: The key, which may be empty.
        :raise: IOError, EOFError and ParseError.
        """
        self._skip_whitespace(single_line_only)

        key = u""
        while True:
            try:
                c = self._peek()
            except EOFError:
                break

            if c == "\\":
                # Figure out how we need to handle this escape sequence.
                key += self._handle_escape(not single_line_only)
                continue

            if c in self._ALLWHITESPACE or c in ":=":
                # End of key.
                break

            # Else this character is still part of the key.
            key += self._getc()

        return key

    def _parse_value(self, single_line_only=False):
        """
        Parse and return the value of a key-value pair, possibly split over multiple natural lines.

        :param single_line_only: True to ignore line continuation, False to allow it.
        :rtype : unicode
        :return: The value, which may be an empty string.
        :raise: IOError, EOFError and ParseError.
        """
        # First skip a separator (: or =), if present. It may be surrounded by whitespace.
        try:
            self._skip_whitespace(True)
            if self._peek() in ":=":
                self._getc()
            self._skip_whitespace(True)
        except EOFError:
            # Still no value.
            return u""

        # Now there's definitely a value present.
        # Simply read until the end of the line (or EOF), processing escapes as usual.
        value = u""
        while True:
            try:
                c = self._peek()
            except EOFError:
                break

            if c == "\\" and self._process_escapes_in_values:
                # Figure out how we need to handle this escape sequence.
                value += self._handle_escape(not single_line_only)
                continue

            if c in self._EOL:
                # That's it. We have collected the value.
                self._handle_eol()
                break

            value += self._getc()

        # Done!
        return value

    def _parse_logical_line(self):
        """
        Parse a single logical line which may consist of multiple natural lines.

        Actually this may skip multiple empty natural lines at once and thus "parse" multiple (empty) logical lines
        (since an empty natural line is a single logical line). In practice, this does not matter.

        :return: False if there is nothing more to parse, True if this method should be called again to continue
                 parsing. A return value of False means that EOF was encountered in a non-error state.
        :raise: ParseError.
        """
        # Skip whitespace.
        try:
            self._skip_whitespace()
            c = self._peek()
        except EOFError:
            # Nothing more to parse.
            return False

        # Is this a comment line?
        if c in "!#":
            try:
                self._parse_comment()
            except EOFError:
                # Nothing more to parse.
                return False

            # Comment parsed, that's it for this line.
            return True

        # Now comes the key-value pair.
        try:
            key = self._parse_key()
            value = self._parse_value()
        except EOFError:
            return False

        # Remember the key order.
        if key not in self._properties:
            self._key_order.append(key)

        # That's it, we got the key and the value.
        self._properties[key] = value

        # Were there any preceding metadata comment lines? If yes, then use them as metadata for this key.
        if len(self._next_metadata):
            self._metadata[key] = self._next_metadata
            self._next_metadata = {}
        self._prev_key = key

        return True

    def _parse(self):
        """
        Parse the entire input stream and record parsed key-value pairs.

        :return: None.
        :raise: See :meth:`._parse__parse_logical_line`.
        """
        while self._parse_logical_line():
            pass

    # noinspection PyAttributeOutsideInit
    def reset(self, metadoc=False):
        """
        Reset the parser state so that a new file can be parsed.

        Does not clear the internal property list so that multiple files can parsed consecutively.

        :return: None
        """
        # Current source file.
        self._source_file = None

        # Current line number.
        self._line_number = 1

        # Lookahead of one character used by self._getc().
        self._lookahead = None

        # Parsed metadata for the next key-value pair.
        self._next_metadata = {}

        # To handle comments after the key/value pair as documentation, we
        # need the previous key (to update the associated metadata) and
        # the flag on whether or not this handling should happen.
        self._prev_key = None
        self._metadoc = metadoc

    # noinspection PyAttributeOutsideInit
    def clear(self):
        """
        Remove all properties and related metadata.

        :return: None.
        """
        # The actual properties.
        self._properties = {}

        # Metadata for properties (dict of dicts).
        self._metadata = {}

        # Key order. Populated when parsing so that key order can be preserved when writing the data back.
        self._key_order = []

    def load(self, source_data, encoding="iso-8859-1", metadoc=False):
        """
        Load, decode and parse an input stream (or string).

        :param source_data: Input data to parse. May be a :class:`str` which will be decoded according to
                `encoding`, a :class:`unicode` object or a file-like object. In the last case, if `encoding`
                is None, the file-like object is expected to provide transparent decoding to :class:`unicode`
                (see :func:`codecs.open`); otherwise, if `encoding` is not None, then data from the file-like
                object will be decoded using that encoding.
        :param encoding: If `source_data` is a :class:`str`, this specifies what encoding should be used to decode it.
        :return: None
        :raise: IOError, EOFError, ParseError, UnicodeDecodeError (if source_data needs to be decoded),
                 LookupError (if encoding is unknown).
        """
        self.reset(metadoc)

        if isinstance(source_data, six.binary_type):
            # Byte string. Need to decode.
            self._source_file = six.StringIO(source_data.decode(encoding))
        elif isinstance(source_data, six.text_type):
            # No need to decode.
            self._source_file = six.StringIO(source_data)
        elif encoding is not None:
            # We treat source_data as a file-like object and wrap it with a StreamReader
            # for the requested encoding so that we don't need to str.decode() the data manually.
            self._source_file = codecs.getreader(encoding)(source_data)
        else:
            # Else source_data should be a file-like object providing transparent decoding,
            # i. e. a file opened with codecs.open().
            #
            # noinspection PyAttributeOutsideInit
            self._source_file = source_data

        self._parse()

    def store(self, out_stream, initial_comments=None, encoding="iso-8859-1", strict=True, strip_meta=True,
              timestamp=True):
        """
        Write a plain text representation of the properties to a stream.

        :param out_stream: The target stream where the data should be written. Should be opened in binary mode.
        :type initial_comments: unicode
        :param initial_comments: A string to output as an initial comment (a commented "header"). May safely contain
                unescaped line terminators.
        :param encoding: The encoding to write the data in.
        :param strict: Set to True to exactly behave like the Java property file writer. In particular, this will cause
            any non-printing characters in property keys and values to be escaped using "<BACKSLASH>uXXXX" escape
            sequences if the encoding is set to iso-8859-1. False causes sane behaviour, i. e. only use unicode escape
            sequences if the characters cannot be represented in the target encoding.
        :param strip_meta: Whether to strip metadata when writing.
        :param timestamp: True to write a comment line with the current time and date after the initial comments.
        :return: None.
        :raise: LookupError (if encoding is unknown), IOError, UnicodeEncodeError (if data cannot be encoded as
                 `encoding`.
        """
        # Wrap the stream in an EncodedFile so that we don't need to always call str.encode().
        out_codec_info = codecs.lookup(encoding)
        wrapped_out_stream = out_codec_info.streamwriter(
            out_stream,
            "jproperties.jbackslashreplace"
        )
        properties_escape_nonprinting = strict and out_codec_info == codecs.lookup("latin_1")

        # Print initial comment line(s), if provided.
        if initial_comments is not None:
            # Normalize line endings.
            initial_comments = re.sub(
                r"(\r\n|\r)",
                "\n",
                initial_comments
            )

            # Embedded line terminator sequences in initial_comments need to be replaced by
            # \n<hash sign> to correctly yield multiple comment lines.
            initial_comments = re.sub(
                r"\n(?![#!])",
                "\n#",
                initial_comments
            )

            # Make sure that no line of the initial comment is accidentally treated as a metadata comment.
            # Because we already replaced all line terminator sequences in the comment with the sequence "\n#" and also
            # normalized the line endings, we simply need to match on all occurrences of "\n#:" or "\n::".
            initial_comments = re.sub(
                r"(\n[#!]):",
                r"\g<1>\:",
                initial_comments
            )

            print(u"#" + initial_comments, file=wrapped_out_stream)

        if timestamp:
            # Print a comment line with the current time and date.
            # Yes, this is ugly, but we need to print an ENGLISH string. It can't be locale-dependent.
            day_of_week = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
            month = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
            now = time.gmtime()
            print(
                u"#%s %s %02d %02d:%02d:%02d UTC %04d" % (
                    day_of_week[now.tm_wday],
                    month[now.tm_mon - 1],
                    now.tm_mday,
                    now.tm_hour,
                    now.tm_min,
                    now.tm_sec,
                    now.tm_year
                ),
                file=wrapped_out_stream
            )

        # Now come the properties themselves.
        #
        # We want to make sure to restore the original key order (i. e. the order in which the keys were read in from a
        # file), but we need to be careful not to overlook keys which haven't been read from a file (they may have been
        # added programmatically by the "user" of this class; obviously not every key-value pair we know of needs to
        # come from a file).
        #
        # We do this by putting all the keys we know in a set. Then, we do the same for the keys in self._key_order
        # (i. e. those which have an explicit order). Finally, we take the difference of those two sets and order the
        # resulting unordered keys alphabetically, appending them to the ordered keys. This at least gives a somewhat
        # nice initial ordering.
        #
        # Admittedly, this is not very space efficient.
        unordered_keys = set(self._properties)
        ordered_keys = set(self._key_order)
        unordered_keys -= ordered_keys

        unordered_keys_xs = list(unordered_keys)
        unordered_keys_xs.sort()

        for key in itertools.chain(self._key_order, unordered_keys_xs):
            if key in self._properties:
                # First, write the metadata.
                metadata = self.getmeta(key)
                if not strip_meta and len(metadata):
                    for mkey in sorted(metadata):
                        if _is_runtime_meta(mkey):
                            continue

                        print(
                            u"#: %s=%s" % (
                                _escape_str(mkey),
                                _escape_str(metadata[mkey], True)
                            ),
                            file=wrapped_out_stream
                        )

                # Now write the key-value pair itself.
                print(
                    u"%s=%s" % (
                        _escape_str(
                            key,
                            escape_non_printing=properties_escape_nonprinting
                        ),
                        _escape_str(
                            self._properties[key],
                            True,
                            escape_non_printing=properties_escape_nonprinting,
                            line_breaks_only=not self._process_escapes_in_values
                        )
                    ),
                    file=wrapped_out_stream
                )


    def list(self, out_stream=sys.stderr):
        """
        Debugging method: Print an unsorted list of properties to `out_stream`.
        :param out_stream: Where to print the property list.
        :return: None
        """
        print("-- listing properties --", file=out_stream)
        for key in self._properties:
            msg = "%s=%s" % (key, self._properties[key])
            print(msg, file=out_stream)


def main():
    """
    Main function for interactive use.

    Loads a property file and dumps it to stdout, optionally converting between encodings.
    Escape sequence handling in property values can also be disabled (it is enabled by default).

    Reads parameters from `sys.argv`:

    - Input file path.
    - Optional: Input file encoding, defaults to `utf-8`.
    - Optional: Output encoding, defaults to `utf-8`.
    - Optional: `false` to take escape sequences in property values literally instead of parsing them.
      This will also try hard not to output any escape sequences in values and thus will output everything
      literally, as long as this does not lead to invalid output.

    :rtype: int
    :return: 0 on success, non-zero on error.
    """
    prog_name = os.path.basename(sys.argv[0])

    if len(sys.argv) < 2 or sys.argv[1] == "-h":
        print("Loads a property file and dumps it to stdout, optionally converting between encodings.", file=sys.stderr)
        print("Escape sequence handling can also be disabled (it is enabled by default).\n", file=sys.stderr)
        print("Usage:", file=sys.stderr)
        print(" %s [-h] input_file [input_encoding [output_encoding [process_escapes]]]\n" % \
            prog_name, file=sys.stderr)
        print("The input and output encodings default to `utf-8'. If `false' is given for the", file=sys.stderr)
        print("`process_escapes' parameter, then escape sequences in property values are taken", file=sys.stderr)
        print("literally, i. e. they are treated like normal characters. This will also cause", file=sys.stderr)
        print("the writer to try hard not to output any escape sequences in values and thus it", file=sys.stderr)
        print("will output everything literally, as long as this does not lead to invalid output.", file=sys.stderr)

        return 1

    in_enc = "utf-8" if len(sys.argv) < 3 else sys.argv[2]
    out_enc = "utf-8" if len(sys.argv) < 4 else sys.argv[3]
    process_escapes_in_values = len(sys.argv) < 5 or sys.argv[4].lower() != "false"

    try:
        f = open(sys.argv[1], "rb")
        p = Properties(process_escapes_in_values)
        p.load(f, in_enc)
    except (IOError, EOFError, ParseError, UnicodeDecodeError, LookupError) as e:
        print("Error: Could not read input file:", e, file=sys.stderr)
        return 2

    try:
        p.store(
            sys.stdout.buffer if six.PY3 else sys.stdout,
            u"File generated by %s (escapes in values: %s)" % (
                prog_name, process_escapes_in_values
            ),
            out_enc,
            False
        )
    except (LookupError, IOError, UnicodeEncodeError) as e:
        print("Error: Could not dump properties:", e, file=sys.stderr)
        return 3

    return 0


if __name__ == '__main__':
    sys.exit(main())

# vim:set fileencoding=utf-8:
