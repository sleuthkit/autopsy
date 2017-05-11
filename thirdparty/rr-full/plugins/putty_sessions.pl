#-----------------------------------------------------------
# putty_sessions.pl
#   Extracts the sessions for PuTTY
#
# Change history
#   20170321 Created
#
# No copyright: Mark McCurdy
#-----------------------------------------------------------
# Require #
package putty_sessions;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20170321);
my $VERSION = getVersion();

my @ReturnValues = ("HostName", "LogFileName", "LogType", "LogFlush", "SSHLogOmitPasswords", \
	"SSHLogOmitData", "Protocol", "PortNumber", "TerminalType", "ProxyDNS", "ProxyLocalhost", \
	"ProxyMethod", "ProxyHost", "ProxyPort", "ProxyUsername", "ProxyPassword", "UserName", \
	"LocalUserName", "AgentFwd", "PublicKeyFile", "RemoteCommand", "PortForwardings");

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts the saved sessions for PuTTY.";
}
sub getRefs {
	my %refs = ("PuTTY Homepage:" =>
	            "http://www.chiark.greenend.org.uk/~sgtatham/putty/");
	return %refs;	
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching putty_sessions v.".$VERSION);
    ::rptMsg("putty_sessions v.".$VERSION); # 20170321 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\SimonTatham\\PuTTY\\Sessions";

	# If # PuTTY path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		::rptMsg("PuTTY");

		my $session;
		my @skarray;
                @skarray = $key->get_list_of_subkeys();
                foreach my $session (@skarray) {

			# Return last modified date #
			::rptMsg("LastWrite Time ".gmtime($session->get_timestamp())." (UTC)");

			# Extract # all keys from PuTTY registry path #
			my %keys;
			my @vals = $session->get_list_of_values();

			# If # registry keys exist in path #
			if (scalar(@vals) > 0) {

				# Extract # all key names+values for PuTTY registry path #
				foreach my $v (@vals) {
					if (grep { $v->get_name() eq $_ } @ReturnValues) {
						$keys{$v->get_name()} = $v->get_data();
						::rptMsg($v->get_name()." -> ".$v->get_data());
					}
				}
			# Error # key value is null #
			} else {
				::rptMsg($key_path." has no values.");
			}
			::rptMsg("");
		}

	# Error # PuTTY isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
