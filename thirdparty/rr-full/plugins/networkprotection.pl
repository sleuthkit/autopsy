#-----------------------------------------------------------
# networkprotection.pl
# Get Windows Defender NetworkProtection settings
#
# Change history:
#   20221114 - created
#
# References:
#   https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/enable-network-protection?view=o365-worldwide
#   https://www.stigviewer.com/stig/windows_defender_antivirus/2017-12-27/finding/V-77979
#        
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package networkprotection;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562\.001",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20221114);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Windows Defender NetworkProtection settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my $key;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $wd_count = 0;
	::logMsg("Launching networkprotection v.".$VERSION);
	::rptMsg("networkprotection v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Policies\\Microsoft\\Windows Defender\\Policy Manager";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $n = $key->get_value("EnableNetworkProtection")->get_data();
			::rptMsg("EnableNetworkProtection value: ".$n);
		};
		::rptMsg("EnableNetworkProtection value not found.") if ($@);
			
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	my $key_path = "Microsoft\\Windows Defender\\Windows Defender Exploit Guard\\NetworkProtection";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $n = $key->get_value("EnableNetworkProtection")->get_data();
			::rptMsg("EnableNetworkProtection value: ".$n);
		};
		::rptMsg("EnableNetworkProtection value not found.") if ($@);
			
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
	::rptMsg("");
	
	::rptMsg("Analysis Tip: Windows Defender can be configured to prevent users/apps from accessing dangerous websites, via");
	::rptMsg("the \"EnableNetworkProtection\" value.");
	::rptMsg("");
	::rptMsg("0 - Off");
	::rptMsg("1 - On ");
	::rptMsg("2 - Audit mode");
	::rptMsg("");
	::rptMsg("Ref: https://www.stigviewer.com/stig/windows_defender_antivirus/2017-12-27/finding/V-77979");
	::rptMsg("Ref: https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/enable-network-protection?view=o365-worldwide ");
}
1