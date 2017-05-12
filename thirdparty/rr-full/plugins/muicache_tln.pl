#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# muicache_tln.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# MUICache values 
#
# Change history
#  20130425 - added alertMsg() functionality
#  20120522 - updated to collect info from Win7 USRCLASS.DAT
#
# 
# copyright 2013 Quantum Research Analytics, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package muicache_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT,USRCLASS\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130425);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets EXEs from user's MUICache key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching muicache_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\Microsoft\\Windows\\ShellNoRoam\\MUICache';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				next if ($name =~ m/^@/ || $name eq "LangID");
				my $data = $v->get_data();
				::alertMsg($lw."|ALERT|||HKCU\\".$key_path." ".$name." has \"Temp\" in path: ".$data) if (grep(/[Tt]emp/,$name));
#				::rptMsg("  ".$name." (".$data.")");
			}
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
#		::rptMsg("");
	}
# Added for access to USRCLASS.DAT
	$key_path = 'Local Settings\\Software\\Microsoft\\Windows\\Shell\\MUICache';
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		::rptMsg("");
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				next if ($name =~ m/^@/ || $name eq "LangID");
				my $data = $v->get_data();
				::alertMsg($lw."|ALERT|||HKCU\\".$key_path." ".$name." has \"Temp\" in path: ".$data) if (grep(/[Tt]emp/,$name));
			}
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	} 	

}
1;
