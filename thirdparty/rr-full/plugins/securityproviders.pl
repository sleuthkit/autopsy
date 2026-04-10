#-----------------------------------------------------------
# securityproviders.pl
# Get contents of SecurityProviders value in System hive; MS says
# that Win32/Hioles.C uses this key as a persistence mechanism
# 
# Change history
#   20201005 - MITRE update
#   20200526 - updated date output format
#   20120312 - added Hostname
#
# References
#   https://attack.mitre.org/techniques/T1547/005/
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package securityproviders;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547\.005",
              category      => "persistence",
			  output		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets SecurityProvider value from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching securityproviders v.".$VERSION);
	::rptMsg("Launching securityproviders v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $sel_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($sel_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $key_path = $ccs."\\Control\\SecurityProviders";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("LastWrite: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			my $providers = $key->get_value("SecurityProviders")->get_data();
			::rptMsg("SecurityPrividers = ".$providers);
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	else {
		::rptMsg($sel_path." not found.");
	}	
}
1;