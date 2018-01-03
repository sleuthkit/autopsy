#-----------------------------------------------------------
# user_run
# Get contents of Run key from NTUSER.DAT hive
#
# Change History
#   20140115 - added code to check for odd char in path
#   20130603 - updated alert functionality
#   20130425 - added alertMsg() functionality
#   20120329 - added additional keys
#   20130314 - updated to include Policies keys
#   20130313 - updated to include additional keys
#   20130115 - updated to include 64-bit, additional keys/values
#   20080328 - created
#
# References:
#   http://msdn2.microsoft.com/en-us/library/aa376977.aspx
#   http://support.microsoft.com/kb/170086
#   
#
# copyright 2013 Quantum Analytics Research,
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package user_run;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20140115);

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
	::logMsg("Launching user_run v.".$VERSION);
	::rptMsg("user_run v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
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
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@run) {
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

					::rptMsg("  ".$v.": ".$vals{$v});
				}
			}
			else {
				::rptMsg("");
				::rptMsg($key_path." has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
	
# This section was added on 20130115 to address the 'run' and 'load' values that
# could be added to the key	
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	
		my $run;
		my $count = 0;
		eval {
			$run = $key->get_value("Run")->get_data();
			::rptMsg("Run value = ".$run);
			::alertMsg("ALERT: user_run: ".$key_path." Run value found: ".$run) unless ($run eq "");
		};
		if ($@) {
			::rptMsg("Run value not found.");
		}
		
		eval {
			$run = $key->get_value("run")->get_data();
			::rptMsg("run value = ".$run);
			::alertMsg("ALERT: user_run: ".$key_path." run value found: ".$run) unless ($run eq "");
		};
		if ($@) {
			::rptMsg("run value not found.");
		}
		
		my $load;
		eval {
			$load = $key->get_value("load")->get_data();
			::rptMsg("load value = ".$load);
			::alertMsg("ALERT: user_run: ".$key_path." load value found: ".$load) unless ($load eq "");
		};
		if ($@) {
			::rptMsg("load value not found.");
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
			::alertMsg("ALERT: user_run: ".$a." found in path: ".$path);              
		}
	}
	
	my $cnt = 0;
	my @list = split(//,$path);
	foreach my $n (@list) {
		my $ch = ord($n);
#		print $n." - ".$ch."\n";
		if ($ch < 0x20 || $ch > 0x7e) {
			$cnt = 1;
		}
	}
 	::alertMsg("ALERT: user_run: Odd char in path: ".$path) if ($cnt > 0);
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
			::alertMsg("ALERT: user_run: ".$path." ends in ".$e);              
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
	::alertMsg("ALERT: user_run: Poss. ADS found in path: ".$path) if grep(/:/,$last);
}
1;
