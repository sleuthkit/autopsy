#-----------------------------------------------------------
# gthist.pl
# Google Toolbar Search History plugin
# 
#
# Change history
#   20100218 - created
#
# References
#
# 
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package gthist;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100218);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Google Toolbar Search History";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	my %hist;
	::logMsg("Launching gthist v.".$VERSION);
	::rptMsg("gthist v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Google\\NavClient\\1.1\\History';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			::rptMsg("");
			foreach my $v (@vals) {
				my $tv = unpack("V",$v->get_data());
				$hist{$tv} = $v->get_name();
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %hist) {
				my $str = gmtime($t)."  ".$hist{$t};
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