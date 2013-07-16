# winscp_sessions.pl
#
# RegRipper module to extract saved session data from NTUSER.DAT
# Software\Martin Prikryl\WinSCP 2\Sessions key.  Password decoding
# algorithm adapted from Metasploit's winscp.rb module, originally
# written by TheLightCosine (http://cosine-security.blogspot.com)
#
# Change History
#		04/02/2013  Added rptMsg for key not found errors by Corey Harrell
#
# RegRipper module author Hal Pomeranz <hal.pomeranz@mandiant.com>

package winscp_sessions;

use strict;

my %config = ('hive' => 'NTUSER.DAT',
	      'hasShortDescr' => 1,
	      'hasDescr' => 0,
	      'hasRefs' => 0,
	      'osmask' => 22,
	      'version' => '20120809');

sub getConfig { return(%config); }
sub getShortDescr { return('Extracts WinSCP stored session data'); }
sub getDescr {}
sub getRefs {}
sub getHive { return($config{'hive'}); }
sub getVersion { return($config{'version'}); }

my $VERSION = $config{'version'};

sub pluginmain {
    my($class, $hive) = @_;
    my($reg, $root, $key) = ();

    ::logMsg("Launching winscp_sessions v.$VERSION\n");
	::rptMsg("winscp_sessions v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
    unless ($reg = Parse::Win32Registry->new($hive)) {
	::logMsg("Failed to open $hive: $!");
	return();
    }
    unless ($root = $reg->get_root_key()) {
	::logMsg("Failed to get root key from $hive: $!");
	::rptMsg("Failed to get root key from $hive: $!"); # line added on 04/02/2013
	return();
    }

    unless ($key = $root->get_subkey('Software\Martin Prikryl\WinSCP 2\Sessions')) {
	::logMsg('"Software\Martin Prikryl\WinSCP 2\Sessions" does not exist');
	::rptMsg('"Software\Martin Prikryl\WinSCP 2\Sessions" does not exist'); # line added on 04/02/2013
	return();
    }

    my %sessions = ();
    my @subkeys = $key->get_list_of_subkeys();
    foreach my $sk (@subkeys) {
	my $session_name = $sk->get_name();
	my $epoch = $sk->get_timestamp();

	my $host = $sk->get_value_data('HostName');
	my $user = $sk->get_value_data('Username');
	my $enc_pass = $sk->get_value_data('PASSWORD');
	my $dec_pass = undef;
	if (length($enc_pass)) {	
	    $dec_pass = decrypt_password($enc_pass, $user . $host);
	}

	$sessions{$session_name} = {
	    'last_update' => $epoch,
	    'host' => $host,
	    'user' => $user,
	    'password' => $dec_pass
	};
    }

    foreach my $session_name (
	sort { $sessions{$a}{'last_update'} <=> $sessions{$b}{'last_update'} ||
	       $a cmp $b } keys(%sessions)) {

	my $header = sprintf("%-35s Last Updated: %s UTC", $session_name, scalar(gmtime($sessions{$session_name}{'last_update'})));

	::rptMsg("$header");
	::rptMsg("   Host: $sessions{$session_name}{'host'}");
	::rptMsg("   User: $sessions{$session_name}{'user'}");
	::rptMsg("   Password: $sessions{$session_name}{'password'}\n");
    }
}
    

# This code adapted from TheLightCosine's winscp.rb Metasploit module
#
sub decrypt_password {
    my($enc, $prefix) = @_;

    my $user_host_encoded = 0;

    my $length = decode_chars(substr($enc, 0, 2, undef));
    if ($length == 0xFF) {
	$user_host_encoded = 1;
	$enc = substr($enc, 2);
	$length = decode_chars(substr($enc, 0, 2, undef));
    }

    my $skip_len = decode_chars(substr($enc, 0, 2, undef)) * 2;
    $enc = substr($enc, $skip_len);

    my $dec = '';
    for (my $i = 0; $i < $length; $i++) {
	last if (length($enc) < 2);
	$dec .= chr(decode_chars(substr($enc, 0, 2, undef)));
    }
    
    $dec = substr($dec, length($prefix)) if ($user_host_encoded);
    return($dec);
}

sub decode_chars {
    my($hex) = @_;

    return((hex($hex) ^ 0xA3) ^ 0xFF);
}

1;
