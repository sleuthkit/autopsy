#-----------------------------------------------------------
# coinstallers
# Gets contents of Enum\WpdBusEnumRoot keys
# 
#
# History:
#  20211212 - created
#
# Ref:
# https://twitter.com/wdormann/status/1432703702079508480
# https://docs.microsoft.com/en-us/windows-hardware/drivers/install/registering-a-device-specific-co-installer
# 1. Look for CoInstallers32 values for devices; REG_MULTI_SZ value
# 2. Set DisableCoInstallers (not a default value) to "1"
#
#
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package coinstallers;
use strict;

my %config = (hive          => "System",
              MITRE         => "T1546",  #Event triggered execution
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20211212);

sub getConfig{return %config}

sub getShortDescr {
	return "Get device CoInstallers32 values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $reg;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching coinstallers v.".$VERSION);
	::rptMsg("coinstallers v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	$reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Control\\Class";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $k (@subkeys) {
				my $dev_class = $k->get_name();
#				::rptMsg($dev_class);
				
				my @subkeys2 = $k->get_list_of_subkeys();
				if (scalar(@subkeys2) > 0) {
					foreach my $l (@subkeys2) {
						my $sk_name = $l->get_name();
						my $device_descr = "Unknown Device Description";
						
						eval {
							my $d = $l->get_value("DriverDesc")->get_data();
							$device_descr = $d;
						};
						
						eval {
							my $c = $l->get_value("CoInstallers32")->get_data();
							::rptMsg("Device             : ".$device_descr);
							::rptMsg("Key Path           : ".$key_path."\\".$dev_class."\\".$sk_name);
							::rptMsg("LastWrite time     : ".::format8601Date($l->get_timestamp())."Z");
							::rptMsg("CoInstaller32 value: ".$c);
							
							::rptMsg("");
						};
					}
				}
#				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("Analysis Tip: Device-specific co-installers are registered during the process of installing a device, when");
	::rptMsg("the Coinstallers INF section is processed. SetupAPI then calls the co-installers at each subsequent step of");
    ::rptMsg("the installation process. If more than one co-installer is registered for a device, SetupAPI calls them in the");
	::rptMsg("order in which they are listed in the registry.");	
}
1;