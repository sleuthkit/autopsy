#-----------------------------------------------------------
# run
# Get contents of Run key from Software & NTUSER.DAT hives
#
# History:
#   20230102 - created from run.pl
#
# Ref:
#   https://attack.mitre.org/techniques/T1547/001/
#
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package run_json;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              MITRE         => "T1547\.001",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "json",
              version       => 20230102);

sub getConfig{return %config}

sub getShortDescr {
	return "Get autostart key contents from Software/NTUSER\.DAT hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

# https://en.wikipedia.org/wiki/Windows_Registry
my %types = (0 => "REG_NONE",
             1 => "REG_SZ",
			 2 => "REG_EXPAND_SZ",
			 3 => "REG_BINARY",
			 4 => "REG_DWORD",
			 5 => "REG_DWORD_BIG_ENDIAN",
			 6 => "REG_LINK",
			 7 => "REG_MULTI_SZ",
			 8 => "REG_RESOURCE_LIST",
			 9 => "REG_FULL_RESOURCE_DESCRIPTOR",
			 10 => "REG_RESOURCE_REQUIREMENTS_LIST",
			 11 => "REG_QWORD");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::logMsg("Launching run_json v.".$VERSION);
#	::rptMsg("run_json v.".$VERSION); # banner
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @paths = ();

	if ($hive_guess eq "software") {
		@paths = ("Microsoft\\Windows\\CurrentVersion\\Run",
	             "Microsoft\\Windows\\CurrentVersion\\RunOnce",
	             "Microsoft\\Windows\\CurrentVersion\\RunServices",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	             "Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	             "Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	             "Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce");
	}
	elsif ($hive_guess eq "ntuser") {
		@paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunServices",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunServicesOnce",
	           "Software\\Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\".
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\".
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	           "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run");
	}
	else {}
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			
			::rptMsg("{");
			::rptMsg("  \"pluginname\": \"run_json\"");
			::rptMsg("  \"hive\": \"".$reg->get_filename()."\"");
			::rptMsg("  \"hive_timestamp\": \"".::format8601Date($reg->get_timestamp())."Z\"");
			::rptMsg("  \"key\": \"".$key_path."\"");
			::rptMsg("  \"LastWrite Time\": \"".::format8601Date($key->get_timestamp())."Z\"");
			
			my @vals = $key->get_list_of_values();
			
			::rptMsg("  \"Num_values\": \"".(scalar @vals)."\"");
			
			if (scalar @vals > 0) {
				::rptMsg("  \"members\": [");
				foreach my $v (@vals) {
					::rptMsg("    {");
					::rptMsg("      \"value\": \"".$v->get_name()."\"");
					::rptMsg("      \"type\": \"".$types{$v->get_type()}."\"");
					::rptMsg("      \"data\": \"".$v->get_data()."\"");
					::rptMsg("    },");
				}
				::rptMsg("  ]");
			}
			::rptMsg("}");
			::rptMsg("");
		}
		else {
#  If $key_path is not found, no need to do anything

		}
	}
}

1;