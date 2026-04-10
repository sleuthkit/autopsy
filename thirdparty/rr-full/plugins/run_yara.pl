#-----------------------------------------------------------
# run_yara
# Get contents of Run key from Software & NTUSER.DAT hives
#
# History:
#   20230811 - created
#
# Ref:
#   
#
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package run_yara;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              MITRE         => "T1547\.001",
              category      => "persistence",
			  output        => "yara",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20230811);

sub getConfig{return %config}

sub getShortDescr {
	return "Get autostart key contents from Software hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $path_to_yara = ".\\yara64\.exe";
my $path_to_rule_file = ".\\test\.yar";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching run_yara v.".$VERSION);
	::rptMsg("run_yara v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	
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
			
			
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				::rptMsg($key_path);
				::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					
					::rptMsg("Value name: ".$name);
					my $temp_file = ".\\".$name;
					open(FH,">",$temp_file);
					print FH $data;
					close(FH);
					
					eval {
						my $output = qx/$path_to_yara -s -m $path_to_rule_file \"$temp_file\"/;
						if ($output eq "" || $output eq "\n") {
			
						}
						else {
							::rptMsg($output);
						}	

					};
					
					
					unlink($temp_file);
					
				}
				::rptMsg("");
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		
		}
		else {
#			::rptMsg($key_path." not found.");
#			::rptMsg("");
		}
	}
}


#------------------------------------------------------------------------------
#
#
#------------------------------------------------------------------------------


1;