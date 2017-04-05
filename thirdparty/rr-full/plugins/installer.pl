#-----------------------------------------------------------
# installer.pl
# Attempts to get InstallDate, DisplayName, DisplayVersion, and 
# Publisher values from Installer\UserData subkeys
#
# History
#  20120917 - created
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package installer;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31, #XP - Win7
              version       => 20120917);

sub getConfig{return %config}
sub getShortDescr {
	return "Determines product install information";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching installer v.".$VERSION);
	::rptMsg("Launching installer v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Microsoft\\Windows\\CurrentVersion\\Installer\\UserData';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Installer");
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("User SID: ".$s->get_name());
				processSubkeys($s);
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

sub processSubkeys {
	my $key = shift;
	my $name = $key->get_name();
	
	my @subkeys = $key->get_subkey("Products")->get_list_of_subkeys();
	
	if (scalar(@subkeys) > 0) {
		foreach my $s (@subkeys) {
			
			my ($display, $date, $version, $publisher);
			my $str;
			my $lw = $s->get_timestamp();
			::rptMsg("Key      : ".$s->get_name());
			::rptMsg("LastWrite: ".gmtime($lw));
			eval {
				$date = $s->get_subkey("InstallProperties")->get_value("InstallDate")->get_data();	
				$str = $date." - ";
			};
			
			eval {
				$display = $s->get_subkey("InstallProperties")->get_value("DisplayName")->get_data();	
				$str .= $display;
			};
			
			eval {
				$version = $s->get_subkey("InstallProperties")->get_value("DisplayVersion")->get_data();	
				$str .= " ".$version;
			};
			
			eval {
				$publisher = $s->get_subkey("InstallProperties")->get_value("Publisher")->get_data();	
				$str .= " (".$publisher.") ";
			};
			
			::rptMsg($str);
			::rptMsg("");
		}
		
	}
	else {
		::rptMsg("Key ".$name." has no subkeys.");
	}
}
1;