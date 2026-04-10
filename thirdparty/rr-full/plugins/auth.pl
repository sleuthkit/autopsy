#-----------------------------------------------------------
# auth.pl
# Gets information about the most recent login
#
# Change history:
#  20200816 - MITRE update
#  20200724 - created
# 
# Ref:
# 
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package auth;
use strict;

my %config = (hive          => "Software",
			   category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output        => "report",
              version       => 20200816);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Authentication info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching auth v.".$VERSION);
	::rptMsg("auth v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	
	my @paths = ('Microsoft\\Windows\\CurrentVersion\\Authentication\\LogonUI');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			
			my @vals = ("LastLoggedOnSAMUser","LastLoggedOnUser","LastLoggedOnDisplayName","LastLoggedOnUserSID");
			
			foreach my $v (@vals) {
				eval {
					my $i = $key->get_value($v)->get_data();
					::rptMsg(sprintf "%-25s  %-50s",$v,$i);
				};
			}
			
#			if (my $sess = $key->get_subkey("SessionData")){
#				::rptMsg("");
#				my @subkeys = $sess->get_list_of_subkeys();
#				if (scalar @subkeys > 0) {
#					foreach my $s (@subkeys) {
#						::rptMsg($s->get_name());
#						::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
#						foreach my $v (@vals) {
#							eval {
#								my $i = $key->get_value($v)->get_data();
#								::rptMsg(sprintf "%-20s  %-50s",$v,$i);
#							};
#						}
#						::rptMsg("");
#					}
#				}
#			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
}
1;