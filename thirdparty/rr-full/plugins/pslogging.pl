#-----------------------------------------------------------
# pslogging.pl
#   
#
# Change history
#   20181209 - created
#
# References
#   https://getadmx.com/?Category=Windows_10_2016&Policy=Microsoft.Policies.PowerShell::EnableTranscripting
# 
#
# Copyright (c) 2018 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package pslogging;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 0,
              hasDescr      => 1,
              hasRefs       => 0,
              osmask        => 22,
              category      => "config settings",
              version       => 20181209);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Extracts PowerShell logging settings";
}
sub getRefs {}

sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching pslogging v.".$VERSION);
  ::rptMsg("pslogging v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("Software\\Policies\\Microsoft\\Windows\\PowerShell",
	             "Policies\\Microsoft\\Windows\\PowerShell");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			
# Execution Policy
			eval {
				my $e_s = $key->get_value("EnableScripts")->get_data();
				::rptMsg("  EnableScripts   = ".$e_s);
			};			
			
			eval {
				my $e_p = $key->get_value("ExecutionPolicy")->get_data();
				::rptMsg("  ExecutionPolicy = ".$e_p);
			};		
			
# Module Logging			
			eval {
				my $ml = $key->get_subkey("ModuleLogging")->get_value("EnableModuleLogging")->get_data();
				::rptMsg("  ModuleLogging, EnableModuleLogging = ".$ml);
			};
					
# ScriptBlock Logging			
			eval {  
				my $sbl = $key->get_subkey("ScriptBlockLogging")->get_value("EnableScriptBlockLogging")->get_data();
				::rptMsg("  ScriptBlockLogging, EnableScriptBlockLogging = ".$sbl);
			};
			
			eval {  
				my $sbil = $key->get_subkey("ScriptBlockLogging")->get_value("EnableScriptBlockInvocationLogging")->get_data();
				::rptMsg("  ScriptBlockLogging, EnableScriptBlockInvocationLogging = ".$sbil);
			};
# Transcription
			eval {
				my $t_enable = $key->get_subkey("Transcription")->get_value("EnableTranscripting")->get_data();
				::rptMsg("  Transcription, EnableTranscripting = ".$t_enable);
			};		
			
			eval {
				my $t_out = $key->get_subkey("Transcription")->get_value("OutputDirectory")->get_data();
				::rptMsg("  Transcription, OutputDirectory = ".$t_out);
			};	
			
			eval {
				my $t_eih = $key->get_subkey("Transcription")->get_value("EnableInvocationHeader")->get_data();
				::rptMsg("  Transcription, EnableInvocationHeader = ".$t_eih);
			};		
		
		}
		else {
			::rptMsg($key_path." not found.");
		}
	} 
}

1;
