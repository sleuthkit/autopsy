#-----------------------------------------------------------
# usb
# 
# 
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package usb;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20121102);
              
sub getConfig{return %config}

sub getShortDescr {
	return "Get USB device info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $reg;

my %usb = ();

sub pluginmain {
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
	
	my $key_path = $ccs."\\Enum\\USB";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				next unless ($name =~ m/^VID/);
				
				my @n = split(/&/,$name);
				$n[0] =~ s/^VID_//;
				$n[1] =~ s/^PID_//;
				
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $k (@sk) {
						my $serial = $k->get_name();
						my $class = "";
						eval {
							$class = $k->get_value("Class")->get_data();
						};
						next unless ($class =~ m/^USB/ || $class =~ m/^WPD/);
						
						my $serv = "";
						eval {
							$serv = $k->get_value("Service")->get_data();
						};
						next if ($serv =~ m/^usbhub/ || $serv =~ m/^usbprint/);
						$usb{$serial}{usb_class} = $class;
						$usb{$serial}{usb_service} = $serv;
						$usb{$serial}{VID} = $n[0];
						$usb{$serial}{PID} = $n[1];
						$usb{$serial}{sn_lastwrite} = $k->get_timestamp();
						
						eval {
							my $dd = $k->get_value("DeviceDesc")->get_data();
							my @f = split(/;/,$dd);
							if (scalar(@f) > 1) {
								my $n = scalar(@f) - 1;
								$usb{$serial}{usb_devicedesc} = $f[$n];
							}
							else {
								$usb{$serial}{usb_devicedesc} = $dd;
							}
						};
							
						eval {
							my $fr = $k->get_value("FriendlyName")->get_data();
							
							my @f = split(/;/,$fr);
							if (scalar(@f) > 1) {
								my $n = scalar(@f) - 1;
								$usb{$serial}{usb_friendly} = $f[$n];
							}
							else {
								$usb{$serial}{usb_friendly} = $fr;
							}
						};
						
						eval {
							$usb{$serial}{usb_service} = 	$k->get_value("Service")->get_data();
						};
					
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

# Now, access the USBStor key
	my $key_path = $ccs."\\Enum\\USBStor";
	my $key;
	my %usbstor = ();
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
			
				my @n = split(/&/,$name);
				$n[1] =~ s/^Ven_//;
				$n[2] =~ s/^Prod_//;
				
				my @sk = $s->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $k (@sk) {
						my $serial = $k->get_name();
						
						eval {
							my $dd = $k->get_value("DeviceDesc")->get_data();
							my @f = split(/;/,$dd);
							if (scalar(@f) > 1) {
								my $n = scalar(@f) - 1;
								$usbstor{$serial}{usbstor_devicedesc} = $f[$n];
							}
							else {
								$usbstor{$serial}{usbstor_devicedesc} = $dd;
							}
						};
						
						eval {
							$usbstor{$serial}{usbstor_friendly} = $k->get_value("FriendlyName")->get_data();
						};
						$usbstor{$serial}{usbstor_ven} = $n[1];
						$usbstor{$serial}{usbstor_prod} = $n[2];
						
					}
					
				}
			}
			
		}
	}

# Match SNs from USBStor key against those we found in the USB key	
	foreach my $k (keys %usb) {
		foreach my $s (keys %usbstor) {
			if ($s =~ m/^$k&/) {
				$usb{$k}{usbstor_friendly} = $usbstor{$s}{usbstor_friendly};
				$usb{$k}{usbstor_devicedesc} = $usbstor{$s}{usbstor_devicedesc};
				$usb{$k}{usbstor_ven} = $usbstor{$s}{usbstor_ven};
				$usb{$k}{usbstor_prod} = $usbstor{$s}{usbstor_prod};
			}
		}		
	}
	
	foreach my $k (keys %usb) {
		::rptMsg($k);
		::rptMsg("  VID/PID  : ".$usb{$k}{VID}."/".$usb{$k}{PID});
		::rptMsg("  Ven/Prod : ".$usb{$k}{usbstor_ven}."/".$usb{$k}{usbstor_prod});
		::rptMsg("");
	}
	
}
1;