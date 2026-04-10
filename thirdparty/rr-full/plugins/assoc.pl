#-----------------------------------------------------------
# assoc.pl
# Plugin to extract file association data from the Software hive file
# Can take considerable time to run; recommend running it via rip.exe
#
# History
# 20220829 - updated, moved to active plugins folder, added MITRE mapping
# 20180117 - updated, based on input from Jean, jean.crush@hotmail.fr
# 20080815 - created
#
# References
#  https://cocomelonc.github.io/malware/2022/08/26/malware-pers-9.html
#
# copyright 2022, QAR LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package assoc;
use strict;

my %config = (hive          => "software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
			  category      => "persistence",
			  MITRE	        => "T1546\.001",
              version       => 20220829);

sub getConfig{return %config}

sub getShortDescr {
	return "Get shell\\open\\command settings for various file types";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching assoc v.".$VERSION);
	::rptMsg("assoc v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key = ();
	my $key_path = "Classes";
	my @types = ("exefile","evtfile","evtxfile","inifile","Excel\.CSV","WSFFile");
	 
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("assoc");
		foreach my $t (@types) {
	
			eval {
				my $path = $t."\\shell\\open\\command";
				my $cmd = $key->get_subkey($path)->get_value("")->get_data();
				::rptMsg($path);
				::rptMsg("LastWrite time: ".::format8601Date($key->get_subkey($path)->get_timestamp())."Z");
				::rptMsg("Cmd: ".$cmd);
				::rptMsg("");
			};
		}
		
	}
	::rptMsg("Analysis Tip: Malware can persist by taking over the default actions when a user double-clicks a particular file type.");
	::rptMsg("");
#	::rptMsg("");
	::rptMsg("Ref: https://cocomelonc.github.io/malware/2022/08/26/malware-pers-9.html");
}
1;