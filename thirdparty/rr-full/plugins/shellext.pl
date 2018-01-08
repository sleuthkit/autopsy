#-----------------------------------------------------------
# shellext
# Plugin to get approved shell extensions list from the 
#   Software hive
#
# This plugin retrieves the list of approved shell extensions from
#   the Software hive; specifically, the "Shell Extensions\Approved"
#   key.  Once it has the names (GUID) and data (string) of each value,
#   it then goes to the Classes\CLSID\{GUID} key to get the name of/path to
#   the associated DLL, if available. It also gets the LastWrite time of the
#   Classes\CLSID\{GUID} key.
#
# Analysis of an incident showed that the intruder placed their malware in 
# the C:\Windows dir, using the same name as a known valid shell extension.
# When Explorer.exe launches, it reads the list of approved shell extensions,
# then goes to the Classes\CLSID key to get the path to the associated DLL.  The
# intruder chose a shell extension that did not have an explicit path, so when
# explorer.exe looked for it, it started in the C:\Windows dir, and never got to
# the legit DLL in the C:\Windows\system32 dir.
#
# References:
#   http://msdn.microsoft.com/en-us/library/ms682586%28VS.85%29.aspx
#   
#
# Note: This plugin can take several minutes to run
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package shellext;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100515);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Shell Extensions from Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %bhos;
	::logMsg("Launching shellext v.".$VERSION);
	::rptMsg("shellext v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved";;
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my %exts;
		
		my @vals = $key->get_list_of_values();
		if (scalar (@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				$exts{$name}{name} = $v->get_data();
				
				my $clsid_path = "Classes\\CLSID\\".$name;
				my $clsid;
				if ($clsid = $root_key->get_subkey($clsid_path)) {
					eval {
						$exts{$v->get_name()}{lastwrite} = $clsid->get_timestamp();
						$exts{$v->get_name()}{dll} = $clsid->get_subkey("InProcServer32")->get_value("")->get_data();
					};
				}
			}
			foreach my $e (keys %exts) {
				::rptMsg($e."  ".$exts{$e}{name});
				::rptMsg("  DLL: ".$exts{$e}{dll});
				::rptMsg("  Timestamp: ".gmtime($exts{$e}{lastwrite})." Z");
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
}
1;