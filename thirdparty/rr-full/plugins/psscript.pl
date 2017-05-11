#-----------------------------------------------------------
# psscript.pl 
# 
#
#
#  http://www.hexacorn.com/blog/2017/01/07/beyond-good-ol-run-key-part-52/
#
# Also, check folders:
#  c:\Windows\System32\GroupPolicy\Machine\Scripts\psscripts.ini
#  c:\Windows\System32\GroupPolicy\Machine\Scripts\Startup\
#
#
# Change history
#   20170107 - created
#
# Copyright 2017 QAR, LLC
#-----------------------------------------------------------
package psscript;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20170107);

sub getConfig{return %config}

sub getShortDescr {
	return "Get PSScript\.ini values";	
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
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# updated added 20130326  
  my @paths = ("Microsoft\\Windows\\CurrentVersion\\Group Policy\\State\\Machine\\Scripts\\Startup\\0\\0",
               "Microsoft\\Windows\\CurrentVersion\\Group Policy\\Scripts\\Startup\\0\\0");
    
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
			::rptMsg("");
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg($v->get_name()." - ".$v->get_data());
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
			::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
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