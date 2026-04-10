#-----------------------------------------------------------
# tasks.pl
#   I wrote this plugin to assist with parsing and identifying Scheduled Tasks used by
#   threat actors during engagements; in all of the observed cases, these tasks appear within
#   the root of the TaskCache\Tree key
#
# Change history
#   20221222 - updated to map UUID in "Actions" to CLSID\InprocServer32 "(Default)" value 
#   20200831 - added check for 0x03 at beginning of Actions
#   20200825 - Unicode updates
#   20200730 - MITRE ATT&CK updates
#   20200718 - parse Actions data 
#   20200427 - updated output date format
#   20200416 - created
#
# Refs:
#   https://github.com/libyal/winreg-kb/blob/master/documentation/Task%20Scheduler%20Keys.asciidoc
#   http://port139.hatenablog.com/entry/2019/01/12/095429
#   https://blog.codsec.com/posts/malware/gracewire_adventure/
#
#  https://attack.mitre.org/techniques/T1053/005/
#
# Copyright (c) 2022 QAR,LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package tasks;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1053\.005",
              category      => "persistence",
              version       => 20221222);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {return "Checks TaskCache\\Tasks subkeys";}
sub getRefs {}

my $root_key = ();

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching tasks v.".$VERSION);
    ::rptMsg("tasks v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr());   
    ::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");  
	my $reg = Parse::Win32Registry->new($hive);
	$root_key = $reg->get_root_key;
	my $key;
	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\Schedule\\TaskCache\\Tasks';
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				
				eval {
					my $path = $s->get_value("Path")->get_data();
					::rptMsg("Path: ".$path);
				};
				
				eval {
					my $uri = $s->get_value("URI")->get_data();
					::rptMsg("URI : ".$uri);
				};
				
				eval {
					my $data = $s->get_value("DynamicInfo")->get_data();
					if (length($data) == 0x1c) {
						my ($t1,$t2) = processDynamicInfo28($data);
# Registration Time associated with TaskScheduler event IDs 106/140
						if ($t1 != 0) {
							::rptMsg("Task Reg Time : ".::format8601Date($t1)."Z");
						}
# In some cases, the second time stamp seems to be associated with the task
# failing to run for some reason; Last Launch/Last Launch Attempt Time?
						if ($t2 != 0) {
							::rptMsg("Task Last Run : ".::format8601Date($t2)."Z");
						}				
					}
					elsif (length($data) == 0x24) {
						my ($t1,$t2,$t3) = processDynamicInfo36($data);
						if ($t1 != 0) {
							::rptMsg("Task Reg Time : ".::format8601Date($t1)."Z");
						}
						if ($t2 != 0) {
							::rptMsg("Task Last Run : ".::format8601Date($t2)."Z");
						}
						if ($t3 != 0) {
							::rptMsg("Task Completed: ".::format8601Date($t3)."Z");
						}
					}
					else {
						::rptMsg("DynamicInfo data length = ".length($data)." bytes");
					}
				};
				
				eval {
					my $actions = $s->get_value("Actions")->get_data();
					my $data = unpack("v",substr($actions,0,2));
					if ($data == 0x03) {
						my ($user,$act) = parseActions($actions);
						::rptMsg("User   : ".$user);
						
						my $a = (split(/\s/,$act))[0];
						
						$a =~ tr/a-z/A-Z/;
						
						if ($a =~ m/^{/ && $a =~ m/}$/) {
							$act .= " (".mapUUID($a).")";
						}
						
						::rptMsg("Action : ".$act);
					}
				};
				::rptMsg("");
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: This plugin extracts information about Scheduled Tasks from the Software hive\. Where the task points to a UUID, ");
	::rptMsg("the plugin attempts to map to that CLSID subkey, and retrieve the \"(Default)\" value, which in many instances is a DLL. This is");
	::rptMsg("done, because in Q4 2022, malware (i\.e\., \"FlawedGrace\") was observed modifying the UUID for the RegIdleBackup task, to point");
	::rptMsg("to a malicious DLL. By default, the RegIdleBackup task UUID is {ca767aa8-9157-4604-b64b-40747123d5f2}, which points to regidle.dll.");
	::rptMsg("");
	::rptMsg("It is possible that other, similar tasks could be similarly abused in the future.");
	::rptMsg("");
	::rptMsg("Ref: https://blog.codsec.com/posts/malware/gracewire_adventure/");
}

sub processDynamicInfo28 {
#win7
	my $data = shift;
	my ($t0,$t1) = unpack("VV",substr($data,4,8));
	my ($d0,$d1) = unpack("VV",substr($data,12,8));
	return(::getTime($t0,$t1),::getTime($d0,$d1));
}

sub processDynamicInfo36 {
#win10	
	my $data = shift;
	my ($t0,$t1) = unpack("VV",substr($data,4,8));
	my ($d0,$d1) = unpack("VV",substr($data,12,8));
	my ($r0,$r1) = unpack("VV",substr($data,0x1c,8));
	return(::getTime($t0,$t1),::getTime($d0,$d1),::getTime($r0,$r1));
}

#-----------------------------------------------------------
# parseActions()
# Parses Actions data
#-----------------------------------------------------------
sub parseActions {
	my $data = shift;
	my $len  = length($data);
	
	my $cur = unpack("V",substr($data,2,4));
	my $user = substr($data,6,$cur);
	$user = ::getUnicodeStr($user);
#	$user =~ s/\00//g;
	
	my $action = "";
	my $tag = unpack("v",substr($data,6 + $cur,2));
	
	if ($tag == 0x7777) {
		my $g = substr($data,6 + $cur + 2 + 4,16);
		$action = parseGUID($g);
		
		if ($len - (6 + $cur + 2 + 4 + 16) > 4) {
			my $i = unpack("V", substr($data,6 + $cur + 2 + 4 + 16,4));
			my $r = substr($data,6 + $cur + 2 + 4 + 16 + 4,$i);
			$r = ::getUnicodeStr($r);
#			$r =~ s/\00//g;
			$action .= " ".$r;
		}
		
	}
	elsif ($tag == 0x6666) {
		my $l = unpack("V",substr($data,6 + $cur + 2 + 4,4));
		my $n = substr($data,6 + $cur + 2 + 4 + 4,$l);
		$n = ::getUnicodeStr($n);
#		$n =~ s/\00//g;
		$action = $n;
		
		if ($len - (6 + $cur + 2 + 4 + 4 + $l) > 4) {
			my $h = unpack("V",substr($data,6 + $cur + 2 + 4 + 4 + $l,4));
			my $j = substr($data,6 + $cur + 2 + 4 + 4 + $l + 4,$h);
			$j = ::getUnicodeStr($j);
#			$j =~ s/\00//g;
			$action .= " ".$j;
		}

	}
	else {}
	
	return($user,$action);
}

#-----------------------------------------------------------
# parseGUID()
# Takes 16 bytes of binary data, returns a string formatted
# as an MS GUID.
#-----------------------------------------------------------
sub parseGUID {
	my $data     = shift;
	my $d1 = unpack("V",substr($data,0,4));
	my $d2 = unpack("v",substr($data,4,2));
	my $d3 = unpack("v",substr($data,6,2));
	my $d4 = unpack("H*",substr($data,8,2));
	my $d5 = unpack("H*",substr($data,10,6));
	my $guid = sprintf "{%08x-%04x-%04x-$d4-$d5}",$d1,$d2,$d3;

#  if (exists $cp_guids{$guid}) {
#  	return "CLSID_".$cp_guids{$guid};
#  }
#  elsif (exists $folder_types{$guid}) {
#  	return "CLSID_".$folder_types{$guid};
#  }
#  else {
#  	return $guid;
#  }
	return $guid;
}

#-----------------------------------------------------------
# mapUUID()
# Map Action UUID to CLSID\InprocServer32 value
#-----------------------------------------------------------
sub mapUUID {
	my $uuid = shift;
	my $key = ();
	my $rtn = ();
	
	if ($key = $root_key->get_subkey("Classes\\CLSID\\".$uuid."\\InprocServer32")) {
		eval {
			my $dll = $key->get_value("")->get_data();
			$rtn = $dll;
		};
		$rtn = "UUID Default value not found" if ($@);
	}
	else {
		$rtn = "UUID not found";
	}
	return $rtn;
}

1;