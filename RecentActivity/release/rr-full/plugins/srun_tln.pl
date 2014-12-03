#-----------------------------------------------------------
# srun_tln
# Get contents of Run key from Software hive
#
# History:
#   20130425 - created
#
#
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package srun_tln;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20130425);

sub getConfig{return %config}

sub getShortDescr {
	return "[Autostart] Get autostart key contents from Software hive (TLN)";	
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
	::logMsg("Launching srun_tln v.".$VERSION);
#	::rptMsg("srun_tln v.".$VERSION); # banner
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Run",
	             "Microsoft\\Windows\\CurrentVersion\\RunOnce",
	             "Microsoft\\Windows\\CurrentVersion\\RunServices",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\RunOnce",
	             "Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\\Run",
	             "Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\Software\\Microsoft\\".
	             "Windows\\CurrentVersion\\Run",
	             "Microsoft\\Windows NT\\CurrentVersion\\Terminal Server\\Install\\Software\\Microsoft\\".
	             "Windows\\CurrentVersion\\RunOnce",
	             );
	
	my @alertpaths = ("recycle","globalroot","temp","system volume information","appdata",
	              "application data");
	
	foreach my $key_path (@paths) {
	
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
            	::alertMsg($lw."|ALERT|||srun_tln: Software\\".$key_path." Temp path: ".$v.": ".$vals{$v});
						}
					}
# check to see if the data ends in .com					
					if ($vals{$v} =~ m/\.com$/ || $vals{$v} =~ m/\.bat$/ || $vals{$v} =~ m/\.pif$/) {
#						::alertMsg("ALERT: soft_run: Path ends in \.com/\.bat/\.pif: ".$key_path." : ".$v." -> ".$vals{$v});
						::alertMsg($lw."|ALERT|||srun_tln: Software\\".$key_path." ends in \.com/\.bat/\.pif: ".$v.": ".$vals{$v});
					}					
					
					my @list = split(/:/,$vals{$v});
					my $last = $list[scalar(@list) - 1];
					::alertMsg($lw."|ALERT|||srun_tln: Poss. ADS found: ".$v.": ".$vals{$v}) if (grep(/:/,$last));
					
#					::rptMsg("  ".$v." - ".$vals{$v});
				}
#				::rptMsg("");
			}
			else {
#				::rptMsg($key_path." has no values.");
			}
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