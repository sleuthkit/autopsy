#-----------------------------------------------------------
# winvnc.pl
#   Extracts the encrypted password for WinVNC
#
# Change History
#   20110205 [bco] * bug fix, password output now in hex format
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# copyright (c) 2011-02-02 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package winvnc;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110202);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts the encrypted password for WinVNC.";
}
sub getRefs {
	my %refs = ("WinVNC Homepage:" =>
	            "http://www.realvnc.com/");
	return %refs;	
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;
	my @interesting_paths = (
    'Software\\ORL\\WinVNC3',
    'Software\\ORL\\WinVNC3\\Default',
    'Software\\ORL\\WinVNC\\Default',
    'Software\\RealVNC\\WinVNC4',
    'Software\\RealVNC\\Default'
	);
	my @interesting_keys = (
		"Password",
		"PasswordViewOnly"
	);

	# Initialize #
	::logMsg("Launching winvnc v.".$VERSION);
    ::rptMsg("winvnc v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	# Extract # possible registry paths
	foreach my $key_path (@interesting_paths) {

		# If # WinVNC path exists #
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {

			# Return # plugin name, registry key and last modified date #
			::rptMsg("WinVNC");
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");

			# Extract # all keys from winvnc registry path #
			my %keys;
			my @vals = $key->get_list_of_values();

			# If # registry keys exist in path #
			if (scalar(@vals) > 0) {

				# Extract # all key names+values for winvnc registry path #
				foreach my $v (@vals) {
					$keys{$v->get_name()} = $v->get_data();
				}

				# Return # all key names+values for interesting keys #
				foreach my $var (@interesting_keys) {
					if (exists $keys{$var}) {
						my $hstring = unpack ("H*",$keys{$var});
						::rptMsg($var." -> ".$hstring);
					}
				}

				# Return # obligatory new-line #
				::rptMsg("");

			# Error # key value is null #
			} else {
				::rptMsg($key_path." has no values.");
			}

		# Error # WinVNC isn't here, try another castle #
		} else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}

	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
