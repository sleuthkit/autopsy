#-----------------------------------------------------------
# crashdump.pl
# Author: Don C. Weber
# Plugin for Registry Ripper; Access System hive file to get the
# crashdump settings from System hive
# 
# Change history
#
#
# References
#  Overview of memory dump file options for Windows Server 2003, Windows XP, and Windows 2000: http://support.microsoft.com/kb/254649/
# 
# Author: Don C. Weber, http://www.cutawaysecurity.com/blog/cutaway-security
#-----------------------------------------------------------
package crashdump;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20081219);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets crashdump settings from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching crashdump v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $win_path = $ccs."\\Control\\CrashControl";
		my $win;
		if ($win = $root_key->get_subkey($win_path)) {
			::rptMsg("CrashControl Configuration");
			::rptMsg($win_path);
			::rptMsg("LastWrite Time ".gmtime($win->get_timestamp())." (UTC)");
		}
		else {
			::rptMsg($win_path." not found.");
		}
		
		my %vals = getKeyValues($win);
		if (scalar(keys %vals) > 0) {
			foreach my $v (keys %vals) {
				if ($v eq "CrashDumpEnabled"){
					if ($vals{$v} == 0x00){
						::rptMsg("\t".$v." -> None");
					} elsif ($vals{$v} == 0x01){
						::rptMsg("\t".$v." -> Complete memory dump");
					} elsif ($vals{$v} == 0x02){
						::rptMsg("\t".$v." -> Kernel memory dump");
					} elsif ($vals{$v} == 0x03){
						::rptMsg("\t".$v." -> Small memory dump (64KB)");
					} else{
						::rptMsg($v." has no value.");
					}
				}else{
					if (($v eq "MinidumpDir") || ($v eq "DumpFile")){
						::rptMsg("\t".$v." location ".$vals{$v});
					} else{
						($vals{$v}) ? ::rptMsg("\t".$v." is Enabled") : ::rptMsg("\t".$v." is Disabled");
					}
				}
			}
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
		::rptMsg("");
		::rptMsg("Analysis Tips: For crash dump information and tools check http://support.microsoft.com/kb/254649/");
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

sub getKeyValues {
	my $key = shift;
	my %vals;
	
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {
	
	}
	return %vals;
}

1;