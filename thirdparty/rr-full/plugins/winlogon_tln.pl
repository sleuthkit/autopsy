#-----------------------------------------------------------
# WinLogon_tln
# Get values from WinLogon key
# 
# History
#   20130429 - created, from winlogon.pl
# 
# References
#   http://technet.microsoft.com/en-us/library/cc738733(v=ws.10).aspx
#
#   TaskMan: http://technet.microsoft.com/en-us/library/cc957402.aspx
#   System:  http://technet.microsoft.com/en-us/library/cc784246(v=ws.10).aspx
#
# copyright 2013 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package winlogon_tln;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130429);

sub getConfig{return %config}

sub getShortDescr {
	return "Alerts on values from the WinLogon key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winlogon_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my @paths = ("Microsoft\\Windows NT\\CurrentVersion\\Winlogon",
	             "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
#			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");	
			my $lw = $key->get_timestamp();
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				my %wl;
				foreach my $v (@vals) {
					my $lcname = $v->get_name();
					my $name   = $lcname;
					$lcname    =~ tr/[A-Z]/[a-z]/;
					my $data   = $v->get_data();
# checks added 20130425 to winlogon.pl
					if ($name eq "Userinit") {
						my @ui = split(/,/,$data);
						if (scalar(@ui) > 1 && $ui[1] ne "") {
#							::alertMsg("ALERT: winlogon: ".$key_path." Userinit value has multiple entries: ".$data);
							::alertMsg($lw."|ALERT|||".$key_path." Userinit value has multiple entries: ".$data);
						}
# alert if the Userinit value does not end in "userinit.exe" (after taking commas into account)						
						::alertMsg($lw."|ALERT|||".$key_path." Userinit value does not end in \"userinit\.exe\": ".$ui[0]) unless ($ui[0] =~ m/userinit\.exe$/);
					}		
					
					if ($name =~ m/^[Ss]hell/) {
						::alertMsg($lw."|ALERT|||".$key_path." Shell value not explorer\.exe: ".$data) unless ($data eq "explorer\.exe");
					}			
					
					::alertMsg($lw."|ALERT|||".$key_path." TaskMan value found: ".$data) if ($name eq "TaskMan");
					::alertMsg($lw."|ALERT|||".$key_path." System value found: ".$data) if ($name eq "System");
# /end 20130425 additions (winlogon.pl)
				
					my $len  = length($data);
					next if ($name eq "");
					if ($v->get_type() == 3 && $name ne "DCacheUpdate") {
						$data = _translateBinary($data);
					}
				
					$data = sprintf "0x%x",$data if ($name eq "SfcQuota");
					if ($name eq "DCacheUpdate") {
						my @v = unpack("VV",$data);
						$data = gmtime(::getTime($v[0],$v[1]));
					}
					push(@{$wl{$len}},$name." = ".$data);
				}
			
				foreach my $t (sort {$a <=> $b} keys %wl) {
					foreach my $item (@{$wl{$t}}) {
#						::rptMsg("  $item");
					}
				}	
#				::rptMsg("");
# Disabled the check of the Notify subkeys for winlogon_tln.pl as, at this
# point, I don't have any real alerts for those subkeys
#				\checkNotifySubkey($key);
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
#			::rptMsg("");
		}
		
	}
#	::rptMsg("Analysis Tips: The UserInit and Shell values are executed when a user logs on\.");
#	::rptMsg("The UserInit value should contain a reference to userinit.exe; the Shell value");
#	::rptMsg("should contain just 'explorer.exe'\. Check TaskMan & System values, if found\.");
#	::rptMsg("");
	
# SpecialAccounts/UserList functionality added 20130411	
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\SpecialAccounts\\UserList";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my %apps;
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
#				::rptMsg(sprintf "%-20s 0x%x",$v->get_name(),$v->get_data());
			}
		}
		else {
#			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

sub checkNotifySubkey {
	my $key = shift;
	my $notify;
	if ($notify = $key->get_subkey("Notify")) {
#		::rptMsg("Notify subkey contents:");
		my @sk = $notify->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my $name = $s->get_name();
# added 20130425
#        ::alertMsg("winlogon: Notify subkey: possible Troj_Tracor infection\.") if ($name =~ m/^f0bd/);				
				my $lw   = $s->get_timestamp();
#				::rptMsg("  ".$name." - ".gmtime($lw));
				my $dllname;
				eval {
					$dllname = $s->get_value("DLLName")->get_data();
#					::rptMsg("  DLLName: ".$dllname);
				};
#				::rptMsg("");
			}
		}
		else {
#			::rptMsg("Notify subkey has no subkeys.");
		}
	}
	else {
#		::rptMsg("Notify subkey not found\.");
	}
#	::rptMsg("");
}

sub _translateBinary {
	my $str = unpack("H*",$_[0]);
	my $len = length($str);
	my @nstr = split(//,$str,$len);
	my @list = ();
	foreach (0..($len/2)) {
		push(@list,$nstr[$_*2].$nstr[($_*2)+1]);
	}
	return join(' ',@list);
}
1;