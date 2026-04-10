#-----------------------------------------------------------
# screenshotindex.pl 
# 
#
# Change history
#  20230713 - created
#
# References
#  https://twitter.com/keydet89/status/1679474166183936001
#  https://www.tenforums.com/tutorials/6108-reset-screenshot-index-counter-windows-10-a.html
# 
# copyright 2023 QAR,LLC
# author: H. Carvey keydet89@yahoo.com
#-----------------------------------------------------------
package screenshotindex;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "collection",
              MITRE         => "T1074\.001", # local data staging
              version       => 20230713);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks user's ScreenshotIndex value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching screenshotindex v.".$VERSION);
	::rptMsg("screenshotindex v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
 	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer";
 
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $start = $key->get_value("ScreenshotIndex")->get_data();
			::rptMsg($key_path." ScreenshotIndex value = ".$start);
		};
		::rptMsg($key_path." ScreenshotIndex value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." key not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: When a user takes a screenshot via Win + PrtScr, and automatically saves the file, the files is");
	::rptMsg("saved to the user's \"\\Pictures\\Screenshots\" folder, and the ScreenshotIndex value is incremented. This is ");
	::rptMsg("a possible means of data collection for a threat actor, or an insider threat.");
	::rptMsg("");
	::rptMsg("Ref: https://www.tenforums.com/tutorials/6108-reset-screenshot-index-counter-windows-10-a.html");
}

1;