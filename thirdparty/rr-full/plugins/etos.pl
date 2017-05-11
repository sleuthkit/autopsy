#-----------------------------------------------------------
# at.pl
#   
#
# Change history
#   20150325 - created
#
# Ref:
#  http://www.secureworks.com/cyber-threat-intelligence/threats/threat-group-3279-targets-the-video-game-industry/ 
#   
# Per the above reference, if the plugin produces a list of values for either of keys checked,
# the analyst should consider checking the value data, as they may be XOR-encoded data read, 
# decoded and used by the malware.
#
# Copyright (c) 2015 QAR,LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package etos;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "malware",
              version       => 20150325);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {return "Checks Software hive for indicators of Etos malware";}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching etos v.".$VERSION);
  ::rptMsg("etos v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr());   
  ::rptMsg("");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my @paths = ('ODBC.INI', 'ODBC\\ODBC.INI');
	
	foreach my $key_path (@paths) {
	
		if ($key = $root_key->get_subkey($key_path)) {
		
			my @val = $key->get_list_of_values();
			if (scalar @val > 0) {
				my $lw = $key->get_timestamp();
				::rptMsg("LastWrite: ".gmtime($lw));
				foreach my $v (@val) {
					my $name = $v->get_name();
					::rptMsg("  ".$name);
				}
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		
		}
	}
}

1;
