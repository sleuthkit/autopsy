package Parse::Win32Registry::Entry;

use strict;
use warnings;

use Carp;
use Parse::Win32Registry::Base qw(:all);

sub get_regfile {
    my $self = shift;

    return $self->{_regfile};
}

sub get_offset {
    my $self = shift;

    return $self->{_offset};
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}

sub is_allocated {
    my $self = shift;

    return $self->{_allocated};
}

sub get_tag {
    my $self = shift;

    return $self->{_tag};
}

sub as_string {
    my $self = shift;

    my $tag = $self->{_tag};
    $tag = 'unidentified entry' if !defined $tag;
    return "($tag)";
}

sub parse_info {
    my $self = shift;

    my $info = sprintf '0x%x %s len=0x%x',
        $self->{_offset},
        $self->{_tag},
        $self->{_length};

    return $info;
}

sub unparsed {
    my $self = shift;

    return hexdump($self->get_raw_bytes, $self->get_offset);
}

sub get_raw_bytes {
    my $self = shift;

    my $regfile = $self->{_regfile};
    my $fh = $regfile->get_filehandle;
    my $offset = $self->{_offset};
    my $length = $self->{_length};

    if (defined $self->{_header_length}) {
        $length = $self->{_header_length};
    }

    sysseek($fh, $offset, 0);
    my $bytes_read = sysread($fh, my $buffer, $length);
    if ($bytes_read == $length) {
        return $buffer;
    }
    else {
        return '';
    }
}

sub looks_like_key {
    return UNIVERSAL::isa($_[0], "Parse::Win32Registry::Key");
}

sub looks_like_value {
    return UNIVERSAL::isa($_[0], "Parse::Win32Registry::Value");
}

sub looks_like_security {
    return UNIVERSAL::isa($_[0], "Parse::Win32Registry::WinNT::Security");
}

sub _dumpvar {
    my $self = shift;
    my $depth = shift || 1;

    my $dumpvar = '';
    foreach (sort keys %$self) {
        $dumpvar .= ' ' x ($depth*2);
        $dumpvar .= "$_ => ";
        my $var = $self->{$_};
        if (!defined $var) {
            $dumpvar .= "undef\n";
        }
        elsif (/offset/ || /_id$/ || /^_unk/) {
            $dumpvar .= sprintf "0x%x\n", $var;
        }
        elsif (/_flags$/) {
            $dumpvar .= sprintf "0x%x (0b%b)\n", $var, $var;
        }
        elsif (/length/ || /bytes_used/) {
            $dumpvar .= sprintf "0x%x (%d)\n", $var, $var;
        }
        elsif (/_data$/) {
            if (length($var) == 0) {
                $dumpvar .= '(no data)';
            }
            else {
                $dumpvar .= join(' ', unpack('(H2)20', $var));
                if (length($var) > 20) {
                    $dumpvar .= '...';
                }
            }
            $dumpvar .= "\n";
        }
        elsif (/timestamp$/) {
            $dumpvar .= $var . " (" . iso8601($var) . ")\n";
        }
        elsif ($var =~ /^\d+$/) {
            $dumpvar .= sprintf "%d\n", $var;
        }
        elsif (ref($var)) {
            $dumpvar .= "$var\n"; # stringify object ref
        }
        else {
            $dumpvar .= qq{"$var"};
            $dumpvar .= ' ';
            $dumpvar .= Encode::is_utf8($var) ? "(UTF8)" : "(BYTES)";
            $dumpvar .= "\n";
        }
    }

    return $dumpvar;
}

1;
