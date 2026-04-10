#-----------------------------------------------------------
# outlookmacro.pl
# Check 
#
# Change history
#  20201212 - created 
#
# References
#   https://www.linkedin.com/pulse/outlook-backdoor-using-vba-samir-b-/
#   https://www.cybereason.com/hubfs/Cybereason%20Labs%20Analysis%20Operation%20Cobalt%20Kitty-Part2.pdf
#  
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  outlookmacro;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
			  output		=> "report",
              version       => 20201212);

sub getConfig{return %config}
sub getShortDescr {
	return "Get LoadMacroProviderOnBoot value data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getMitre {return $config{MITRE};}

my $VERSION = getVersion();
my $office_version;
           
sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching outlookmacro v.".$VERSION);
	::rptMsg("outlookmacro v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("outlookmacro v.".$VERSION);
	::rptMsg("MITRE ATT&CK subtechnique ".getMitre());
	::rptMsg("");
# First, let's find out which version of Office is installed
	my @version;
	my $key;
	my $key_path = "Software\\Microsoft\\Office";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@version,$name) if ($name =~ m/^\d/);
		}
	}
# Determine MSOffice version in use	
	my @v = reverse sort {$a<=>$b} @version;
	foreach my $i (@v) {
		eval {
			if (my $o = $key->get_subkey($i."\\User Settings")) {
				$office_version = $i;
			}
		};
	}
	
# Check for LoadMacroProviderOnBoot value	
	eval {
		if (my $id = $key->get_subkey($office_version."\\Outlook")) {
			my $lw   = $id->get_timestamp();
			my $rw = $id->get_value("LoadMacroProviderOnBoot")->get_data();
			::rptMsg("Software\\Microsoft\\Office\\".$office_version."\\Outlook");
			::rptMsg("LastWrite time: ".::format8601Date($lw)."Z");
			::rptMsg("LoadMacroProviderOnBoot value = ".$rw);
			::rptMsg("");
			::rptMsg("Analysis Tip: If the \"LoadMacroProviderOnBoot\" value is set to \"1\", any configured VBA project or module");
			::rptMsg("will be loaded\. Check the contents of the VbaProject\.OTM file\.  This technique was observed being used by ");
			::rptMsg("Cobalt Kitty\.");
		}
	};	
	
# Check Security Level	
	eval {
		if (my $id = $key->get_subkey($office_version."\\Outlook\\Security")) {
			my $lw   = $id->get_timestamp();
			my $rw = $id->get_value("Level")->get_data();
			::rptMsg("Software\\Microsoft\\Office\\".$office_version."\\Outlook\\Security");
			::rptMsg("LastWrite time: ".::format8601Date($lw)."Z");
			::rptMsg("Level value = ".$rw);
			::rptMsg("");
			::rptMsg("Analysis Tip: If the \"Level\" value is set to \"1\", execution of VBA projects is unrestricted.");
			::rptMsg("Ref: https://admx.help/?Category=Office2016&Policy=outlk16.Office.Microsoft.Policies.Windows::L_SecurityLevelOutlook");
		}
	};	
	
# Check Security Level, set via GPO
# https://getadmx.com/HKCU/software/policies/microsoft/office/16.0/outlook/security
	my $gpo_path = "Software\\Policies\\Microsoft\\Office\\".$office_version."\\Outlook\\Security";
	eval {
		if (my $id = $key->get_subkey($gpo_path)) {
			my $lw   = $id->get_timestamp();
			my $rw = $id->get_value("Level")->get_data();
			::rptMsg("Software\\Policies\\Microsoft\\Office\\".$office_version."\\Outlook\\Security");
			::rptMsg("LastWrite time: ".::format8601Date($lw)."Z");
			::rptMsg("Level value = ".$rw);
			::rptMsg("");
			::rptMsg("Analysis Tip: If the \"Level\" value is set to \"1\", execution of VBA projects is unrestricted, set via GPO.");
			::rptMsg("Ref: https://admx.help/?Category=Office2016&Policy=outlk16.Office.Microsoft.Policies.Windows::L_SecurityLevelOutlook");
		}
	};	
}

1;