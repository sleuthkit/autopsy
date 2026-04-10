#-----------------------------------------------------------
# perf.pl
#
# History:
#  20201130 - created
#
# References:
#   https://itm4n.github.io/windows-registry-rpceptmapper-eop/   
# 
#   https://attack.mitre.org/techniques/T1543/003/
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package perf;
use strict;

my %config = (hive          => "System",
			  category      => "privilege escalation",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1543\.003",
              version       => 20201130);

sub getConfig{return %config}
sub getShortDescr {
	return "Get EnablePeriodicBackup value";	
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
	::logMsg("Launching perf v.".$VERSION);
	::rptMsg("perf v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
  my @svcs = ("RpcEptMapper","Dnscache");
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		
		foreach my $svc (@svcs) {
			my $perf = ();
			if ($perf = $key->get_subkey($svc."\\Performance")) {
				::rptMsg("LastWrite time: ".::format8601Date($perf->get_timestamp())."Z");
				
				my @vals = ("Library", "Open", "Collect", "Close");
				foreach my $val (@vals) {
					eval {
						my $data = $perf->get_value($val)->get_data();
						::rptMsg(sprintf "%-12s %-25s",$val,$data);
					};
				}
				::rptMsg("");
			}
			else {
				::rptMsg("Services\\".$svc."\\Performance subkey not found.");
			}
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: This privilege escalation issue is specific to Win7 & Win2008R2\. Permissions on these two Service keys");
	::rptMsg("  allow an actor to create a Performance subkey and auto-load a malicious DLL which will execute with System-level ");
	::rptMsg("  privileges.");
}

1;