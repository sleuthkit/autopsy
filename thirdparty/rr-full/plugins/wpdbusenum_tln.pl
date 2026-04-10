#-----------------------------------------------------------
# wpdbusenum_tln.pl 
# Parses contents of Enum\SWD\WPDBUSENUM key 
# 
# History
#   20220524 - created, copied from usbdevices_tln.pl
#
# References:
#	http://www.swiftforensics.com/2013/11/windows-8-new-registry-artifacts-part-1.html
#   https://www.researchgate.net/publication/318514858_USB_Storage_Device_Forensics_for_Windows_10
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wpdbusenum_tln;
use strict;

my %config = (hive          => "System",
              MITRE         => "",
              category      => "devices",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "tln",
              version       => 20220524);

sub getConfig{return %config}

sub getShortDescr {
	return "Parses Enum\\USBStor key";	
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
#	::logMsg("Launching usbdevices v.".$VERSION);
#	::rptMsg("usbdevices v.".$VERSION); 
#    ::rptMsg("(".getHive().") ".getShortDescr()."\n");

	my $key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Enum\\SWD\\WPDBUSENUM";
	my $key;

	if ($key = $root_key->get_subkey($key_path)) {
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {

				my $f = "";
				my $x = "";
						
				eval {
					$f = $s->get_value("FriendlyName")->get_data();
				};
						
				eval {
					$x = $s->get_value("DeviceDesc")->get_data();
				};
						
				my $name = $x;
				if ($f ne "") {
					$name .= " [".$f."]";
				}
						
# get Properties\{83da6326-97a6-4088-9453-a1923f573b29}						
				eval {
					getProperties($name,$s->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}"));
				};
			}
		}
		else {
#			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}


sub getProperties {
	my $name = shift;
	my $key = shift;

	eval {
		my $r = $key->get_subkey("0064")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg($t."|REG|||First Install - ".$name);
	};

	eval {
		my $r = $key->get_subkey("0065")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg($t."|REG|||First Inserted - ".$name);
	};
	
	eval {
		my $r = $key->get_subkey("0066")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg($t."|REG|||Last Inserted - ".$name);
	};
	
	eval {
		my $r = $key->get_subkey("0067")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg($t."|REG|||Last Removal - ".$name);
		
	};


}


1;