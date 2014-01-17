#-----------------------------------------------------------
# bitbucket
# Get HKLM\..\BitBucket keys\values (if any)
# 
# Change history
#   20091020 - Updated; collected additional values
#
# References
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bitbucket;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080418);

sub getConfig{return %config}

sub getShortDescr {
	return "Get HKLM\\..\\BitBucket keys\\values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching bitbucket v.".$VERSION);
	::rptMsg("bitbucket v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Explorer\\BitBucket";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		eval {
			my $global = $key->get_value("UseGlobalSettings")->get_data();
			::rptMsg("UseGlobalSettings = ".$global);
		};
		
		eval {
			my $nuke = $key->get_value("NukeOnDelete")->get_data();
			::rptMsg("NukeOnDelete      = ".$nuke);
		};	
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($key_path."\\".$s->get_name());
				::rptMsg("LastWrite Time = ".gmtime($s->get_timestamp())." (UTC)");
				eval {
					my $vol = $s->get_value("VolumeSerialNumber")->get_data();
					::rptMsg("VolumeSerialNumber = 0x".uc(sprintf "%1x",$vol));
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
		::logMsg($key_path." not found.");
	}
}

1;