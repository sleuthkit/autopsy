#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# ie_settings.pl
# Gets IE settings
#
# Change history
#
#
# References
#   
# 
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package ie_settings;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20091016);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets IE settings";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ie_settings v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my $ua;
		eval {
			$ua = $key->get_value("User Agent")->get_data();
			::rptMsg("User Agent = ".$ua);
		};
		
		my $zonessecupgrade;
		eval {
			$zonessecupgrade = $key->get_value("ZonesSecurityUpgrade")->get_data();
			my ($z0,$z1) = unpack("VV",$zonessecupgrade);
			::rptMsg("ZonesSecurityUpgrade = ".gmtime(::getTime($z0,$z1))." (UTC)");
		};
		
		my $daystokeep;
		eval {
			$daystokeep = $key->get_subkey("Url History")->get_value("DaysToKeep")->get_data();
			::rptMsg("DaysToKeep = ".$daystokeep);
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;