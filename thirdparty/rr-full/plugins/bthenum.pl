#-----------------------------------------------------------
# bthenum
# Gets contents of Enum\WpdBusEnumRoot keys
# 
#
# History:
#  20200904 - MITRE updates
#  20200515 - updated date output format
#  20191003 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bthenum;
use strict;

my %config = (hive          => "System",
              MITRE         => "",
              category      => "devices",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200904);

sub getConfig{return %config}

sub getShortDescr {
	return "Get BTHENUM subkey info";	
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
	::logMsg("Launching bthenum v.".$VERSION);
	::rptMsg("bthenum v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	$reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
	my $current;
	my $ccs;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}
	else {
		::rptMsg($key_path." not found.");
		return;
	}
	
	my $key_path = $ccs."\\Enum\\BTHENUM";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $k (@subkeys) {
				my $dev_class = $k->get_name();
				next unless ($dev_class =~ m/^Dev/);
				::rptMsg($dev_class);
				
				my @subkeys2 = $k->get_list_of_subkeys();
				if (scalar(@subkeys2) > 0) {
					foreach my $k2 (@subkeys2) {
						::rptMsg($k2->get_name());
						eval {
							::rptMsg("  Properties Key LastWrite: ".::format8601Date($k2->get_subkey("Properties")->get_timestamp())." UTC");
						};
						
						eval {
							my $t = $k2->get_subkey("Properties\\{a35996ab-11cf-4935-8b61-a6761081ecdf}\\000C")->get_value("")->get_data();
							$t =~ s/\00//g;
							::rptMsg("    Device Address        : ".$t);
						};
						
						eval {
							my $t = $k2->get_subkey("Properties\\{2bd67d8b-8beb-48d5-87e0-6cda3428040a}\\0001")->get_value("")->get_data();
							$t =~ s/\00//g;
							::rptMsg("    Device Address        : ".$t);
						};
# https://docs.microsoft.com/en-us/windows/win32/properties/props-system-deviceinterface-bluetooth-lastconnectedtime						
						eval {
							my $t = $k2->get_subkey("Properties\\{2bd67d8b-8beb-48d5-87e0-6cda3428040a}\\000B")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    LastConnectedTime     : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
# 
						eval {
							my $t = $k2->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0064")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    First InstallDate     : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						eval {
							my $t = $k2->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0065")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    InstallDate           : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						eval {
							my $t = $k2->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0066")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    Last Arrival          : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						eval {
							my $t = $k2->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0067")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    Last Removal          : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						::rptMsg("");
					}
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;