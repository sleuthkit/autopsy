#-----------------------------------------------------------
# usb
#
# History:
#   20200916 - MITRE updates
#   20200515 - updated date output format
#   20190819 - updated to include time stamps
#   20141111 - updated check for key LastWrite times
#		20141015 - created
#
# Ref:
#   http://studioshorts.com/blog/2012/10/windows-8-device-property-ids-device-enumeration-pnpobject/
#
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package usb;
use strict;

my %config = (hive          => "System",
              MITRE         => "",
              category      => "devices",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}

sub getShortDescr {
	return "Get USB key info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching usb v.".$VERSION);
	::rptMsg("usb v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
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

	my $key_path = $ccs."\\Enum\\USB";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("USBStor");
		::rptMsg($key_path);
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name()." [".::format8601Date($s->get_timestamp())."Z]");
				
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $k (@sk) {
						my $serial = $k->get_name();
						::rptMsg("  S/N: ".$serial." [".::format8601Date($k->get_timestamp())."Z]");
# added 20141015; updated 20141111						
#						eval {
#							::rptMsg("  Device Parameters LastWrite: [".gmtime($k->get_subkey("Device Parameters")->get_timestamp())."]");
#						};
						
						eval {
							::rptMsg("  Properties Key LastWrite: ".::format8601Date($k->get_subkey("Properties")->get_timestamp())."Z");
						};
						my $friendly;
						eval {
							$friendly = $k->get_value("FriendlyName")->get_data();
						};
						::rptMsg("    FriendlyName    : ".$friendly) if ($friendly ne "");
						my $parent;
						eval {
							$parent = $k->get_value("ParentIdPrefix")->get_data();
						};
						::rptMsg("    ParentIdPrefix: ".$parent) if ($parent ne "");
# Attempt to retrieve InstallDate/FirstInstallDate from Properties subkeys	
# http://studioshorts.com/blog/2012/10/windows-8-device-property-ids-device-enumeration-pnpobject/					
						my $t;
						eval {
							$t = $k->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0064")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    First InstallDate     : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						eval {
							$t = $k->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0065")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    InstallDate           : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						eval {
							$t = $k->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0066")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    Last Arrival          : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
						eval {
							$t = $k->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}\\0067")->get_value("")->get_data();
							my ($t0,$t1) = unpack("VV",$t);
							::rptMsg("    Last Removal          : ".::format8601Date(::getTime($t0,$t1))."Z");
						};
						
					}					
				}
				::rptMsg("");
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