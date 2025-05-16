#-----------------------------------------------------------
# utilities.pl
# 
#
# History
#  20221231 - created
#
# References
#  https://twitter.com/0gtweet/status/1607690354068754433
#
# copyright 2022-2023 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package utilities;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
              category      => "persistence",
			  output		=> "report",
              version       => 20221231);

sub getConfig{return %config}
sub getShortDescr {
	return "Get TS Utilities subkey values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	
	::logMsg("Launching utilities v.".$VERSION);
	::rptMsg("utilities v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("Category: ".$config{category}." - ".$config{MITRE});
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Terminal Server\\Utilities";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($key_path."\\".$s->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
				
				my @values = $s->get_list_of_values();
				if (scalar @values > 0) {
					foreach my $v (@values) {
						my $str = $v->get_data();
						$str =~ s/\00/\s/g;
						::rptMsg(sprintf "%-15s %-15s",$v->get_name(),$str);
					}
					::rptMsg("");
				}
				else {
					::rptMsg("Key ".$s->get_name()." has no values.");
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}

#		::rptMsg("");
		::rptMsg("Analysis Tip: The \"query\" subkey beneath \"\\Terminal Server\\Utilities\" can be used for persistence. Look for ");
		::rptMsg("unusual value names.");
		::rptMsg("");
		::rptMsg("Ref: https://twitter.com/0gtweet/status/1607690354068754433");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1