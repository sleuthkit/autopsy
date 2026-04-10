#-----------------------------------------------------------
# maint.pl
# 
#
# Change history:
#   20210326 - created
#
# References:
#   https://twitter.com/jeffmcjunkin/status/967109511575044096
#   
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package maint;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562\.001",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210326);

sub getConfig{return %config}

sub getShortDescr {
	return "Check for MaintenanceDisabled value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching maint v.".$VERSION);
	::rptMsg("maint v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Schedule\\Maintenance";
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $m = $key->get_value("MaintenanceDisabled")->get_data();
			::rptMsg("MaintenanceDisabled value: ".$m);
		};
		::rptMsg("MaintenanceDisabled value not found.") if ($@);
		
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: If the \"MaintenanceDisabled\" value is set to 1, maintenance functions such as malware scans, defrag, ");
	::rptMsg("etc., will be disabled.  Windows Updates are not affected.");
}
1;