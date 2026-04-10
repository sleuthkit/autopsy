#-----------------------------------------------------------
# iso.pl
# Plugin to extract ISO file mounting settings
#
# History
# 20220829 - created
#
# References
#  https://malicious.link/post/2022/blocking-iso-mounting/
#
# copyright 2022, QAR LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package iso;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  category      => "persistence",
			  MITRE	        => "T1546\.001",
			  output 		=> "report",
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
	::logMsg("Launching iso v.".$VERSION);
	::rptMsg("iso v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key = ();
	my $key_path = "Classes";
	my @types = ("Windows\.IsoFile","Windows\.VhdFile");
	 
	if ($key = $root_key->get_subkey($key_path)) {
		foreach my $t (@types) {
	
			eval {
				my $path = $t."\\shell\\mount\\command";
				my $cmd = $key->get_subkey($path)->get_value("")->get_data();
				::rptMsg($path);
				::rptMsg("LastWrite time: ".::format8601Date($key->get_subkey($path)->get_timestamp())."Z");
				::rptMsg("Cmd: ".$cmd);
			};
			
			if ($t eq "Windows\.IsoFile") {
				eval {
					my $path = $t."\\shell\\mount\\command";
					if (my $p = $key->get_subkey($path)->get_value("ProgrammaticAccessOnly")) {
						::rptMsg("ProgrammaticAccessOnly value found\.");
					}
					else {
						::rptMsg("ProgrammaticAccessOnly value not found\.");
					}
				};
			}	
			::rptMsg("");			
		}
	}
	::rptMsg("Analysis Tip: MS has default settings for mounting various file types (ISO,IMG,VHD)\. The addition of the ");
	::rptMsg("\"ProgrammaticAccessOnly\" value removes the context menu for ISO/IMG files.");
	::rptMsg("");
	::rptMsg("Ref: https://malicious.link/post/2022/blocking-iso-mounting/");
}
1;