#-----------------------------------------------------------
# cpldontload.pl
# Check contents of user's Control Panel\don't load key
#
# Change history
#   20100116 - created
#
# References
#   W32.Nekat - http://www.symantec.com/security_response/
#               writeup.jsp?docid=2008-011419-0705-99&tabid=2
#   http://www.2-viruses.com/remove-antispywarexp2009
#
# Notes: Some malware appears to hide various Control Panel applets
#        using this means.  If some sort of malware/spyware is thought
#        to be on the system, check the settings and note the key 
#        LastWrite time.
#
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package cpldontload;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100116);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Control Panel don't load key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching cpldontload v.".$VERSION);
	::rptMsg("cpldontload v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Control Panel\\don\'t load";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				my $str = sprintf "%-20s %-5s",$v->get_name(),$v->get_data();
				::rptMsg($str);
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

1;