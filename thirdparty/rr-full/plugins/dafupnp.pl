#-----------------------------------------------------------
# dafupnp.pl
#
# Description:
#	Parses Device Association Framework (DAF) for Universal Plug and Play 
# 	(UPnP) data.  DAFUPnP is used to stream media across a network.
#
# History:
#   20180705 - updated, code tweaks
#   20180628 - Created
# 
#
# Author: M. Jones, mictjon@gmail.com
#-----------------------------------------------------------
package dafupnp;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180705);
			  
my $VERSION = getVersion();
			  
sub getConfig{return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Parses data from networked media streaming devices";	
}
sub getDescr{}
sub getRefs {};

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching dafupnp v.".$VERSION);
	::rptMsg("dafupnp v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my ($current,$ccs);
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
				
	$key_path = $ccs."\\Enum\\SWD\\DAFUPnPProvider";
	if ($key = $root_key->get_subkey($key_path)) {
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				my ($desc,$comid,$hid,$loc,$mfg,$fname);
						
				eval {
					$desc = $s->get_value("DeviceDesc")->get_data();
					::rptMsg("DeviceDesc              : ".$desc);
				};
					
				eval {
					$comid = $s->get_value("CompatibleIDs")->get_data();
					::rptMsg("CompatibleID            : ".$comid);
				};
						
				eval {
					$hid = $s->get_value("HardwareID")->get_data();
					::rptMsg("HardwareID              : ".$hid);
				};	
						
				eval {
					$loc = $s->get_value("LocationInformation")->get_data();
					::rptMsg("LocationInformation     : ".$loc);
				};
						
				eval {
					$mfg = $s->get_value("Mfg")->get_data();
					::rptMsg("MFG                     : ".$mfg);
				};
						
				eval {
					$fname = $s->get_value("FriendlyName")->get_data();
					::rptMsg("FriendlyName            : ".$fname);
				};
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
