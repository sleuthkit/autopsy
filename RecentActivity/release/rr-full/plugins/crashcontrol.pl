#-----------------------------------------------------------
# crashcontrol.pl
#
# Ref: 
#   http://support.microsoft.com/kb/254649
#   http://support.microsoft.com/kb/274598
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package crashcontrol;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081212);

sub getConfig{return %config}

sub getShortDescr {
	return "Get crash control information";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %dumpenabled = (0 => "None",
                   1 => "Complete memory dump",
                   2 => "Kernel memory dump",
                   3 => "Small (64kb) memory dump");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching crashcontrol v.".$VERSION);
	::rptMsg("crashcontrol v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		
		my $cc_path = "ControlSet00".$current."\\Control\\CrashControl";
		my $cc;
		
		if ($cc = $root_key->get_subkey($cc_path)) {
			
			eval {
				my $cde = $cc->get_value("CrashDumpEnabled")->get_data();
				::rptMsg("CrashDumpEnabled = ".$cde." [".$dumpenabled{$cde}."]");
			};
			
			eval {
				my $df = $cc->get_value("DumpFile")->get_data();
				::rptMsg("DumpFile         = ".$df);
			};
			
			eval {
				my $mini = $cc->get_value("MinidumpDir")->get_data();
				::rptMsg("MinidumpDir      = ".$mini);
			};
			
			eval {
				my $logevt = $cc->get_value("LogEvent")->get_data();
				::rptMsg("LogEvent         = ".$logevt);
				::rptMsg("  Logs an event to the System Event Log (event ID = 1001, source = Save Dump)") if ($logevt == 1);
			};
			
			eval {
				my $sendalert = $cc->get_value("SendAlert")->get_data();
				::rptMsg("SendAlert        = ".$sendalert);
				::rptMsg("  Sends a \'net send\' pop-up if a crash occurs") if ($sendalert == 1);
			};
			
			
		}	
		else {
			::rptMsg($cc_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;
