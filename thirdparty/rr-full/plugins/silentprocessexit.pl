#-----------------------------------------------------------
# silentprocessexit
#
# Change history:
#  20220501 - updated based on "malmoeb" tweet
#  20201005 - MITRE update
#  20200517 - updated date output format
#  20180601 - created
# 
# Ref:
#  https://oddvar.moe/2018/04/10/persistence-using-globalflags-in-image-file-execution-options-hidden-from-autoruns-exe/
#  https://twitter.com/malmoeb/status/1520458148749971458
#  https://attack.mitre.org/techniques/T1546/
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package silentprocessexit;
use strict;

my %config = (hive          => "Software",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
			  output		=> "report",
              version       => 20220501);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of SilentProcessExit key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching silentProcessexit v.".$VERSION);
	::rptMsg("silentprocessexit v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $key_path = ('Microsoft\\Windows NT\\CurrentVersion\\SilentProcessExit');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @sk = $key->get_list_of_subkeys();
		if (scalar @sk > 0) {
			foreach my $s (@sk) {
				::rptMsg($s->get_name());
				::rptMsg("LastWrite: ".::format8601Date($s->get_timestamp())."Z");
				eval {
					::rptMsg("MonitorProcess: ".$s->get_value("MonitorProcess")->get_data());
				};
				
				eval {
					::rptMsg("ReportingMode : ".$s->get_value("ReportingMode")->get_data());
				};
				
				::rptMsg("");
			}
			::rptMsg("Analysis Tip: Application names listed indicate that when that process exits, another process may be launched.");
			::rptMsg("Review the below reference for other applicable settings. Also check \"Image File Execution Options\" key for a");
			::rptMsg("GlobalFlag value that includes 0x200");
			::rptMsg("");
			::rptMsg("Ref: https://oddvar.moe/2018/04/10/persistence-using-globalflags-in-image-file-execution-options-hidden-from-autoruns-exe/");
		}
	}
}
1;