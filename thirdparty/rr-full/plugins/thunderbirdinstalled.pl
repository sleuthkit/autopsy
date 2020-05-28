#-----------------------------------------------------------
# thunderbirdinstalled
#  Shows install current status for Mozilla Thunderbird
# 
# References
#  https://www.thunderbird.net/en-US/
#
# History:
#  20180712 - created
#
# Author: 
#  M. Jones, mictjon@gmail.com
#-----------------------------------------------------------
package thunderbirdinstalled;
use strict;

my %config = (hive          => "Software,NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20120524);

sub getConfig{return %config}

sub getShortDescr {
	return "Shows install status of Thunderbird";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Mozilla" => 
	            "https://www.thunderbird.net/en-US/");	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching thunderbirdinstalled v.".$VERSION);
	::rptMsg("thunderbirdinstalled v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# used a list of values to address the need for parsing the App Paths key
# in the Wow6432Node key, if it exists.
	my @paths = ("Microsoft\\Windows\\CurrentVersion\\App Paths\\thunderbird.exe",
	             "WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\App Paths\\thunderbird.exe");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("Thunderbird installed");
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
			my %apps;
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
				
					my $name = $s->get_name();
					my $lastwrite = $s->get_timestamp();
					my $path;
					eval {
						$path = $s->get_value("")->get_data();
					};
					push(@{$apps{$lastwrite}},$name." - ".$path);
				}
			
				foreach my $t (reverse sort {$a <=> $b} keys %apps) {
					::rptMsg(gmtime($t)." (UTC)");
					foreach my $item (@{$apps{$t}}) {
						::rptMsg("  $item");
					}
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::rptMsg(" Thunderbird not installed.");
		}
	}
}
1;