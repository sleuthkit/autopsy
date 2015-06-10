#-----------------------------------------------------------
# ares.pl
# 
#
# Change History
#   20140730 - updated search terms detection (G. Neives)
#   20130312 - updated based on data provided by J. Weg
#   20120507 - modified to remove the traversing function, to only get
#              a limited amount of data.
#   20110603 - modified F. Kolenbrander
#	       parsing some values according ares source code, like searches and 
#        timestamps.
#   20110530 - created
#   
# References
#
# 
# copyright 2012 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ares;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140730);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Software/Ares key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}


my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ares v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Ares';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		::rptMsg("");
		my %ares = ();
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				$ares{$v->get_name()} = $v->get_data();
			}
			::rptMsg("");
			::rptMsg("RegisterEmail: ".$ares{"RegisterEmail"}) if (exists $ares{"RegisterEmail"});
			::rptMsg("Stats\.LstConnect: ".gmtime($ares{"Stats\.LstConnect"})." UTC") if (exists $ares{"Stats\.LstConnect"});
			::rptMsg("Personal\.Nickname: ".hex2ascii($ares{"Personal\.Nickname"})) if (exists $ares{"Personal\.Nickname"});
			::rptMsg("General\.Language: ".hex2ascii($ares{"General\.Language"})) if (exists $ares{"General\.Language"});
			::rptMsg("PrivateMessage\.AwayMessage: ".hex2ascii($ares{"PrivateMessage\.AwayMessage"})) if (exists $ares{"PrivateMessage\.AwayMessage"});

		}
		else {
			::rptMsg($key->get_name()." has no values.");
		}
		::rptMsg("");
		getSearchTerms($key);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub getSearchTerms {
	my $key = shift;
	
	my $count = 0;
	::rptMsg("Search Terms:");
	my @subkeys = ("audio\.gen","gen\.gen","image\.gen","video\.aut","video\.dat","video\.gen","video\.tit");
	
	foreach my $sk (@subkeys) {
		my $gen = $key->get_subkey("Search\.History")->get_subkey($sk);
		my @vals = $gen->get_list_of_values();
		if (scalar(@vals) > 0) {
			$count = 1;
			::rptMsg($gen->get_name());
			::rptMsg("LastWrite: ".gmtime($gen->get_timestamp())." (UTC)");
			foreach my $v (@vals) {
				next if ($v->get_name() eq "");
				::rptMsg("  ".hex2ascii($v->get_name()));
			}
		}
	}
	::rptMsg("No search terms found\.") if ($count == 0);
}

sub hex2ascii {
  return pack('H*',shift); 
}

1;
