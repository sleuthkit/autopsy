#-----------------------------------------------------------
# printprocessors.pl
#
# History:
#  20200922 - MITRE update
#  20200710 - created
#
# References:
#  https://www.welivesecurity.com/2020/05/21/no-game-over-winnti-group/
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package printprocessors;
use strict;

my %config = (hive          => "System",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",  
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Get entries from PrintProcessors subkeys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching printprocessors v.".$VERSION);
	::rptMsg("printprocessors v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my $ccs = ::getCCS($root_key);
		my $pp_path = $ccs."\\Control\\Print\\Environments";
		my $pp;
		if ($pp = $root_key->get_subkey($pp_path)) {
			my @subkeys1 = $pp->get_list_of_subkeys();
			if (scalar @subkeys1 > 0) {
				foreach my $s1 (@subkeys1) {

					if (my $prt = $s1->get_subkey("Print Processors")) {
						my @subkeys2 = $prt->get_list_of_subkeys();
						if (scalar @subkeys2 > 0) {
							foreach my $s2 (@subkeys2) {
								eval {
									if (my $driver = $s2->get_value("Driver")->get_data()) {
										::rptMsg("");
										::rptMsg($pp_path."\\".$s1->get_name()."\\Print Processors\\".$s2->get_name());
										::rptMsg("LastWrite time: ".::format8601Date($s2->get_timestamp())."Z");
										::rptMsg("Driver value = ".$driver);
									}
								};
								
							}
						}
					}
				}
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: Alternative Print Processors have been used for persistence. Verify unusual DLLs listed and");
			::rptMsg("suspicious print processor names.");
			::rptMsg("https://www.welivesecurity.com/2020/05/21/no-game-over-winnti-group/");
		}
		else {
			::rptMsg($pp_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;