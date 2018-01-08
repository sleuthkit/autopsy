#-----------------------------------------------------------
# shc.pl
# This key may have something to do with the Start Menu Cache - nothing 
#  definitive yet.
#
# Change history
#   20130412 - created - IN PROCESS; NOT COMPLETE
#   
#
# References
#   
#   https://chentiangemalc.wordpress.com/2011/11/02/customizing-default-start-menu-in-windows-developer-preview/
#   http://social.msdn.microsoft.com/Forums/en-US/windowsdeveloperpreviewgeneral/thread/296cd88b-d806-4a81-a3d0-ea27de4c8b52
# 
# Copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
							hivemask      => 16,
							output        => "report",
							category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 32,  #Windows 8
              version       => 20130412);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets SHC entries from user hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching shc v.".$VERSION);
	::rptMsg("shc v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				
				
			}
		}
		else {
			::rptMsg($key_path." has no values.");
			::rptMsg("File History may not be configured for this user.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;