#-----------------------------------------------------------
# devclass
# Get USB device info from the DeviceClasses keys in the System
# hive (Disks and Volumes GUIDs)
#
# Change History:
#   20100901 - spelling error in output corrected
#   20080331 - created
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package devclass;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100901);

sub getConfig{return %config}

sub getShortDescr {
	return "Get USB device info from the DeviceClasses keys in the System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching devclass v.".$VERSION);
	::rptMsg("devclass v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
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
		::logMsg("Could not find ".$key_path);
		return
	}
# Get devices from the Disk GUID
	my $key_path = $ccs."\\Control\\DeviceClasses\\{53f56307-b6bf-11d0-94f2-00a0c91efb8b}";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("DevClasses - Disks");
		::rptMsg($key_path);
		::rptMsg("");
		my %disks;
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				next unless (grep(/USBSTOR/,$name));
				my $lastwrite = $s->get_timestamp();
				my ($dev, $serial) = (split(/#/,$name))[4,5];
				push(@{$disks{$lastwrite}},$dev.",".$serial);
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %disks) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$disks{$t}}) {
					::rptMsg("\t$item");
				}
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
	::rptMsg("");
# Get devices from the Volume GUID
	my $key_path = $ccs."\\Control\\DeviceClasses\\{53f5630d-b6bf-11d0-94f2-00a0c91efb8b}";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("DevClasses - Volumes");
		::rptMsg($key_path);
		::rptMsg("");
		my %vols;
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				next unless (grep(/RemovableMedia/,$name));
				my $lastwrite = $s->get_timestamp();
				my $ppi = (split(/#/,$name))[5];
				push(@{$vols{$lastwrite}},$ppi);
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %vols) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$vols{$t}}) {
					::rptMsg("\tParentIdPrefix: ".$item);
				}
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