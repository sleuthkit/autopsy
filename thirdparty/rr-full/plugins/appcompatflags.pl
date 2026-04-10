#-----------------------------------------------------------
# appcompatflags.pl
#   Extracts AppCompatFlags for Windows.
#   This is a list of applications configured to run in
#   compatibility mode. Some applications may be configured 
#   to run with elevated privilages (Tested in Vista only) :
#   "ELEVATECREATEPROCESS" "RUNASADMIN" "WINXPSP2 RUNASADMIN"
#
# Change history
#   20220328 - pulled out TelemetryController key content, to make it's own plugin
#   20200730 - updated with MITRE ATT&CK 
#   20200609 - updates
#   20200525 - updated date output format
#   20130930 - added support for Windows 8 Store key (thanks to
#              Eric Zimmerman for supplying test data)
#   20130905 - added support for both NTUSER.DAT and Software hives;
#              added support for Wow6432Node
#   20130706 - added Persisted key values (H. Carvey)
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   http://msdn.microsoft.com/en-us/library/bb756937.aspx
#
#  https://attack.mitre.org/techniques/T1546/011/
#
# Copyright 2022 Quantum Analytics Research, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appcompatflags;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              MITRE         => "T1546\.011",
              category      => "persistence",
			  output        => "report",
              version       => 20220328);
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {
	return "Extracts AppCompatFlags for Windows. This is a list".
	       " of applications configured to run in compatibility".
	       " mode. Some applications may be configured to run".
	       " with elevated privilages (Tested in Vista only) :".
	       '"ELEVATECREATEPROCESS" "RUNASADMIN" "WINXPSP2 RUNASADMIN"';
}
sub getShortDescr {
	return "Extracts AppCompatFlags values.";
}
sub getRefs {
	my %refs = ("Application Compatibility: Program Compatibility Assistant" =>
	            "http://msdn.microsoft.com/en-us/library/bb756937.aspx");
	return %refs;	
}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching appcompatflags v.".$VERSION);
	::rptMsg("appcompatflags v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");    
	::rptMsg("MITRE ATT&CK subtechnique ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers",
	             "Wow6432Node\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers",
	             "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers",
	             "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");

			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg($v->get_name()." -> ".$v->get_data());
				}
			} else {
				::rptMsg($key_path." found, has no values.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	} 
	::rptMsg("");
	
# Get all programs for which PCA "came up", for a user, even if no compatibility modes were
# selected	
# Added 20130706 by H. Carvey
	@paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted",
	          "Wow6432Node\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted",
	          "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted",
	          "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted");
	   
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg("  ".$v->get_name());
				}
			}
			else {
				::rptMsg($key_path." found, has no values\.");
			}
		}
		else {
#			::rptMsg($key_path." not found\.");
		}
	}
	
# Added 20130930 by H. Carvey
	@paths = ("Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store",
	          "Wow6432Node\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store",
	          "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store",
	          "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Store");
	   
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					
					my ($t0,$t1) = unpack("VV",substr($v->get_data(),0x2C,8));
					my $t = ::getTime($t0,$t1);
					
					::rptMsg("  ".::format8601Date($t)."Z - ".$v->get_name());
				}
			}
			else {
				::rptMsg($key_path." found, has no values\.");
			}
		}
		else {		
#			::rptMsg($key_path." not found\.");
		}
	}	
	
# Added check for use of AppCompat DB for persistence
# 21051021, H. Carvey	
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Custom";
	if ($key = $root_key->get_subkey($key_path)){
		my @subkeys = $key->get_list_of_subkeys($key);
		if (scalar @subkeys > 0) {
			foreach my $sk (@subkeys) {
				::rptMsg("Key name: ".$sk->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($sk->get_timestamp())."Z");
				
				my @vals = $sk->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						my $name = $v->get_name();
						my ($t0,$t1) = unpack("VV",$v->get_data());
						my $l = ::getTime($t0,$t1);
						my $ts   = ::format8601Date($l);
						::rptMsg("  ".$name."  ".$ts."Z");
					}
				}
				::rptMsg("");
			}
		}
	}
	
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\InstalledSDB";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys($key);
		if (scalar @subkeys > 0) {
			foreach my $sk (@subkeys) {
				my($path, $descr, $ts);
				eval {
					$descr = $sk->get_value("DatabaseDescription")->get_data();
					::rptMsg("Description: ".$descr);
				};
				
				eval {
					$path = $sk->get_value("DatabasePath")->get_data();
					::rptMsg("  Path: ".$path);
				};
				
				eval {
					my ($t0,$t1) = unpack("VV",$sk->get_value("DatabaseInstallTimeStamp")->get_data());
					my $l = ::getTime($t0,$t1);
					$ts = ::format8601Date($l);
					::rptMsg("  Install TimeStamp: ".$ts."Z");
				};
				::rptMsg("");
			}
		}
	}

}

1;
