#-----------------------------------------------------------
# drwatson.pl
# Author: Don C. Weber
# Plugin for Registry Ripper; Access Software hive file to get the
# Dr. Watson settings from Software hive
# 
# Change history
#
#
# References
#  Dr Watson: http://www.windowsnetworking.com/kbase/WindowsTips/Windows2000/RegistryTips/RegistryTools/DrWatson.html
# 
# Author: Don C. Weber, http://www.cutawaysecurity.com/blog/cutaway-security
#-----------------------------------------------------------
package drwatson;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20081219);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Dr. Watson settings from Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching drwatson v.".$VERSION);
	::rptMsg("drwatson v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\AeDebug";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		($key->get_value('Auto') == 0x0) ? ::rptMsg("Debugging is Disabled") : ::rptMsg("Debugging is Enabled");
		eval {
			::rptMsg("Debugger: ".$key->get_value('Debugger')->get_data());
		};
			
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
		
	::rptMsg("");
	$key_path = "Microsoft\\DrWatson";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		($key->get_value('LogFilePath')) ? ::rptMsg("DrWatson LogFile Path location: ".$key->get_value('LogFilePath')->get_data()) : ::rptMsg("DrWatson LogFile Path location: %SystemRoot%\\Documents and Settings\\All Users\\Documents\\DrWatson");
		($key->get_value('CreateCrashDump') == 0x0) ? ::rptMsg("CreateCrashDump is Disabled") : ::rptMsg("CreateCrashDump is Enabled");
		($key->get_value('CrashDumpFile')) ? ::rptMsg("Crash Dump Path and Name: ".$key->get_value('CrashDumpFile')->get_data()) : ::rptMsg("CrashDumpFile is not set");
		($key->get_value('AppendToLogFile') == 0x0) ? ::rptMsg("AppendToLogFile is set to create a new file each time") : ::rptMsg("AppendToLogFile is set to append");
			
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
	::rptMsg("");
	::rptMsg("Analysis Tips: For Dr. Watson settings information check: http://www.windowsnetworking.com/kbase/WindowsTips/Windows2000/RegistryTips/RegistryTools/DrWatson.html");
}

1;
