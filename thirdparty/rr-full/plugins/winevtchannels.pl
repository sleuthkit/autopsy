#-----------------------------------------------------------
# winevtchannels
#
# Change history:
#  20220516 - created
# 
# Ref:
#  
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winevtchannels;
use strict;

my %config = (hive          => "Software",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.002",
			  output		=> "report",
              version       => 20220516);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets WINEVT\\Channels info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching winevtchannels v.".$VERSION);
	::rptMsg("winevtchannels v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $key_path = ('Microsoft\\Windows\\CurrentVersion\\WINEVT\\Channels');
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				
				eval {
					my $e = $s->get_value("Enabled")->get_data();
					::rptMsg("  Enabled        : ".$e);
				};
				
				eval {
					my $o = $s->get_value("OwningPublisher")->get_data();
					::rptMsg("  OwningPublisher: ".$o);
				
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

	::rptMsg("Analysis Tip: A number of Windows Event Logs can be disabled simply by changing the \"Enabled\" value in the");
	::rptMsg("Channels subkey.");
}
1;