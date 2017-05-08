#-----------------------------------------------------------
# urun_tln.pl
# Get contents of Run key from NTUSER.DAT hive
#
# Change History
#   20130425 - created
#
# References:
#   http://msdn2.microsoft.com/en-us/library/aa376977.aspx
#   http://support.microsoft.com/kb/170086
#   
#
# copyright 2013 Quantum Analytics Research,
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package urun_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20130425);

sub getConfig{return %config}

sub getShortDescr {
	return "[Autostart] Get autostart key contents from NTUSER.DAT hive";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Definition of the Run keys in the WinXP Registry" =>
	            "http://support.microsoft.com/kb/314866");	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching urun_tln v.".$VERSION);
#	::rptMsg("urun_tln v.".$VERSION); # banner
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my @run = ("Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunServices",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunServicesOnce",
	           "Software\\Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\".
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	           "Software\\Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\".
	           "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	           "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	           "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run");
	
	my @alertpaths = ("recycle","globalroot","temp","system volume information","appdata",
	              "application data");
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@run) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
#			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
      my $lw = $key->get_timestamp();
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					my $lc_path = lc($vals{$v});
					foreach my $a (@alertpaths) {			
						if (grep(/$a/,$lc_path)) {
#						::alertMsg("ALERT: soft_run: Temp Path found: ".$key_path." : ".$v." -> ".$vals{$v});
            	::alertMsg($lw."|ALERT|||HKCU\\".$key_path." Temp path: ".$v.": ".$vals{$v});
						}
					}
# check to see if the data ends in .com					
					if ($vals{$v} =~ m/\.com$/ || $vals{$v} =~ m/\.bat$/ || $vals{$v} =~ m/\.pif$/) {
#						::alertMsg("ALERT: user_run: Path ends in \.com/\.bat: ".$key_path." : ".$v." -> ".$vals{$v});
            ::alertMsg($lw."|ALERT|||HKCU\\".$key_path." \.com/\.bat/\.pif file found: ".$v.": ".$vals{$v});
					}					
					
					my @list = split(/:/,$vals{$v});
					my $last = $list[scalar(@list) - 1];
					::alertMsg($lw."|ALERT|||Poss. ADS found: ".$v.": ".$vals{$v}) if (grep(/:/,$last));
					
#					::rptMsg("  ".$v.": ".$vals{$v});
				}
			}
			else {
#				::rptMsg("");
#				::rptMsg($key_path." has no values.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
#		::rptMsg("");
	}
	
# This section was added on 20130115 to address the 'run' and 'load' values that
# could be added to the key	
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	  my $lw = $key->get_timestamp();
		my $run;
		my $count = 0;
		eval {
			$run = $key->get_value("Run")->get_data();
#			::rptMsg("Run value = ".$run);
#			::alertMsg("ALERT: user_run: ".$key_path." Run value found: ".$run);
			::alertMsg($lw."|ALERT|||urun_tln: HKCU\\".$key_path." Run value found: ".$run);
		};
		if ($@) {
#			::rptMsg("Run value not found.");
		}
		
		eval {
			$run = $key->get_value("run")->get_data();
#			::rptMsg("run value = ".$run);
#			::alertMsg("ALERT: user_run: ".$key_path." run value found: ".$run);
			::alertMsg($lw."|ALERT|||urun_tln: HKCU\\".$key_path." run value found: ".$run);
		};
		if ($@) {
#			::rptMsg("run value not found.");
		}
		
		my $load;
		eval {
			$load = $key->get_value("load")->get_data();
#			::rptMsg("load value = ".$load);
#			::alertMsg("ALERT: user_run: ".$key_path." load value found: ".$load);
			::alertMsg($lw."|ALERT|||urun_tln: HKCU\\".$key_path." load value found: ".$load);
		};
		if ($@) {
#			::rptMsg("load value not found.");
		}
		
	}
}

sub getKeyValues {
	my $key = shift;
	my %vals;
	
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {
	
	}
	return %vals;
}

1;
