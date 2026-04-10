#-----------------------------------------------------------
# wpbt.pl
# Get Windows Platform Binary Table Settings
# 
# Change history
#   20220718 - created
#
# References
#  https://persistence-info.github.io/Data/wpbbin.html
#  https://github.com/Jamesits/dropWPBT
#  
#
# copyright 2022 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wpbt;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "persistence",
              MITRE         => "T1542\.001",
              version       => 20220718);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Windows Platform Binary Table Settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching wpbt v.".$VERSION);
	::rptMsg("wpbt v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr());
  ::rptMsg("MITRE ATT&CK technique: ".$config{MITRE}." (".$config{category}.")");
  ::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Session Manager";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $a = $key->get_value("DisableWpbtExecution")->get_data();
			::rptMsg("DisableWpbtExecution value: ".$a);
		};

		::rptMsg("");
		::rptMsg("Analysis Tip: Setting the DisableWpbtExecution to \"1\" disables reading of the platform binary table.");
		::rptMsg("");
		::rptMsg("Ref: https://persistence-info.github.io/Data/wpbbin.html");
		::rptMsg("Ref: https://grzegorztworek.medium.com/using-uefi-to-inject-executable-files-into-bitlocker-protected-drives-8ff4ca59c94c");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;