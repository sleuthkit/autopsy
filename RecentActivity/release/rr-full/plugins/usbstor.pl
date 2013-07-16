#-----------------------------------------------------------
# usbstor
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package usbstor;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080418);

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
	::logMsg("Launching usbstor v.".$VERSION);
	::rptMsg("usbstor v.".$VERSION); # banner
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

	my $key_path = $ccs."\\Enum\\USBStor";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("USBStor");
		::rptMsg($key_path);
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name()." [".gmtime($s->get_timestamp())."]");
				
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $k (@sk) {
						my $serial = $k->get_name();
						::rptMsg("  S/N: ".$serial." [".gmtime($k->get_timestamp())."]");
						my $friendly;
						eval {
							$friendly = $k->get_value("FriendlyName")->get_data();
						};
						::rptMsg("    FriendlyName  : ".$friendly) if ($friendly ne "");
						my $parent;
						eval {
							$parent = $k->get_value("ParentIdPrefix")->get_data();
						};
						::rptMsg("    ParentIdPrefix: ".$parent) if ($parent ne "");
					}
				}
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;