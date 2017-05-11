#-----------------------------------------------------------
# environment.pl
#   Extracts user's Environment paths from NTUSER.DAT
# 
# Change history
#   20150910 - added check for specific value, per Hexacorn blog
#   20110830 [fpi] + banner, no change to the version number
#
# References
#  http://www.hexacorn.com/blog/2014/11/14/beyond-good-ol-run-key-part-18/
#
# Copyright (c) 2011-02-04 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
package environment;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150910);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getRefs {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts user's Environment paths from NTUSER.DAT";
}

sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching environment v.".$VERSION);
  ::rptMsg("environment v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Environment";

	# If # Environment path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("Environment");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from Environment registry path #
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for Environment registry path #
			foreach my $v (@vals) {
				my $name = $v->get_name();
				::rptMsg($name." -> ".$v->get_data());
				
				if ($name eq "UserInitMprLogonScript") {
					::rptMsg("**ALERT: UserInitMprLogonScript value found: ".$v->get_data());
				}

			}

		# Error # key value is null #
		} 
		else {
			::rptMsg($key_path." has no values.");
		}

	# Error # Environment isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
	}
	# Return # obligatory new-line #
	::rptMsg("");
}
# Error # oh snap! #
1;
