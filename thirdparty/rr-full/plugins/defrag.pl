#-----------------------------------------------------------
# defrag.pl
#
# History:
#  20201130 - created
#
# References:
#  
# 
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package defrag;
use strict;

my %config = (hive          => "system",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1027",
			  output 		=> "report",
              version       => 20201130);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Defrag LastRun value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching defrag v.".$VERSION);
	::rptMsg("defrag v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Session Manager\\Configuration Manager\\Defrag";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $last = $key->get_value("LastRun")->get_data();
			::rptMsg("LastRun value : ".$last);
		};
		::rptMsg("LastRun value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Defrag is very often run automatically on systems.");
}

1;