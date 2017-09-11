#-----------------------------------------------------------
# cmd_shell
# 
# Change History
#   20130405 - added Clients subkey
#   20100830 - added "cs" shell command to the path
#   20080328 - created
# 
# References
#   http://www.microsoft.com/security/portal/Threat/Encyclopedia/Entry.aspx?
#        Name=TrojanClicker%3AWin32%2FVB.GE
#
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cmd_shell;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20130405);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets shell open cmds for various file types";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("You Are Unable to Start a Program with an .exe File Extension" =>
	            "http://support.microsoft.com/kb/310585");
	return %refs;	
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cmd_shell v.".$VERSION);
	::rptMsg("cmd_shell v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my @shells = ("exe","cmd","bat","cs","hta","pif");
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $sh (@shells) {
		my $key_path = "Classes\\".$sh."file\\shell\\open\\command";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#			::rptMsg("");
			my $val;
			eval {
				$val = $key->get_value("")->get_data();
				::rptMsg("  Cmd: ".$val);
				
				if ($sh eq "hta") {
					if ($val eq "C:\\Windows\\SysWOW64\\mshta\.exe \"%1\" %*" || $val eq "C:\\WINDOWS\\system32\\mshta\.exe \"%1\" %*") {
						
					}
					else {
						::alertMsg("ALERT: cmd_shell: ".$key_path." warning: ".$val);
					}
				}
				else {
					::alertMsg("ALERT: cmd_shell: ".$key_path." warning: ".$val) unless ($val eq "\"%1\" %*");
				}
				
				::rptMsg("");
			};
			::rptMsg("Error: ".$@) if ($@);
		
		}
		else {
			::rptMsg($key_path." not found.");
			::rptMsg("");
		}
	}
	::rptMsg("");
	
	my $key_path = "Clients\\StartMenuInternet\\IExplore.exe\\shell\\open\\command";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		eval {
			my $cmd = $key->get_value("")->get_data();
			::rptMsg("  Cmd: ".$cmd);
			
			if ($cmd eq "\"C:\\Program Files\\Internet Explorer\\iexplore\.exe\"" || 
			  $cmd eq "\"C:\\Program Files (x86)\\Internet Explorer\\iexplore\.exe\"") {
			  
			}
			else {
				::alertMsg("ALERT: cmd_shell: ".$key_path." warning: ".$cmd);
			}
		};
		::rptMsg("Error: ".$@) if ($@);
	}
	else {
		::rptMsg($key_path." not found\.");
	}

}
1;