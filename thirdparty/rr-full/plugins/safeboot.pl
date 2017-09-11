#-----------------------------------------------------------
# safeboot.pl
#
# Some malware is known to maintain persistence, even when the system
# is booted to SafeMode by writing entries to the SafeBoot subkeys
# ex: http://www.symantec.com/security_response/writeup.jsp?
#            docid=2008-011507-0108-99&tabid=2
#	
# Ref: 
#   http://support.microsoft.com/kb/315222
#   http://support.microsoft.com/kb/202485/
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package safeboot;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081216);

sub getConfig{return %config}

sub getShortDescr {
	return "Check SafeBoot entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching safeboot v.".$VERSION);
	::rptMsg("safeboot v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;

		my $sb_path = $ccs."\\Control\\SafeBoot";
		my $sb;
		if ($sb = $root_key->get_subkey($sb_path)) {
			
			my @sks = $sb->get_list_of_subkeys();
			
			if (scalar(@sks) > 0) {
				
				foreach my $s (@sks) {
					my $name = $s->get_name();
					my $ts   = $s->get_timestamp();
					::rptMsg($name."  [".gmtime($ts)." Z]");
					my %sk;
					my @subkeys = $s->get_list_of_subkeys();
					
					if (scalar(@subkeys) > 0) {
						foreach my $s2 (@subkeys) {
							my $str;
							my $default;
							eval {
								$default = $s2->get_value("")->get_data();
							};
							($@)?($str = $s2->get_name()):($str = $s2->get_name()." (".$default.")");
							push(@{$sk{$s2->get_timestamp()}},$str);
						}
						
						foreach my $t (sort keys %sk) {
							::rptMsg(gmtime($t)." Z");
							foreach my $i (@{$sk{$t}}) {
								::rptMsg("  ".$i);
							}
						}
						::rptMsg("");
					}
					else {
						::rptMsg($name." has no subkeys.");
					}
				}
			}
			else {
				::rptMsg($sb_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($sb_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
#		::logMsg($key_path." not found.");
	}
}
1;