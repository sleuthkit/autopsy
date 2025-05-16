#-----------------------------------------------------------
# comautoapproval.pl
# check the COMAutoApprovalList key for potential UAC bypasses; not all listed 
# value names will be for COM objects that actually exist on the system, so the
# plugin runs thru the HKLM\Software\Classes\CLSID subkeys to verify those that
# exist on that system.
#
# Change history:
#   20220829 - created
#
# References:
#   https://twitter.com/d4rksystem/status/1562507028337131520?s=20&t=3k45RhMaSRvLr6kNc0fdKg
#   https://swapcontext.blogspot.com/2020/11/uac-bypasses-from-comautoapprovallist.html
#   https://docs.velociraptor.app/exchange/artifacts/pages/windows.registry.comautoapprovallist/
#        
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package comautoapproval;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1548\.002",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20220829);

sub getConfig{return %config}

sub getShortDescr {
	return "Check COMAutoApprovalList for potential UAC bypasses";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $root_key = ();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $wd_count = 0;
	::logMsg("Launching comautoapproval v.".$VERSION);
	::rptMsg("comautoapproval v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	my $reg = Parse::Win32Registry->new($hive);
	$root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\UAC\\ComAutoApprovalList";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path      : ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
#				::rptMsg($v->get_name());
				processGUID($v->get_name());
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}

#	::rptMsg("");
	::rptMsg("Analysis Tip: The COMAutoApprovalList key provides a list of special, elevated COM objects that can");
	::rptMsg("lead to UAC bypasses\. This plugin runs through that list and enumerates those that exist within the ");
	::rptMsg("Software hive, providing information about each.");
	::rptMsg("");
	::rptMsg("Ref: https://swapcontext.blogspot.com/2020/11/uac-bypasses-from-comautoapprovallist.html");
}

sub processGUID {
	my $guid = shift;
	my $key_path = "Classes\\CLSID\\".$guid;
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			::rptMsg("  Class            : ".$key->get_value("")->get_data());
		};
		
		eval {
			::rptMsg("  Elevation\\Enabled: ".$key->get_subkey("Elevation")->get_value("Enabled")->get_data());
		};
		
		eval {  
			::rptMsg("  InProcServer32   : ".$key->get_subkey("InProcServer32")->get_value("")->get_data());
		};
	}
}

1;