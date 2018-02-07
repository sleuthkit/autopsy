package Parse::Win32Registry::Value;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Entry);

use Carp;
use Parse::Win32Registry::Base qw(:all);

sub get_name {
    my $self = shift;

    return $self->{_name};
}

sub get_type {
    my $self = shift;

    return $self->{_type};
}

our @Types = qw(
    REG_NONE
    REG_SZ
    REG_EXPAND_SZ
    REG_BINARY
    REG_DWORD
    REG_DWORD_BIG_ENDIAN
    REG_LINK
    REG_MULTI_SZ
    REG_RESOURCE_LIST
    REG_FULL_RESOURCE_DESCRIPTOR
    REG_RESOURCE_REQUIREMENTS_LIST
    REG_QWORD
);

sub get_type_as_string {
    my $self = shift;

    my $type = $self->get_type;
    if (exists $Types[$type]) {
        return $Types[$type];
    }
    else {
        # Return unrecognised types as REG_<number>
        # REGEDIT displays them as formatted hex numbers, e.g. 0x1f4
        return "REG_$type";
    }
}

sub get_data_as_string {
    my $self = shift;

    my $type = $self->get_type;
    my $data = $self->get_data;
    if (!defined($data)) {
        return '(invalid data)';
    }
    elsif (length($data) == 0) {
        return '(no data)';
    }
    elsif ($type == REG_SZ || $type == REG_EXPAND_SZ) {
        return $data;
    }
    elsif ($type == REG_MULTI_SZ) {
        my @data = $self->get_data;
        my $i = 0;
        return join(' ', map { "[" . $i++ . "] $_" } @data);
    }
    elsif ($type == REG_DWORD || $type == REG_DWORD_BIG_ENDIAN) {
        return sprintf '0x%08x (%u)', $data, $data;
    }
    else {
        return join(' ', unpack('(H2)*', $data));
    }
}

sub get_raw_data {
    my $self = shift;

    return $self->{_data};
}

sub as_string {
    my $self = shift;

    my $name = $self->get_name;
    $name = '(Default)' if $name eq '';
    my $type_as_string = $self->get_type_as_string;
    my $data_as_string = $self->get_data_as_string;
    return "$name ($type_as_string) = $data_as_string";
}

sub print_summary {
    my $self = shift;

    print $self->as_string, "\n";
}

1;
