#-----------------------------------------------------------
# allow_upgrade.pl
# 
#
# Change history:
#   20230725 - created
#
# References:
#   https://support.microsoft.com/en-us/windows/ways-to-install-windows-11-e0edbbfb-cfc5-4011-868b-2ce77ac7c70e
#   
#        
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package allow_upgrade;
use strict;

my %config = (hive          => "system",
			  category      => "defense evasion",
			  MITRE         => "T1601",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 2023075);

sub getConfig{return %config}

sub getShortDescr {
	return "Check for AllowUpgradesWithUnsupportedTPMOrCPU value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching allow_upgrade v.".$VERSION);
	::rptMsg("allow_upgrade v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Setup\\MoSetup";
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Key path: ".$key_path);
		::rptMsg("Key LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $a = $key->get_value("AllowUpgradesWithUnsupportedTPMOrCPU")->get_data();
			::rptMsg("AllowUpgradesWithUnsupportedTPMOrCPU value: ".$a);
		};
		::rptMsg("AllowUpgradesWithUnsupportedTPMOrCPU value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: The \"AllowUpgradesWithUnsupportedTPMOrCPU\" value set to 1 is a hack to allow Windows 11");
	::rptMsg("updates to be installed on systems that did not meet the TPM or CPU checks. This could be interpreted as ");
	::rptMsg("an attempt at defense evasion, by upgrading the system image to provide additional capabilities, such as");
	::rptMsg("Windows Subsystem for Android.");
	::rptMsg("");
	::rptMsg("Ref: https://support.microsoft.com/en-us/windows/ways-to-install-windows-11-e0edbbfb-cfc5-4011-868b-2ce77ac7c70e");
}
1;