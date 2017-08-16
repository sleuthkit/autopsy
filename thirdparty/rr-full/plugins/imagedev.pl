#-----------------------------------------------------------
# imagedev.pl
#
# History:
#  20140104 - changed "FriendlyName" to "DeviceDesc" (value)
#  20080813 - created
#
#
# copyright 2014 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package imagedev;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20140104);

sub getConfig{return %config}

sub getShortDescr {
	return " -- ";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching imagedev v.".$VERSION);
	::rptMsg("imagedev v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 	my $current;
 	my $ccs;
 	eval {
		my $key_path = 'Select';
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			$current = $key->get_value("Current")->get_data();
			$ccs = "ControlSet00".$current;
		}
	};
	if ($@) {
		::rptMsg("Problem locating proper controlset: $@");
		return;
	}
	
	my $key_path = $ccs."\\Control\\Class\\{6BDD1FC6-810F-11D0-BEC7-08002BE2092F}";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("imagedev");
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @sk = $key->get_list_of_subkeys();
		
		if (scalar(@sk) > 0) {
			::rptMsg("Still Image Capture Devices");
			foreach my $s (@sk) {
				my $name = $s->get_name();
				next unless ($name =~ m/^\d{4}$/);
				my $desc;
				eval {
					$desc = $s->get_value("DeviceDesc")->get_data();
					::rptMsg("  ".$desc);
				};
				
				
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