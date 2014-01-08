#-----------------------------------------------------------
# snapshot.pl
# Plugin to check the ActiveX component for the MS Access Snapshot
# Viewer kill bit
#
# Ref: US-CERT Vuln Note #837785, http://www.kb.cert.org/vuls/id/837785
# 
# Note: Look for each GUID key, and check for the Compatibility Flags value;
#       if the value is 0x400, the kill bit is set; a vulnerable system is 
#       indicated by having IE version 6.x, and the kill bits NOT set (IE 7
#       requires user interaction to download the ActiveX component
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package snapshot;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20080725);

sub getConfig{return %config}

sub getShortDescr {
	return "Check ActiveX comp kill bit; Access Snapshot";	
}
sub getDescr{}
sub getRefs {"US-CERT Vuln Note 837785" => "http://www.kb.cert.org/vuls/id/837785"}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my @guids = ("{F0E42D50-368C-11D0-AD81-00A0C90DC8D9}",
      			 "{F0E42D60-368C-11D0-AD81-00A0C90DC8D9}",
      			 "{F2175210-368C-11D0-AD81-00A0C90DC8D9}");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching snapshot v.".$VERSION);
	::rptMsg("snapshot v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Microsoft\\Internet Explorer";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("ActiveX Snapshot Vuln");
		::rptMsg($key_path);
		::rptMsg("");
		my $ver;
		eval {
			$ver = $key->get_value("Version")->get_data();
		};
		if ($@) {
			::rptMsg("IE Version not found.");
		}
		else {
			::rptMsg("IE Version = ".$ver)
		}
		
		::rptMsg("");
		foreach my $guid (@guids) {
			my $g;
			eval {
				$g = $key->get_subkey("ActiveX Compatibility\\".$guid);
			};
			if ($@) {
				::rptMsg("$guid not found.");
			}
			else {
				::rptMsg("GUID: $guid");
				my $flag;
				eval {
					$flag = $g->get_value("Compatibility Flags")->get_data();
				};
				if ($@) {
					::rptMsg("Compatibility Flags value not found.");
				}
				else {
					my $str = sprintf "Compatibility Flags  0x%x",$flag;
					::rptMsg($str); 
				}
			}
			::rptMsg("");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;