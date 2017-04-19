#-----------------------------------------------------------
# samparse_tln.pl
# Parse the SAM hive file for user/group membership info
#
# Change history:
#    20120827 - TLN version created from original samparse.pl
#    20120722 - updated %config hash
#    20110303 - Fixed parsing of SID, added check for account type
#               Acct type determined based on Dustin Hulburt's "Forensic
#               Determination of a User's Logon Status in Windows" 
#               from 10 Aug 2009 (link below)
#    20100712 - Added References entry
#    20091020 - Added extracting UserPasswordHint value
#    20090413 - Added account creation date
#    20080415 - created
#
# References
#    Source available here: http://pogostick.net/~pnh/ntpasswd/
#    http://accessdata.com/downloads/media/Forensic_Determination_Users_Logon_Status.pdf
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package samparse_tln;
use strict;

my %config = (hive          => "SAM",
              hivemask      => 2,
              output        => "report",
              category      => "User Activity",
              class         => 0, # system
              output        => "TLN",
              osmask        => 63, #XP - Win8
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20120827);

sub getConfig{return %config}

sub getShortDescr {
	return "Parse SAM file for user acct info (TLN)";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Well-known SIDs" => "http://support.microsoft.com/kb/243330");	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %acb_flags = (0x0001 => "Account Disabled",
                 0x0002 => "Home directory required",
								 0x0004 => "Password not required",
 								 0x0008 => "Temporary duplicate account",
                 0x0010 => "Normal user account",
                 0x0020 => "MNS logon user account",
                 0x0040 => "Interdomain trust account",
                 0x0080 => "Workstation trust account",
                 0x0100 => "Server trust account",
                 0x0200 => "Password does not expire",
                 0x0400 => "Account auto locked");
                 
my %types = (0xbc => "Default Admin User",
             0xd4 => "Custom Limited Acct",
             0xb0 => "Default Guest Acct");
             
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching samparse_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
# Get user information

	my $key_path = 'SAM\\Domains\\Account\\Users';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @user_list = $key->get_list_of_subkeys();
		if (scalar(@user_list) > 0) {
			foreach my $u (@user_list) {
				my $rid = $u->get_name();
				my $ts  = $u->get_timestamp();
				my $tag = "0000";
				if ($rid =~ m/^$tag/) {	
					my $v_value = $u->get_value("V");
					my $v = $v_value->get_data();
					my %v_val = parseV($v);
					$rid =~ s/^0000//;
					$rid = hex($rid);
					
					my $c_date;
					eval {
						my $create_path = $key_path."\\Names\\".$v_val{name};
						if (my $create = $root_key->get_subkey($create_path)) {
							$c_date = $create->get_timestamp();
						}
					};
				
#					::rptMsg("Username        : ".$v_val{name}." [".$rid."]");
#					::rptMsg("Full Name       : ".$v_val{fullname});
# 				::rptMsg("User Comment    : ".$v_val{comment});
#	  			::rptMsg("Account Type    : ".$v_val{type});
#					::rptMsg("Account Created : ".gmtime($c_date)." Z") if ($c_date > 0); 
					
					my $f_value = $u->get_value("F");
					my $f = $f_value->get_data();
					my %f_val = parseF($f);
					
#					my $lastlogin;
#					my $pwdreset;
#					my $pwdfail;
#					($f_val{last_login_date} == 0) ? ($lastlogin = "Never") : ($lastlogin = gmtime($f_val{last_login_date})." Z");
#					($f_val{pwd_reset_date} == 0) ? ($pwdreset = "Never") : ($pwdreset = gmtime($f_val{pwd_reset_date})." Z");
#					($f_val{pwd_fail_date} == 0) ? ($pwdfail = "Never") : ($pwdfail = gmtime($f_val{pwd_fail_date})." Z");
					
					my $pw_hint;
					my $c_descr = "Acct Created (".$v_val{type}.")";
					eval {
						$pw_hint = $u->get_value("UserPasswordHint")->get_data();
						$pw_hint =~ s/\00//g;
						$c_descr .= " (Pwd Hint: ".$pw_hint.")";
					};
					
					if ($c_date > 0) {
						::rptMsg($c_date."|SAM||".$v_val{name}."|".$c_descr);
					}
					
					if ($f_val{pwd_reset_date} > 0) {
						::rptMsg($f_val{pwd_reset_date}."|SAM||".$v_val{name}."|Password Reset Date");
					}
					
					if ($f_val{pwd_fail_date} > 0) {
						::rptMsg($f_val{pwd_fail_date}."|SAM||".$v_val{name}."|Password Failure Date");
					}
					
					if ($f_val{last_login_date} > 0) {
						::rptMsg($f_val{last_login_date}."|SAM||".$v_val{name}."|Last Login (".$f_val{login_count}.")");
					}
					
					
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub parseF {
	my $f = shift;
	my %f_value = ();
	my @tv;
# last login date	
	@tv = unpack("VV",substr($f,8,8));
	$f_value{last_login_date} = ::getTime($tv[0],$tv[1]);
#	password reset/acct creation
	@tv = unpack("VV",substr($f,24,8));
	$f_value{pwd_reset_date} = ::getTime($tv[0],$tv[1]);
# Account expires
	@tv = unpack("VV",substr($f,32,8));
	$f_value{acct_exp_date} = ::getTime($tv[0],$tv[1]);
# Incorrect password 	
	@tv = unpack("VV",substr($f,40,8));
	$f_value{pwd_fail_date} = ::getTime($tv[0],$tv[1]);
	$f_value{rid} = unpack("V",substr($f,48,4));
	$f_value{acb_flags} = unpack("v",substr($f,56,2));
	$f_value{failed_count} = unpack("v",substr($f,64,2));
	$f_value{login_count} = unpack("v",substr($f,66,2));
	return %f_value;
}

sub parseV {
	my $v = shift;
	my %v_val = ();
	my $header = substr($v,0,44);
	my @vals = unpack("V*",$header);   
	$v_val{type}     = $types{$vals[1]}; 
	$v_val{name}     = _uniToAscii(substr($v,($vals[3] + 0xCC),$vals[4]));
	$v_val{fullname} = _uniToAscii(substr($v,($vals[6] + 0xCC),$vals[7])) if ($vals[7] > 0);
	$v_val{comment}  = _uniToAscii(substr($v,($vals[9] + 0xCC),$vals[10])) if ($vals[10] > 0);
	return %v_val;
}

sub parseC {
	my $cv = $_[0];
	my %c_val = ();
	my $header = substr($cv,0,0x34);
	my @vals = unpack("V*",$header);
	
	$c_val{group_name} = _uniToAscii(substr($cv,(0x34 + $vals[4]),$vals[5]));
	$c_val{comment}    = _uniToAscii(substr($cv,(0x34 + $vals[7]),$vals[8]));
	$c_val{num_users}  = $vals[12];

	return %c_val;
}

sub parseCUsers {
	my $cv = $_[0];
	my %members = ();
	my $header = substr($cv,0,0x34);
	my @vals = unpack("V*",$header);
	
	my $num = $vals[12];
	
	my @users = ();
	my $ofs;		
	if ($num > 0) {
		my $count = 0;
		foreach my $c (1..$num) {
			my $ofs = $vals[10] + 52 + $count;
			my $tmp = unpack("V",substr($cv,$ofs,4));
			
			if ($tmp == 0x101) {
				$ofs++ if (unpack("C",substr($cv,$ofs,1)) == 0);
				$members{_translateSID(substr($cv,$ofs,12))} = 1;
				$count += 12;
			}
			elsif ($tmp == 0x501) {
				$members{_translateSID(substr($cv,$ofs,28))} = 1;
				$count += 28;
			}
			else {
			
			}
		}
	}
	return %members;
}

#---------------------------------------------------------------------
# _translateSID()
# Translate binary data into a SID
# References:
#   http://blogs.msdn.com/oldnewthing/archive/2004/03/15/89753.aspx  
#   http://support.microsoft.com/kb/286182/
#   http://support.microsoft.com/kb/243330
#---------------------------------------------------------------------
sub _translateSID {
	my $sid = $_[0];
	my $len = length($sid);
	my $revision;
	my $dashes;
	my $idauth;
	if ($len < 12) {
# Is a SID ever less than 12 bytes?		
		return "SID less than 12 bytes";
	}
	elsif ($len == 12) {
		$revision = unpack("C",substr($sid,0,1));
		$dashes   = unpack("C",substr($sid,1,1));
		$idauth   = unpack("H*",substr($sid,2,6));
		$idauth   =~ s/^0+//g;
		my $sub   = unpack("V",substr($sid,8,4));
		return "S-".$revision."-".$idauth."-".$sub;
	}
	elsif ($len > 12) {
		$revision = unpack("C",substr($sid,0,1));
		$dashes   = unpack("C",substr($sid,1,1));
		$idauth   = unpack("H*",substr($sid,2,6));
		$idauth   =~ s/^0+//g;
		my @sub   = unpack("V4",substr($sid,8,16));
		my $rid   = unpack("V",substr($sid,24,4));
		my $s = join('-',@sub);
		return "S-".$revision."-".$idauth."-".$s."-".$rid;
	}
	else {
# Nothing to do		
	}
}

#---------------------------------------------------------------------
# _uniToAscii()
#---------------------------------------------------------------------
sub _uniToAscii {
  my $str = $_[0];
  $str =~ s/\00//g;
  return $str;
}

1;