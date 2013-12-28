#-----------------------------------------------------------
# gtwhitelist.pl
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
package gtwhitelist;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100218);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Google Toolbar whitelist values";	
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
	::logMsg("Launching gtwhitelist v.".$VERSION);
	::rptMsg("gtwhitelist v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Google\\Google Toolbar\\4.0\\whitelist';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $allow2;
		eval {
			$allow2 = $key->get_value("allow2")->get_data();
			my @vals = split(/\|/,$allow2);
			::rptMsg("");
			::rptMsg("whitelist");
			foreach my $v (@vals) {
				next if ($v eq "");
				::rptMsg("  ".$v);
			}
			::rptMsg("");
		};
		
		my $lastmod;
		eval {
			$lastmod = $key->get_value("lastmod")->get_data();
			::rptMsg("lastmod ".gmtime($lastmod)." (UTC)");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;