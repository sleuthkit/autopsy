package Parse::Win32Registry::WinNT::Entry;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Entry);

use Carp;
use Parse::Win32Registry::Base qw(:all);
use Parse::Win32Registry::WinNT::Key;
use Parse::Win32Registry::WinNT::Value;
use Parse::Win32Registry::WinNT::Security;

sub new {
    my $class = shift;
    my $regfile = shift;
    my $offset = shift;

    croak 'Missing registry file' if !defined $regfile;
    croak 'Missing offset' if !defined $offset;

    my $fh = $regfile->get_filehandle;

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $entry_header, 8);
    if ($bytes_read != 8) {
        return;
    }

    my ($length,
        $tag) = unpack('Va2', $entry_header);

    my $allocated = 0;
    if ($length > 0x7fffffff) {
        $allocated = 1;
        $length = (0xffffffff - $length) + 1;
    }

    $tag = '' if $tag !~ /(nk|vk|lh|lf|li|ri|sk)/;

    if ($tag eq 'nk') {
        if (my $key = Parse::Win32Registry::WinNT::Key->new($regfile,
                                                            $offset))
        {
            $key->regenerate_path;
            return $key;
        }
    }
    elsif ($tag eq 'vk') {
        if (my $value = Parse::Win32Registry::WinNT::Value->new($regfile,
                                                                $offset))
        {
            return $value;
        }
    }
    elsif ($tag eq 'sk') {
        if (my $value = Parse::Win32Registry::WinNT::Security->new($regfile,
                                                                   $offset))
        {
            return $value;
        }
    }

    my $self = {};
    $self->{_regfile} = $regfile,
    $self->{_offset} = $offset,
    $self->{_length} = $length,
    $self->{_tag} = $tag,
    $self->{_allocated} = $allocated,
    bless $self, $class;

    return $self;
}

sub as_string {
    my $self = shift;

    my $tag = $self->{_tag};
    if ($tag eq 'nk') {
        return '(key entry)';
    }
    elsif ($tag eq 'vk') {
        return '(value entry)';
    }
    elsif ($tag eq 'sk') {
        return '(security entry)';
    }
    elsif ($tag =~ /(lh|lf|li|ri)/) {
        return '(subkey list entry)';
    }
    return '(unidentified entry)';
}

sub parse_info {
    my $self = shift;

    my $tag = $self->{_tag};
    $tag = defined($tag) && $tag ne ''
         ? $tag . ' '
         : '.. ';
    my $info = sprintf '0x%x %slen=0x%x alloc=%d',
        $self->{_offset},
        $tag,
        $self->{_length},
        $self->{_allocated};
    return $info;
}

1;
