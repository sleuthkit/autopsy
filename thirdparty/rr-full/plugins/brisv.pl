#-----------------------------------------------------------
# brisv.pl
# Plugin to detect the presence of Trojan.Brisv.A
# Symantec write-up: http://www.symantec.com/security_response/writeup.jsp
#                    ?docid=2008-071823-1655-99
#
# Change History:
#   20130429: added alertMsg() functionality
#   20090210: Created
#
# Info on URLAndExitCommandsEnabled value:
#   http://support.microsoft.com/kb/828026
#   http://www.hispasec.com/laboratorio/GetCodecAnalysis.pdf
#
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package brisv;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "malware",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130429);

sub getConfig{return %config}

sub getShortDescr {
	return "Detect artifacts of a Troj.Brisv.A infection";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching brisv v.".$VERSION);
	::rptMsg("brisv v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\PIMSRV";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $mp_path = "Software\\Microsoft\\MediaPlayer\\Preferences";
		my $url;
		eval {
			$url = $key->get_subkey($mp_path)->get_value("URLAndExitCommandsEnabled")->get_data();
			::rptMsg($mp_path."\\URLAndExitCommandsEnabled value set to ".$url);
			::alertMsg($mp_path."\\URLAndExitCommandsEnabled value set: ".$url);
		};
# if an error occurs within the eval{} statement, do nothing		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
