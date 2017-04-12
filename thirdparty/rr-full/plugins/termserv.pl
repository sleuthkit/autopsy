#-----------------------------------------------------------
# termserv.pl
# Plugin for Registry Ripper; 
# 
# Change history
#   20131007 - updated with Sticky Keys info
#   20130307 - updated with autostart locations
#   20100713 - Updated to include additional values, based on references
#   20100119 - updated
#   20090727 - created
#
# Category: Autostart
#
# References
#   Change TS listening port number - http://support.microsoft.com/kb/187623
#   Examining TS key - http://support.microsoft.com/kb/243215
#   Win2K8 TS stops listening - http://support.microsoft.com/kb/954398
#   XP/Win2K3 TSAdvertise value - http://support.microsoft.com/kb/281307
#   AllowTSConnections value - http://support.microsoft.com/kb/305608
#   TSEnabled value - http://support.microsoft.com/kb/222992
#   TSUserEnabled value - http://support.microsoft.com/kb/238965
#   
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package termserv;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130307);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Terminal Server values from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching termserv v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $ts_path = $ccs."\\Control\\Terminal Server";
		my $ts;
		if ($ts = $root_key->get_subkey($ts_path)) {
			::rptMsg($ts_path);
			::rptMsg("LastWrite Time ".gmtime($ts->get_timestamp())." (UTC)");
			::rptMsg("");
			::rptMsg("Reference: http://support.microsoft.com/kb/243215");
			::rptMsg("");
			
			my $ver;
			eval {
				$ver = $ts->get_value("ProductVersion")->get_data();
				::rptMsg("  ProductVersion = ".$ver);
			};
			::rptMsg("");
			
			my $fdeny;
			eval {
				$fdeny = $ts->get_value("fDenyTSConnections")->get_data();
				::rptMsg("  fDenyTSConnections = ".$fdeny);
				::rptMsg("  1 = connections denied");
			};
			::rptMsg("fDenyTSConnections value not found.") if ($@);
			::rptMsg("");
			
			my $allow;
			eval {
				$allow = $ts->get_value("AllowTSConnections")->get_data();
				::rptMsg("  AllowTSConnections = ".$allow);
				::rptMsg("  Ref: http://support.microsoft.com/kb/305608");
			};
			::rptMsg("");
			
			my $ad;
			eval {
				$ad = $ts->get_value("TSAdvertise")->get_data();
				::rptMsg("  TSAdvertise = ".$ad);
				::rptMsg("  0 = disabled, 1 = enabled (advertise Terminal Services)");
				::rptMsg("  Ref: http://support.microsoft.com/kb/281307");
			};
			::rptMsg("");
			
			my $enabled;
			eval {
				$enabled = $ts->get_value("TSEnabled")->get_data();
				::rptMsg("  TSEnabled = ".$enabled);
				::rptMsg("  0 = disabled, 1 = enabled (Terminal Services enabled)");
				::rptMsg("  Ref: http://support.microsoft.com/kb/222992");
			};
			::rptMsg("");
			
			my $user;
			eval {
				$user = $ts->get_value("TSUserEnabled")->get_data();
				::rptMsg("  TSUserEnabled = ".$user);
				::rptMsg("  1 = All users logging in are automatically part of the");
				::rptMsg("  built-in Terminal Server User group. 0 = No one is a");
				::rptMsg("  member of the built-in group.");
				::rptMsg("  Ref: http://support.microsoft.com/kb/238965");
			};
			::rptMsg("");
			
			my $help;
			eval {
				$help = $ts->get_value("fAllowToGetHelp")->get_data();
				::rptMsg("  fAllowToGetHelp = ".$user);
				::rptMsg("  1 = Users can request assistance from friend or a ");
				::rptMsg("  support professional.");
				::rptMsg("  Ref: http://www.pctools.com/guides/registry/detail/1213/");
			};
			
			::rptMsg("AutoStart Locations");
			eval {
				my $start = $ts->get_subkey("Wds\\rdpwd")->get_value("StartupPrograms")->get_data();
				::rptMsg("Wds\\rdpwd key");
				::rptMsg("  StartupPrograms: ".$start);
				::rptMsg("Analysis Tip: This value usually contains 'rdpclip'; any additional entries ");
				::rptMsg("should be investigated\.");
				::rptMsg("");
			};
			::rptMsg("  StartupPrograms value not found\.") if ($@);
			
			eval {
				my $init = $ts->get_subkey("WinStations\\RDP-Tcp")->get_value("InitialProgram")->get_data();
				::rptMsg("WinStations\\RDP-Tcp key");
				$init = "{blank}" if ($init eq "");
				::rptMsg("  InitialProgram: ".$init);
				::rptMsg("Analysis Tip: Maybe be empty; appears as '{blank}'");
			};
			::rptMsg(" InitialProgram value not found\.") if ($@);

# Sticky Keys info, added 20131007
# ref: http://www.room362.com/blog/2012/5/25/sticky-keys-and-utilman-against-nla.html					
			eval {
				::rptMsg("");
				my $ua = $ts->get_subkey("WinStations\\RDP-Tcp")->get_value("UserAuthentication")->get_data();
				::rptMsg("WinStations\\RDP-Tcp key");
				::rptMsg("  UserAuthentication: ".$ua);
				::rptMsg("Analysis Tip: If the UserAuthentication value is 0, the system may be");
				::rptMsg("susceptible to a priv escalation exploitation via Sticky Keys.  See:");
				::rptMsg("http://www.room362.com/blog/2012/5/25/sticky-keys-and-utilman-against-nla.html");
			};
			::rptMsg("UserAuthentication value not found\.") if ($@);
	
		}
		else {
			::rptMsg($ts_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;