#-----------------------------------------------------------
# schedagent
# Get contents of SchedulingAgent key from Software hive 
#
# History
#   20200925 - MITRE update
#   20200518 - updated date output format
#   20100817 - created
#
# copyright 2020 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package schedagent;
use strict;

my %config = (hive          => "Software",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
			  output		=> "report",
              version       => 20200925);

sub getConfig{return %config}

sub getShortDescr {
	return "Get SchedulingAgent key contents";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching schedagent v.".$VERSION);
	::rptMsg("schedagent v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\SchedulingAgent";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my ($oldname,$logpath,$folder,$lastrun,$size);
		eval {
			$oldname = $key->get_value("OldName")->get_data();
			::rptMsg("OldName      = ".$oldname);
		};
		
		eval {
			$logpath = $key->get_value("LogPath")->get_data();
			::rptMsg("LogPath      = ".$logpath);
		};
		
		eval {
			$size = $key->get_value("MaxLogSizeKB")->get_data();
			::rptMsg("MaxLogSizeKB = ".$size);
		};
		
		eval {
			$folder = $key->get_value("TasksFolder")->get_data();
			::rptMsg("TasksFolder  = ".$folder);
		};
#		
		eval {
			$lastrun = $key->get_value("LastTaskRun")->get_data();
			::rptMsg("LastTaskRun  = ".::convertSystemTime($lastrun)."Z");
			::rptMsg("");
			::rptMsg("Note: LastTaskRun time is written in local system time, not GMT");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;