#-----------------------------------------------------------
# wordstartup.pl
# Display location of MSWord startup folder, if changed
#
# Change history
#  20220529 - created 
#
# References
#  https://twitter.com/malmoeb/status/1530862908871163905
#  https://www.thewindowsclub.com/how-to-change-the-startup-folder-of-word#:~:text=Where%20is%20Word%20Startup%20folder,%5CMicrosoft%5CWord%5CSTARTUP.
#  https://insight-jp.nttsecurity.com/post/102hojk/operation-restylink-apt-campaign-targeting-japanese-companies
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  wordstartup;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1112",
			  output		=> "report",
              version       => 20220529);

sub getConfig{return %config}
sub getShortDescr {
	return "Display MSWord StartUp folder, if changed";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
	
my $VERSION = getVersion();
my $office_version;
           
sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching  wordstartup v.".$VERSION);
	::rptMsg("wordstartup v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("wordstartup v.".$VERSION);
	::rptMsg("MITRE ATT&CK: ".$config{category}." (".$config{MITRE}.")");
	::rptMsg("");
# First, let's find out which version of Office is installed
	my @version;
	my $key;
	my $key_path = "Software\\Microsoft\\Office";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@version,$name) if ($name =~ m/^\d/);
		}
	}
# Determine MSOffice version in use	
	my @v = reverse sort {$a<=>$b} @version;
	foreach my $i (@v) {
		eval {
			if (my $o = $key->get_subkey($i."\\User Settings")) {
				$office_version = $i;
			}
		};
	}
	
	my $key = "";
	my $key_path = "Software\\Policies\\Microsoft\\office\\".$office_version."\\word\\options";
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $start = $key->get_value("startup-path")->get_data();
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("MSWord STARTUP folder: ".$start);
		};
		::rptMsg("startup-path value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: By default, the MSWord STARTUP folder is located at \"%AppData%\\Roaming\\Microsoft\\Word\\STARTUP\"");
	::rptMsg("\.dot files in this folder may contain macros that are run each time MSWord is launched, and this folder can be");
	::rptMsg("changed via GPO or the Registry. Use of the MSWord STARTUP folder was observed in the RestyLink APT campaign:");
	::rptMsg("https://insight-jp.nttsecurity.com/post/102hojk/operation-restylink-apt-campaign-targeting-japanese-companies");
#	::rptMsg("");
}

1;