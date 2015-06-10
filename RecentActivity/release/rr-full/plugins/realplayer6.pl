#-----------------------------------------------------------
# realplayer6.pl
# Plugin for Registry Ripper 
# Get Real Player 6 MostRecentClipsx values
#
# Change history
#
#
# References
#
# Note: LastWrite times on c subkeys will all be the same,
#       as each subkey is modified as when a new entry is added
#
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package realplayer6;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's RealPlayer v6 MostRecentClips\(Default) values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching realplayer6 v.".$VERSION);
	::rptMsg("realplayer6 v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Software\\RealNetworks\\RealPlayer\\6.0\\Preferences";   
	my $key = $root_key->get_subkey($key_path);
	if ($key) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my %rpkeys;
		my $tag = "MostRecentClips";
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				if ($name =~ m/^$tag/) {
					my $num = $name;
					$num =~ s/$tag//;
					$rpkeys{$num}{name} = $name;
					$rpkeys{$num}{data} = $s->get_value('')->get_data();
					$rpkeys{$num}{lastwrite} = $s->get_timestamp();
				}
			}
			foreach my $k (sort keys %rpkeys) {
				::rptMsg("\t".$rpkeys{$k}{name}." -> ".$rpkeys{$k}{data});
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