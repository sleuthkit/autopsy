#-----------------------------------------------------------
# localdumps.pl
# Get WER LocalDumps settings
#
# Change history:
#   20220419 - updated references
#   20210107 - created
#
# References:
#   https://twitter.com/Hexacorn/status/1346579978549399552
#   https://twitter.com/daniel_bilar/status/988925269229568000
#   https://docs.microsoft.com/en-us/windows/win32/wer/collecting-user-mode-dumps
#   https://bmcder.com/blog/extracting-cobalt-strike-from-windows-error-reporting (added 20220419)
#       
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package localdumps;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562\.001",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20220419);

sub getConfig{return %config}

sub getShortDescr {
	return "Get WER LocalDumps settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching localdumps v.".$VERSION);
	::rptMsg("localdumps v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows\\Windows Error Reporting\\LocalDumps");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("");
			
			eval {
				my $folder = $key->get_value("DumpFolder")->get_value();
				::rptMsg("DumpFolder value = ".$folder);
				::rptMsg("");
			};
			
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					
					eval {
						my $folder = $s->get_value("DumpFolder")->get_value();
						::rptMsg($s->get_name()." DumpFolder value = ".$folder);
						::rptMsg("");
					};
					
				}
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::rptMsg("");
		}
	}
	::rptMsg("Analysis Tip: The location where user-mode dumps are written can be configured, either universally, or for ");
	::rptMsg("specific applications.  This means that a dump can be written to a UNC path, controlled by the threat actor.");
	::rptMsg("");
	::rptMsg("Ref: https://bmcder.com/blog/extracting-cobalt-strike-from-windows-error-reporting");
}
1;