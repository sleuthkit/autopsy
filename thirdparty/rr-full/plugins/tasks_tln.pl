#-----------------------------------------------------------
# tasks_tln.pl
#
# Change history
#   20200831 - added check for 0x03 at beginning of Actions
#   20200825 - Unicode updates
#   20200730 - MITRE ATT&CK updates
#   20200718 - created from tasks.pl
#
# Refs:
#   https://github.com/libyal/winreg-kb/blob/master/documentation/Task%20Scheduler%20Keys.asciidoc
#   http://port139.hatenablog.com/entry/2019/01/12/095429
#
#  https://attack.mitre.org/techniques/T1053/005/
#
# Copyright (c) 2020 QAR,LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package tasks_tln;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1053\.005",
              category      => "persistence",
			  output		=> "tln",
              version       => 20200831);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {return "Checks TaskCache\\Tasks subkeys";}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

#	::logMsg("Launching tasks v.".$VERSION);
#  ::rptMsg("tasks v.".$VERSION); 
#  ::rptMsg("(".$config{hive}.") ".getShortDescr());   
#  ::rptMsg("");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\Schedule\\TaskCache\\Tasks';
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				
				my $path = "";
				eval {
					$path = $s->get_value("Path")->get_data();
				};
				
				my $uri = "";
				eval {
					$uri = $s->get_value("URI")->get_data();
				};
				
				my $user = "";
				my $act  = "";
				
				eval {
					my $actions = $s->get_value("Actions")->get_data();
					my $data = unpack("v",substr($actions,0,2));
					if ($data == 0x03) {
						($user,$act) = parseActions($actions);
					}
				};
				
				if ($act ne "") {
					$path .= "  Actions: ".$act;
				}
				
				my $reg_time  = "";
				my $last_run  = "";
				my $completed = "";
				
				eval {
					my $data = $s->get_value("DynamicInfo")->get_data();
					if (length($data) == 0x1c) {
						my ($t1,$t2) = processDynamicInfo28($data);
# Registration Time associated with TaskScheduler event IDs 106/140
						if ($t1 != 0) {
							$reg_time = $t1;
							::rptMsg($t1."|REG||".$user."|[T1053\.005] Task Reg Time ".$path);
						}
# In some cases, the second time stamp seems to be associated with the task
# failing to run for some reason; Last Launch/Last Launch Attempt Time?
						if ($t2 != 0) {
							$last_run = $t2;
							::rptMsg($t2."|REG||".$user."|[T1053\.005] Task Last Run ".$path);
						}				
					}
					elsif (length($data) == 0x24) {
						my ($t1,$t2,$t3) = processDynamicInfo36($data);
						if ($t1 != 0) {
							$reg_time = $t1;
							::rptMsg($t1."|REG||".$user."|[T1053\.005] Task Reg Time ".$path);
						}
						if ($t2 != 0) {
							$last_run = $t2;
							::rptMsg($t2."|REG||".$user."|[T1053\.005] Task Last Run ".$path);
						}
						if ($t3 != 0) {
							$completed = $t3;
							::rptMsg($t3."|REG||".$user."|[T1053\.005] Task Completed ".$path);
						}
					}
					else {
#						::rptMsg("DynamicInfo data length = ".length($data)." bytes");
					}
				};			
			}
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
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
1;