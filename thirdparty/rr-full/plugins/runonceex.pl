#-----------------------------------------------------------
# runonceex
#
# Change history:
#  20190716 - created
# 
# Ref:
#  https://oddvar.moe/2018/03/21/persistence-using-runonceex-hidden-from-autoruns-exe/
#
# copyright 2019 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package runonceex;
use strict;

my %config = (hive          => "Software",
							category      => "autostart",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190716);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of RunOnceEx values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching runonceex v.".$VERSION);
	::rptMsg("runonceex v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $key_path = ('Microsoft\\Windows\\CurrentVersion\\RunOnceEx');
	
	::rptMsg("RunOnceEx");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				::rptMsg($s->get_name());
				::rptMsg("LastWrite Time ".gmtime($s->get_timestamp())." (UTC)");

# Gets values and data				
				my @vals = $s->get_list_of_values();
				if (scalar(@vals) > 0) {
					foreach my $v (@vals) {
						::rptMsg($v->get_name()." -> ".$v->get_data());
					}
				}
				::rptMsg("");
				
# Check for Depend key				
				if (my $dep = $s->get_subkey("Depend")) {
					my @vals2 = $dep->get_list_of_values();
					if (scalar(@vals2) > 0) {
						foreach my $v2 (@vals2) {
							::rptMsg($v2->get_name()." -> ".$v2->get_data());
						}
					}
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}	
}
1;