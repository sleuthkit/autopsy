#-----------------------------------------------------------
# eventlog.pl
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package eventlog;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090112);

sub getConfig{return %config}

sub getShortDescr {
	return "Get EventLog configuration info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching eventlog v.".$VERSION);
	::rptMsg("eventlog v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		
		my $evt_path = "ControlSet00".$current."\\Services\\Eventlog";
		my $evt;
		if ($evt = $root_key->get_subkey($evt_path)) {
			::rptMsg("");
			my @subkeys = $evt->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $logname = $s->get_name();
					::rptMsg($logname." \\ ".scalar gmtime($s->get_timestamp())."Z");
					eval {
						my $file = $s->get_value("File")->get_data();
						::rptMsg("  File               = ".$file);
					};
					
					eval {
						my $display = $s->get_value("DisplayNameFile")->get_data();
						::rptMsg("  DisplayNameFile    = ".$display);
					};
					
					eval {
						my $max = $s->get_value("MaxSize")->get_data();
						::rptMsg("  MaxSize            = ".processSize($max));
					};
					
					eval {
						my $ret = $s->get_value("Retention")->get_data();
						::rptMsg("  Retention          = ".processRetention($ret));
					};

# AutoBackupLogFiles; http://support.microsoft.com/kb/312571/					
					eval {
						my $auto = $s->get_value("AutoBackupLogFiles")->get_data();
						::rptMsg("  AutoBackupLogFiles = ".$auto);
					};

# Check WarningLevel value on Security EventLog; http://support.microsoft.com/kb/945463
					eval {
						if ($logname eq "Security") {
							my $wl = $s->get_value("WarningLevel")->get_data();
							::rptMsg("  WarningLevel       = ".$wl);
						}
					};
					
					::rptMsg("");
				}
				
			}
			else {
				::rptMsg($evt_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($evt_path." not found.");
			::logMsg($evt_path." not found.");
		}	
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;

sub processSize {
	my $sz = shift;
	
	my $kb = 1024;
	my $mb = $kb * 1024;
	my $gb = $mb * 1024;
	
	if ($sz > $gb) {
		my $d = $sz/$gb;
		my $l = length((split(/\./,$d,2))[0]) + 2;
		return sprintf "%$l.2fGB",$d;
	}
	elsif ($sz > $mb) {
		my $d = $sz/$mb;
		my $l = length((split(/\./,$d,2))[0]) + 2;
		return sprintf "%$l.2fMB",$d;
	}
	elsif ($sz > $kb) {
		my $d = $sz/$kb;
		my $l = length((split(/\./,$d,2))[0]) + 2;
		return sprintf "%$l.2fKB",$d;
	}
	else {return $sz."B"};
}

sub processRetention {
# Retention maintained in seconds
# http://www.microsoft.com/technet/prodtechnol/windows2000serv/reskit/
#        regentry/30709.mspx?mfr=true
	my $ret = shift;
	
	my $min = 60;
	my $hr  = $min * 60;
	my $day = $hr * 24;
	
	if ($ret > $day) {
		my $d = $ret/$day;
		my $l = length((split(/\./,$d,2))[0]) + 2;
		return sprintf "%$l.2f days",$d; 
	}
	elsif ($ret > $hr) {
		my $d = $ret/$hr;
		my $l = length((split(/\./,$d,2))[0]) + 2;
		return sprintf "%$l.2f hr",$d;
	}
	elsif ($ret > $min) {
		my $d = $ret/$min;
		my $l = length((split(/\./,$d,2))[0]) + 2;
		return sprintf "%$l.2f min",$d;
	}
	else {return $ret." sec"};
}