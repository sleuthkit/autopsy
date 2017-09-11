#-----------------------------------------------------------
# ntusernetwork.pl
# Plugin for Registry Ripper,
# Network key parser
# 
#-----------------------------------------------------------
package ntusernetwork;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20110601);

sub getConfig{return %config}
sub getShortDescr {
	return "Returns contents of user's Network subkeys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ntusernetwork v.".$VERSION);
	::rptMsg("ntusernetwork v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Network';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($key_path."\\".$s->get_name());
				::rptMsg("LastWrite time: ".gmtime($s->get_timestamp()));
				my @vals = $s->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						::rptMsg(sprintf "  %-15s  %-25s",$v->get_name(),$v->get_data());
					}
					::rptMsg("");
				}
			}
		}
		else {
			::rptMsg($key_path." key has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." key not found.");
	}
}
1;
