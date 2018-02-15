package Parse::Win32Registry::Win95::Key;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Key);

use Carp;
use Parse::Win32Registry::Base qw(:all);
use Parse::Win32Registry::Win95::Value;

use constant RGKN_ENTRY_LENGTH => 0x1c;
use constant OFFSET_TO_RGKN_BLOCK => 0x20;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift; # offset to RGKN key entry relative to start of RGKN
    my $parent_key_path = shift; # parent key path (optional)

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    # RGKN Key Entry
    # 0x00 dword
    # 0x04 dword
    # 0x08 dword
    # 0x0c dword = offset to parent RGKN entry
    # 0x10 dword = offset to first child RGKN entry
    # 0x14 dword = offset to next sibling RGKN entry
    # 0x18 dword = entry id of RGDB entry

    # Extracted offsets are relative to the start of the RGKN block

    # Any offset of 0xffffffff marks the end of a list.
    # An entry id of 0xffffffff means the RGKN entry has no RGDB entry.
    # This occurs for the root key of the registry file.

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $rgkn_entry, RGKN_ENTRY_LENGTH);
    if ($bytes_read != RGKN_ENTRY_LENGTH) {
        warnf('Could not read RGKN key at 0x%x', $offset);
        return;
    }

    my ($offset_to_parent,
        $offset_to_first_child,
        $offset_to_next_sibling,
        $key_id) = unpack('x12VVVV', $rgkn_entry);

    $offset_to_parent += OFFSET_TO_RGKN_BLOCK
        if $offset_to_parent != 0xffffffff;
    $offset_to_first_child += OFFSET_TO_RGKN_BLOCK
        if $offset_to_first_child != 0xffffffff;
    $offset_to_next_sibling += OFFSET_TO_RGKN_BLOCK
        if $offset_to_next_sibling != 0xffffffff;

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = RGKN_ENTRY_LENGTH;
    $self->{_allocated} = 1;
    $self->{_tag} = 'rgkn key';
    $self->{_offset_to_parent} = $offset_to_parent;
    $self->{_offset_to_first_child} = $offset_to_first_child;
    $self->{_offset_to_next_sibling} = $offset_to_next_sibling;
    $self->{_id} = $key_id;
    bless $self, $class;

    # Look up corresponding rgdb entry
    my $index = $regfile->{_rgdb_index};
    croak 'Missing rgdb index' if !defined $index;
    if (exists $index->{$key_id}) {
        my $rgdb_key = $index->{$key_id};
        $self->{_rgdb_key} = $rgdb_key;
        $self->{_name} = $rgdb_key->get_name;
    }
    else {
        $self->{_name} = '';
        # Only the root key should have no matching RGDB entry
        if (!$self->is_root) {
            warnf('Could not find RGDB entry for RGKN key at 0x%x', $offset);
        }
    }

    my $name = $self->{_name};
    $self->{_key_path} = defined($parent_key_path)
                       ? "$parent_key_path\\$name"
                       : $name;

    return $self;
}

sub get_timestamp {
    return undef;
}

sub get_timestamp_as_string {
    return iso8601(undef);
}

sub get_class_name {
    return undef;
}

sub is_root {
    my $self = shift;

    my $offset = $self->{_offset};
    my $regfile = $self->{_regfile};

    my $rgkn_block = $regfile->get_rgkn;
    my $offset_to_root_key = $rgkn_block->{_offset_to_root_key};

    # This gives better results than checking id == 0xffffffff
    return $offset == $offset_to_root_key;
}

sub get_parent {
    my $self = shift;

    my $regfile = $self->{_regfile};
    my $offset_to_parent = $self->{_offset_to_parent};
    my $key_path = $self->{_key_path};

    return if $self->is_root;

    my $grandparent_key_path;
    my @keys = split(/\\/, $key_path, -1);
    if (@keys > 2) {
        $grandparent_key_path = join("\\", @keys[0..$#keys-2]);
    }

    return Parse::Win32Registry::Win95::Key->new($regfile,
                                                 $offset_to_parent,
                                                 $grandparent_key_path);
}

sub get_security {
    return undef;
}

sub as_string {
    my $self = shift;

    return $self->get_path;
}

sub parse_info {
    my $self = shift;

    my $info = sprintf '0x%x rgkn key len=0x%x par=0x%x,child=0x%x,next=0x%x id=0x%x',
        $self->{_offset},
        $self->{_length},
        $self->{_offset_to_parent},
        $self->{_offset_to_first_child},
        $self->{_offset_to_next_sibling},
        $self->{_id};
    return $info;
}

sub get_subkey_iterator {
    my $self = shift;

    my $regfile = $self->{_regfile};
    my $key_path = $self->{_key_path};

    my $offset_to_next_key = $self->{_offset_to_first_child};

    my $end_of_file = $regfile->get_length;
    my $rgkn_block = $regfile->get_rgkn;
    my $end_of_rgkn_block = $rgkn_block->get_offset + $rgkn_block->get_length;

    return Parse::Win32Registry::Iterator->new(sub {
        if ($offset_to_next_key == 0xffffffff) {
            return; # no more subkeys
        }
        if ($offset_to_next_key > $end_of_rgkn_block) {
            return;
        }
        if (my $key = Parse::Win32Registry::Win95::Key->new($regfile,
                                       $offset_to_next_key, $key_path))
        {
            $offset_to_next_key = $key->{_offset_to_next_sibling};
            return $key;
        }
        else {
            return; # no more subkeys
        }
    });
}

sub get_value_iterator {
    my $self = shift;

    my $rgdb_key = $self->{_rgdb_key};
    if (defined $rgdb_key) {
        return $rgdb_key->get_value_iterator;
    }
    else {
        return Parse::Win32Registry::Iterator->new(sub {});
    }
}

1;
