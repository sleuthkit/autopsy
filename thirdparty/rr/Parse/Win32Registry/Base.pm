package Parse::Win32Registry::Base;

use strict;
use warnings;

use base qw(Exporter);

use Carp;
use Encode;
use Time::Local qw(timegm);

our @EXPORT_OK = qw(
    warnf
    iso8601
    hexdump
    format_octets
    unpack_windows_time
    unpack_string
    unpack_unicode_string
    unpack_guid
    unpack_sid
    unpack_ace
    unpack_acl
    unpack_security_descriptor
    unpack_series
    make_multiple_subkey_iterator
    make_multiple_value_iterator
    make_multiple_subtree_iterator
    compare_multiple_keys
    compare_multiple_values
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

our %EXPORT_TAGS = (
    all => [@EXPORT_OK],
);

use constant REG_NONE => 0;
use constant REG_SZ => 1;
use constant REG_EXPAND_SZ => 2;
use constant REG_BINARY => 3;
use constant REG_DWORD => 4;
use constant REG_DWORD_BIG_ENDIAN => 5;
use constant REG_LINK => 6;
use constant REG_MULTI_SZ => 7;
use constant REG_RESOURCE_LIST => 8;
use constant REG_FULL_RESOURCE_DESCRIPTOR => 9;
use constant REG_RESOURCE_REQUIREMENTS_LIST => 10;
use constant REG_QWORD => 11;

our $WARNINGS = 0;

our $CODEPAGE = 'cp1252';

sub warnf {
    my $message = shift;
    warn sprintf "$message\n", @_ if $WARNINGS;
}

sub hexdump {
    my $data = shift; # packed binary data
    my $start = shift || 0; # starting value for displayed offset

    return '' if !defined($data);

    my $output = '';

    my $fake_start = $start & ~0xf;
    my $end = length($data);

    my $pos = 0;
    if ($fake_start < $start) {
        $output .= sprintf '%8x  ', $fake_start;
        my $indent = $start - $fake_start;
        $output .= '   ' x $indent;
        my $row = substr($data, $pos, 16 - $indent);
        my $len = length($row);
        $output .= join(' ', unpack('H2' x $len, $row));
        if ($indent + $len < 16) {
            my $padding = 16 - $len - $indent;
            $output .= '   ' x $padding;
        }
        $output .= '  ';
        $output .= ' ' x $indent;
        $row =~ tr/\x20-\x7e/./c;
        $output .= $row;
        $output .= "\n";
        $pos += $len;
    }
    while ($pos < $end) {
        $output .= sprintf '%8x  ', $start + $pos;
        my $row = substr($data, $pos, 16);
        my $len = length($row);
        $output .= join(' ', unpack('H2' x $len, $row));
        if ($len < 16) {
            my $padding = 16 - $len;
            $output .= '   ' x $padding;
        }
        $output .= '  ';
        $row =~ tr/\x20-\x7e/./c;
        $output .= $row;
        $output .= "\n";
        $pos += 16;
    }

    return $output;
}

sub format_octets {
    my $data = shift; # packed binary data
    my $col = shift || 0; # starting column, e.g. length of initial string

    return "\n" if !defined($data);

    my $output = '';

    $col = 76 if $col > 76;
    my $max_octets = int((76 - $col) / 3) + 1;

    my $end = length($data);
    my $pos = 0;
    my $num_octets = $end - $pos;
    $num_octets = $max_octets if $num_octets > $max_octets;
    while ($pos < $end) {
        $output .= join(',', unpack("x$pos(H2)$num_octets", $data));
        $pos += $num_octets;
        $num_octets = $end - $pos;
        $num_octets = 25 if $num_octets > 25;
        if ($num_octets > 0) {
            $output .= ",\\\n  ";
        }
    }
    $output .= "\n";
    return $output;
}

sub unpack_windows_time {
    my $data = shift;

    if (!defined $data) {
        return;
    }

    if (length($data) < 8) {
        return;
    }

    # The conversion uses real numbers
    # as 32-bit perl does not provide 64-bit integers.
    # The equation can be found in several places on the Net.
    # My thanks go to Dan Sully for Audio::WMA's _fileTimeToUnixTime
    # which shows a perl implementation of it.
    my ($lo, $hi) = unpack("VV", $data);
#   my $filetime = $high * 2 ** 32 + $low;
#   my $epoch_time = int(($filetime - 116444736000000000) / 10000000);
		
		my $epoch_time;

		if ($lo == 0 && $hi == 0) {
			$epoch_time = 0;
		} else {
			$lo -= 0xd53e8000;
			$hi -= 0x019db1de;
			$epoch_time = int($hi*429.4967296 + $lo/1e7);
		};
		$epoch_time = 0 if ($epoch_time < 0);
	
		
    # adjust the UNIX epoch time to the local OS's epoch time
    # (see perlport's Time and Date section)
 #  my $epoch_offset = timegm(0, 0, 0, 1, 0, 70);
 #  $epoch_time += $epoch_offset;

    if ($epoch_time < 0 || $epoch_time > 0x7fffffff) {
        $epoch_time = undef;
    }

    return wantarray ? ($epoch_time, 8) : $epoch_time;
}

sub iso8601 {
    my $time = shift;
    my $tz = shift;

    if (!defined $time) {
        return '(undefined)';
    }

    if (!defined $tz || $tz ne 'Z') {
        $tz = 'Z'
    }

    # On Windows, gmtime will return undef if $time < 0 or > 0x7fffffff
    if ($time < 0 || $time > 0x7fffffff) {
        return '(undefined)';
    }
    my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday) = gmtime $time;

    # The final 'Z' indicates UTC ("zero meridian")
    return sprintf '%04d-%02d-%02dT%02d:%02d:%02d%s',
        1900+$year, 1+$mon, $mday, $hour, $min, $sec, $tz;
}

sub unpack_string {
    my $data = shift;

    if (!defined $data) {
        return;
    }

    my $str;
    my $str_len;
    if ((my $end = index($data, "\0")) != -1) {
        $str = substr($data, 0, $end);
        $str_len = $end + 1; # include the final null in the length
    }
    else {
        $str = $data;
        $str_len = length($data);
    }

    return wantarray ? ($str, $str_len) : $str;
}

sub unpack_unicode_string {
    my $data = shift;

    if (!defined $data) {
        return;
    }

    my $str_len = 0;
    foreach my $v (unpack('v*', $data)) {
        $str_len += 2;
        last if $v == 0; # include the final null in the length
    }
    my $str = decode('UCS-2LE', substr($data, 0, $str_len));

    # The decode function from Encode may create invalid unicode characters
    # which cause subsequent warnings (e.g. during regex matching).
    # For example, characters in the 0xd800 to 0xdfff range of the
    # basic multilingual plane (0x0000 to 0xffff) are 'surrogate pairs'
    # and are expected to appear as a 'high surrogate' (0xd800 to 0xdbff)
    # followed by a 'low surrogate' (0xdc00 to 0xdfff).

    # remove any final null
    if (length($str) > 0 && substr($str, -1, 1) eq "\0") {
        chop $str;
    }

    return wantarray ? ($str, $str_len) : $str;
}

sub unpack_guid {
    my $guid = Parse::Win32Registry::GUID->new($_[0]);
    return if !defined $guid;
    return wantarray ? ($guid, $guid->get_length) : $guid;
}

sub unpack_sid {
    my $sid = Parse::Win32Registry::SID->new($_[0]);
    return if !defined $sid;
    return wantarray ? ($sid, $sid->get_length) : $sid;
}

sub unpack_ace {
    my $ace = Parse::Win32Registry::ACE->new($_[0]);
    return if !defined $ace;
    return wantarray ? ($ace, $ace->get_length) : $ace;
}

sub unpack_acl {
    my $acl = Parse::Win32Registry::ACL->new($_[0]);
    return if !defined $acl;
    return wantarray ? ($acl, $acl->get_length) : $acl;
}

sub unpack_security_descriptor {
    my $sd = Parse::Win32Registry::SecurityDescriptor->new($_[0]);
    return if !defined $sd;
    return wantarray ? ($sd, $sd->get_length) : $sd;
}

sub unpack_series {
    my $function = shift;
    my $data = shift;

    if (!defined $function || !defined $data) {
        croak "Usage: unpack_series(\\\&unpack_function, \$data)";
    }

    my $pos = 0;
    my @items = ();
    while (my ($item, $item_len) = $function->(substr($data, $pos))) {
        push @items, $item;
        $pos += $item_len;
    }
    return @items;
}

sub make_multiple_subkey_iterator {
    my @keys = @_;

    # check @keys contains keys
    if (@keys == 0 ||
        grep { defined && !UNIVERSAL::isa($_, 'Parse::Win32Registry::Key') }
        @keys) {
        croak 'Usage: make_multiple_subkey_iterator($key1, $key2, ...)';
    }

    my %subkeys_seen = ();
    my @subkeys_queue;
    for (my $i = 0; $i < @keys; $i++) {
        my $key = $keys[$i];
        next if !defined $key;
        foreach my $subkey ($key->get_list_of_subkeys) {
            my $name = $subkey->get_name;
            $subkeys_seen{$name}[$i] = $subkey;
        }
    }
    foreach my $name (sort keys %subkeys_seen) {
        # make sure number of subkeys matches number of keys
        if (@{$subkeys_seen{$name}} != @keys) {
            @{$subkeys_seen{$name}}[@keys - 1] = undef;
        }
        push @subkeys_queue, $subkeys_seen{$name};
    }

    return Parse::Win32Registry::Iterator->new(sub {
        my $subkeys = shift @subkeys_queue;
        if (defined $subkeys) {
            return $subkeys;
        }
        else {
            return;
        }
    });
}

sub make_multiple_value_iterator {
    my @keys = @_;

    # check @keys contains keys
    if (@keys == 0 ||
        grep { defined && !UNIVERSAL::isa($_, 'Parse::Win32Registry::Key') }
        @keys) {
        croak 'Usage: make_multiple_value_iterator($key1, $key2, ...)';
    }

    my %values_seen = ();
    my @values_queue;
    for (my $i = 0; $i < @keys; $i++) {
        my $key = $keys[$i];
        next if !defined $key;
        foreach my $value ($key->get_list_of_values) {
            my $name = $value->get_name;
            $values_seen{$name}[$i] = $value;
        }
    }
    foreach my $name (sort keys %values_seen) {
        # make sure number of values matches number of keys
        if (@{$values_seen{$name}} != @keys) {
            @{$values_seen{$name}}[@keys - 1] = undef;
        }
        push @values_queue, $values_seen{$name};
    }

    return Parse::Win32Registry::Iterator->new(sub {
        my $values = shift @values_queue;
        if (defined $values) {
            return $values;
        }
        else {
            return;
        }
    });
}

sub make_multiple_subtree_iterator {
    my @keys = @_;

    # check @keys contains keys
    if (@keys == 0 ||
        grep { defined && !UNIVERSAL::isa($_, 'Parse::Win32Registry::Key') }
        @keys) {
        croak 'Usage: make_multiple_subtree_iterator($key1, $key2, ...)';
    }

    my @start_keys = (\@keys);
    push my (@subkey_iters), Parse::Win32Registry::Iterator->new(sub {
        return shift @start_keys;
    });
    my $value_iter;
    my $subkeys; # used to remember subkeys while iterating values

    return Parse::Win32Registry::Iterator->new(sub {
        if (defined $value_iter && wantarray) {
            my $values = $value_iter->();
            if (defined $values) {
                return ($subkeys, $values);
            }
        }
        while (@subkey_iters > 0) {
            $subkeys = $subkey_iters[-1]->(); # depth-first
            if (defined $subkeys) {
                push @subkey_iters, make_multiple_subkey_iterator(@$subkeys);
                $value_iter = make_multiple_value_iterator(@$subkeys);
                return $subkeys;
            }
            pop @subkey_iters; # iter finished, so remove it
        }
        return;
    });
}

sub compare_multiple_keys {
    my @keys = @_;

    # check @keys contains keys
    if (@keys == 0 ||
        grep { defined && !UNIVERSAL::isa($_, 'Parse::Win32Registry::Key') }
        @keys) {
        croak 'Usage: compare_multiple_keys($key1, $key2, ...)';
    }

    my @changes = ();

    my $benchmark_key;
    foreach my $key (@keys) {
        my $diff = '';
        # Skip comparison for the first value
        if (@changes > 0) {
            $diff = _compare_keys($benchmark_key, $key);
        }
        $benchmark_key = $key;
        push @changes, $diff;
    }
    return @changes;
}

sub compare_multiple_values {
    my @values = @_;

    # check @values contains values
    if (@values == 0 ||
        grep { defined && !UNIVERSAL::isa($_, 'Parse::Win32Registry::Value') }
        @values) {
        croak 'Usage: compare_multiple_values($value1, $value2, ...)';
    }

    my @changes = ();

    my $benchmark_value;
    foreach my $value (@values) {
        my $diff = '';
        # Skip comparison for the first value
        if (@changes > 0) {
            $diff = _compare_values($benchmark_value, $value);
        }
        $benchmark_value = $value;
        push @changes, $diff;
    }
    return @changes;
}

sub _compare_keys {
    my ($key1, $key2) = @_;

    if (!defined $key1 && !defined $key2) {
        return ''; # 'MISSING'
    }
    elsif (defined $key1 && !defined $key2) {
        return 'DELETED';
    }
    elsif (!defined $key1 && defined $key2) {
        return 'ADDED';
    }

    my $timestamp1 = $key1->get_timestamp;
    my $timestamp2 = $key2->get_timestamp;
    if ($key1->get_name ne $key2->get_name) {
        return 'CHANGED';
    }
    elsif (defined $timestamp1 && defined $timestamp2) {
        if ($timestamp1 < $timestamp2) {
            return 'NEWER';
        }
        elsif ($timestamp1 > $timestamp2) {
            return 'OLDER';
        }
    }
    else {
        return ''; # comment out to check values...
        my $value_iter = make_multiple_value_iterator($key1, $key2);
        while (my ($val1, $val2) = $value_iter->get_next) {
            if (_compare_values($val1, $val2) ne '') {
                return 'VALUES';
            }
        }
        return '';
    }
}

sub _compare_values {
    my ($val1, $val2) = @_;

    if (!defined $val1 && !defined $val2) {
        return ''; # 'MISSING'
    }
    elsif (defined $val1 && !defined $val2) {
        return 'DELETED';
    }
    elsif (!defined $val1 && defined $val2) {
        return 'ADDED';
    }

    my $data1 = $val1->get_data;
    my $data2 = $val2->get_data;
    if ($val1->get_name ne $val2->get_name ||
        $val1->get_type != $val2->get_type ||
         defined $data1 ne defined $data2 ||
        (defined $data1 && defined $data2 && $data1 ne $data2)) {
        return 'CHANGED';
    }
    else {
        return '';
    }
}


package Parse::Win32Registry::Iterator;

use Carp;

sub new {
    my $class = shift;
    my $self = shift;

    my $type = ref $self;
    croak 'Missing iterator subroutine' if $type ne 'CODE'
                                        && $type ne __PACKAGE__;

    bless $self, $class;
    return $self;
}

sub get_next {
    $_[0]->();
}


package Parse::Win32Registry::GUID;

sub new {
    my $class = shift;
    my $data = shift;

    if (!defined $data) {
        return;
    }

    if (length($data) < 16) {
        return;
    }

    my $guid = sprintf '{%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X}',
        unpack('VvvC2C6', $data);

    my $self = {
        _guid => $guid,
        _length => 16,
    };
    bless $self, $class;

    return $self;
}

sub as_string {
    my $self = shift;

    return $self->{_guid};
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}


package Parse::Win32Registry::SID;

sub new {
    my $class = shift;
    my $data = shift;

    if (!defined $data) {
        return;
    }

    # 0x00 byte  = revision
    # 0x01 byte  = number of sub authorities
    # 0x07 byte  = identifier authority
    # 0x08 dword = 1st sub authority
    # 0x0c dword = 2nd sub authority
    # ...

    if (length($data) < 8) {
        return;
    }

    my ($rev, $num_sub_auths, $id_auth) = unpack('CCx5C', $data);

    if ($num_sub_auths == 0) {
        return;
    }

    my $sid_len = 8 + 4 * $num_sub_auths;

    if (length($data) < $sid_len) {
        return;
    }

    my @sub_auths = unpack("x8V$num_sub_auths", $data);
    my $sid = "S-$rev-$id_auth-" . join('-', @sub_auths);

    my $self = {
        _sid => $sid,
        _length => $sid_len,
    };
    bless $self, $class;

    return $self;
}

# See KB243330 for a list of well known sids
our %WellKnownSids = (
    'S-1-0-0' => 'Nobody',
    'S-1-1-0' => 'Everyone',
    'S-1-3-0' => 'Creator Owner',
    'S-1-3-1' => 'Creator Group',
    'S-1-3-2' => 'Creator Owner Server',
    'S-1-3-3' => 'Creator Group Server',
    'S-1-5-1' => 'Dialup',
    'S-1-5-2' => 'Network',
    'S-1-5-3' => 'Batch',
    'S-1-5-4' => 'Interactive',
    'S-1-5-5-\\d+-\\d+' => 'Logon Session',
    'S-1-5-6' => 'Service',
    'S-1-5-7' => 'Anonymous',
    'S-1-5-8' => 'Proxy',
    'S-1-5-9' => 'Enterprise Domain Controllers',
    'S-1-5-10' => 'Principal Self',
    'S-1-5-11' => 'Authenticated Users',
    'S-1-5-12' => 'Restricted Code',
    'S-1-5-13' => 'Terminal Server Users',
    'S-1-5-18' => 'Local System',
    'S-1-5-19' => 'Local Service',
    'S-1-5-20' => 'Network Service',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-500' => 'Administrator',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-501' => 'Guest',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-502' => 'KRBTGT',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-512' => 'Domain Admins',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-513' => 'Domain Users',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-514' => 'Domain Guests',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-515' => 'Domain Computers',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-516' => 'Domain Controllers',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-517' => 'Cert Publishers',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-518' => 'Schema Admins',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-519' => 'Enterprise Admins',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-520' => 'Group Policy Creator Owners',
    'S-1-5-\\d+-\\d+-\\d+-\\d+-533' => 'RAS and IAS Servers',
    'S-1-5-32-544' => 'Administrators',
    'S-1-5-32-545' => 'Users',
    'S-1-5-32-546' => 'Guest',
    'S-1-5-32-547' => 'Power Users',
    'S-1-5-32-548' => 'Account Operators',
    'S-1-5-32-549' => 'Server Operators',
    'S-1-5-32-550' => 'Print Operators',
    'S-1-5-32-551' => 'Backup Operators',
    'S-1-5-32-552' => 'Replicators',
    'S-1-16-4096' => 'Low Integrity Level',
    'S-1-16-8192' => 'Medium Integrity Level',
    'S-1-16-12288' => 'High Integrity Level',
    'S-1-16-16384' => 'System Integrity Level',
);

sub get_name {
    my $self = shift;

    my $sid = $self->{_sid};

    foreach my $regexp (keys %WellKnownSids) {
        if ($sid =~ m/^$regexp$/) {
            return $WellKnownSids{$regexp};
        }
    }
    return;
}

sub as_string {
    my $self = shift;

    return $self->{_sid};
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}


package Parse::Win32Registry::ACE;

sub new {
    my $class = shift;
    my $data = shift;

    if (!defined $data) {
        return;
    }

    # 0x00 byte  = type
    # 0x01 byte  = flags
    # 0x02 word  = length

    # Types:
    # ACCESS_ALLOWED_ACE_TYPE = 0
    # ACCESS_DENIED_ACE_TYPE  = 1
    # SYSTEM_AUDIT_ACE_TYPE   = 2
    # SYSTEM_MANDATORY_LABEL_ACE_TYPE = x011

    # Flags:
    # OBJECT_INHERIT_ACE         = 0x01
    # CONTAINER_INHERIT_ACE      = 0x02
    # NO_PROPAGATE_INHERIT_ACE   = 0x04
    # INHERIT_ONLY_ACE           = 0x08
    # INHERITED_ACE              = 0x10
    # SUCCESSFUL_ACCESS_ACE_FLAG = 0x40 (Audit Success)
    # FAILED_ACCESS_ACE_FLAG     = 0x80 (Audit Failure)

    if (length($data) < 4) {
        return;
    }

    my ($type, $flags, $ace_len) = unpack('CCv', $data);

    if (length($data) < $ace_len) {
        return;
    }

    # The data following the header varies depending on the type.
    # For ACCESS_ALLOWED_ACE, ACCESS_DENIED_ACE, SYSTEM_AUDIT_ACE
    # the header is followed by an access mask and a sid.
    # 0x04 dword = access mask
    # 0x08       = SID

    # Only the following types are currently unpacked:
    # 0 (ACCESS_ALLOWED_ACE), 1 (ACCESS_DENIED_ACE), 2 (SYSTEM_AUDIT_ACE)
    if ($type >= 0 && $type <= 2 || $type == 0x11) {
        my $access_mask = unpack('x4V', $data);
        my $sid = Parse::Win32Registry::SID->new(substr($data, 8,
                                                        $ace_len - 8));

        # Abandon ace if sid is invalid
        if (!defined $sid) {
            return;
        }

        # Abandon ace if not the expected length
        if (($sid->get_length + 8) != $ace_len) {
            return;
        }

        my $self = {
            _type => $type,
            _flags => $flags,
            _mask => $access_mask,
            _trustee => $sid,
            _length => $ace_len,
        };
        bless $self, $class;

        return $self;
    }
    else {
        return;
    }
}

our @Types = qw(
    ACCESS_ALLOWED
    ACCESS_DENIED
    SYSTEM_AUDIT
    SYSTEM_ALARM
    ALLOWED_COMPOUND
    ACCESS_ALLOWED_OBJECT
    ACCESS_DENIED_OBJECT
    SYSTEM_AUDIT_OBJECT
    SYSTEM_ALARM_OBJECT
    ACCESS_ALLOWED_CALLBACK
    ACCESS_DENIED_CALLBACK
    ACCESS_ALLOWED_CALLBACK_OBJECT
    ACCESS_DENIED_CALLBACK_OBJECT
    SYSTEM_AUDIT_CALLBACK
    SYSTEM_ALARM_CALLBACK
    SYSTEM_AUDIT_CALLBACK_OBJECT
    SYSTEM_ALARM_CALLBACK_OBJECT
    SYSTEM_MANDATORY_LABEL
);

sub _look_up_ace_type {
    my $type = shift;

    if (exists $Types[$type]) {
        return $Types[$type];
    }
    else {
        return '';
    }
}

sub get_type {
    return $_[0]->{_type};
}

sub get_type_as_string {
    return _look_up_ace_type($_[0]->{_type});
}

sub get_flags {
    return $_[0]->{_flags};
}

sub get_access_mask {
    return $_[0]->{_mask};
}

sub get_trustee {
    return $_[0]->{_trustee};
}

sub as_string {
    my $self = shift;

    my $sid = $self->{_trustee};
    my $string = sprintf '%s 0x%02x 0x%08x %s',
        _look_up_ace_type($self->{_type}),
        $self->{_flags},
        $self->{_mask},
        $sid->as_string;
    my $name = $sid->get_name;
    $string .= " [$name]" if defined $name;
    return $string;
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}


package Parse::Win32Registry::ACL;

use Carp;

sub new {
    my $class = shift;
    my $data = shift;

    if (!defined $data) {
        return;
    }

    # 0x00 byte  = revision
    # 0x01
    # 0x02 word  = length
    # 0x04 word  = number of aces
    # 0x06
    # 0x08       = first ace (variable length)
    # ...        = second ace (variable length)
    # ...

    if (length($data) < 8) {
        return;
    }

    my ($rev, $acl_len, $num_aces) = unpack('Cxvv', $data);

    if (length($data) < $acl_len) {
        return;
    }

    my $pos = 8;
    my @acl = ();
    foreach (my $num = 0; $num < $num_aces; $num++) {
        my $ace = Parse::Win32Registry::ACE->new(substr($data, $pos,
                                                        $acl_len - $pos));
        # Abandon acl if any single ace is undefined
        return if !defined $ace;
        push @acl, $ace;
        $pos += $ace->get_length;
    }

    # Abandon acl if not expected length, but don't use
    # $pos != $acl_len as some acls contain unused space.
    if ($pos > $acl_len) {
        return;
    }

    my $self = {
        _acl => \@acl,
        _length => $acl_len,
    };
    bless $self, $class;

    return $self;
}

sub get_list_of_aces {
    my $self = shift;

    return @{$self->{_acl}};
}

sub as_string {
    croak 'Usage: ACLs do not have an as_string method; use as_stanza instead';
}

sub as_stanza {
    my $self = shift;

    my $stanza = '';
    foreach my $ace (@{$self->{_acl}}) {
        $stanza .= 'ACE: '. $ace->as_string . "\n";
    }
    return $stanza;
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}


package Parse::Win32Registry::SecurityDescriptor;

use Carp;

sub new {
    my $class = shift;
    my $data = shift;

    if (!defined $data) {
        return;
    }

    # Unpacks "self-relative" security descriptors

    # 0x00 word  = revision
    # 0x02 word  = control flags
    # 0x04 dword = offset to owner sid
    # 0x08 dword = offset to group sid
    # 0x0c dword = offset to sacl
    # 0x10 dword = offset to dacl

    # Offsets are relative to the start of the security descriptor

    # Control Flags:
    # SE_OWNER_DEFAULTED        0x0001
    # SE_GROUP_DEFAULTED        0x0002
    # SE_DACL_PRESENT           0x0004
    # SE_DACL_DEFAULTED         0x0008
    # SE_SACL_PRESENT           0x0010
    # SE_SACL_DEFAULTED         0x0020
    # SE_DACL_AUTO_INHERIT_REQ  0x0100
    # SE_SACL_AUTO_INHERIT_REQ  0x0200
    # SE_DACL_AUTO_INHERITED    0x0400
    # SE_SACL_AUTO_INHERITED    0x0800
    # SE_DACL_PROTECTED         0x1000
    # SE_SACL_PROTECTED         0x2000
    # SE_RM_CONTROL_VALID       0x4000
    # SE_SELF_RELATIVE          0x8000

    if (length($data) < 20) {
        return;
    }

    my ($rev,
        $flags,
        $offset_to_owner,
        $offset_to_group,
        $offset_to_sacl,
        $offset_to_dacl) = unpack('vvVVVV', $data);

    my %sd = ();
    my $sd_len = 20;

    my $self = {};
    if ($offset_to_owner > 0 && $offset_to_owner < length($data)) {
        my $owner = Parse::Win32Registry::SID->new(substr($data,
                                                          $offset_to_owner));
        return if !defined $owner;
        $self->{_owner} = $owner;
        if ($offset_to_owner + $owner->get_length > $sd_len) {
            $sd_len = $offset_to_owner + $owner->get_length;
        }
    }
    if ($offset_to_group > 0 && $offset_to_group < length($data)) {
        my $group = Parse::Win32Registry::SID->new(substr($data,
                                                          $offset_to_group));
        return if !defined $group;
        $self->{_group} = $group;
        if ($offset_to_group + $group->get_length > $sd_len) {
            $sd_len = $offset_to_group + $group->get_length;
        }
    }
    if ($offset_to_sacl > 0 && $offset_to_sacl < length($data)) {
        my $sacl = Parse::Win32Registry::ACL->new(substr($data,
                                                         $offset_to_sacl));
        return if !defined $sacl;
        $self->{_sacl} = $sacl;
        if ($offset_to_sacl + $sacl->get_length > $sd_len) {
            $sd_len = $offset_to_sacl + $sacl->get_length;
        }
    }
    if ($offset_to_dacl > 0 && $offset_to_dacl < length($data)) {
        my $dacl = Parse::Win32Registry::ACL->new(substr($data,
                                                         $offset_to_dacl));
        return if !defined $dacl;
        $self->{_dacl} = $dacl;
        if ($offset_to_dacl + $dacl->get_length > $sd_len) {
            $sd_len = $offset_to_dacl + $dacl->get_length;
        }
    }
    $self->{_length} = $sd_len;
    bless $self, $class;

    return $self;
}

sub get_owner {
    my $self = shift;

    return $self->{_owner};
}

sub get_group {
    my $self = shift;

    return $self->{_group};
}

sub get_sacl {
    my $self = shift;

    return $self->{_sacl};
}

sub get_dacl {
    my $self = shift;

    return $self->{_dacl};
}

sub as_string {
    croak 'Usage: Security Descriptors do not have an as_string method; use as_stanza instead';
}

sub as_stanza {
    my $self = shift;

    my $stanza = '';
    if (defined(my $owner = $self->{_owner})) {
        $stanza .= 'Owner SID: ' . $owner->as_string;
        my $name = $owner->get_name;
        $stanza .= " [$name]" if defined $name;
        $stanza .= "\n";
    }
    if (defined(my $group = $self->{_group})) {
        $stanza .= 'Group SID: ' . $group->as_string;
        my $name = $group->get_name;
        $stanza .= " [$name]" if defined $name;
        $stanza .= "\n";
    }
    if (defined(my $sacl = $self->{_sacl})) {
        foreach my $ace ($sacl->get_list_of_aces) {
            $stanza .= 'SACL ACE: ' . $ace->as_string . "\n";
        }
    }
    if (defined(my $dacl = $self->{_dacl})) {
        foreach my $ace ($dacl->get_list_of_aces) {
            $stanza .= 'DACL ACE: ' . $ace->as_string . "\n";
        }
    }
    return $stanza;
}

sub get_length {
    my $self = shift;

    return $self->{_length};
}

1;
