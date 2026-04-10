#-----------------------------------------------------------
# psscript.pl 
# 
#  http://www.hexacorn.com/blog/2017/01/07/beyond-good-ol-run-key-part-52/
#
# Also, check folders:
#  c:\Windows\System32\GroupPolicy\Machine\Scripts\psscripts.ini
#  c:\Windows\System32\GroupPolicy\Machine\Scripts\Startup\
#
#
# Change history
#   20200922 - MITRE update
#   20200525 - updated date output format
#   20170107 - created
#
# Copyright 2020 QAR, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package psscript;
use strict;

my %config = (hive          => "Software, NTUSER\.DAT",
              MITRE         => "T1546",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}

sub getShortDescr {
	return "Get values assoc with PSScript\.ini";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my (@ts,$d);

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching psscript v.".$VERSION);
	::rptMsg("psscript v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

  my @paths = ("Microsoft\\Windows\\CurrentVersion\\Group Policy\\State\\Machine\\Scripts\\Startup\\0\\0",
               "Microsoft\\Windows\\CurrentVersion\\Group Policy\\Scripts\\Startup\\0\\0",
               "Microsoft\\Windows\\CurrentVersion\\Group Policy\\History\\{42B5FAAE-6536-11d2-AE5A-0000F87571E3}\\0");
    
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg($v->get_name()." - ".$v->get_data());
					
					if ($v->get_name() eq "ExecTime") {
						my $t = ::convertSystemTime($v->get_data());
						::rptMsg("ExecTime: ".$t);
					
					}
			
				}	
				::rptMsg("");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
# Also, need to check Microsoft\Windows\CurrentVersion\Group Policy\State\[SID]\Scripts
	
#	::rptMsg("");
# NTUSER.DAT checks
	my @paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\Group Policy\\Scripts");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");	
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");	
		
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
				}
			}
		}
		else {
#			::rptMsg($key_path." not found\.");
		}
	}	
}

1;