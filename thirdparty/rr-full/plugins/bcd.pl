#-----------------------------------------------------------
# bcd.pl 
#   
# Change history
#   20220531 - created
#
# References
#   https://blog.nviso.eu/2022/05/30/detecting-bcd-changes-to-inhibit-system-recovery/
#
# Copyright (c) 2022 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bcd;
use strict;

my %config = (hive          => "bcd",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              MITRE         => "",
              category      => "",
			  output		=> "report",
              version       => 20220531);
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Parse BCD hive for boot config settings";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching bcd v.".$VERSION);
    ::rptMsg("bcd v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
    my $key_path = 'Objects';
	
	if ($key = $root_key->get_subkey($key_path)) {
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				my $k = "";
				if ($k = $s->get_subkey("Elements")) {
					
					my @subkeys2 = $k->get_list_of_subkeys();
					if (scalar @subkeys2 > 0) {
						foreach my $t (@subkeys2) {
							::rptMsg("  ".$t->get_name());
							if ($t->get_name() eq "16000009") {
								::rptMsg("Key 16000009 found.");
							
							}
							elsif ($t->get_name eq "250000e0") {
								::rptMsg("Key 250000e0 found.");
							
							}
							else {}
						
						}
					}
				}
				else {
					::rptMsg("Elements subkey not found.");
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	
	}
	
}




1;
