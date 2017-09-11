#-----------------------------------------------------------
# vmplayer.pl
#   Extracts full filepath for recent VMware Player VM images
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# copyright (c) 2011-02-04 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package vmplayer;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110204);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts full filepath for recent VMware Player VM images.";
}
sub getRefs {
	my %refs = ("VMware Player Homepage:" =>
	            "http://www.vmware.com/products/player/");
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
	::logMsg("Launching vmplayer v.".$VERSION);
    ::rptMsg("vmplayer v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\VMware, Inc.\\VMware Player\\VMplayer\\Window position";

	# If # VMware Player path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("VMware Player");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from VMware Player registry path #
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for VMware Player registry path #
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." -> ".$v->get_data());
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # VMware Player isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
