#-----------------------------------------------------------
# remoteuac.pl
# Get setting for remote UAC
#
# Change history:
#   20220101 - created
#
# References:
#   https://docs.microsoft.com/en-us/troubleshoot/windows-server/windows-security/user-account-control-and-remote-restriction
#   https://redcanary.com/blog/blackbyte-ransomware/ <- Added 02142022
#       
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package remoteuac;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220101);

sub getConfig{return %config}

sub getShortDescr {
	return "Get setting for remote UAC";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching remoteuac v.".$VERSION);
	::rptMsg("remoteuac v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Policies\\System";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $uac = $key->get_value("LocalAccountTokenFilterPolicy")->get_data();
			::rptMsg("LocalAccountTokenFilterPolicy value: ".$uac);
			::rptMsg("");
			::rptMsg("0 - Filtered token created\. No Admin\. Default\.");
			::rptMsg("1 - Elevated token created\.");
		};
		::rptMsg("LocalAccountTokenFilterPolicy value not found.") if ($@);
	}
	else {
#			::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: As of Vista, Windows implements UAC restrictions on the network\. Users logging in remotely to");
	::rptMsg("target systems will not be provided an elevated token when logging in via a local Admin account.  UAC ");
	::rptMsg("restrictions are enabled by default\.");
	::rptMsg("Ref: https://docs.microsoft.com/en-us/troubleshoot/windows-server/windows-security/user-account-control-and-remote-restriction");
}
1;