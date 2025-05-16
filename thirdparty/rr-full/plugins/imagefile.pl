#-----------------------------------------------------------
# imagefile
#
# References:
#  http://msdn2.microsoft.com/en-us/library/a329t4ed(VS\.80)\.aspx
#  CWDIllegalInDllSearch: http://support.microsoft.com/kb/2264107
#  http://carnal0wnage.attackresearch.com/2012/04/privilege-escalation-via-sticky-keys.html
#  'Auto' value - https://docs.microsoft.com/en-us/windows/desktop/debug/configuring-automatic-debugging
#  https://docs.microsoft.com/en-us/previous-versions/windows/it-pro/windows-server-2012-R2-and-2012/dn408187(v=ws.11)
#  https://cybellum.com/doubleagentzero-day-code-injection-and-persistence-technique/
#  https://twitter.com/0gtweet/status/1336035383948275714
#
# Change history:
#  20201207 - added check of HKCU, rewrote most of the code to check for values
#  20200730 - MITRE ATT&CK updates
#  20200515 - updated date output format
#  20190829 - added check for AuditLevel value
#  20190511 - added search for 'auto' value
#  20131007 - added Carnal0wnage reference
#  20130425 - added alertMsg() functionality
#  20130410 - added Wow6432Node support
#  20100824 - added check for "CWDIllegalInDllSearch" value
#
#  https://attack.mitre.org/techniques/T1546/012/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package imagefile;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546\.012",
              category      => "persistence",
			  output		=> "report",
              version       => 20200730);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks Image File Execution Options subkeys values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my @vals = ("Debugger","GlobalFlag","VerifierDlls","Auto","AuditLevel","CWDIllegalInDllSearch");
my %key_values = ();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching imagefile v.".$VERSION);
	::rptMsg("imagefile v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
  
  my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @paths = ();
  
	if ($hive_guess eq "software") {
		@paths = ("Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options",
	            "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options");
	}
	elsif ($hive_guess eq "ntuser") {
		@paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options",
	            "Software\\Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options");
	}
	else {}
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				my %debug;
				my $i = "Your Image File Name here without a path";
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					next if ($name =~ m/^$i/i);
					
					foreach my $v (@vals) {
						eval {
							$key_values{$v} = $s->get_value($v)->get_data();
						};
					}
					
					if (scalar keys %key_values > 0) {
						foreach my $k (keys %key_values) {
							::rptMsg($name);
							::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp()."Z"));
							if ($k eq "CWDIllegalInDllSearch" || $k eq "GlobalFlag") {
								::rptMsg(sprintf "%-25s 0x%x",$k,$key_values{$k});
							}
							else {
								::rptMsg(sprintf "%-25s %-50s",$k,$key_values{$k});
							}
						}
						%key_values = ();
						::rptMsg("");
					}
				}
			}
			else {
#				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: If the plugin responds with any value names and data, including but not limited to the Debugger value");
	::rptMsg("  those value names should be explored and analyzed further.");
}
1;