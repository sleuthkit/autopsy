#-----------------------------------------------------------
# domains.pl
#  
#
# Change history
#		 20100116 - Created
#
# References
#    http://support.microsoft.com/kb/919748
#    http://support.microsoft.com/kb/922704
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package domains;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100116);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents Internet Settings\\ZoneMap\\Domains key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching domains v.".$VERSION);
	::rptMsg("domains v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\ZoneMap";
	my $key;
	if ($key = $root_key->get_subkey($key_path."\\Domains")) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				::rptMsg($s->get_name()." [".gmtime($s->get_timestamp())." (UTC)]");
				
				my @vals = $s->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						::rptMsg("  ".$v->get_name()." -> ".$v->get_data);
					}
				}
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;