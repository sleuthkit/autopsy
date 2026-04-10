#-----------------------------------------------------------
# speech_tln.pl
# The key and values in question are associated with the Windows text-to-speech
# functionality.  It turns out that there are several malware variants, including
# ransomware (Cerber, MiliCry) that deliver an audio message.  While not definitive,
# the results of this plugin provide a low fidelity indicator that may be useful.
#
# Change history
#   20201005 - MITRE update
#	  20191010 - created
#
# References
#   https://www.trendmicro.com/vinfo/us/threat-encyclopedia/malware/ransom_cerber.vsafi
#   https://www.trendmicro.com/vinfo/us/threat-encyclopedia/malware/ransom_milicry.gqs
#   
# 
# copyright 2019-2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package speech_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1059",
              category      => "program execution",
			  output		=> "tln",
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
#	::logMsg("Launching speech v.".$VERSION);
#	::rptMsg("speech v.".$VERSION); 
# ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Speech";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		eval {
			my $file = $key->get_subkey("CurrentUserLexicon\\{C9E37C15-DF92-4727-85D6-72E5EEB6995A}\\Files");
			my $lw   = $file->get_timestamp();
			my $val  = $file->get_value("Datafile")->get_data();
			::rptMsg($lw."|REG|||Speech CurrentUserLexicon Datafile value : ".$val);
		};
		
		eval {
			my $voices = $key->get_subkey("Voices");
			my $lw     = $voices->get_timestamp();
			my $val    = $voices->get_value("DefaultTokenId")->get_data();
			::rptMsg($lw."|REG|||Speech Voices DefaultTokenId value : ".$val);
		};
		
		eval {
			my $phone = $key->get_subkey("PhoneConverters");
			my $lw    = $phone->get_timestamp();
			my $val   = $phone->get_value("DefaultTokenId")->get_data();
			::rptMsg($lw."|REG|||Speech PhoneConverters DefaultTokenId value : ".$val);
		};
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;