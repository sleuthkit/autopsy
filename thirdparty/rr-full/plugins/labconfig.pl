#-----------------------------------------------------------
# labconfig.pl
# Get bypass settings to install Win11
#
# History
#  20220819 - updated with MoSetup key
#  20220816 - created
#
# References
#	https://github.com/St1ckys/Win11/blob/main/BypassTPMCheck%26SecureBoot.reg   
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package labconfig;
use strict;
my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1601", #Modify System Image
              category      => "defense evasion",
              version       => 20220819);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Win11 install bypass settings";	
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
	my $ccs = ();
	::logMsg("Launching labconfig v.".$VERSION);
	::rptMsg("labconfig v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Setup\\LabConfig";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = ("BypassTPMCheck", "BypassSecureBootCheck","BypassRAMCheck");
		
		foreach my $v (@vals) {
			eval {
				my $i = $key->get_value($v)->get_data();
				::rptMsg(sprintf "%-25s 0x%04x",$v,$i);
			};
			::rptMsg("Error getting ".$v." value: ".$@) if ($@);
		}
		
		
		::rptMsg("");
		::rptMsg("Analysis Tip: The values listed allow the user to bypass checks to install Win11 on an unsupported system");
		::rptMsg("configuration.");
		::rptMsg("");
		::rptMsg("Ref: https://github.com/St1ckys/Win11/blob/main/BypassTPMCheck%26SecureBoot\.reg");
	}
	else {
		::rptMsg($key_path." not found.");
	}
# added 20220819
# https://www.pcmag.com/news/microsoft-offers-tpm-20-bypass-to-install-windows-11-on-unsupported-pcs	
	my $key_path = $ccs."\\Setup\\MoSetup";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $a = $key->get_value("AllowUpgradesWithUnsupportedTPMOrCPU")->get_data();
			::rptMsg("AllowUpgradesWithUnsupportedTPMOrCPU value: ".$a);
		
		};
		::rptMsg("");
		::rptMsg("Analysis Tip: If the \"AllowUpgradesWithUnsupportedTPMOrCPU\" is set to \"1\", the TPM 2.0 requirement for");
		::rptMsg("Windows 11 is bypassed.");
		::rptMsg("");
		::rptMsg("Ref: https://www.pcmag.com/news/microsoft-offers-tpm-20-bypass-to-install-windows-11-on-unsupported-pcs");
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;