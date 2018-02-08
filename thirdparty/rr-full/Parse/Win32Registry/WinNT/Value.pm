package Parse::Win32Registry::WinNT::Value;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Value);

use Carp;
use Encode;
use Parse::Win32Registry::Base qw(:all);

use constant VK_HEADER_LENGTH => 0x18;
use constant OFFSET_TO_FIRST_HBIN => 0x1000;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift; # offset to vk record relative to first hbin

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    # 0x00 dword = value length (negative = allocated)
    # 0x04 word  = 'vk' signature
    # 0x06 word  = value name length
    # 0x08 dword = value data length (bit 31 set => data stored inline)
    # 0x0c dword = offset to data/inline data
    # 0x10 dword = value type
    # 0x14 word  = flags (bit 1 set => compressed name)
    # 0x16 word
    # 0x18       = value name [for value name length bytes]

    # Extracted offsets are always relative to first hbin

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $vk_header, VK_HEADER_LENGTH);
    if ($bytes_read != VK_HEADER_LENGTH) {
        warnf('Could not read value at 0x%x', $offset);
        return;
    }

    my ($length,
        $sig,
        $name_length,
        $data_length,
        $offset_to_data,
        $type,
        $flags,
        ) = unpack('Va2vVVVv', $vk_header);

    my $allocated = 0;
    if ($length > 0x7fffffff) {
        $allocated = 1;
        $length = (0xffffffff - $length) + 1;
    }
    # allocated should be true

    if ($length < VK_HEADER_LENGTH) {
        warnf('Invalid value entry length at 0x%x', $offset);
        return;
    }

    if ($sig ne 'vk') {
        warnf('Invalid signature for value at 0x%x', $offset);
        return;
    }

    $bytes_read = sysread($fh, my $name, $name_length);
    if ($bytes_read != $name_length) {
        warnf('Could not read name for value at 0x%x', $offset);
        return;
    }

    if ($flags & 1) {
        $name = decode($Parse::Win32Registry::Base::CODEPAGE, $name);
    }
    else {
        $name = decode('UCS-2LE', $name);
    };

    # If the top bit of the data_length is set, then
    # the value is inline and stored in the offset to data field (at 0xc).
    my $data;
    my $data_inline = $data_length >> 31;
    if ($data_inline) {
        # REG_DWORDs are always inline, but I've also seen
        # REG_SZ, REG_BINARY, REG_EXPAND_SZ, and REG_NONE inline
        $data_length &= 0x7fffffff;
        if ($data_length > 4) {
            warnf("Invalid inline data length for value '%s' at 0x%x",
                $name, $offset);
            $data = undef;
        }
        else {
            # unpack inline data from header
            $data = substr($vk_header, 0xc, $data_length);
        }
    }
    else {
        if ($offset_to_data != 0 && $offset_to_data != 0xffffffff) {
            $offset_to_data += OFFSET_TO_FIRST_HBIN;
            if ($offset_to_data < ($regfile->get_length - $data_length)) {
                $data = _extract_data($fh, $offset_to_data, $data_length);
            }
            else {
                warnf("Invalid offset to data for value '%s' at 0x%x",
                    $name, $offset);
            }
        }
    }

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = $length;
    $self->{_allocated} = $allocated;
    $self->{_tag} = $sig;
    $self->{_name} = $name;
    $self->{_name_length} = $name_length;
    $self->{_type} = $type;
    $self->{_data} = $data;
    $self->{_data_length} = $data_length;
    $self->{_data_inline} = $data_inline;
    $self->{_offset_to_data} = $offset_to_data;
    $self->{_flags} = $flags;
    bless $self, $class;

    return $self;
}

sub _extract_data {
    my $fh = shift;
    my $offset_to_data = shift;
    my $data_length = shift;

    if ($offset_to_data == 0 || $offset_to_data == 0xffffffff) {
        return undef;
    }

    sysseek($fh, $offset_to_data, 0);
    my $bytes_read = sysread($fh, my $data_header, 4);
    if ($bytes_read != 4) {
        warnf('Could not read data at 0x%x', $offset_to_data);
        return undef;
    }

    my ($max_data_length) = unpack('V', $data_header);

    my $data_allocated = 0;
    if ($max_data_length > 0x7fffffff) {
        $data_allocated = 1;
        $max_data_length = (0xffffffff - $max_data_length) + 1;
    }
    # data_allocated should be true

    my $data;

    if ($data_length > $max_data_length) {
        $bytes_read = sysread($fh, my $db_entry, 8);
        if ($bytes_read != 8) {
            warnf('Could not read data at 0x%x', $offset_to_data);
            return undef;
        }

        my ($sig, $num_data_blocks, $offset_to_data_block_list)
            = unpack('a2vV', $db_entry);
        if ($sig ne 'db') {
            warnf('Invalid signature for big data at 0x%x', $offset_to_data);
            return undef;
        }
        $offset_to_data_block_list += OFFSET_TO_FIRST_HBIN;

        sysseek($fh, $offset_to_data_block_list + 4, 0);
        $bytes_read = sysread($fh, my $data_block_list, $num_data_blocks * 4);
        if ($bytes_read != $num_data_blocks * 4) {
            warnf('Could not read data block list at 0x%x',
                $offset_to_data_block_list);
            return undef;
        }

        $data = "";
        my @offsets = map { OFFSET_TO_FIRST_HBIN + $_ }
            unpack("V$num_data_blocks", $data_block_list);
        foreach my $offset (@offsets) {
            sysseek($fh, $offset, 0);
            $bytes_read = sysread($fh, my $block_header, 4);
            if ($bytes_read != 4) {
                warnf('Could not read data block at 0x%x', $offset);
                return undef;
            }
            my ($block_length) = unpack('V', $block_header);
            if ($block_length > 0x7fffffff) {
                $block_length = (0xffffffff - $block_length) + 1;
            }
            $bytes_read = sysread($fh, my $block_data, $block_length - 8);
            if ($bytes_read != $block_length - 8) {
                warnf('Could not read data block at 0x%x', $offset);
                return undef;
            }
            $data .= $block_data;
        }
        if (length($data) < $data_length) {
            warnf("Insufficient data blocks for data at 0x%x", $offset_to_data);
            return undef;
        }
        $data = substr($data, 0, $data_length);
        return $data;
    }
    else {
        $bytes_read = sysread($fh, $data, $data_length);
        if ($bytes_read != $data_length) {
            warnf("Could not read data at 0x%x", $offset_to_data);
            return undef;
        }
    }
    return $data;
}

sub get_data {
    my $self = shift;

    my $type = $self->get_type;

    my $data = $self->{_data};
    return if !defined $data;

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
        $data = decode('UCS-2LE', $data);
        # snip off any terminating null
        chop $data if substr($data, -1, 1) eq "\0";
    }
    elsif ($type == REG_MULTI_SZ) {
        $data = decode('UCS-2LE', $data);
        # snip off any terminating nulls
        chop $data if substr($data, -1, 1) eq "\0";
        chop $data if substr($data, -1, 1) eq "\0";
        my @multi_sz = split("\0", $data, -1);
        # make sure there is at least one empty string
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
        $export .= "hex:";
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
                 ? encode("ascii", $self->{_data}) # unicode->ascii
                 : $self->{_data}; # raw data
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

    my $info = sprintf '0x%x vk len=0x%x alloc=%d "%s" type=%d',
        $self->{_offset},
        $self->{_length},
        $self->{_allocated},
        $self->{_name},
        $self->{_type};
    if ($self->{_data_inline}) {
        $info .= sprintf ' data=inline,len=0x%x',
            $self->{_data_length};
    }
    else {
        $info .= sprintf ' data=0x%x,len=0x%x',
            $self->{_offset_to_data},
            $self->{_data_length};
    }
    return $info;
}

1;
