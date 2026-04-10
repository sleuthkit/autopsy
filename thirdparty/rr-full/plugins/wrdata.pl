#-----------------------------------------------------------
# wrdata.pl
#
# Change history:
#  20200916 - MITRE updates
#  20200427 - updated output date format
#  20200413 - created
# 
# Ref:
#  
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  wrdata;
use strict;

my %config = (hive          => "Software",
			  category      => "antivirus",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}
sub getShortDescr {
	return "Collects WebRoot AV Data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching wrdata v.".$VERSION);
	::rptMsg("wrdata v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my @paths = ('WRData','Wow6432Node\\WRData');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			
			eval{
				my $li = $key->get_value("LastInfection")->get_data();
				::rptMsg("");
				::rptMsg("LastInfection: ".$li);
				::rptMsg("");
			};
			
# Status subkey			
			if (my $s = $key->get_subkey("Status")) {
				::rptMsg($key_path."\\Status");
				
				my @vals = ("CurrentUser","DNSServer","Email","HostName","InternalIP","IPV4","MACAddress","OS","PrimaryBrowser");
				foreach my $v (@vals) {
					eval {
						::rptMsg(sprintf "%-15s  %-30s",$v,$s->get_value($v)->get_data());
					};
				}
				::rptMsg("");
				
				eval {
					my $lb = $s->get_value("LastBlockedURL")->get_data();
					my $ls = $s->get_value("LastBlockedURLSeen")->get_data();
					::rptMsg("Last Blocked URL: ".$lb."  [".::format8601Date($ls)."Z]");
				};
				
				eval {
					my $lt = $s->get_value("LatestThreat")->get_data();
					my $l  = $s->get_value("LastThreatSeen")->get_data();
					::rptMsg("Lastest Threat: ".$lt."  [".::format8601Date($l)."Z]");
					::rptMsg("");
				};
				
			}
			else {
				::rptMsg("Key ".$key_path."\\Status not found.");
			}
			
# Journal subkey
			if (my $j = $key->get_subkey("Journal")) {
				my @vals = $j->get_list_of_values();
				if (scalar @vals > 0) {
					::rptMsg($key_path."\\Journal");
					foreach my $v (@vals) {
						my ($file,$hash,$ts) = split(/,/,$v->get_data(),3);
						my $f = (split(/=/,$file,2))[1];
						my $h = (split(/=/,$hash,2))[1];
						my $t = (split(/=/,$ts,2))[1];
						::rptMsg("Filename  : ".$f);
						::rptMsg("MD5 Hash  : ".$h);
						::rptMsg("Time stamp: ".::format8601Date($t)."Z");
						::rptMsg("");
					}
				}
			}
			else {
				::rptMsg("Key ".$key_path."\\Journal not found.");
			}

# Threats\Active subkey
			if (my $a = $key->get_subkey("Threats\\Active")) {
				my @vals = $a->get_list_of_values();
				if (scalar @vals > 0) {
					::rptMsg($key_path."\\Threats\\Active");
					foreach my $v (@vals) {
						next if ($v->get_name() eq "Count");
						my ($file,$id,$t) = split(/\|/,$v->get_data(),3);
						::rptMsg($id." = ".$file." [".::format8601Date(hex($t))."Z]");
					}
					::rptMsg("");
				}
			}
			else {
				::rptMsg("Key ".$key_path."\\Threats\\Active not found.");
			}

# Threats\History subkey			
			if (my $h = $key->get_subkey("Threats\\History")) {
				my @vals = $h->get_list_of_values();
				if (scalar @vals > 0) {
					::rptMsg($key_path."\\Threats\\History");
					foreach my $v (@vals) {
						next if ($v->get_name() eq "Count");
						my ($file,$id,$t) = split(/\|/,$v->get_data(),3);
						::rptMsg($id." = ".$file." [".::format8601Date(hex($t))."Z]");
					}
					::rptMsg("");
				}
			}
			else {
				::rptMsg("Key ".$key_path."\\Threats\\History not found.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;