#-----------------------------------------------------------
# auditpol
# Get the audit policy from the Security hive file (Win7+)
# *Works for Win7 and Win10 at the moment
#
# History
#   20190510 - updated; Win2016
#   20151202 - created
#
# Ref:
#   http://www.kazamiya.net/structure/poladtev
#   http://www.kazamiya.net/en/poladtev
#   http://blogs.technet.com/b/askds/archive/2011/03/11/getting-the-effective-audit-policy-in-windows-7-and-2008-r2.aspx
#
# Equiv: auditpol /get /category:*
#
# copyright 2015 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package auditpol;
use strict;

my %config = (hive          => "Security",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190510);

sub getConfig{return %config}
sub getShortDescr {
	return "Get audit policy from the Security hive file";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %audit = (0 => "N",
             1 => "S",
             2 => "F",
             3 => "S/F");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching auditpol v.".$VERSION);
	::rptMsg("auditpol v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policy\\PolAdtEv";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("auditpol");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $data;
		my @policy;
		eval {
			$data = $key->get_value("")->get_data();
#			::rptMsg("Length of data: ".length($data)." bytes.");
			my $id = unpack("v",substr($data,8,2));
#			::rptMsg(sprintf "Offset value is: 0x%x",$id);
			
			if (length($data) == 148 && $id == 0x82) {
				@policy = processWin10($data)
			}
			elsif (length($data) == 138 && $id == 0x78) {
				::rptMsg("Possible Win7/Win2008");
				@policy = processWin7($data);
			}
			elsif (length($data) == 0x96 && $id == 0x84) {
				::rptMsg("Possible Win10(1607+)/Win2016");
				@policy = processWin2016($data);
			}
			else {
				::rptMsg(sprintf "Data Length: 0x%x",length($data));
				my @d = printData($data);
				foreach (0..(scalar(@d) - 1)) {
					::rptMsg($d[$_]);
				}
			}
			
			foreach (0..((scalar @policy) - 1)) {
				my ($aud,$pol) = split(/;/,$policy[$_],2);
				::rptMsg(sprintf "%-50s %-5s",$aud,$audit{$pol});
			} 
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub processWin10 {
	my $data = shift;
	my @win = ("System:Security State Change;".unpack("v",substr($data,0x0c,2)),
	             "System:Security System Extension;".unpack("v",substr($data,0x0e,2)),
	             "System:System Integrity;".unpack("v",substr($data,0x10,2)),
	             "System:IPsec Driver;".unpack("v",substr($data,0x12,2)),
	             "System:Other System Events;".unpack("v",substr($data,0x14,2)),
	             "Logon/Logoff:Logon;".unpack("v",substr($data,0x16,2)),
	             "Logon/Logoff:Logoff;".unpack("v",substr($data,0x18,2)),
	             "Logon/Logoff:Account Lockout;".unpack("v",substr($data,0x1a,2)),
	             "Logon/Logoff:IPsec Main Mode;".unpack("v",substr($data,0x1c,2)),
	             "Logon/Logoff:IPsec Quick Mode;".unpack("v",substr($data,0x1e,2)),
	             "Logon/Logoff:IPsec Extended Mode;".unpack("v",substr($data,0x20,2)),
	             "Logon/Logoff:Special Logon;".unpack("v",substr($data,0x22,2)),
	             "Logon/Logoff:Other Logon/Logoff Events;".unpack("v",substr($data,0x24,2)),
	             "Logon/Logoff:Network Policy Server;".unpack("v",substr($data,0x26,2)),
	             "Logon/Logoff:User Device Claims;".unpack("v",substr($data,0x28,2)),
	             "Logon/Logoff:Group Membership;".unpack("v",substr($data,0x2a,2)),
	             "Object Access:File System;".unpack("v",substr($data,0x2c,2)),
	             "Object Access:Registry;".unpack("v",substr($data,0x2e,2)),
	             "Object Access:Kernel Object;".unpack("v",substr($data,0x30,2)),
	             "Object Access:SAM;".unpack("v",substr($data,0x32,2)),
	             "Object Access:Certification Services;".unpack("v",substr($data,0x34,2)),
	             "Object Access:Application Generated;".unpack("v",substr($data,0x36,2)),
	             "Object Access:Handle Manipulation;".unpack("v",substr($data,0x38,2)),
	             "Object Access:File Share;".unpack("v",substr($data,0x3a,2)),
	             "Object Access:Filtering Platform Packet Drop;".unpack("v",substr($data,0x3c,2)),
	             "Object Access:Filtering Platform Connection;".unpack("v",substr($data,0x3e,2)),
	             "Object Access:Other Object Access Events;".unpack("v",substr($data,0x40,2)),
	             "Object Access:Detailed File Share;".unpack("v",substr($data,0x42,2)),
	             "Object Access:Removable Storage;".unpack("v",substr($data,0x44,2)),
	             "Object Access:Central Policy Staging;".unpack("v",substr($data,0x46,2)),
	             "Privilege Use:Sensitive Privilege Use;".unpack("v",substr($data,0x48,2)),
	             "Privilege Use:Non Sensitive Privilege Use;".unpack("v",substr($data,0x4a,2)),
	             "Privilege Use:Other Privilege Use Events;".unpack("v",substr($data,0x4c,2)),
	             "Detailed Tracking:Process Creation;".unpack("v",substr($data,0x4e,2)),
	             "Detailed Tracking:Process Termination;".unpack("v",substr($data,0x50,2)),
	             "Detailed Tracking:DPAPI Activity;".unpack("v",substr($data,0x52,2)),
	             "Detailed Tracking:RPC Events;".unpack("v",substr($data,0x54,2)),
	             "Detailed Tracking:Plug and Play Events;".unpack("v",substr($data,0x56,2)),
	             "Policy Change:Audit Policy Change;".unpack("v",substr($data,0x58,2)),
	             "Policy Change:Authentication Policy Change;".unpack("v",substr($data,0x5a,2)),
	             "Policy Change:Authorization Policy Change;".unpack("v",substr($data,0x5c,2)),
	             "Policy Change:MPSSVC Rule-Level Policy Change;".unpack("v",substr($data,0x5e,2)),
	             "Policy Change:Filtering Platform Policy Change;".unpack("v",substr($data,0x60,2)),
	             "Policy Change:Other Policy Change Events;".unpack("v",substr($data,0x62,2)),
	             "Account Management:User Account Management;".unpack("v",substr($data,0x64,2)),
	             "Account Management:Computer Account Management;".unpack("v",substr($data,0x66,2)),
	             "Account Management:Security Group Management;".unpack("v",substr($data,0x68,2)),
	             "Account Management:Distribution Group Management;".unpack("v",substr($data,0x6a,2)),
	             "Account Management:Application Group Management;".unpack("v",substr($data,0x6c,2)),
	             "Account Management:Other Account Management Events;".unpack("v",substr($data,0x6e,2)),
	             "DS Access:Directory Service Access;".unpack("v",substr($data,0x70,2)),
	             "DS Access:Directory Service Changes;".unpack("v",substr($data,0x72,2)),
	             "DS Access:Directory Service Replication;".unpack("v",substr($data,0x74,2)),
	             "DS Access:Detailed Directory Service Replication;".unpack("v",substr($data,0x76,2)),
	             "Account Logon:Credential Validation;".unpack("v",substr($data,0x78,2)),
	             "Account Logon:Kerberos Service Ticket Operations;".unpack("v",substr($data,0x7a,2)),
	             "Account Logon:Other Account Logon Events;".unpack("v",substr($data,0x7c,2)),
	             "Account Logon:Kerberos Authentication Service;".unpack("v",substr($data,0x7e,2)));
	           
	return @win;
}

sub processWin7 {
	my $data = shift;
	my @win = ("System:Security State Change;".unpack("v",substr($data,0x0c,2)),
	             "System:Security System Extension;".unpack("v",substr($data,0x0e,2)),
	             "System:System Integrity;".unpack("v",substr($data,0x10,2)),
	             "System:IPsec Driver;".unpack("v",substr($data,0x12,2)),
	             "System:Other System Events;".unpack("v",substr($data,0x14,2)),
	             "Logon/Logoff:Logon;".unpack("v",substr($data,0x16,2)),
	             "Logon/Logoff:Logoff;".unpack("v",substr($data,0x18,2)),
	             "Logon/Logoff:Account Lockout;".unpack("v",substr($data,0x1a,2)),
	             "Logon/Logoff:IPsec Main Mode;".unpack("v",substr($data,0x1c,2)),
	             "Logon/Logoff:IPsec Quick Mode;".unpack("v",substr($data,0x1e,2)),
	             "Logon/Logoff:IPsec Extended Mode;".unpack("v",substr($data,0x20,2)),
	             "Logon/Logoff:Special Logon;".unpack("v",substr($data,0x22,2)),
	             "Logon/Logoff:Other Logon/Logoff Events;".unpack("v",substr($data,0x24,2)),
	             "Logon/Logoff:Network Policy Server;".unpack("v",substr($data,0x26,2)),
	             "Object Access:File System;".unpack("v",substr($data,0x28,2)),
	             "Object Access:Registry;".unpack("v",substr($data,0x2a,2)),
	             "Object Access:Kernel Object;".unpack("v",substr($data,0x2c,2)),
	             "Object Access:SAM;".unpack("v",substr($data,0x2e,2)),
	             "Object Access:Other Object Access Events;".unpack("v",substr($data,0x30,2)),
	             "Object Access:Certification Services;".unpack("v",substr($data,0x32,2)),
	             "Object Access:Application Generated;".unpack("v",substr($data,0x34,2)),
	             "Object Access:Handle Manipulation;".unpack("v",substr($data,0x36,2)),
	             "Object Access:File Share;".unpack("v",substr($data,0x38,2)),
	             "Object Access:Filtering Platform Packet Drop;".unpack("v",substr($data,0x3a,2)),
	             "Object Access:Filtering Platform Connection;".unpack("v",substr($data,0x3c,2)),
	             "Object Access:Detailed File Share;".unpack("v",substr($data,0x3e,2)),
	             "Privilege Use:Sensitive Privilege Use;".unpack("v",substr($data,0x40,2)),
	             "Privilege Use:Non Sensitive Privilege Use;".unpack("v",substr($data,0x42,2)),
	             "Privilege Use:Other Privilege Use Events;".unpack("v",substr($data,0x44,2)),
	             "Detailed Tracking:Process Creation;".unpack("v",substr($data,0x46,2)),
	             "Detailed Tracking:Process Termination;".unpack("v",substr($data,0x48,2)),
	             "Detailed Tracking:DPAPI Activity;".unpack("v",substr($data,0x4a,2)),
	             "Detailed Tracking:RPC Events;".unpack("v",substr($data,0x4c,2)),
	             "Policy Change:Audit Policy Change;".unpack("v",substr($data,0x4e,2)),
	             "Policy Change:Authentication Policy Change;".unpack("v",substr($data,0x50,2)),
	             "Policy Change:Authorization Policy Change;".unpack("v",substr($data,0x52,2)),
	             "Policy Change:MPSSVC Rule-Level Policy Change;".unpack("v",substr($data,0x54,2)),
	             "Policy Change:Filtering Platform Policy Change;".unpack("v",substr($data,0x56,2)),
	             "Policy Change:Other Policy Change Events;".unpack("v",substr($data,0x58,2)),
	             "Account Management:User Account Management;".unpack("v",substr($data,0x5a,2)),
	             "Account Management:Computer Account Management;".unpack("v",substr($data,0x5c,2)),
	             "Account Management:Security Group Management;".unpack("v",substr($data,0x5e,2)),
	             "Account Management:Distribution Group Management;".unpack("v",substr($data,0x60,2)),
	             "Account Management:Application Group Management;".unpack("v",substr($data,0x62,2)),
	             "Account Management:Other Account Management Events;".unpack("v",substr($data,0x64,2)),
	             "DS Access:Directory Service Access;".unpack("v",substr($data,0x66,2)),
	             "DS Access:Directory Service Changes;".unpack("v",substr($data,0x68,2)),
	             "DS Access:Directory Service Replication;".unpack("v",substr($data,0x6a,2)),
	             "DS Access:Detailed Directory Service Replication;".unpack("v",substr($data,0x6c,2)),
	             "Account Logon:Credential Validation;".unpack("v",substr($data,0x6e,2)),
	             "Account Logon:Kerberos Service Ticket Operations;".unpack("v",substr($data,0x70,2)),
	             "Account Logon:Other Account Logon Events;".unpack("v",substr($data,0x72,2)),
	             "Account Logon:Kerberos Authentication Service;".unpack("v",substr($data,0x74,2)));	           
	return @win;
}

sub processWin2016 {
	my $data = shift;
	my @win = ("System:Security State Change;".unpack("v",substr($data,0x0c,2)),
	             "System:Security System Extension;".unpack("v",substr($data,0x0e,2)),
	             "System:System Integrity;".unpack("v",substr($data,0x10,2)),
	             "System:IPsec Driver;".unpack("v",substr($data,0x12,2)),
	             "System:Other System Events;".unpack("v",substr($data,0x14,2)),
	             "Logon/Logoff:Logon;".unpack("v",substr($data,0x16,2)),
	             "Logon/Logoff:Logoff;".unpack("v",substr($data,0x18,2)),
	             "Logon/Logoff:Account Lockout;".unpack("v",substr($data,0x1a,2)),
	             "Logon/Logoff:IPsec Main Mode;".unpack("v",substr($data,0x1c,2)),
	             "Logon/Logoff:Special Logon;".unpack("v",substr($data,0x1e,2)),
	             "Logon/Logoff:IPsec Quick Mode;".unpack("v",substr($data,0x20,2)),
	             "Logon/Logoff:IPsec Extended Mode;".unpack("v",substr($data,0x22,2)),
	             "Logon/Logoff:Other Logon/Logoff Events;".unpack("v",substr($data,0x24,2)),
	             "Logon/Logoff:Network Policy Server;".unpack("v",substr($data,0x26,2)),
	             "Logon/Logoff:User/Device Claims;".unpack("v",substr($data,0x28,2)),
	             "Logon/Logoff:Group Membership;".unpack("v",substr($data,0x2a,2)),
	             "Object Access:File System;".unpack("v",substr($data,0x2c,2)),
	             "Object Access:Registry;".unpack("v",substr($data,0x2e,2)),
	             "Object Access:Kernel Object;".unpack("v",substr($data,0x30,2)),
	             "Object Access:SAM;".unpack("v",substr($data,0x32,2)),
	             "Object Access:Other Object Access Events;".unpack("v",substr($data,0x34,2)),
	             "Object Access:Certification Services;".unpack("v",substr($data,0x36,2)),
	             "Object Access:Application Generated;".unpack("v",substr($data,0x38,2)),
	             "Object Access:Handle Manipulation;".unpack("v",substr($data,0x3a,2)),
	             "Object Access:File Share;".unpack("v",substr($data,0x3c,2)),
	             "Object Access:Filtering Platform Packet Drop;".unpack("v",substr($data,0x3e,2)),
	             "Object Access:Filtering Platform Connection;".unpack("v",substr($data,0x40,2)),
	             "Object Access:Detailed File Share;".unpack("v",substr($data,0x42,2)),
	             "Object Access:Removable Storage;".unpack("v",substr($data,0x44,2)),
	             "Object Access:Central Policy Staging;".unpack("v",substr($data,0x46,2)),
	             "Privilege Use:Sensitive Privilege Use;".unpack("v",substr($data,0x48,2)),
	             "Privilege Use:Non Sensitive Privilege Use;".unpack("v",substr($data,0x4a,2)),
	             "Privilege Use:Other Privilege Use Events;".unpack("v",substr($data,0x4c,2)),
	             "Detailed Tracking:Process Creation;".unpack("v",substr($data,0x4e,2)),
	             "Detailed Tracking:Process Termination;".unpack("v",substr($data,0x50,2)),
	             "Detailed Tracking:DPAPI Activity;".unpack("v",substr($data,0x52,2)),
	             "Detailed Tracking:RPC Events;".unpack("v",substr($data,0x54,2)),
	             "Detailed Tracking:Plug and Play Events;".unpack("v",substr($data,0x56,2)),
	             "Detailed Tracking:Token Right Adjusted Events;".unpack("v",substr($data,0x58,2)),
	             "Policy Change:Audit Policy Change;".unpack("v",substr($data,0x5a,2)),
	             "Policy Change:Authentication Policy Change;".unpack("v",substr($data,0x5c,2)),
	             "Policy Change:Authorization Policy Change;".unpack("v",substr($data,0x5e,2)),
	             "Policy Change:MPSSVC Rule-Level Policy Change;".unpack("v",substr($data,0x60,2)),
	             "Policy Change:Filtering Platform Policy Change;".unpack("v",substr($data,0x62,2)),
	             "Policy Change:Other Policy Change Events;".unpack("v",substr($data,0x64,2)),
	             "Account Management:User Account Management;".unpack("v",substr($data,0x66,2)),
	             "Account Management:Computer Account Management;".unpack("v",substr($data,0x68,2)),
	             "Account Management:Security Group Management;".unpack("v",substr($data,0x6a,2)),
	             "Account Management:Distribution Group Management;".unpack("v",substr($data,0x6c,2)),
	             "Account Management:Application Group Management;".unpack("v",substr($data,0x6e,2)),
	             "Account Management:Other Account Management Events;".unpack("v",substr($data,0x70,2)),
	             "DS Access:Directory Service Access;".unpack("v",substr($data,0x72,2)),
	             "DS Access:Directory Service Changes;".unpack("v",substr($data,0x74,2)),
	             "DS Access:Directory Service Replication;".unpack("v",substr($data,0x76,2)),
	             "DS Access:Detailed Directory Service Replication;".unpack("v",substr($data,0x78,2)),
	             "Account Logon:Credential Validation;".unpack("v",substr($data,0x7a,2)),
	             "Account Logon:Kerberos Service Ticket Operations;".unpack("v",substr($data,0x7c,2)),
	             "Account Logon:Other Account Logon Events;".unpack("v",substr($data,0x73,2)),
	             "Account Logon:Kerberos Authentication Service;".unpack("v",substr($data,0x80,2)));
# The rest of the data is apparently footer
	return @win;
}

#-----------------------------------------------------------
# printData()
# subroutine used primarily for debugging; takes an arbitrary
# length of binary data, prints it out in hex editor-style
# format for easy debugging
#
# my @d = printData($data);
#	foreach (0..(scalar(@d) - 1)) {
#		::rptMsg($d[$_]);
#	}
#-----------------------------------------------------------
sub printData {
	my $data = shift;
	my $len = length($data);
	my $tag = 1;
	my $cnt = 0;
	my @display = ();
	
	my $loop = $len/16;
	$loop++ if ($len%16);
	
	foreach my $cnt (0..($loop - 1)) {
#	while ($tag) {
		my $left = $len - ($cnt * 16);
		
		my $n;
		($left < 16) ? ($n = $left) : ($n = 16);

		my $seg = substr($data,$cnt * 16,$n);
		my @str1 = split(//,unpack("H*",$seg));

		my @s3;
		my $str = "";

		foreach my $i (0..($n - 1)) {
			$s3[$i] = $str1[$i * 2].$str1[($i * 2) + 1];
			
			if (hex($s3[$i]) > 0x1f && hex($s3[$i]) < 0x7f) {
				$str .= chr(hex($s3[$i]));
			}
			else {
				$str .= "\.";
			}
		}
		my $h = join(' ',@s3);
#		::rptMsg(sprintf "0x%08x: %-47s  ".$str,($cnt * 16),$h);
		$display[$cnt] = sprintf "0x%08x: %-47s  ".$str,($cnt * 16),$h;
	}
	return @display;
}


1;