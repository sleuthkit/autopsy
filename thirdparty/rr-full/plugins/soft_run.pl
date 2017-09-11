#-----------------------------------------------------------
# soft_run
# Get contents of Run key from Software hive
#
# History:
#   20130603 - updated alert functionality
#   20130425 - added alertMsg() functionality
#   20130329 - added additional keys
#   20130314 - updated to include Policies keys
#   20120524 - updated to support newer OS's, and 64-bit
#   20080328 - created
#
#
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package soft_run;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20130603);

sub getConfig{return %config}

sub getShortDescr {
	return "[Autostart] Get autostart key contents from Software hive";	
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
	::logMsg("Launching soft_run v.".$VERSION);
	::rptMsg("soft_run v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
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
	
	foreach my $key_path (@paths) {
	
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
# added 20130603					
					alertCheckPath($vals{$v});
					alertCheckExt($vals{$v});
					alertCheckADS($vals{$v});
										
					::rptMsg("  ".$v." - ".$vals{$v});
				}
				::rptMsg("");
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					::rptMsg("");
					::rptMsg($key_path."\\".$s->get_name());
					::rptMsg("LastWrite Time ".gmtime($s->get_timestamp())." (UTC)");
					my %vals = getKeyValues($s);
					foreach my $v (keys %vals) {
						::rptMsg("  ".$v." -> ".$vals{$v});
					}
					::rptMsg("");
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::rptMsg("");
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

#-----------------------------------------------------------
# alertCheckPath()
#-----------------------------------------------------------
sub alertCheckPath {
	my $path = shift;
	$path = lc($path);
	my @alerts = ("recycle","globalroot","temp","system volume information","appdata",
	              "application data");
	
	foreach my $a (@alerts) {
		if (grep(/$a/,$path)) {
			::alertMsg("ALERT: soft_run: ".$a." found in path: ".$path);              
		}
	}
}

#-----------------------------------------------------------
# alertCheckExt()
#-----------------------------------------------------------
sub alertCheckExt {
	my $path = shift;
	$path = lc($path);
	my @exts = ("\.com","\.bat","\.pif");
	
	foreach my $e (@exts) {
		if ($path =~ m/$e$/) {
			::alertMsg("ALERT: soft_run: ".$path." ends in ".$e);              
		}
	}
}
#-----------------------------------------------------------
# alertCheckADS()
#-----------------------------------------------------------
sub alertCheckADS {
	my $path = shift;
	my @list = split(/\\/,$path);
	my $last = $list[scalar(@list) - 1];
	::alertMsg("ALERT: soft_run: Poss. ADS found in path: ".$path) if grep(/:/,$last);
}
1;