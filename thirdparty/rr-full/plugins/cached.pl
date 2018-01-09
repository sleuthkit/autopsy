#-----------------------------------------------------------
# cached.pl
# Plugin to get cached shell extensions list from the 
#   NTUSER.DAT hive
#
# History:
#   20150608 - created
#
# References:
#   http://herrcore.blogspot.com.tr/2015/06/malware-persistence-with.html
#   http://www.nobunkum.ru/analytics/en-com-hijacking
#
#
# copyright 2015 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cached;
use strict;

my %config = (hive          => "NTUSER.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150608);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets cached Shell Extensions from NTUSER.DAT hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %clsids = ("{3EC36F3E-5BA3-4C3D-BF39-10F76C3F7CC6}" => "IDriveFolderExt",
              "{000214E4-0000-0000-C000-000000000046}" => "IContextMenu",
              "{7F9185B0-CB92-43C5-80A9-92277A4F7B54}" => "IExecuteCommand",
              "{000214FA-0000-0000-C000-000000000046}" => "IExtractIconW",
              "{000214F9-0000-0000-C000-000000000046}" => "IShellLinkW",
              "{0C6C4200-C589-11D0-999A-00C04FD655E1}" => "IShellIconOverlayIdentifier",
              "{BDDACB60-7657-47AE-8445-D23E1ACF82AE}" => "IExplorerCommandState",
              "{0C733A8A-2A1C-11CE-ADE5-00AA0044773D}" => "IDBProperties",
              "{ADD8BA80-002B-11D0-8F0F-00C04FD7D062}" => "IDelegateFolder",
              "{000214E6-0000-0000-C000-000000000046}" => "IShellFolder",
              "{000214FC-0000-0000-C000-000000000046}" => "IShellCopyHookW",
              "{A08CE4D0-FA25-44AB-B57C-C7B1C323E0B9}" => "IExplorerCommand",
              "{00000122-0000-0000-C000-000000000046}" => "IDropTarget");
              
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cached v.".$VERSION);
	::rptMsg("cached v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Cached";;
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		if (scalar (@vals) > 0) {
			foreach my $v (@vals) {
				my ($clsid1, $clsid2, $mask) = split(/\s/,$v->get_name(),3);
				my @t = unpack("VV",substr($v->get_data(),8,8));
				my $tm = gmtime(::getTime($t[0],$t[1]));
				my $str = $tm."  First Load: ".$clsid1." (";
				if (exists $clsids{$clsid2}) {
					$str .= $clsids{$clsid2}.")";
				}
				else {
					$str .= $clsid2.")";
				}
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
