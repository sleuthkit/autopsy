#-----------------------------------------------------------
# ryuk_gpo.pl
#   
# Get GPO policy settings from Software hive related to Ryuk
#
# Change history
#   20201005 - MITRE update
#   20200427 - updated output date format
#   20200312 - created
#
# References
#   https://thebinaryhick.blog/2019/12/22/ryuk-and-gpos-and-powershell-oh-my/
#		https://attack.mitre.org/techniques/T1562/001/
#
# Copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ryuk_gpo;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1562\.001",
              category      => "defense evasion",
              version       => 20201005);
              
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Get GPO policy settings from Software hive related to Ryuk";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching ryuk_gpo v.".$VERSION);
	::rptMsg("ryuk_gpo v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Policies\\Microsoft";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
# Powershell Policies		
		eval {
			my $scripts = $key->get_subkey("Windows\\PowerShell")->get_value("EnableScripts")->get_data();
			::rptMsg("PowerShell EnableScripts value = ".$scripts);
		};
		
		eval {
			my $ep = $key->get_subkey("Windows\\PowerShell")->get_value("ExecutionPolicy")->get_data();
			::rptMsg("PowerShell ExecutionPolicy value = ".$ep);
		};
		
		my @sys = ("EnableLogonScriptDelay","AsyncScriptDelay","GroupPolicyRefreshTime","GroupPolicyRefreshTimeOffset");
		foreach my $s (@sys) {
			eval {
				my $t = $key->get_subkey("Windows\\System")->get_value($s)->get_data();
				::rptMsg("System ".$s." value = ".$t);
			};	
		}

# WinRM
		my @client = ("AllowBasic","AllowCredSSP","AllowUnencryptedTraffic","TrustedHosts","TrustedHostsList");
		foreach my $c (@client) {
			eval {
				my $t = $key->get_subkey("Windows\\WinRM\\Client")->get_value($c)->get_data();
				::rptMsg("WinRM\\Client ".$c." value = ".$t);
			};
		}
		
		my @service = ("AllowBasic","AllowCredSSP","AllowAutoConfig","IPv4Filter", "IPv6Filter","AllowUnencryptedTraffic","HttpCompatibilityListener","HttpsCompatibilityListener");
		foreach my $s (@service) {
			eval {
				my $t = $key->get_subkey("Windows\\WinRM\\Service")->get_value($s)->get_data();
				::rptMsg("WinRM\\Service ".$s." value = ".$t);
			};
		}
		
		eval {
			my $t = $key->get_subkey("Windows\\WinRM\\Service\\WinRS")->get_value("AllowRemoteShellAccess")->get_data();
			::rptMsg("WinRM\\Service\\WinRS AllowRemoteShellAccess value = ".$t);
		};
		
# Defender, Security Services
		eval {
			my $t = $key->get_subkey("Windows Defender")->get_value("DisableAntiSpyware")->get_data();
			::rptMsg("Windows Defender DisableAntiSpyware value = ".$t);
		};
		
		eval {
			my $t = $key->get_subkey("Windows Defender\\Real-Time Protection")->get_value("DisableRealtimeMonitoring")->get_data();
			::rptMsg("Windows Defender\\Real-Time Protection DisableRealtimeMonitoring value = ".$t);
		};
		
		eval {
			my $t = $key->get_subkey("Windows NT\\Security Center")->get_value("SecurityCenterInDomain")->get_data();
			::rptMsg("Windows NT\\Security Center SecurityCenterInDomain value = ".$t);
		};
		
		eval {
			my $t = $key->get_subkey("Windows NT\\Terminal Services")->get_value("fAllowUnlistedRemotePrograms")->get_data();
			::rptMsg("Windows NT\\Terminal Services fAllowUnlistedRemotePrograms value = ".$t);
		};
		
		eval {
			my $t = $key->get_subkey("Windows NT\\Terminal Services")->get_value("fDenyTSConnections")->get_data();
			::rptMsg("Windows NT\\Terminal Services fDenyTSConnections value = ".$t);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;
