#-----------------------------------------------------------
# latentbot.pl
# 
#
# Change History
#   20151213 - created
#
# References:
#   https://www.fireeye.com/blog/threat-research/2015/12/latentbot_trace_me.html
#   
#
# copyright 2015 Quantum Analytics Research,
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package latentbot;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20151213);

sub getConfig{return %config}

sub getShortDescr {
	return "Check NTUSER.DAT for indications of LatentBot";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching latentbot v.".$VERSION);
	::rptMsg("latentbot v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	
# 20151213: LatentBot persists via the 'load' value
# https://www.fireeye.com/blog/threat-research/2015/12/latentbot_trace_me.html	
# look for odd value data, such as "dlrznz68mkaa.exe"	
		my $load;
		eval {
			$load = $key->get_value("load")->get_data();
			::rptMsg("load value = ".$load);
			::alertMsg("ALERT: user_run: ".$key_path." load value found: ".$load) unless ($load eq "");
		};
		if ($@) {
			::rptMsg("load value not found.");
		}
	}
  ::rptMsg("");
# Look for odd, randomly named subkeys, which may indicate the existence of the
# modules; the names aren't actually random, but XOR encoded	
	$key_path = "Software\\Google\\Update\\network\\secure";
	if ($key = $root_key->get_subkey($key_path)) {
		
		my @subkeys;
		eval {
			@subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				::rptMsg($key_path);
				::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
				foreach my $s (@subkeys) {
					::rptMsg("  ".$s->get_name()." - ".gmtime($s->get_timestamp())." (UTC)");
				}
			}
			else {
				
			}
		};
	}
}
1;
