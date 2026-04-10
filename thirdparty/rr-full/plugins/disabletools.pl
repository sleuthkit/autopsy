#-----------------------------------------------------------
# disabletools.pl 
# Check settings that disable access to tools
#
# Change history
#  20220114 - created
#
# References
#   https://docs.microsoft.com/en-us/troubleshoot/windows-server/system-management-components/task-manager-disabled-by-administrator
#	http://systemmanager.ru/win2k_regestry.en/93466.htm
#   https://blog.malwarebytes.com/detections/pum-optional-disableregistrytools/
# 
# copyright 2022 QAR,LLC
# author: H. Carvey keydet89@yahoo.com
#-----------------------------------------------------------
package disabletools;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              category      => "defense evasion",
              MITRE         => "T1562\.001",
              version       => 20220114);

sub getConfig{return %config}
sub getShortDescr {
	return "Check settings disabling access to tools";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disabletools v.".$VERSION);
	::rptMsg("disabletools v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path;
	
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	} 
	
	if ($hive_guess eq "software") {
		$key_path = "Microsoft\\Windows\\Policies\\System";
	}
	elsif ($hive_guess eq "ntuser") {
		$key_path = "Software\\Microsoft\\Windows\\Policies\\System";
	}
	else {}
	
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
			
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-15s %-5s",$v->get_name(),$v->get_data());
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Access to Registry Tools, the Task Manager, etc., can be disabled via GPOs or direct access");
	::rptMsg("to the Registry\. Admins may disable access as a matter of policy, or threat actors may disable access as a");
	::rptMsg("means of hampering response\.");
}

1;