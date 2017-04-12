#-----------------------------------------------------------
# usbdevices.pl
# Parses contents of Enum\USB key for web cam
# 
# History
#   20100219 - created
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package autopsyusbdevices;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100219);

sub getConfig{return %config}

sub getShortDescr {
	return "Parses Enum\\USB key for devices";	
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
	$reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
::logMsg("Launching usbdevices v.".$VERSION);
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
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my @sk = $s->get_list_of_subkeys();
				if (scalar @sk > 0) {
					foreach my $s2 (@sk) {
						::rptMsg("");
						eval {
							my $desc = $s2->get_value("DeviceDesc")->get_data();
							::rptMsg($desc." [".$s->get_name()."\\".$s2->get_name()."]");
						};
						
						my $str;
						eval {
							my $class = $s2->get_value("Class")->get_data();
							::rptMsg("  Class               : ".$class);
						};
						
						eval {
							my $serv = $s2->get_value("Service")->get_data();
							::rptMsg("  Service             : ".$serv);
						};
						
						eval {
							my $serv = $s2->get_value("LocationInformation")->get_data();
							::rptMsg("  Location Information: ".$serv);
						};
						
						eval {
							my $serv = $s2->get_value("Mfg")->get_data();
							::rptMsg("  Mfg                 : ".$serv);
						};

#						eval {
#							if ($s2->get_value("Class")->get_data() eq "Image") {
#								::rptMsg("Possible webcam at ".$s->get_name()."\\".$s2->get_name());
#							}
#						};
#						::rptMsg("Error: ".$@) if ($@);
					}
				}
			}
		}
		else {
			#::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		#::rptMsg($key_path." not found.");
	}
}
1;