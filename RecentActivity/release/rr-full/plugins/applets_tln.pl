#-----------------------------------------------------------
# applets_tln.pl
# Plugin for Registry Ripper 
# Windows\CurrentVersion\Applets Recent File List values 
#
# Change history
#  20120613 - created
#
# References
#
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package applets_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120613);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Applets key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching applets_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Applets';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("Applets");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		::rptMsg("");
# Locate files opened in MS Paint		
		my $paint_key = 'Paint\\Recent File List';
		my $paint = $key->get_subkey($paint_key);
		if (defined $paint) {
#			::rptMsg($key_path."\\".$paint_key);
#			::rptMsg("LastWrite Time ".gmtime($paint->get_timestamp())." (UTC)");
			
			my @vals = $paint->get_list_of_values();
			if (scalar(@vals) > 0) {
				::rptMsg($paint->get_timestamp()."|REG|||MS Paint Most Recent File = ".$paint->get_value("File1")->get_data());
			}
			else {
#				::rptMsg($key_path."\\".$paint_key." has no values.");
			}			
		}
		else {
#			::rptMsg($key_path."\\".$paint_key." not found.");
		}
# Get Last Registry key opened in RegEdit
		my $reg_key = "Regedit";
		my $reg = $key->get_subkey($reg_key);
		if (defined $reg) {
#			::rptMsg("");
#			::rptMsg($key_path."\\".$reg_key);
#			::rptMsg("LastWrite Time ".gmtime($reg->get_timestamp())." (UTC)"); 
			my $lastkey = $reg->get_value("LastKey")->get_data();
			::rptMsg($reg->get_timestamp()."|REG|||RegEdit LastKey value -> ".$lastkey);
		}		
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;