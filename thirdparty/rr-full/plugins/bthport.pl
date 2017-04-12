#-----------------------------------------------------------
# bthport.pl
# Get BlueTooth device information from the Registry; assumes
# MS drivers (other drivers, such as BroadComm, will be found in
# other locations)
# 
# Change history
#   20130115 - created
#
# Category:
# 
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bthport;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130115);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Bluetooth-connected devices from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching bthport v.".$VERSION);
	::rptMsg("bthport v.".$VERSION); # banner
::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $cn_path = $ccs."\\services\\BTHPORT\\Parameters\\Devices";
		my $cn;
		if ($cn = $root_key->get_subkey($cn_path)) {
			::rptMsg($cn_path);
			::rptMsg("LastWrite: ".gmtime($cn->get_timestamp())." UTC");
			
			my @sk = $cn->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				::rptMsg("");
				foreach my $s (@sk) {
					my $name = $s->get_name();
					my $lw   = $s->get_timestamp();
					::rptMsg("Device Unique ID: ".$name);
# Note: Need to get VID and PID values for translation and mapping					
					my $devname;
					eval {
# May need to work on parsing the binary "Name" value data into an actual name...
						my @str1 = split(//,unpack("H*",$s->get_value("Name")->get_data()));
						my @s3;
						my $str;
						foreach my $i (0..((scalar(@str1)/2) - 1)) {
							$s3[$i] = $str1[$i * 2].$str1[($i * 2) + 1];
							if (hex($s3[$i]) > 0x1f && hex($s3[$i]) < 0x7f) {
								$str .= chr(hex($s3[$i]));
							}
							else {
								$str .= " ";
							}
						}
						::rptMsg("Device Name: ".$str);
					};
					
				}
			}
			else {
				::rptMsg($cn_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($cn_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}

}

1;