#-----------------------------------------------------------
# appenvironment.pl
# 
# 
# Change history
#   20230726 - updated to include AppExit key
#   20230725 - created
#
# References
#   https://nssm.cc/usage
# 
# copyright 2023 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appenvironment;
#use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "persistence",
              MITRE         => "T1547",
			  output        => "report",
              version       => 20230726);

sub getConfig{return %config}
sub getShortDescr {
	return "Check services for AppEnvironment/AppEnvironmentExtra values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching appenvironment v.".$VERSION);
	::rptMsg("appenvironment v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services";
	my $key;
	
	my $count1 = 0;
	my $count2 = 0;
	my $count3 = 0;
	
	if ($key = $root_key->get_subkey($key_path)) {
		my @serv = $key->get_list_of_subkeys();
		if (scalar @serv > 0) {
			foreach my $s (@serv) {
				eval {
					my $a = $s->get_subkey("Parameters")->get_value("AppEnvironment")->get_data();
					::rptMsg("AppEnvironment value: ".$a);
					$count1++;
				};
#				::rptMsg("AppEnvironment value not found.") if ($@);
				
				eval {
					my $a = $s->get_subkey("Parameters")->get_value("AppEnvironmentExtra")->get_data();
					::rptMsg("AppEnvironmentExtra value: ".$a);
					$count2++;
				};
#				::rptMsg("AppEnvironmentExtra value not found.") if ($@);

# check for AppExit key				
				eval {
					if ($s->get_subkey("Parameters\\AppExit")) {
						::rptMsg($key_path."\\".$s->get_name()."\\Parameters\\AppExit key found.");
						::rptMsg("LastWrite time: ".::format8601Date($s->get_subkey("Parameters\\AppExit")->get_timestamp())."Z");
						::rptMsg("");
						
						my $k = $s->get_subkey("Parameters\\AppExit");
						$count3++;
						my @vals = $k->get_list_of_values();
						if (scalar @vals > 0) {
							foreach my $v (@vals) {
								::rptMsg(sprintf "%-10s %-10s",$v->get_name(),$v->get_data());
							}
						}
						else {
# no values found						
						}
					}
					else {
#						::rptMsg($key_path."\\".$s->get_name()."\\Parameters\\AppExit key not found.");
					}
				};
			}
		}
		else {
# Services key has no subkeys		
		}
		::rptMsg("No AppEnvironment values found.") if ($count1 == 0);
		::rptMsg("No AppEnvironmentExtra values found.") if ($count2 == 0);
		::rptMsg("No Parameters\\AppExit keys found.") if ($count3 == 0);
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: The AppEnvironment and AppEnvironmentExtra values allow a service to have access to environment");
	::rptMsg("variables that override those set by the system at service startup. These values are used by svrany\.exe and ");
	::rptMsg("nssm\.exe.");
	::rptMsg("");
	::rptMsg("Nssm\.exe makes use of the Parameters\\AppExit subkey to determine actions to take upon exit, and can be used");
	::rptMsg("to specify specific actions based on the app's exit code.");
	::rptMsg("");
	::rptMsg("Ref: https://nssm.cc/usage");
}

1;