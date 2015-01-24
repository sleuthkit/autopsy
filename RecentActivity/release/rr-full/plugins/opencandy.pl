#-----------------------------------------------------------
# opencandy.pl - plugin to detect possible presence of OpenCandy adware
#   
# Change history
#   20131008 - created
#
# References
#   http://www.microsoft.com/security/portal/threat/encyclopedia/Entry.aspx?Name=Adware%3AWin32%2FOpenCandy#tab=2
#
# Copyright (c) 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
# Require #
package opencandy;
use strict;

# Declarations #
my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "malware",
              version       => 20131008);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Detect possible presence of OpenCandy adware";
}
sub getRefs {}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching opencandy v.".$VERSION);
  ::rptMsg("opencandy v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my @count = ();
	
	my @paths = ("ADatumCorporation\\OpenCandy",
	             "Wow6432Node\\ADatumCorporation\\OpenCandy");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			push(@count,$key_path);
		}
	}
	
	if (scalar(@count) > 0) {
		::rptMsg("Possible OpenCandy infection detected\.");
		foreach (@count) {
			::rptMsg("  Key: ".$_);
		}
		::rptMsg("");
		::rptMsg("See: http://www.microsoft.com/security/portal/threat/encyclopedia/Entry.aspx?Name=Adware%3AWin32%2FOpenCandy#tab=2");
	}
	else {
		::rptMsg("Indicators not found\.");
	}
}

1;
