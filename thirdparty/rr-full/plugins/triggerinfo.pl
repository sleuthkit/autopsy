#-----------------------------------------------------------
# triggerinfo.pl
# 
# 
# References:
#   https://docs.microsoft.com/en-us/windows/win32/api/winsvc/ns-winsvc-service_trigger 
#   https://docs.microsoft.com/en-us/windows/win32/services/service-trigger-events
#
# Change history:
#  20201020 - created
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package triggerinfo;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
              category      => "persistence",
			  output		=> "report",
              version       => 20201020);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks Services TriggerInfo settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching triggerinfo v.".$VERSION);
	::rptMsg("triggerinfo v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path;
	my $key;

# System Hive
	my $ccs = ::getCCS($root_key);
	
	$key_path = $ccs."\\Services";
	if ($key = $root_key->get_subkey($key_path)){
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) { 
				if (my $trig = $s->get_subkey("TriggerInfo")) {
					::rptMsg($s->get_name());
					processTriggerInfo($trig);					
				}
				else {
# Service key does not have a TriggerInfo subkey					
				}
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: Services can be configured to perform actions based on trigger events.");
			::rptMsg("Ref: https://docs.microsoft.com/en-us/windows/win32/api/winsvc/ns-winsvc-service_trigger");
			::rptMsg("Ref: https://docs.microsoft.com/en-us/windows/win32/services/service-trigger-events");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub processTriggerInfo {
	my $key = shift;
	
	my @subkeys = ();
	if (@subkeys = $key->get_list_of_subkeys()) {
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("  ".$s->get_name());
				
				eval {
					my $g = $s->get_value("GUID")->get_data();
					my $guid = ::parseGUID($g);
					::rptMsg(sprintf "    %-10s %-50s","GUID",$guid); 
				};
				
				eval {
					my $action = $s->get_value("Action")->get_data();
					::rptMsg(sprintf "    %-10s %-50s","Action",$action);
				};
				
				eval {
					my $type = $s->get_value("Type")->get_data();
					::rptMsg(sprintf "    %-10s %-50s","Type",$type);
				};
				
				eval {
					my $type = $s->get_value("DataType0")->get_data();
#					::rptMsg(sprintf "    %-10s %-50s","DataType0",$type);
					my $d = $s->get_value("Data0")->get_data();
					if ($type == 2) {
						my $data = ::getUnicodeStr($d);
						$data =~ s/\00/ /g;
						::rptMsg(sprintf "    %-10s %-50s","Data0",$data);
					}
					elsif ($type == 1) {
						my $data = join ' ', unpack '(H2)*',$d;
						::rptMsg(sprintf "    %-10s %-50s","Data0",$data);
					}
					else {}
					
				};
				
			}
			::rptMsg("");
		}
	}
}

1;