#-----------------------------------------------------------
# usbstor2
# Similar to usbstor plugin, but prints output in .csv format;
# also checks MountedDevices keys
# 
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package usbstor2;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080825);

sub getConfig{return %config}

sub getShortDescr {
	return "Get USBStor key info; csv output";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $reg;

sub pluginmain {
	::logMsg("Launching usbstor2 v.".$VERSION);
	::rptMsg("usbstor2 v.".$VERSION); # banner
	my $class = shift;
	my $hive = shift;
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
	
	my $name_path = $ccs."\\Control\\ComputerName\\ComputerName";
	my $comp_name;
	eval {
		$comp_name = $root_key->get_subkey($name_path)->get_value("ComputerName")->get_data();
	};
	$comp_name = "Test" if ($@);
	
	$key_path = $ccs."\\Enum\\USBStor";
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $dev_class = $s->get_name();
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $k (@sk) {
						my $serial = $k->get_name();
						my $sn_lw = $k->get_timestamp();
						my $str = $comp_name.",".$dev_class.",".$serial.",".$sn_lw;
						
						my $friendly;
						eval {
							$friendly = $k->get_value("FriendlyName")->get_data();
							$str .= ",".$friendly;
						};
						$str .= ", " if ($@);

						my $parent;
						eval {
							$parent = $k->get_value("ParentIdPrefix")->get_data();
							$str .= ",".$parent;
							
							my $dev = checkMountedDevices($parent);
							$str .= ",".$dev if ($dev);
							
						};


						::rptMsg($str);
					}
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

sub checkMountedDevices {
	my $pip = shift;
	my $root_key = $reg->get_root_key;
	my $key_path = 'MountedDevices';
	my $key;
	my %md;
	if ($key = $root_key->get_subkey($key_path)) {
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				next unless ($name =~ m/^\\DosDevices/);
				my $data = $v->get_data();
				if (length($data) > 12) {
					$data =~ s/\x00//g; 
					return $name if (grep(/$pip/,$data));
				}
			}
		}
	}
	else {
		return undef;
	}
	return undef;
}

1;
