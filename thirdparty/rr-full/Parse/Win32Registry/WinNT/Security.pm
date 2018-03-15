package Parse::Win32Registry::WinNT::Security;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Entry);

use Carp;
use Parse::Win32Registry::Base qw(:all);

use constant SK_HEADER_LENGTH => 0x18;
use constant OFFSET_TO_FIRST_HBIN => 0x1000;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift; # offset to sk record relative to start of file

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    if (defined(my $cache = $regfile->{_security_cache})) {
        if (exists $cache->{$offset}) {
            return $cache->{$offset};
        }
    }

    my $fh = $regfile->get_filehandle;

    # 0x00 dword = security length (negative = allocated)
    # 0x04 word  = 'sk' signature
    # 0x08 dword = offset to previous sk
    # 0x0c dword = offset to next sk
    # 0x10 dword = ref count
    # 0x14 dword = length of security descriptor
    # 0x18       = start of security descriptor

    # Extracted offsets are always relative to first hbin

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $sk_header, SK_HEADER_LENGTH);
    if ($bytes_read != SK_HEADER_LENGTH) {
        warnf('Could not read security at 0x%x', $offset);
        return;
    }

    my ($length,
        $sig,
        $offset_to_previous,
        $offset_to_next,
        $ref_count,
        $sd_length,
        ) = unpack('Va2x2VVVV', $sk_header);

    $offset_to_previous += OFFSET_TO_FIRST_HBIN
        if $offset_to_previous != 0xffffffff;
    $offset_to_next += OFFSET_TO_FIRST_HBIN
        if $offset_to_next != 0xffffffff;

    my $allocated = 0;
    if ($length > 0x7fffffff) {
        $allocated = 1;
        $length = (0xffffffff - $length) + 1;
    }
    # allocated should be true

    if ($sig ne 'sk') {
        warnf('Invalid signature for security at 0x%x', $offset);
        return;
    }

    $bytes_read = sysread($fh, my $sd_data, $sd_length);
    if ($bytes_read != $sd_length) {
        warnf('Could not read security descriptor for security at 0x%x',
            $offset);
        return;
    }

    my $sd = unpack_security_descriptor($sd_data);
    if (!defined $sd) {
        warnf('Invalid security descriptor for security at 0x%x',
            $offset);
        # Abandon security object if security descriptor is invalid
        return;
    }

    my $self = {};
    $self->{_regfile} = $regfile;
    $self->{_offset} = $offset;
    $self->{_length} = $length;
    $self->{_allocated} = $allocated;
    $self->{_tag} = $sig;
    $self->{_offset_to_previous} = $offset_to_previous;
    $self->{_offset_to_next} = $offset_to_next;
    $self->{_ref_count} = $ref_count;
    $self->{_security_descriptor_length} = $sd_length;
    $self->{_security_descriptor} = $sd;
    bless $self, $class;

    if (defined(my $cache = $regfile->{_security_cache})) {
        $cache->{$offset} = $self;
    }

    return $self;
}

sub get_previous {
    my $self = shift;
    my $regfile = $self->{_regfile};
    my $offset_to_previous = $self->{_offset_to_previous};

    return Parse::Win32Registry::WinNT::Security->new($regfile,
                                                      $offset_to_previous);
}

sub get_next {
    my $self = shift;
    my $regfile = $self->{_regfile};
    my $offset_to_next = $self->{_offset_to_next};

    return Parse::Win32Registry::WinNT::Security->new($regfile,
                                                      $offset_to_next);
}

sub get_reference_count {
    my $self = shift;

    return $self->{_ref_count};
}

sub get_security_descriptor {
    my $self = shift;

    return $self->{_security_descriptor};
}

sub as_string {
    my $self = shift;

    return '(security entry)';
}

sub parse_info {
    my $self = shift;

    my $info = sprintf '0x%x sk len=0x%x alloc=%d prev=0x%x,next=0x%x refs=%d',
        $self->{_offset},
        $self->{_length},
        $self->{_allocated},
        $self->{_offset_to_previous},
        $self->{_offset_to_next},
        $self->{_ref_count};

    return $info;
}

1;
