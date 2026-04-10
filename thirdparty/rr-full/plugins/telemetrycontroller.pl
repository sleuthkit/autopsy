#-----------------------------------------------------------
# telemetrycontroller.pl
#
# Change history
#   20220707 - added Scythe blog reference
#   20220328 - updated with values beneath TelemetryController key
#   20200609 - content created in appcompatflags.pl plugin
#   
# References
#   https://www.trustedsec.com/blog/abusing-windows-telemetry-for-persistence/
#   https://www.scythe.io/library/windows-telemetry-persistence
#
#  https://attack.mitre.org/techniques/T1546/
#
# Copyright 2022 Quantum Analytics Research, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package telemetrycontroller;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
              category      => "persistence",
			  output		=> "report",
              version       => 20220707);
			  
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Checks for persistence beneath the TelemetryController subkey";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching telemetrycontroller v.".$VERSION);
	::rptMsg("telemetrycontroller v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());    
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\TelemetryController";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $t = $key->get_value("RunsBlocked")->get_data();
			::rptMsg(sprintf "%-20s %-20s","RunsBlocked",$t);
		};
		
		eval {
			my ($t0,$t1) = unpack("VV",$key->get_value("LastMaintenanceRun")->get_data());
			if ($t0 > 0 && $t1 > 0) {
				::rptMsg(sprintf "%-20s %-20s","LastMaintenanceRun",::format8601Date(::getTime($t0,$t1))."Z");
			}
			else {
				::rptMsg(sprintf "%-20s %-20s","LastMaintenanceRun","0");
			}
		};
		
		eval {
			my ($t0,$t1) = unpack("VV",$key->get_value("LastNormalRun")->get_data());
			if ($t0 > 0 && $t1 > 0) {
				::rptMsg(sprintf "%-20s %-20s","LastNormalRun",::format8601Date(::getTime($t0,$t1))."Z");
			}
			else {
				::rptMsg(sprintf "%-20s %-20s","LastNormalRun","0");
			}
		};
		
		eval {
			my ($t0,$t1) = unpack("VV",$key->get_value("LastOobeRun")->get_data());
			if ($t0 > 0 && $t1 > 0) {
				::rptMsg(sprintf "%-20s %-20s","LastOobeRun",::format8601Date(::getTime($t0,$t1))."Z");
			}
			else {
				::rptMsg(sprintf "%-20s %-20s","LastOobeRun","0");
			}
		};
		
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys($key);
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($key_path."\\".$s->get_name());
				::rptMsg(sprintf "%-15s %-20s","LastWrite time",::format8601Date($s->get_timestamp())."Z");
				
				my @vals = $s->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						next if ($v->get_name() eq "");
						::rptMsg(sprintf "%-15s %-20s",$v->get_name(),$v->get_data());
					}
				}
				::rptMsg("");
			}
		}
	}
	::rptMsg("Analysis Tip: TelemetryController subkeys can be used for persistence.");
	::rptMsg("");
	::rptMsg("Ref: https://www.trustedsec.com/blog/abusing-windows-telemetry-for-persistence/");
	::rptMsg("Ref: https://www.scythe.io/library/windows-telemetry-persistence");
}

1;
