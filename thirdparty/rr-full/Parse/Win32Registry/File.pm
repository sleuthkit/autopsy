package Parse::Win32Registry::File;

use strict;
use warnings;

sub get_filehandle {
    my $self = shift;

    return $self->{_filehandle};
}

sub get_filename {
    my $self = shift;

    return $self->{_filename};
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}

sub get_entry_iterator {
    my $self = shift;

    my $entry_iter;
    my $block_iter = $self->get_block_iterator;

    return Parse::Win32Registry::Iterator->new(sub {
        while (1) {
            if (defined $entry_iter) {
                my $entry = $entry_iter->();
                if (defined $entry) {
                    return $entry;
                }
            }
            # entry iterator is undefined or finished
            my $block = $block_iter->();
            if (!defined $block) {
                return; # block iterator finished
            }
            $entry_iter = $block->get_entry_iterator;
        }
    });
}

# method provided for backwards compatibility
sub move_to_first_entry {
    my $self = shift;

    $self->{_entry_iter} = undef;
}

# method provided for backwards compatibility
sub get_next_entry {
    my $self = shift;

    my $entry_iter = $self->{_entry_iter};
    if (!defined $entry_iter) {
        $self->{_entry_iter} = $entry_iter = $self->get_entry_iterator;
    }
    return $entry_iter->();
}

1;
