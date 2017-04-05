#-----------------------------------------------------------
# mixer_tln.pl
#   Get audio mixer info specific to users; Brian Baskin's research seems
#   to indicate that malware (DarkComet) that includes the option to listen
#   in on the user may have been active
#
# Category: Malware
#
# Change history
#  20141112 - created
#
# References
#   http://www.ghettoforensics.com/2014/11/dj-forensics-analysis-of-sound-mixer.html
# 
# copyright 2014 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mixer_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20141112);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks user's audio mixer info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	
	::logMsg("Launching mixer v.".$VERSION);
#	::rptMsg("mixer v.".$VERSION); # banner
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Internet Explorer\\LowRegistry\\Audio\\PolicyConfig\\PropertyStore';
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @sk = $key->get_list_of_subkeys();
		if (scalar @sk > 0) {
#			::rptMsg("LastWrite Time, App, Device GUID");
			foreach my $sub (@sk) {
				my $lw = $sub->get_timestamp();
				my $def;
				eval {
					$def = $sub->get_value("")->get_data();
					my ($p1,$p2) = split(/\|/,$def,2);
					my $dev = (split(/}\./,$p1,2))[1];
					my $app = (split(/%b/,$p2,2))[0];
#					::rptMsg(gmtime($lw).",".$app.",".$dev);
					::rptMsg($lw."|REG|||App:".$app." - Device GUID: ".$dev);
				};
			}
		}
		else {
			::rptMsg($key_path." has no subkeys\.");
		}
	}
}

1;