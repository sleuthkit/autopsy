#-----------------------------------------------------------
# registerspooler.pl
# Per \@onelin, setting the RegisterSpoolerRemoteRpcEndPoint value to "2" mitigates CVE-2021-34527
# without having to disable the Spooler service.
#
# Change history:
#   20210705 - created
#
# References:
#   https://twitter.com/onelin/status/1411085783545622531
#   https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.Printing.2::RegisterSpoolerRemoteRpcEndPoint
#   
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package registerspooler;
use strict;

my %config = (hive          => "software",
			  category      => "config",
			  MITRE         => "N/A",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210705);

sub getConfig{return %config}

sub getShortDescr {
	return "Look for BlackLivesMatter key assoc. w/ REvil ransomware";	
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
	::logMsg("Launching registerspooler v.".$VERSION);
	::rptMsg("registerspooler v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	my $key_path = "Policies\\Microsoft\\Windows NT\\Printers";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $i = $key->get_value("RegisterSpoolerRemoteRpcEndPoint")->get_data();
			::rptMsg("RegisterSpoolerRemoteRpcEndPoint value: ".$i);
		};
	}
	else {
		::rptMsg($key_path." key not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Per \@onelin, setting the RegisterSpoolerRemoteRpcEndPoint value to \"2\" mitigates CVE-2021-34527");
	::rptMsg("without having to disable the Spooler service.");
	::rptMsg("Ref: https://twitter.com/onelin/status/1411085783545622531");
}
1;