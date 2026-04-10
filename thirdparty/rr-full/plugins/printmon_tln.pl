#-----------------------------------------------------------
# printmon_tln.pl
# Plugin for Registry Ripper; Access System hive file to get the
# printer monitors
# 
# MITRE ATT&CK Technique: https://attack.mitre.org/techniques/T1013/
# 
# Change history
#   20200922 - MITRE update
#   20191122 - created
#
# References
#  https://www.bleepingcomputer.com/news/security/deprimon-malware-registers-itself-as-a-windows-print-monitor/
#  https://www.welivesecurity.com/2019/11/21/deprimon-default-print-monitor-malicious-downloader/
# 
# copyright 2019-2020 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package printmon_tln;


my %config = (hive          => "System",
              hasShortDescr => 1,
              category      => "persistence",
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
			  output		=> "tln",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists installed Print Monitors";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#	::logMsg("Launching printmon v.".$VERSION);
#	::rptMsg("printmon v.".$VERSION); # banner
#  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $path = $ccs."\\Control\\Print\\Monitors";
		
		if ($pm = $root_key->get_subkey($path)) {
			::rptMsg($path);
			::rptMsg(getShortDescr());
			::rptMsg("");
# Get all subkeys and sort based on LastWrite times
			my @subkeys = $pm->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					my $lw   = $s->get_timestamp();
					my $driver = "";
					eval {
						$driver = $s->get_value("Driver")->get_data();
					};
					
					::rptMsg($lw."|REG|||Printer Monitor ".$name.": Driver: ".$driver);
					
				}
			}
			else {
				::rptMsg($path." has no subkeys.");
			}			
		}
		else {
			::rptMsg($path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;