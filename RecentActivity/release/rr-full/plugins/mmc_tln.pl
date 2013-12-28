#-----------------------------------------------------------
# mmc_tln.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# Microsoft Management Console Recent File List values 
#
# Change history
#   20120828 - updated, transitioned to TLN format output
#   20080324 - created
#
# References
#
# 
# copyright 2012
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mmc_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120828);

sub getConfig{return %config}
sub getShortDescr {
	return "Get contents of user's MMC\\Recent File List key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching mmc v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Microsoft Management Console\\Recent File List';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("MMC - Recent File List");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $lw = $key->get_timestamp();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my $file1;
			eval {
				$file1 = $key->get_value("File1")->get_data();
				::rptMsg($lw."|REG|||[Program Execution] MMC - Recent File List - ".$file1);
			};
			
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