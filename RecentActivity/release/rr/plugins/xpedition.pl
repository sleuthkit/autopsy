#-----------------------------------------------------------
# xpedition.pl
# Determine the edition of XP (MediaCenter, TabletPC)
#
# History
#
# References
#   http://windowsitpro.com/article/articleid/94531/
#      how-can-a-script-determine-if-windows-xp-tablet-pc-edition-is-installed.html
#   http://unasked.com/question/view/id/119610
#
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package xpedition;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090727);

sub getConfig{return %config}
sub getShortDescr {
	return "Queries System hive for XP Edition info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	my $edition = 0;
	
	::logMsg("Launching xpedition v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::rptMsg("xpedition v.".$VERSION);
	eval {
		$key = $root_key->get_subkey("WPA\\MediaCenter")->get_value("Installed")->get_data();
		if ($key == 1) {
			::rptMsg("MediaCenter Edition"); 
			$edition = 1;
		}
	};
	
	eval {
		$key = $root_key->get_subkey("WPA\\TabletPC")->get_value("Installed")->get_data();
		if ($key == 1) {
			::rptMsg("TabletPC Edition"); 
			$edition = 1;
		}
	};	
}
1