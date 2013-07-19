#-----------------------------------------------------------
# decaf.pl
#   Extracts the AcceptedEULA value for DECAF
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   Detect and Eliminate Computer Acquired Forensics
#       http://en.wikipedia.org/wiki/DECAF
#
# Copyright (c) 2011-02-10 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package decaf;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110210);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts the EULA value for DECAF.";
}
sub getRefs {
	my %refs = ("Detect and Eliminate Computer Acquired Forensics:" =>
	            "http://en.wikipedia.org/wiki/DECAF");
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
	::logMsg("Launching decaf v.".$VERSION);
    ::rptMsg("decaf v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # 20110830 [fpi] + banner   
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\DECAFme";

	# If # DECAF path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("DECAF");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from DECAF registry path #
		my %keys;
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for DECAF registry path #
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." -> ".$v->get_data());
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # DECAF isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
