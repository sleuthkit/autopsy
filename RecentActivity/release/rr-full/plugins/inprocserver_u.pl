#-----------------------------------------------------------
# inprocserver_u.pl
# Plugin to extract file association data from the Software hive file
# Can take considerable time to run; recommend running it via rip.exe
#
# History
#   20130429 - added alertMsg() functionality
#   20130212 - fixed retrieving LW time from correct key
#   20121219 - created
#
# To-Do:
#   - add support for NTUSER.DAT (XP) and USRCLASS.DAT (Win7)
#
# References
#   http://www.sophos.com/en-us/why-sophos/our-people/technical-papers/zeroaccess-botnet.aspx
#   Apparently, per Sophos, ZeroAccess remains persistent by modifying a CLSID value that
#   points to a WMI component.  The key identifier is that it employs a path to 
#   "\\.\globalroot...", hence the match function.
#
# copyright 2012, Quantum Analytics Research, LLC
#-----------------------------------------------------------
package inprocserver_u;
use strict;

my %config = (hive          => "USRCLASS\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130429);

sub getConfig{return %config}

sub getShortDescr {
	return "Checks CLSID InProcServer32 values for indications of ZeroAccess infection";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %clsid;
	my %susp = ();
	
	::logMsg("Launching inprocserver_u v.".$VERSION);
	::rptMsg("inprocserver_u v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "CLSID";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
# First step will be to get a list of all of the file extensions
		my %ext;
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my $name = $s->get_name();
				eval {
					my $n = $s->get_subkey("InprocServer32")->get_value("")->get_data();
#					::rptMsg("  -> ".$n);
					if (($n =~ m/^C:\\Users/) || grep(/Recycle/,$n) || grep(/RECYCLE/,$n)|| grep(/globalroot/,$n) || $n =~ m/\\n\.$/) {
						my $lw = $s->get_subkey("InprocServer32")->get_timestamp();
						$susp{$lw}{name} = $name;
						$susp{$lw}{data} = $n;
					}
				};
				
			}
			
			if (scalar(keys %susp) > 0) {
				foreach my $t (sort {$a <=> $b} keys %susp) {
					::rptMsg("Key path: ".$key_path."\\".$susp{$t}{name});
					::rptMsg("LastWrite: ".gmtime($t));
					::rptMsg("Value Data: ".$susp{$t}{data});
					::alertMsg($key_path."\\".$susp{$t}{name}.": ".$susp{$t}{data});
					::rptMsg("");
				}
			}
			else {
				::rptMsg("No suspicious InprocServer32 values found.");
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