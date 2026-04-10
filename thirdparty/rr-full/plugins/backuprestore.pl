#-----------------------------------------------------------
# backuprestore.pl
#   Access System hive file to get the contents of the FilesNotToSnapshot, KeysNotToRestore, and FilesNotToBackup keys
# 
# Threat actors have been observed modifying the contents of the FilesNotToSnapshot OutlookOST value, and then
# stealing a copy of user OST files by creating a snapshot, or via esentutl.exe.
# 
# Change history
#   20201012 - MITRE updates
#   20200517 - updated date output format
#   20130904 - cleaned up code
#   9/14/2012 - retired the filesnottosnapshot.pl plugin since BackupRestore checks the same key
#
# References
#   Troy Larson's Windows 7 presentation slide deck http://computer-forensics.sans.org/summit-archives/2010/files/12-larson-windows7-foreniscs.pdf
#   QCCIS white paper Reliably recovering evidential data from Volume Shadow Copies http://www.qccis.com/downloads/whitepapers/QCC%20VSS
#	http://msdn.microsoft.com/en-us/library/windows/desktop/bb891959(v=vs.85).aspx
# 
#   https://attack.mitre.org/techniques/T1562/001/
#
# original plugin written by Corey Harrell (Journey Into Incident Response)
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package backuprestore;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1562\.001",
              category      => "defense evasion",
              version       => 20201012);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets the contents of the FilesNotToSnapshot, KeysNotToRestore, and FilesNotToBackup keys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching backuprestore v.".$VERSION);
	::rptMsg("backuprestore v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		
		my $fns_path = $ccs."\\Control\\BackupRestore\\FilesNotToSnapshot";
		my $fns;
		if ($fns = $root_key->get_subkey($fns_path)) {
#			::rptMsg("FilesNotToSnapshot key");
			::rptMsg($fns_path);
			::rptMsg("LastWrite Time ".::format8601Date($fns->get_timestamp())."Z");
			::rptMsg("The listed directories/files are not backed up in Volume Shadow Copies");
			::rptMsg("");
		
			my %cv;
			my @valfns = $fns->get_list_of_values();
			if (scalar(@valfns) > 0) {
				foreach my $v (@valfns) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					my $len  = length($data);
					next if ($name eq "");
					push(@{$cv{$len}},$name." : ".$data);
				}
			
				foreach my $t (sort {$a <=> $b} keys %cv) {
					foreach my $item (@{$cv{$t}}) {
						::rptMsg("  $item");
					}
				}
				::rptMsg("Analysis Tip: A threat actor can add entries in order to gain access to files that are normally");
				::rptMsg("locked; creating a VSC would then allow them to access that file.");
#				::rptMsg("");
			}
			else {
				::rptMsg($fns_path." has no values.");
			}
		}
		else {
			::rptMsg($fns_path." not found.");
		}
		::rptMsg("");
		my $fnb_path = $ccs."\\Control\\BackupRestore\\FilesNotToBackup";
		my $fnb;
		if ($fnb = $root_key->get_subkey($fnb_path)) {
			::rptMsg("FilesNotToBackup key");
			::rptMsg($fnb_path);
			::rptMsg("LastWrite Time ".::format8601Date($fnb->get_timestamp())."Z");
			::rptMsg("Specifies the directories and files that backup applications should not backup or restore");
			::rptMsg("");
		
			my %cq;
			my @valfnb = $fnb->get_list_of_values();;
			if (scalar(@valfnb) > 0) {
				foreach my $v (@valfnb) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					my $len  = length($data);
					next if ($name eq "");
					push(@{$cq{$len}},$name." : ".$data);
				}
				
				foreach my $t (sort {$a <=> $b} keys %cq) {
					foreach my $item (@{$cq{$t}}) {
						::rptMsg("  $item");
					}
				}
			}
			else {
				::rptMsg($fnb_path." has no values.");
			}
		}
		else {
			::rptMsg($fnb_path." not found.");
		}
		::rptMsg("");
		my $knr_path = $ccs."\\Control\\BackupRestore\\KeysNotToRestore";
		my $knr;
		if ($knr = $root_key->get_subkey($knr_path)) {
			::rptMsg("KeysNotToRestore key");
			::rptMsg($knr_path);
			::rptMsg("LastWrite Time ".::format8601Date($knr->get_timestamp())."Z");
			::rptMsg("");
			::rptMsg("Specifies the names of the registry subkeys and values that backup applications should not restore");
			::rptMsg("");
		
			my %cw;
			my @valknr = $knr->get_list_of_values();;
				if (scalar(@valknr) > 0) {
					foreach my $v (@valknr) {
						my $name = $v->get_name();
						my $data = $v->get_data();
						my $len  = length($data);
						next if ($name eq "");
						push(@{$cw{$len}},$name." : ".$data);
					}
					
					foreach my $t (sort {$a <=> $b} keys %cw) {
						foreach my $item (@{$cw{$t}}) {
							::rptMsg("  $item");
						}
					}
				}
				else {
					::rptMsg($knr_path." has no values.");
				}
		}
		else {
			::rptMsg($knr_path." not found.");
		}
	}
}

1;