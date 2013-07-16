#-----------------------------------------------------------
# warcraft3.pl
#   Extract usernames for Warcraft III
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# copyright (c) 2011-02-02 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package warcraft3;
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
	return "Extract usernames for Warcraft 3.";
}
sub getRefs {
	my %refs = ("Warcraft 3 Homepage:" =>
	            "http://us.blizzard.com/games/war3/");
	return %refs;	
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;
	my @interesting_keys = (
		"userbnet",
		"userlocal"
	);

	# Initialize #
	::logMsg("Launching warcraft3 v.".$VERSION);
    ::rptMsg("warcraft3 v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Blizzard Entertainment\\Warcraft III\\String";

	# If # Warcraft III path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("Warcraft III");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from Warcraft III registry path #
		my %keys;
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for Warcraft III registry path #
			foreach my $v (@vals) {
				$keys{$v->get_name()} = $v->get_data();
			}

			# Return # all key names+values for interesting keys #
			foreach my $var (@interesting_keys) {
				if (exists $keys{$var}) {
					::rptMsg($var." -> ".$keys{$var});
				}
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # Warcraft III isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
