#-----------------------------------------------------------
# sbs
# 
# 
# References
#   http://www.hexacorn.com/blog/2017/12/29/beyond-good-ol-run-key-part-69/
#
# History:
#  20180101 - created
#
# copyright 2018 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sbs;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20180101);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets PreferExternalManifest value";	
}
sub getDescr{}
sub getRefs {
	my %refs = ();	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching sbs v.".$VERSION);
	::rptMsg("sbs v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# used a list of values to address the need for parsing the App Paths key
# in the Wow6432Node key, if it exists.
	my @paths = ("Microsoft\\Windows\\CurrentVersion\\SideBySide",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\SideBySide");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("SBS");
			::rptMsg($key_path);
			::rptMsg("");
			
			my $sbs;
			eval {
				$sbs = $key->get_value("SideBySide")->get_data();
				::rptMsg("SideBySide = ".$sbs);
			};
			::rptMsg("SideBySide value not found.") if ($@);
		}
	}
}
1;