#-----------------------------------------------------------
# usbstor3
# Collects USBStor information, output in .csv
#
# History
#   20100312 - created
#
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package usbstor3;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100312);

sub getConfig{return %config}

sub getShortDescr {
	return "Get USBStor key info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching usbstor3 v.".$VERSION);
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

	$key_path = $ccs."\\Enum\\USBStor";
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("USBStor");
#		::rptMsg($key_path);
#		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
#				::rptMsg($s->get_name()." [".gmtime($s->get_timestamp())."]");
				my $name1 = $s->get_name();
				my $time1 = gmtime($s->get_timestamp());
				
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $k (@sk) {
						my $serial = $k->get_name();
#						::rptMsg("  S/N: ".$serial." [".gmtime($k->get_timestamp())."]");
						my $str = $name1.",".$time1.",".$serial.",".gmtime($k->get_timestamp());
						
						my $friendly;
						eval {
							$friendly = $k->get_value("FriendlyName")->get_data();
							$str .= ",".$friendly;
						};
						$str .= "," if ($@);
#						::rptMsg("    FriendlyName  : ".$friendly) if ($friendly ne "");
						my $parent;
						eval {
							$parent = $k->get_value("ParentIdPrefix")->get_data();
							$str .= ",".$parent;
						};
						$str .= "," if ($@);
#						::rptMsg("    ParentIdPrefix: ".$parent) if ($parent ne "");
						::rptMsg($str);
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
}
1;
