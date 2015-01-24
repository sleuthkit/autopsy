#-----------------------------------------------------------
# cdstaginginfo.pl
# Plugin for Registry Ripper 
#
# Change history
#   20131118 - created
#
# References
#   http://secureartisan.wordpress.com/2012/06/04/windows-7-cddvd-burning/
#		
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cdstaginginfo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "useractivity",
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20131118);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's CD Burning\\StagingInfo key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my ($name, $lw, $drvnum, $stage);

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching cdstaginginfo v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("cdstaginginfo v.".$VERSION);
	::rptMsg("");
# LastVistedMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\CD Burning\\StagingInfo";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				$name = $s->get_name();
				$lw   = $s->get_timestamp();
				::rptMsg($name);
				::rptMsg("LastWrite: ".gmtime($lw)." Z");
				eval {
					$stage = $s->get_value("StagingPath")->get_data();
					::rptMsg("  StagingPath: ".$stage);			
				};
			
				eval {
					$drvnum = $s->get_value("DriveNumber")->get_data();
					::rptMsg("  DriveNumber: ".$drvnum);
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
	}
}	

1;		