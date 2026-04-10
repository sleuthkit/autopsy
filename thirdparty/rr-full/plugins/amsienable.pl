#-----------------------------------------------------------
# amsienable.pl
# Plugin for Registry Ripper 
#  
#
# Change history
#  20210217 - created
#
# References
#		https://twitter.com/tal_liberman/status/1097145117809541121
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package amsienable;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1562\.001",
              version       => 20210217);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's AMSIEnable value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching amsienable v.".$VERSION);
	::rptMsg("amsienable v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows Script\\Settings';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("amsienable");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $ae = $key->get_value("AmsiEnable")->get_data();
			::rptMsg("AmsiEnable value: ".$ae);
		};
		::rptMsg("AmsiEnable value not found.") if ($@);
		::rptMsg("");
		::rptMsg("Analysis Tip: If the AmsiEnable value is 0, AMSI is disabled.");		
	}
	else {
		::rptMsg($key_path." key not found.");
	}
}

1;