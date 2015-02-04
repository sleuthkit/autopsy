#-----------------------------------------------------------
# appinitdlls
#
# Change history:
#  20130425 - added alertMsg() functionality
#  20130305 - updated to address 64-bit systems
#  20080324 - created
# 
# Ref:
#  http://msdn.microsoft.com/en-us/library/windows/desktop/dd744762(v=vs.85).aspx
#  http://support.microsoft.com/kb/q197571
#
# copyright 2013 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appinitdlls;
use strict;

my %config = (hive          => "Software",
							category      => "autostart",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20130425);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of AppInit_DLLs value";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Working with the AppInit_DLLs Reg Value" => 
	            "http://support.microsoft.com/kb/q197571");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching appinitdlls v.".$VERSION);
	::rptMsg("appinitdlls v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my @paths = ('Microsoft\\Windows NT\\CurrentVersion\\Windows',
	         'Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\Windows');
	
	::rptMsg("AppInit_DLLs");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			
			eval {
				my $app = $key->get_value("AppInit_DLLs")->get_data();
				
				if ($app eq "") {
					$app = "{blank}";
				}
				else {
					::alertMsg("ALERT: appinitdlls: Entry not blank: ".$app);
				}
				::rptMsg("  AppInit_DLLs : ".$app);
			};
			
			eval {
				my $load = $key->get_value("LoadAppInit_DLLs")->get_data();
				::rptMsg("  LoadAppInit_DLLs : ".$load);
				::rptMsg("*LoadAppInit_DLLs value globally enables/disables AppInit_DLLS\.");
				::rptMsg("0 = disabled (default)");
			};
			
			eval {
				my $req = $key->get_value("RequireSignedAppInit_DLLs")->get_data();
				::rptMsg("  RequireSignedAppInit_DLLs : ".$req);
			};
			
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("Analysis Tip: The AppInit_DLLs value should be blank; any DLL listed");
	::rptMsg("is launched with each user-mode process\.  ");
}
1;