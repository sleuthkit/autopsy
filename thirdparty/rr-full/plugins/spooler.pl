#-----------------------------------------------------------
# spooler.pl
# Check Spooler service RequiredPrivileges value
#
# History
#  20230715 - created
#
# References
#   https://thedfirreport.com/2023/06/12/a-truly-graceful-wipe-out/
#
# copyright 2023 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package spooler;
use strict;
my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547\.012",
              category      => "privilege escalation",
			  output		=> "report",
              version       => 20230715);

sub getConfig{return %config}
sub getShortDescr {
	return "Check Spooler service RequiredPrivileges value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	
	::logMsg("Launching spooler v.".$VERSION);
	::rptMsg("spooler v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\Spooler";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
				
		eval {
			my $i = $key->get_value("RequiredPrivileges")->get_data();
			::rptMsg("RequiredPrivileges value: ".$i);
		};
		::rptMsg("RequiredPrivileges value not found.") if ($@);

		::rptMsg("");
		::rptMsg("Analysis Tip: A threat actor was observed performing privilege escalation by stopping the Spooler service,");
		::rptMsg("deleting the RequiredPrivileges value, restarting the Spooler service, and then injecting into the newly");
		::rptMsg("created spoolsv.exe process.");
		::rptMsg("");
		::rptMsg("Ref: https://thedfirreport.com/2023/06/12/a-truly-graceful-wipe-out/");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1