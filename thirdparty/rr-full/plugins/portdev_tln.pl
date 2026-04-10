#-----------------------------------------------------------
# portdev
# Parse Microsoft\Windows Portable Devices\Devices key 
# Get historical information about drive letter assigned to devices
#
# NOTE: Credit for original "discovery" of the key goes to Rob Lee
#
# Change History:
#  20220527 - created from portdev.pl
#
# copyright 2022 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package portdev_tln;
use strict;

my %config = (hive          => "Software",
              MITRE         => "",
              category      => "devices",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "tln",
              version       => 20220527);

sub getConfig{return %config}

sub getShortDescr {
	return "Parses Windows Portable Devices info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::logMsg("Launching portdev v.".$VERSION);
#	::rptMsg("portdev v.".$VERSION); 
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows Portable Devices\\Devices";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $dev = "";
				my $sn  = "";
				my @items = split(/\#/,$name);
				if ($items[0] eq "SWD") {
					$dev = $items[3];
					$sn  = $items[4];
				}
				elsif ($items[0] eq "USB") {
					$dev = $items[1];
					$sn  = $items[2];
				}
				else {
				
				}
				
				my $f = "";
				eval {
					$f = $s->get_value("FriendlyName")->get_data();
				};
				my $str = $dev;
				$str .= "\\$sn" if ($sn ne "");
				$str .= " [".$f."]" if ($f ne "");
				::rptMsg($s->get_timestamp()."|REG|||WinPortDev - $str");
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