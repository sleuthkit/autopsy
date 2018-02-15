package Parse::Win32Registry::Win95::File;

use strict;
use warnings;

use base qw(Parse::Win32Registry::File);

use Carp;
use File::Basename;
use Parse::Win32Registry::Base qw(:all);
use Parse::Win32Registry::Win95::Key;

use constant CREG_HEADER_LENGTH => 0x20;
use constant OFFSET_TO_RGKN_BLOCK => 0x20;

sub new {
    my $class = shift;
    my $filename = shift or croak 'No filename specified';

    open my $fh, '<', $filename or croak "Unable to open '$filename': $!";

    # CREG Header
    # 0x00 dword = 'CREG' signature
    # 0x04
    # 0x08 dword = offset to first rgdb block
    # 0x0c
    # 0x10 word  = number of rgdb blocks

    my $bytes_read = sysread($fh, my $creg_header, CREG_HEADER_LENGTH);
    if ($bytes_read != CREG_HEADER_LENGTH) {
        warnf('Could not read registry file header');
        return;
    }

    my ($creg_sig,
        $offset_to_first_rgdb_block,
        $num_rgdb_blocks) = unpack('a4x4Vx4v', $creg_header);

    if ($creg_sig ne 'CREG') {
        warnf('Invalid registry file signature');
        return;
    }

    my $self = {};
    $self->{_filehandle} = $fh;
    $self->{_filename} = $filename;
    $self->{_length} = (stat $fh)[7];
    $self->{_offset_to_first_rgdb_block} = $offset_to_first_rgdb_block;
    $self->{_num_rgdb_blocks} = $num_rgdb_blocks;
    bless $self, $class;

    # get_rgkn will cache the rgkn block for subsequent calls
    my $rgkn_block = $self->get_rgkn;
    return if !defined $rgkn_block; # warning will already have been made

    # Index the rgdb entries by id for faster look up
    $self->_index_rgdb_entries;

    return $self;
}

sub get_timestamp {
    return undef;
}

sub get_timestamp_as_string {
    return iso8601(undef);
}

sub get_embedded_filename {
    return undef;
}

sub get_root_key {
    my $self = shift;

    return $self->get_rgkn->get_root_key;
}

sub get_virtual_root_key {
    my $self = shift;
    my $fake_root = shift;

    my $root_key = $self->get_root_key;
    return if !defined $root_key;

    if (!defined $fake_root) {
        # guess virtual root from filename
        my $filename = basename $self->{_filename};

        if ($filename =~ /USER/i) {
            $fake_root = 'HKEY_USERS';
        }
        elsif ($filename =~ /SYSTEM/i) {
            $fake_root = 'HKEY_LOCAL_MACHINE';
        }
        else {
            $fake_root = 'HKEY_UNKNOWN';
        }
    }

    $root_key->{_name} = $fake_root;
    $root_key->{_key_path} = $fake_root;

    return $root_key;
}

sub _index_rgdb_entries {
    my $self = shift;

    my %index = ();

    # Build index of rgdb key entries
    # Entries are only included if $key_block_num matches $rgdb_block_num
    my $rgdb_block_num = 0;
    my $rgdb_iter = $self->get_rgdb_iterator;
    while (my $rgdb = $rgdb_iter->()) {
        my $rgdb_key_iter = $rgdb->get_key_iterator;
        while (my $rgdb_key = $rgdb_key_iter->()) {
            my $key_id = $rgdb_key->{_id};
            my $key_block_num = $key_id >> 16;
            if ($rgdb_block_num == $key_block_num) {
                $index{$key_id} = $rgdb_key;
            }
        }
        $rgdb_block_num++;
    }

    $self->{_rgdb_index} = \%index;
}

sub _dump_rgdb_index {
    my $self = shift;

    my $rgdb_index = $self->{_rgdb_index};

    foreach my $key_id (sort { $a <=> $b } keys %$rgdb_index) {
        my $rgdb_key = $rgdb_index->{$key_id};
        printf qq{id=0x%x 0x%x,%d/%d "%s" vals=%d\n},
            $key_id,
            $rgdb_key->{_offset},
            $rgdb_key->{_length_used},
            $rgdb_key->{_length},
            $rgdb_key->{_name},
            $rgdb_key->{_num_values};
    }
}

sub get_rgkn {
    my $self = shift;

    # Return cached rgkn block if present
    if (defined $self->{_rgkn}) {
        return $self->{_rgkn};
    }

    my $offset = OFFSET_TO_RGKN_BLOCK;
    my $rgkn_block = Parse::Win32Registry::Win95::RGKN->new($self, $offset);
    $self->{_rgkn} = $rgkn_block;
    return $rgkn_block;
}

sub get_rgdb_iterator {
    my $self = shift;

    my $offset_to_next_rgdb_block = $self->{_offset_to_first_rgdb_block};
    my $num_rgdb_blocks = $self->{_num_rgdb_blocks};

    my $end_of_file = $self->{_length};

    my $rgdb_block_num = 0;

    return Parse::Win32Registry::Iterator->new(sub {
        if ($offset_to_next_rgdb_block > $end_of_file) {
            return; # no more rgdb blocks
        }
        if ($rgdb_block_num >= $num_rgdb_blocks) {
            return; # no more rgdb blocks
        }
        $rgdb_block_num++;
        if (my $rgdb_block = Parse::Win32Registry::Win95::RGDB->new($self,
                                               $offset_to_next_rgdb_block))
        {
            return unless $rgdb_block->get_length > 0;
            $offset_to_next_rgdb_block += $rgdb_block->get_length;
            return $rgdb_block;
        }
    });
}

sub get_block_iterator {
    my $self = shift;

    my $rgdb_iter;

    return Parse::Win32Registry::Iterator->new(sub {
        if (!defined $rgdb_iter) {
            $rgdb_iter = $self->get_rgdb_iterator;
            return $self->get_rgkn;
        }
        return $rgdb_iter->();
    });
}

*get_hbin_iterator = \&get_block_iterator;


package Parse::Win32Registry::Win95::RGKN;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Entry);

use Carp;
use Parse::Win32Registry::Base qw(:all);

use constant RGKN_HEADER_LENGTH => 0x20;
use constant OFFSET_TO_RGKN_BLOCK => 0x20;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift || OFFSET_TO_RGKN_BLOCK;

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    # RGKN Block Header
    # 0x0 dword = 'RGKN' signature
    # 0x4 dword = length of rgkn block
    # 0x8 dword = offset to root key entry (relative to start of rgkn block)

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $rgkn_header, RGKN_HEADER_LENGTH);
    if ($bytes_read != RGKN_HEADER_LENGTH) {
        warnf('Could not read RGKN header at 0x%x', $offset);
        return;
    }

    my ($sig,
        $rgkn_block_length,
        $offset_to_root_key) = unpack('a4VV', $rgkn_header);

    if ($sig ne 'RGKN') {
        warnf('Invalid RGKN block signature at 0x%x', $offset);
        return;
    }

    $offset_to_root_key += $offset;

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = $rgkn_block_length;
    $self->{_header_length} = RGKN_HEADER_LENGTH;
    $self->{_allocated} = 1;
    $self->{_tag} = 'rgkn block';
    $self->{_offset_to_root_key} = $offset_to_root_key;
    bless $self, $class;

    return $self;
}

sub get_root_key {
    my $self = shift;

    my $regfile = $self->{_regfile};
    my $offset_to_root_key = $self->{_offset_to_root_key};

    my $root_key = Parse::Win32Registry::Win95::Key->new($regfile,
                                                         $offset_to_root_key);
    return $root_key;
}

sub get_entry_iterator {
    my $self = shift;

    my $root_key = $self->get_root_key;

    # In the unlikely event there is no root key, return an empty iterator
    if (defined $root_key) {
        return $root_key->get_subtree_iterator;
    }
    else {
        return Parse::Win32Registry::Iterator->new(sub {});
    }
}


package Parse::Win32Registry::Win95::RGDB;

use base qw(Parse::Win32Registry::Entry);

use Carp;
use Parse::Win32Registry::Base qw(:all);

use constant RGDB_HEADER_LENGTH => 0x20;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift;

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    # RGDB Block Header
    # 0x0 dword = 'RDGB' signature
    # 0x4 dword = length of rgdb block

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $rgdb_header, RGDB_HEADER_LENGTH);
    if ($bytes_read != RGDB_HEADER_LENGTH) {
        return;
    }

    my ($sig,
        $rgdb_block_length) = unpack('a4V', $rgdb_header);

    if ($sig ne 'RGDB') {
        return;
    }

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = $rgdb_block_length;
    $self->{_header_length} = RGDB_HEADER_LENGTH;
    $self->{_allocated} = 1;
    $self->{_tag} = 'rgdb block';
    bless $self, $class;

    return $self;
}

sub get_key_iterator {
    my $self = shift;

    my $regfile = $self->{_regfile};
    my $offset = $self->{_offset};
    my $length = $self->{_length};

    my $offset_to_next_rgdb_key = $offset + RGDB_HEADER_LENGTH;
    my $end_of_rgdb_block = $offset + $length;

    return Parse::Win32Registry::Iterator->new(sub {
        if ($offset_to_next_rgdb_key >= $end_of_rgdb_block) {
            return;
        }
        if (my $rgdb_key = Parse::Win32Registry::Win95::RGDBKey->new($regfile,
                                                     $offset_to_next_rgdb_key))
        {
            return unless $rgdb_key->get_length > 0;
            $offset_to_next_rgdb_key += $rgdb_key->get_length;

            # Check rgdb key has not run past end of rgdb block
            if ($offset_to_next_rgdb_key > $end_of_rgdb_block) {
                return;
            }
            return $rgdb_key;
        }
    });
}

sub get_entry_iterator {
    my $self = shift;

    my $value_iter;
    my $key_iter = $self->get_key_iterator;

    return Parse::Win32Registry::Iterator->new(sub {
        if (defined $value_iter) {
            my $value = $value_iter->();
            if (defined $value) {
                return $value;
            }
        }

        my $key = $key_iter->();
        if (!defined $key) {
            return; # key iterator finished
        }

        $value_iter = $key->get_value_iterator;
        return $key;
    });
}


package Parse::Win32Registry::Win95::RGDBKey;

use base qw(Parse::Win32Registry::Entry);

use Carp;
use Encode;
use Parse::Win32Registry::Base qw(:all);

use constant RGDB_ENTRY_HEADER_LENGTH => 0x14;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift;

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    # RGDB Key Entry
    # 0x00 dword = length of rgdb entry / offset to next rgdb entry
    #              (this length includes any following value entries)
    # 0x04 dword = id (top word = block num, bottom word = id)
    # 0x08 dword = bytes used (unpacked, but not used)
    # 0x0c word  = key name length
    # 0x0e word  = number of values
    # 0x10 dword
    # 0x14       = key name [for key name length bytes]
    # followed immediately by any RGDB Value Entries belonging to this key

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $rgdb_key_entry, RGDB_ENTRY_HEADER_LENGTH);
    if ($bytes_read != RGDB_ENTRY_HEADER_LENGTH) {
        return;
    }

    my ($length,
        $key_id,
        $length_used,
        $name_length,
        $num_values) = unpack('VVVvv', $rgdb_key_entry);

    $bytes_read = sysread($fh, my $name, $name_length);
    if ($bytes_read != $name_length) {
        return;
    }
    $name = decode($Parse::Win32Registry::Base::CODEPAGE, $name);

    # Calculate the length of the entry's key header
    my $header_length = RGDB_ENTRY_HEADER_LENGTH + $name_length;

    # Check for invalid/unused entries
    if ($key_id == 0xffffffff || $length_used == 0xffffffff
                              || $header_length > $length)
    {
        $name = '';
        $header_length = RGDB_ENTRY_HEADER_LENGTH;
    }

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = $length;
    $self->{_length_used} = $length_used;
    $self->{_header_length} = $header_length;
    $self->{_allocated} = 1;
    $self->{_tag} = 'rgdb key';
    $self->{_id} = $key_id;
    $self->{_name} = $name;
    $self->{_name_length} = $name_length;
    $self->{_num_values} = $num_values;
    bless $self, $class;

    return $self;
}

sub get_name {
    my $self = shift;

    return $self->{_name};
}

sub parse_info {
    my $self = shift;

    my $info = sprintf '0x%x rgdb key len=0x%x/0x%x "%s" id=0x%x vals=%d',
        $self->{_offset},
        $self->{_length_used},
        $self->{_length},
        $self->{_name},
        $self->{_id},
        $self->{_num_values};

    return $info;
}

sub get_value_iterator {
    my $self = shift;

    my $regfile = $self->{_regfile};

    my $num_values_remaining = $self->{_num_values};

    my $offset = $self->{_offset};

    # offset_to_next_rgdb_value can only be set to a valid offset
    # if num_values_remaining > 0
    my $offset_to_next_rgdb_value = 0xffffffff;
    if ($num_values_remaining > 0) {
        $offset_to_next_rgdb_value = $offset
                                   + $self->{_header_length};
    }

    my $end_of_rgdb_key = $offset + $self->{_length};

    # don't attempt to return values if id is invalid...
    if ($self->{_id} == 0xffffffff) {
        $num_values_remaining = 0;
    }

    return Parse::Win32Registry::Iterator->new(sub {
        if ($num_values_remaining-- <= 0) {
            return;
        }
        if ($offset_to_next_rgdb_value == 0xffffffff) {
            return;
        }
        if ($offset_to_next_rgdb_value > $end_of_rgdb_key) {
            return;
        }
        if (my $value = Parse::Win32Registry::Win95::Value->new($regfile,
                                              $offset_to_next_rgdb_value))
        {
            return unless $value->get_length > 0;
            $offset_to_next_rgdb_value += $value->get_length;
            return $value;
        }
        else {
            return; # no more values
        }
    });
}

1;

