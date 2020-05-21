#-----------------------------------------------------------
# bthport.pl
# Get BlueTooth device information from the Registry; assumes
# MS drivers (other drivers, such as BroadComm, will be found in
# other locations)
# 
# Change history
#   20180705 - updated to support Win10, per data provided by Micah Jones
#   20170129 - added support for http://www.hexacorn.com/blog/2017/01/29/beyond-good-ol-run-key-part-59/
#   20130115 - created
#
# Category:
# 
# copyright 2018 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bthport;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180705);

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
						my $n = $s->get_value("Name")->get_data();
						::rptMsg("Name            : ".$n);
					};
					
					eval {
						my ($t0,$t1) = unpack("VV",$s->get_value("LastSeen")->get_data());
						::rptMsg("LastSeen        : ".gmtime(::getTime($t0,$t1))." Z");
					};
					
					eval {
						my ($t0,$t1) = unpack("VV",$s->get_value("LastConnected")->get_data());
						::rptMsg("LastConnected   : ".gmtime(::getTime($t0,$t1))." Z");
					};
					
					::rptMsg("");
				}
			}
			else {
				::rptMsg($cn_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($cn_path." not found.");
		}
		::rptMsg("");
		my $rs_path = $ccs."\\services\\BTHPORT\\Parameters\\Radio Support";
		my $rs;
		if ($rs = $root_key->get_subkey($rs_path)) {
			::rptMsg($rs_path);
			::rptMsg("LastWrite: ".gmtime($rs->get_timestamp())." UTC");
			
			eval {
				my $spt = $rs->get_value("SupportDLL")->get_data();
				::rptMsg("SupportDLL = ".$spt);
			};
		}
		else {
			::rptMsg($rs_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;