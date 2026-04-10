#-----------------------------------------------------------
# defender.pl
#   
# Get Windows Defender settings from the Software hive
#
# Change history
#   20211027 - added Controls key check (were signatures removed?)
#   20210812 - added ThreatFileHashLogging check
#   20210705 - "Controlled Folder Access" update; assoc. w/ Kaseya REvil attack
#   20200904 - MITRE updates
#   20200427 - updated output date format
#   20200409 - updates
#   20191202 - updated to include Defender settings affected by Clop ransomware
#   20191018 - created
#
# References
#   *Observed a case where a folder containing malware was added to Exclusions, causing
#    Defender to bypass and not detect/quarantine the malware
#   https://www.bleepingcomputer.com/news/security/clop-ransomware-tries-to-disable-windows-defender-malwarebytes/
#  
#   https://attack.mitre.org/techniques/T1562/001/
#
# Copyright 2021 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package defender;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1562\.001",
              category      => "defense evasion",
              version       => 20211027);
              
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Get Windows Defender settings";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching defender v.".$VERSION);
	::rptMsg("defender v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Microsoft\\Windows Defender";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		foreach my $i ("Paths","Extensions","Processes","TemporaryPaths","IpAddresses") {
			eval {
				if (my $excl = $key->get_subkey("Exclusions\\".$i)) {
					my @vals = $excl->get_list_of_values();
					if (scalar @vals > 0) {
						::rptMsg("Exclusions\\".$i." key LastWrite time: ".::format8601Date($excl->get_timestamp())."Z");
						foreach my $v (@vals) {
							::rptMsg(sprintf "  %-50s %2d",$v->get_name(),$v->get_data());
						}
						::rptMsg("");
					}
				}
			};
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
# Check Tamper Protection
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $tamp = $key->get_subkey("Features")->get_value("TamperProtection")->get_data();
			::rptMsg("TamperProtection value = ".$tamp);
			::rptMsg("If TamperProtection value = 1, it's disabled");
		};
	}

# 20211026 - check Signatures
# https://m365internals.com/2021/08/06/dfir-windows-and-active-directory-attacks-and-persistence/
# Possible command: "C:\Program Files\Windows Defender\MpCmdRun.exe" -RemoveDefinitions -All
	if ($key = $root_key->get_subkey($key_path."\\Features\\Controls")) {
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%04d %04d",$v->get_name(),$v->get_data());
			}
		}
		else {
			::rptMsg("The ".$key_path."\\Features\\Controls key has no values, indicating that signatures may have been removed.");
		}
	}
# 
	my $path_str = "Microsoft\\Windows Defender";
	my @key_paths = ($path_str, "Policies\\".$path_str);
#	my $key_path = "Policies\\Microsoft\\Windows Defender";	
	foreach my $key_path (@key_paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp()));
			
			eval {
				if (my $as = $key->get_value("DisableAntiSpyware")->get_data()) {
					::rptMsg("DisableAntiSpyware value = ".$as) if ($as == 1);
					::rptMsg("");
				}
			};

# added 20210812
# https://admx.help/?Category=SystemCenterEndpointProtection&Policy=Microsoft.Policies.Antimalware::system_center_endpoint_protection_threatfile_hashlogging
			eval {
				if (my $as = $key->get_value("ThreatFileHashLogging")->get_data()) {
					::rptMsg("ThreatFileHashLogging value = ".$as) if ($as == 1);
					::rptMsg("");
				}
			};
		
			if (my $block = $key->get_subkey("MpEngine")) {
				eval {
					if (my $b = $block->get_value("MpCloudBlockLevel")->get_data()) {
						::rptMsg("Key path: ".$key_path."\\MpEngine");
						::rptMsg("LastWrite Time: ".::format8601Date($block->get_timestamp())."Z");
						::rptMsg("MpEngine\\MpCloudBlockLevel value = ".$b);
						::rptMsg("");
					}
				};
			}
		
			if (my $spy = $key->get_subkey("Spynet")) {
				eval {
					if (my $s = $spy->get_value("SpynetReporting")->get_data()) {
						::rptMsg("Key path: ".$key_path."\\Spynet");
						::rptMsg("LastWrite Time: ".::format8601Date($spy->get_timestamp())."Z");
						::rptMsg("Spynet\\SpynetReporting value = ".$s);
						::rptMsg("");
					}
				};
				
				eval {
					if (my $samp = $spy->get_value("SubmitSamplesConsent")->get_data()) {
						::rptMsg("Spynet\\SubmitSamplesConsent value = ".$samp);
						::rptMsg("");
					}
				};
			}
		
			if (my $t = $key->get_subkey("Real-Time Protection")) {
				my @vals = ("DisableBehaviorMonitoring","DisableOnAccessProtection","DisableRealtimeMonitoring",
		            "DisableScanOnRealtimeEnable");
		    ::rptMsg("Key path: ".$key_path."\\Real-Time Protection");
		    ::rptMsg("LastWrite Time: ".::format8601Date($t->get_timestamp())."Z");        
				foreach my $val (@vals) { 
					eval {
						my $v = $t->get_value($val)->get_data();
						::rptMsg($val." value = ".$v);
					};
				}
				::rptMsg("");
			}
# Controlled Folder Access
# https://www.tenforums.com/tutorials/113380-how-enable-disable-controlled-folder-access-windows-10-a.html			
			if (my $c = $key->get_subkey("Windows Defender Exploit Guard")) {
				::rptMsg("Key path: ".$key_path."\\Windows Defender Exploit Guard");
		    ::rptMsg("LastWrite Time: ".::format8601Date($c->get_timestamp())."Z"); 
				eval {
					my $f = $c->get_value("Controlled Folder Access")->get_data();
					::rptMsg("\"Controlled Folder Access\" value: ".$f);
					::rptMsg("");
					::rptMsg("0 - Disabled");
					::rptMsg("1 - Enabled");
				};
				::rptMsg("\"Controlled Folder Access\" value not found") if ($@);
				
			}            
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}

1;
