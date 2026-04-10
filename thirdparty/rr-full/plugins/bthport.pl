#-----------------------------------------------------------
# bthport.pl
# Get BlueTooth device information from the Registry; assumes
# MS drivers (other drivers, such as BroadComm, will be found in
# other locations)
# 
# Change history
#   20200904 - MITRE updates
#   20200517 - updated date output format
#   20180705 - updated to support Win10, per data provided by Micah Jones
#   20170129 - added support for http://www.hexacorn.com/blog/2017/01/29/beyond-good-ol-run-key-part-59/
#   20130115 - created
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bthport;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "",
              category      => "devices",
              version       => 20200904);

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
	::rptMsg("bthport v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
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
			::rptMsg("LastWrite: ".::format8601Date($cn->get_timestamp())."Z");
			
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
						::rptMsg("LastSeen        : ".::format8601Date(::getTime($t0,$t1))."Z");
					};
					
					eval {
						my ($t0,$t1) = unpack("VV",$s->get_value("LastConnected")->get_data());
						::rptMsg("LastConnected   : ".::format8601Date(::getTime($t0,$t1))."Z");
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
			::rptMsg("LastWrite: ".::format8601Date($rs->get_timestamp())."Z");
			
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