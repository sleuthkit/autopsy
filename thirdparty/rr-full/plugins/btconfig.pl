#-----------------------------------------------------------
# btconfig.pl
#
#
# History:
#  20130117 - created
#
# copyright 2013 Quantum Research Analytics, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package btconfig;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130117);

sub getConfig{return %config}
sub getShortDescr {
	return "Determines BlueTooth devices 'seen' by BroadComm drivers";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching btconfig v.".$VERSION);
	::rptMsg("Launching btconfig v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'WidComm\\BTConfig\\Devices';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @sk = $key->get_list_of_subkeys();
		foreach my $s (@sk) {
			my $name = $s->get_name();
			my $lw   = $s->get_timestamp();
			
			::rptMsg("Unique ID: ".$name);
			::rptMsg("  LastWrite: ".gmtime($lw)." Z");
			
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
						$str .= "";
					}
				}
				::rptMsg("  Device Name: ".$str);
			};
			
			::rptMsg("");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;