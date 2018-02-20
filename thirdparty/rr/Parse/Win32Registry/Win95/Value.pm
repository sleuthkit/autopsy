package Parse::Win32Registry::Win95::Value;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Value);

use Carp;
use Encode;
use Parse::Win32Registry::Base qw(:all);

use constant RGDB_VALUE_HEADER_LENGTH => 0xc;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift; # offset to RGDB value entry

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    # RGDB Value Entry
    # 0x00 dword = value type
    # 0x04
    # 0x08 word  = value name length
    # 0x0a word  = value data length
    # 0x0c       = value name [for name length bytes]
    #            + value data [for data length bytes]
    # Value type may just be a word, not a dword;
    # following word always appears to be zero.

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $rgdb_value_entry,
                             RGDB_VALUE_HEADER_LENGTH);
    if ($bytes_read != RGDB_VALUE_HEADER_LENGTH) {
        warnf('Could not read RGDB value at 0x%x', $offset);
        return;
    }

    my ($type,
        $name_length,
        $data_length) = unpack('Vx4vv', $rgdb_value_entry);

    $bytes_read = sysread($fh, my $name, $name_length);
    if ($bytes_read != $name_length) {
        warnf('Could not read name for RGDB value at 0x%x', $offset);
        return;
    }
    $name = decode($Parse::Win32Registry::Base::CODEPAGE, $name);

    $bytes_read = sysread($fh, my $data, $data_length);
    if ($bytes_read != $data_length) {
        warnf('Could not read data for RGDB value at 0x%x', $offset);
        return;
    }

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = RGDB_VALUE_HEADER_LENGTH + $name_length + $data_length;
    $self->{_allocated} = 1;
    $self->{_tag} = 'rgdb value';
    $self->{_name} = $name;
    $self->{_name_length} = $name_length;
    $self->{_type} = $type;
    $self->{_data} = $data;
    $self->{_data_length} = $data_length;
    bless $self, $class;

    return $self;
}

sub get_data {
    my $self = shift;

    my $type = $self->get_type;

    my $data = $self->{_data};
    return if !defined $data; # actually, Win95 value data is always defined

    # apply decoding to appropriate data types
    if ($type == REG_DWORD) {
        if (length($data) == 4) {
            $data = unpack('V', $data);
        }
        else {
            # incorrect length for dword data
            $data = undef;
        }
    }
    elsif ($type == REG_DWORD_BIG_ENDIAN) {
        if (length($data) == 4) {
            $data = unpack('N', $data);
        }
        else {
            # incorrect length for dword data
            $data = undef;
        }
    }
    elsif ($type == REG_SZ || $type == REG_EXPAND_SZ) {
        # Snip off any terminating null.
        # Typically, REG_SZ values will not have a terminating null,
        # while REG_EXPAND_SZ values will have a terminating null
        chop $data if substr($data, -1, 1) eq "\0";
    }
    elsif ($type == REG_MULTI_SZ) {
        # Snip off any terminating nulls
        chop $data if substr($data, -1, 1) eq "\0";
        chop $data if substr($data, -1, 1) eq "\0";
        my @multi_sz = split("\0", $data, -1);
        # Make sure there is at least one empty string
        @multi_sz = ('') if @multi_sz == 0;
        return wantarray ? @multi_sz : join($", @multi_sz);
    }

    return $data;
}

sub as_regedit_export {
    my $self = shift;
    my $version = shift || 5;

    my $name = $self->get_name;
    my $export = $name eq '' ? '@=' : '"' . $name . '"=';

    my $type = $self->get_type;

    # XXX
#    if (!defined $self->{_data}) {
#        $name = $name eq '' ? '@' : qq{"$name"};
#        return qq{; $name=(invalid data)\n};
#    }

    if ($type == REG_SZ) {
        $export .= '"' . $self->get_data . '"';
        $export .= "\n";
    }
    elsif ($type == REG_BINARY) {
        $export .= 'hex:';
        $export .= format_octets($self->{_data}, length($export));
    }
    elsif ($type == REG_DWORD) {
        my $data = $self->get_data;
        $export .= defined($data)
            ? sprintf("dword:%08x", $data)
            : "dword:";
        $export .= "\n";
    }
    elsif ($type == REG_EXPAND_SZ || $type == REG_MULTI_SZ) {
        my $data = $version == 4
                 ? $self->{_data} # raw data
                 : encode("UCS-2LE", $self->{_data}); # ansi->unicode
        $export .= sprintf("hex(%x):", $type);
        $export .= format_octets($data, length($export));
    }
    else {
        $export .= sprintf("hex(%x):", $type);
        $export .= format_octets($self->{_data}, length($export));
    }
    return $export;
}

sub parse_info {
    my $self = shift;

    my $info = sprintf '0x%x rgdb value len=0x%x "%s" type=%d data,len=0x%x',
        $self->{_offset},
        $self->{_length},
        $self->{_name},
        $self->{_type},
        $self->{_data_length};
    return $info;
}

1;
