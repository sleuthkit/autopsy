#-----------------------------------------------------------
# angelfire.pl
#
# History:
#  20170831 - created
#
# References:
#  https://wikileaks.org/vault7/document/Angelfire-2_0-UserGuide/Angelfire-2_0-UserGuide.pdf
#
# 
# copyright 2017 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package angelfire;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "report",
							category      => "malware",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  #XP - Win7
              version       => 20170831);

sub getConfig{return %config}
sub getShortDescr {
	return "Detects AngelFire";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();


sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching angelfire v.".$VERSION);
	::rptMsg("angelfire v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $af_path = $ccs."\\Control\\Windows\\SystemLookup";
		my $af;
		if ($af = $root_key->get_subkey($af_path)) {
			::rptMsg("AngelFire found.");
			::rptMsg("Path: ".$af_path);
		}
		else {
			::rptMsg("AngelFire not found.");
		}
	}		
}
1;