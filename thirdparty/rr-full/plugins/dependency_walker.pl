#-----------------------------------------------------------
# dependency_walker.pl
#   Extracts Recent File List for Dependency Walker.
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   Dependency Walker Homepage
#       http://www.dependencywalker.com/
#
# Copyright (c) 2011-02-04 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package dependency_walker;
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
	return "Extracts Recent File List for Dependency Walker.";
}
sub getRefs {
	my %refs = ("Dependency Walker Homepage:" =>
	            "http://www.dependencywalker.com/");
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
	::logMsg("Launching dependency_walker v.".$VERSION);
    ::rptMsg("dependency_walker v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # 20110830 [fpi] + banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Microsoft\\Dependency Walker\\Recent File List";

	# If # Dependency Walker path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("Dependency Walker");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from Dependency Walker registry path #
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for Dependency Walker registry path #
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." -> ".$v->get_data());
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # Dependency Walker isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
