#-----------------------------------------------------------
# defender-autologger.pl
# Get WMI\AutoLogger settings for Defender
# 
# Change history
#   20220303 - created
#
# References
#  https://thedfirreport.com/2021/10/18/icedid-to-xinglocker-ransomware-in-24-hours/
# 
#  MITRE: https://attack.mitre.org/techniques/T1562/002/
#
# copyright 2022 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package defenderautologger;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "defense evasion",
              MITRE         => "T1562\.001",
              version       => 20220303);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Defender AutoLogger settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching defender-autologger v.".$VERSION);
	::rptMsg("defender-autologger v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
    ::rptMsg("MITRE ATT&CK technique: ".$config{MITRE}." (".$config{category}.")");
    ::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\WMI\\AutoLogger";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $a = $key->get_subkey("DefenderApiLogger")->get_value("Start")->get_data();
			::rptMsg("DefenderApiLogger   Start value: ".$a);
		};
		
		eval {
			my $a = $key->get_subkey("DefenderAuditLogger")->get_value("Start")->get_data();
			::rptMsg("DefenderAuditLogger Start value: ".$a);
		};

		::rptMsg("");
		::rptMsg("Analysis Tip: Threat actors, such as XingLocker, set the values to \"0\" to disable Defender logging.");
		::rptMsg("");
		::rptMsg("Ref: https://thedfirreport.com/2021/10/18/icedid-to-xinglocker-ransomware-in-24-hours/");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;