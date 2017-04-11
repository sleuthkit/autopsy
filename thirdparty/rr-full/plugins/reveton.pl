#-----------------------------------------------------------
# reveton.pl
# 
#
# Change history
#  20131010 - created
#
# References
#  http://www.microsoft.com/security/portal/threat/encyclopedia/Entry.aspx?Name=Trojan%3AWin32%2FReveton#tab=2
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package reveton;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "malware",
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20131010);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for possible Reveton infection";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching reveton v.".$VERSION);
	::rptMsg("reveton v.".$VERSION); # banner
    ::rptMsg(getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $count = 0;
	my $key_path;
	
	my @paths = ('Software\\Microsoft\\Windows\\CurrentVersion\\Run',
	             'Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run');
	my $key;
# Check #1	
	foreach $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $lcname = $name;
					$lcname =~ tr/[A-Z]/[a-z]/;
					my $data = $v->get_data();
					my $lcdata = $data;
					$lcdata =~ tr/[A-Z]/[a-z]/;
					
					if ($lcname =~ m/^task scheduler/ || $lcdata =~ m/task scheduler\.exe$/) {
						::rptMsg("Possible Reveton infection: ".$name." - ".$data);
						$count++;
					}
				}
			}
			else {
				::rptMsg($key_path." has no values\.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
# Check #2	
	$key_path = 'Software\\Microsoft\\Internet Explorer\\Main';
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $banner = $key->get_value("NoProtectedModeBanner")->get_data();
			::rptMsg($key_path."\\NoProtectedModeBanner value = ".$banner);
			::rptMsg("");
			if ($banner == 1) {
				::rptMsg("Internet Explorer\\Main\\NoProtectedModeBanner set to 0x1: possible Reveton infection\.");
				$count++;
				::rptMsg("");
			}
		};
	}
	else {
		::rptMsg($key_path." not found\.");
	}
	
# Check to see if IE toolbar is locked	
# Check #3
	$key_path = 'Software\\Microsoft\\Internet Explorer\\Toolbar';
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $tb = $key->get_value("Locked")->get_data();
			::rptMsg($key_path."\\Locked value = ".$tb);
			::rptMsg("");
			if ($tb == 1) {
				::rptMsg("Internet Explorer Toolbar is locked: possible Reveton infection\.");
				$count++;
				::rptMsg("");
			}
		};
	}
	else {
		::rptMsg($key_path." not found\.");
	}
	
# check Internet Zone Settings
# Check #4 - performs 5 identical checks
	::rptMsg("Checking Internet Zones Settings...");
	foreach my $z (0..4) {
		$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\Zones\\'.$z;
		if ($key = $root_key->get_subkey($key_path)) {
			eval {
				my $val = $key->get_value("1609")->get_data();
#				::rptMsg($key_path."\\1609 value = ".$val);
#				::rptMsg("");
				if ($val == 0x0) {
					::rptMsg("Internet Settings\\Zones\\".$z."\\1609 value is set to 0x0: possible Reveton infection\.");
					::rptMsg("");
					$count++;
				}
			};
		}
		else {
			::rptMsg($key_path." not found\.");
		}
	}
	
# Check #5 - see if Task Manager has been disalbed
	::rptMsg("Checking Task Manager Setting...");
	$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System\\';
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $val = $key->get_value("DisableTaskMgr")->get_data();
			if ($val == 0x1) {
				::rptMsg("Task Manager disabled: possible Reveton infection\.");
				::rptMsg("");
				$count++;
			}
		};
	}
	else {
		::rptMsg($key_path." not found\.");
		::rptMsg("");
	}

# Check #6	
  ::rptMsg("Checking HideIcons Setting...");
	$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\\';
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $val = $key->get_value("HideIcons")->get_data();
			if ($val == 0x1) {
				::rptMsg("HideIcons value set to 0x1: possible Reveton infection\.");
				::rptMsg("");
				$count++;
			}
		};
	}
	else {
		::rptMsg($key_path." not found\.");
		::rptMsg("");
	}
	
	::rptMsg("Final Score: ".$count."/6 checks succeeded\.");
}

1;