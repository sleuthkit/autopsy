#-----------------------------------------------------------
# speech.pl
# The key and values in question are associated with the Windows text-to-speech
# functionality.  It turns out that there are several malware variants, including
# ransomware (Cerber, MiliCry) that deliver an audio message.  While not definitive,
# the results of this plugin provide a low fidelity indicator that may be useful.
#
# Change history
#   20201005 - MITRE update
#   20200427 - updated output date format
#	  20191010 - created
#
# References
#   https://www.trendmicro.com/vinfo/us/threat-encyclopedia/malware/ransom_cerber.vsafi
#   https://www.trendmicro.com/vinfo/us/threat-encyclopedia/malware/ransom_milicry.gqs
#   
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package speech;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1059",
              category      => "program execution",
			  output		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Get values from user's Speech key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching speech v.".$VERSION);
	::rptMsg("speech v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Speech";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		
		eval {
			::rptMsg("CurrentUserLexicon Datafile value    : ".$key->get_subkey("CurrentUserLexicon\\{C9E37C15-DF92-4727-85D6-72E5EEB6995A}\\Files")->get_value("Datafile")->get_data());
		  ::rptMsg("");
		};
		
		eval {
			::rptMsg("Voices DefaultTokenId value          : ".$key->get_subkey("Voices")->get_value("DefaultTokenId")->get_data());
			::rptMsg("");
		};
		
		eval {
			::rptMsg("PhoneConverters DefaultTokenId value : ".$key->get_subkey("PhoneConverters")->get_value("DefaultTokenId")->get_data());
		};
		::rptMsg("Analysis Tip: A few ransomware variants have been observed providing indications of infection via the MS text-to-speech function.");
		::rptMsg("A such, this plugin may provide low fidelity indicators of malicious activity.");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;