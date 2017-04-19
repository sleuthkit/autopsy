#-----------------------------------------------------------
# auditfail.pl
#
# Ref: 
#   http://support.microsoft.com/kb/140058
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package auditfail;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081212);

sub getConfig{return %config}

sub getShortDescr {
	return "Get CrashOnAuditFail value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %val = (0 => "Feature is off; the system will not halt",
           1 => "Feature is on; the system will halt when events cannot be written to the ".
                "Security Event Log",
           2 => "Feature is on and has been triggered; only Administrators can log in");
           
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching auditfail v.".$VERSION);
	::rptMsg("auditfail v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		
		my $lsa_path = "ControlSet00".$current."\\Control\\Lsa";
		my $lsa;
		if ($lsa = $root_key->get_subkey($lsa_path)) {
			
			eval {
				my $crash = $lsa->get_value("crashonauditfail")->get_data();
				::rptMsg("CrashOnAuditFail = ".$crash);
				::rptMsg($val{$crash});
			};
			::rptMsg($@) if ($@);
		}	
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;
