package Parse::Win32Registry::Key;

use strict;
use warnings;

use base qw(Parse::Win32Registry::Entry);

use Carp;

sub get_name {
    my $self = shift;

    # the root key of a windows 95 registry has no defined name
    # but this should be set to '' when created
    return $self->{_name};
}

sub get_path {
    my $self = shift;

    return $self->{_key_path};
}

sub _look_up_subkey {
    my $self = shift;
    my $subkey_name = shift;

    croak 'Missing subkey name' if !defined $subkey_name;

    foreach my $subkey ($self->get_list_of_subkeys) {
        if (uc $subkey_name eq uc $subkey->{_name}) {
            return $subkey;
        }
    }
    return;
}

sub get_subkey {
    my $self = shift;
    my $subkey_path = shift;

    # check for definedness in case key name is '' or '0'
    croak "Usage: get_subkey('key name')" if !defined $subkey_path;

    my $key = $self;

    # Current path component separator is '\' to match that used in Windows.
    # split returns nothing if it is given an empty string,
    # and without a limit of -1 drops trailing empty fields.
    # The following returns a list with a single zero-length string ("")
    # for an empty string, as split(/\\/, $subkey_path, -1) returns (),
    # an empty list.
    my @path_components = index($subkey_path, "\\") == -1
                        ? ($subkey_path)
                        : split(/\\/, $subkey_path, -1);

    my %offsets_seen = ();
    $offsets_seen{$key->get_offset} = undef;

    foreach my $subkey_name (@path_components) {
        if (my $subkey = $key->_look_up_subkey($subkey_name)) {
            if (exists $offsets_seen{$subkey->get_offset}) {
                return; # found loop
            }
            $key = $subkey;
            $offsets_seen{$key->get_offset} = undef;
        }
        else { # subkey name not found, abort look up
            return;
        }
    }
    return $key;
}

sub get_value {
    my $self = shift;
    my $value_name = shift;

    # check for definedness in case value name is '' or '0'
    croak "Usage: get_value('value name')" if !defined $value_name;

    foreach my $value ($self->get_list_of_values) {
        if (uc $value_name eq uc $value->{_name}) {
            return $value;
        }
    }
    return undef;
}

sub print_summary {
    my $self = shift;

    print $self->as_string, "\n";
}

sub as_regedit_export {
    my $self = shift;

    return "[" . $self->{_key_path} . "]\n";
}

sub regenerate_path {
    my $self = shift;

    # ascend to the root
    my $key = $self;
    my @key_names = ($key->get_name);

    my %offsets_seen = ();
    while (!$key->is_root) {
        $offsets_seen{$key->get_offset}++;
        $key = $key->get_parent;
        if (!defined $key) { # found an undefined parent key
            unshift @key_names, '(Invalid Parent Key)';
            last;
        }
        if (exists $offsets_seen{$key->get_offset}) { # found loop
            unshift @key_names, '(Invalid Parent Key)';
            last;
        }
        unshift @key_names, $key->get_name;
    }

    my $key_path = join('\\', @key_names);
    $self->{_key_path} = $key_path;
    return $key_path;
}

sub get_value_data {
    my $self = shift;
    my $value_name = shift;

    croak "Usage: get_value_data('value name')" if !defined $value_name;

    if (my $value = $self->get_value($value_name)) {
        return $value->get_data;
    }
    return;
}

sub get_mru_list_of_values {
    my $self = shift;

    my @values = ();

    if (my $mrulist = $self->get_value('MRUList')) {
        foreach my $ch (split(//, $mrulist->get_data)) {
            if (my $value = $self->get_value($ch)) {
                push @values, $value;
            }
        }
    }
    elsif (my $mrulistex = $self->get_value('MRUListEx')) {
        foreach my $item (unpack('V*', $mrulistex->get_data)) {
            last if $item == 0xffffffff;
            if (my $value = $self->get_value($item)) {
                push @values, $value;
            }
        }
    }
    return @values;
}

sub get_list_of_subkeys {
    my $self = shift;

    my $subkey_iter = $self->get_subkey_iterator;
    my @subkeys;
    while (my $subkey = $subkey_iter->()) {
        push @subkeys, $subkey;
    }
    return @subkeys;
}

sub get_list_of_values {
    my $self = shift;

    my $value_iter = $self->get_value_iterator;
    my @values;
    while (my $value = $value_iter->()) {
        push @values, $value;
    }
    return @values;
}

sub get_subtree_iterator {
    my $self = shift;

    my @start_keys = ($self);
    push my (@subkey_iters), Parse::Win32Registry::Iterator->new(sub {
        return shift @start_keys;
    });
    my $value_iter;
    my $key; # used to remember key while iterating values

    return Parse::Win32Registry::Iterator->new(sub {
        if (defined $value_iter && wantarray) {
            my $value = $value_iter->();
            if (defined $value) {
                return ($key, $value);
            }
            # $value_iter finished, so fetch a new one
            # from the (current) $subkey_iter[-1]
        }
        while (@subkey_iters > 0) {
            $key = $subkey_iters[-1]->(); # depth-first
            if (defined $key) {
                push @subkey_iters, $key->get_subkey_iterator;
                $value_iter = $key->get_value_iterator;
                return $key;
            }
            pop @subkey_iters; # $subkey_iter finished, so remove it
        }
        return;
    });
}

sub walk {
    my $self = shift;
    my $key_enter_func = shift;
    my $value_func = shift;
    my $key_leave_func = shift;

    if (!defined $key_enter_func &&
        !defined $value_func &&
        !defined $key_leave_func) {
        $key_enter_func = sub { print "+ ", $_[0]->get_path, "\n"; };
        $value_func = sub { print "  '", $_[0]->get_name, "'\n"; };
        $key_leave_func = sub { print "- ", $_[0]->get_path, "\n"; };
    }

    $key_enter_func->($self) if ref $key_enter_func eq 'CODE';

    foreach my $value ($self->get_list_of_values) {
        $value_func->($value) if ref $value_func eq 'CODE';
    }

    foreach my $subkey ($self->get_list_of_subkeys) {
        $subkey->walk($key_enter_func, $value_func, $key_leave_func);
    }

    $key_leave_func->($self) if ref $key_leave_func eq 'CODE';
}

1;
