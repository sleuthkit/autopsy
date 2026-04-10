#-----------------------------------------------------------
# networkproviders.pl - 
#
# History:
#  20220803 - updated Analysis Tip
#  20220217 - added reference, updated output
#  20210421 - created
#
# References:
#  https://twitter.com/0gtweet/status/1283532806816137216
#  https://github.com/gtworek/PSBits/blob/master/PasswordStealing/NPPSpy/Get-NetworkProviders.ps1
#  https://www.scip.ch/en/?labs.20220217 <-- added 20220217
#  https://attack.mitre.org/techniques/T1556/003/
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package networkproviders;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "credential access",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1556\.003",  
              version       => 20220803);

sub getConfig{return %config}
sub getShortDescr {
	return "Get NetworkProviders info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching networkproviders v.".$VERSION);
	::rptMsg("networkproviders v.".$VERSION);
	::rptMsg("Category: ".$config{category}."  MITRE: ".$config{MITRE});
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\NetworkProvider\\Order";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		my @prov = ();
		eval {
			my $po = $key->get_value("ProviderOrder")->get_data();
			::rptMsg("ProviderOrder value: ".$po);
			::rptMsg("");
			@prov = split(/,/,$po);
		};
		
		if (scalar @prov > 0) {
			foreach my $p (@prov) {
				my $key_path = $ccs."\\Services\\".$p."\\NetworkProvider";
				if ($key = $root_key->get_subkey($key_path)) {
					::rptMsg($key_path);
					::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
					eval {
						my $name = $key->get_value("Name")->get_data();
						::rptMsg("Name              : ".$name);
					};
# added 20220217					
					eval {
						my $t = $key->get_value("TriggerStartPrefix")->get_data();
						::rptMsg("TriggerStartPrefix: ".$t);
					};
					
					eval {
						my $disp = $key->get_value("DisplayName")->get_data();
						::rptMsg("DisplayName       : ".$disp);
					};
					
					eval {
						my $dev = $key->get_value("DeviceName")->get_data();
						::rptMsg("DeviceName        : ".$dev);
					};
					
					eval {
						my $path = $key->get_value("ProviderPath")->get_data();
						::rptMsg("ProviderPath      : ".$path);
						
					};
					::rptMsg("");
				}
				else {
					::rptMsg($key_path." not found.");
				}
				
			}
		
		}
#		::rptMsg("");
		::rptMsg("Analysis Tip: Network providers can be used to load NPLogonNotify API-based password theft tools. This plugin");
		::rptMsg("enumerates installed Network Provider DLLs (ProviderPath) so that they can be checked and verified\. One provider");
		::rptMsg("to specifically look for is \"logincontroll\", which may indicate NPPSpy was installed.");
		::rptMsg("");
		::rptMsg("Ref:  https://www.scip.ch/en/?labs.20220217");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;