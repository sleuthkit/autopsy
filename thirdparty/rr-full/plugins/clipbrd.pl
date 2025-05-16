#-----------------------------------------------------------
# clipbrd.pl
# Threat actors, particularly those interested in cryptocurrency wallets, have been observed
# targeting the clipboard on user's systems. In some instances, they will retrieve data from
# the clipboard; in others, they will replace wallet IDs with their own, hoping that the user
# will paste the wallet address into an app, unknowingly sending the cryptocurrency to the 
# attacker's wallet.
#
# Change history:
#   20230419 - added Inversecos' reference
#   20221018 - Updated to check for AllowClipboardHistory value
#   20210801 - created
#
# References:
#   https://twitter.com/R3MRUM/status/1412064892870434818
#   https://twitter.com/Max_Mal_/status/1411261131033923586
#   https://www.inversecos.com/2022/05/how-to-perform-clipboard-forensics.html
#   
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package clipbrd;
use strict;

my %config = (hive          => "software,ntuser\.dat",
			  category      => "collection",
			  MITRE         => "T1115",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20230419);

sub getConfig{return %config}

sub getShortDescr {
	return "Check clipboard settings (possible exfil)";	
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
	::logMsg("Launching clipbrd v.".$VERSION);
	::rptMsg("clipbrd v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	} 
	my $key; 
	my $key_path = ();
	
 	if ($hive_guess eq "software") {
 		$key_path = "Policies\\Microsoft\\Windows\\System";
 		if ($key = $root_key->get_subkey($key_path)) {
 			eval {
 				my $c = $key->get_value("AllowCrossDeviceClipboard")->get_data();
 				::rptMsg("AllowCrossDeviceClipboard value: ".$c);
 				::rptMsg("");
				::rptMsg("Analysis Tip: If the AllowCrossDeviceClipboard value is set to \"1\", clipboard contents are shared across");
				::rptMsg("devices, and malware that extracts data from the clipboard could exfil extremely sensitive data.");
 			};
 			::rptMsg($key_path."\\AllowCrossDeviceClipboard value not found.") if ($@);
			
			eval {
				my $a = $key->get_value("AllowClipboardHistory")->get_data();
				::rptMsg("AllowClipboardHistory value: ".$a);
			
			};
			::rptMsg($key_path."\\AllowClipboardHistory value not found.") if ($@);
			
 		}
 		else {
 			::rptMsg($key_path." not found.");
 		}
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Microsoft\\Clipboard";
 		if ($key = $root_key->get_subkey($key_path)) {
 			eval {
 				my $c = $key->get_value("EnableClipboardHistory")->get_data();
 				::rptMsg("EnableClipboardHistory value: ".$c);
 				::rptMsg("");
				::rptMsg("Analysis Tip: If the EnableClipboardHistory value is set to \"1\", malware that extracts data from the");
				::rptMsg("clipboard could exfil extremely sensitive data.");
				::rptMsg("");
				::rptMsg("Further, if both values are set, there may be data within the user's ActivitiesCache\.db file that can provide");
				::rptMsg("valuable insight/evidence.");
				::rptMsg("");
				::rptMsg("Ref: https://www.inversecos.com/2022/05/how-to-perform-clipboard-forensics.html");
 			};
 			::rptMsg($key_path."\\EnableClipboardHistory value not found.") if ($@);
 		}
 		else {
 			::rptMsg($key_path." not found.");
 		}
 	}
 	else {}
	
}
1;