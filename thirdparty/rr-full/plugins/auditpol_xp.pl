#-----------------------------------------------------------
# auditpol
# Get the audit policy from the Security hive file
# 
#
# History
#   20121128 - updated for later versions of Windows
#   20080327 - created
#
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package auditpol_xp;
use strict;

my %config = (hive          => "Security",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20121128);

sub getConfig{return %config}
sub getShortDescr {
	return "Get audit policy from the Security hive file";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("How To Determine Audit Policies from the Registry" => 
	            "http://support.microsoft.com/default.aspx?scid=kb;EN-US;q246120");
	return %refs;	
}
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
	::logMsg("Launching auditpol_xp v.".$VERSION);
	::rptMsg("auditpol_xp v.".$VERSION); # banner
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
		eval {
			$data = $key->get_value("")->get_data();
			::rptMsg("Length of data: ".length($data)." bytes.");
			
			my @d = printData($data);
			foreach (0..(scalar(@d) - 1)) {
				::rptMsg($d[$_]);
			}
			
		};
		if ($@) {
			::rptMsg("Error occurred getting data from ".$key_path);
			::rptMsg(" - ".$@);
		}
		else {
# Check to see if auditing is enabled
			my $enabled = unpack("C",substr($data,0,1));
			if ($enabled) {
				::rptMsg("Auditing is enabled.");
# Get audit configuration settings			
				my @vals = unpack("V*",$data);
				::rptMsg("\tAudit System Events        = ".$audit{$vals[1]});
				::rptMsg("\tAudit Logon Events         = ".$audit{$vals[2]});
				::rptMsg("\tAudit Object Access        = ".$audit{$vals[3]});
				::rptMsg("\tAudit Privilege Use        = ".$audit{$vals[4]});
				::rptMsg("\tAudit Process Tracking     = ".$audit{$vals[5]});
				::rptMsg("\tAudit Policy Change        = ".$audit{$vals[6]});
				::rptMsg("\tAudit Account Management   = ".$audit{$vals[7]});
				::rptMsg("\tAudit Dir Service Access   = ".$audit{$vals[8]});
				::rptMsg("\tAudit Account Logon Events = ".$audit{$vals[9]});
			}
			else {
				::rptMsg("**Auditing is NOT enabled.");
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}


#-----------------------------------------------------------
# printData()
# subroutine used primarily for debugging; takes an arbitrary
# length of binary data, prints it out in hex editor-style
# format for easy debugging
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