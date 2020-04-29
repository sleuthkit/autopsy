#-----------------------------------------------------------
# amcache_tln.pl 
#   
# Change history
#   20180311 - updated to support newer version files, albeit without parsing devices
#   20170315 - added output for Product Name and File Description values
#   20160818 - added check for value 17
#   20131218 - fixed bug computing compile time
#   20131213 - updated 
#   20131204 - created
#
# References
#   https://binaryforay.blogspot.com/2017/10/amcache-still-rules-everything-around.html
#   http://www.swiftforensics.com/2013/12/amcachehve-in-windows-8-goldmine-for.html
#
# Copyright (c) 2018 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package amcache_tln;
use strict;

my %config = (hive          => "amcache",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "program execution",
              version       => 20180311);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Parse AmCache\.hve file";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching amcache_tln v.".$VERSION);
#  ::rptMsg("amcache v.".$VERSION); 
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;

# Newer version Amcache.hve files
# Devices not parsed at this time
  my $key_path = 'Root\\InventoryApplicationFile';
  if ($key = $root_key->get_subkey($key_path)) {
		parseInventoryApplicationFile($key);
		
	}
	else {
#		::rptMsg($key_path." not found.");
	}
#  ::rptMsg("");
  
#  my $key_path = 'Root\\InventoryApplication';
#  if ($key = $root_key->get_subkey($key_path)) {
#		parseInventoryApplication($key);
#		
#	}
#	else {
#		::rptMsg($key_path." not found.");
#	}
#  ::rptMsg("");
	
# Older version AmCache.hve files
# Root\Files subkey	
	my $key_path = 'Root\\File';
	if ($key = $root_key->get_subkey($key_path)) {
		parseFile($key);
		
	}
	else {
#		::rptMsg($key_path." not found.");
	}
#	::rptMsg("");
}

sub parseInventoryApplicationFile {
	my $key = shift;
#	::rptMsg("***InventoryApplicationFile***");
	my @sk = $key->get_list_of_subkeys();
	if (scalar(@sk) > 0) {
		foreach my $s (@sk) {
		  my $lw = $s->get_timestamp();
		  
		  my $path;
		  eval {
		  	$path = $s->get_value("LowerCaseLongPath")->get_data();
		  };
			
			my $hash;
			eval {
				$hash = $s->get_value("FileID")->get_data();
				$hash =~ s/^0000//;
			};
			
			::rptMsg($lw."|AmCache|||Key LastWrite - ".$path." (".$hash.")");
		}
	}
	else {
		
	}		
}

sub parseInventoryApplication {
	my $key = shift;
	my @sk = $key->get_list_of_subkeys();
	if (scalar(@sk) > 0) {
		foreach my $s (@sk) {
		  my $lw = $s->get_timestamp();		
			my $name;
			eval {
				$name = $s->get_value("Name")->get_data();
			};	
			
			my $version;
			eval {
				$version = "v.".$s->get_value("Version")->get_data();
			};	
			::rptMsg(gmtime($lw)." - ".$name." ".$version);
		}
	}
	else {
		
	}		
}


sub parseFile {
	my $key = shift;
#	::rptMsg("***Files***");
	my (@t,$gt);
	my @sk1 = $key->get_list_of_subkeys();
	foreach my $s1 (@sk1) {
# Volume GUIDs			
		::rptMsg($s1->get_name());
		my @sk = $s1->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my $fileref = $s->get_name();
				my $lw      = $s->get_timestamp();

# First, report key lastwrite time (== execution time??)					
				eval {
					$fileref = $fileref.":".$s->get_value("15")->get_data();
				};
				::rptMsg($lw."|AmCache|||Key LastWrite   - ".$fileref);

# get last mod./creation times									
				my @dots = qw/. . . ./;
				my %t_hash = ();
				my @vals = ();
					
# last mod time
				eval {
					my @t = unpack("VV",$s->get_value("11")->get_data());
					$vals[1] = ::getTime($t[0],$t[1]);
				};
# creation time
				eval {
					my @t = unpack("VV",$s->get_value("12")->get_data());
					$vals[3] = ::getTime($t[0],$t[1]);
				};

				foreach my $v (@vals) {
					@{$t_hash{$v}} = @dots unless ($v == 0);
				}

				${$t_hash{$vals[0]}}[1] = "A" unless ($vals[0] == 0);
				${$t_hash{$vals[1]}}[0] = "M" unless ($vals[1] == 0);
				${$t_hash{$vals[2]}}[2] = "C" unless ($vals[2] == 0);
				${$t_hash{$vals[3]}}[3] = "B" unless ($vals[3] == 0);

				foreach my $t (reverse sort {$a <=> $b} keys %t_hash) {
					my $str = join('',@{$t_hash{$t}});
					::rptMsg($t."|AmCache|||".$str."  ".$fileref);
				}
						
# check for PE Compile times					
				eval {
					my $pe = $s->get_value("f")->get_data();
					::rptMsg($pe."|AmCache|||PE Compile time - ".$fileref);
					::rptMsg("Compile Time  : ".$gt." Z");
				};				
		
			}
		}
		else {
#				::rptMsg("Key ".$s1->get_name()." has no subkeys.");
		}		
	}
	
}

# Root\Programs subkey
sub parsePrograms {
	my $key = shift;
#	::rptMsg("***Programs***");
	my @sk1 = $key->get_list_of_subkeys();
	if (scalar(@sk1) > 0) {
		foreach my $s1 (@sk1) {
			my $str;
			$str = "Name       : ".$s1->get_value("0")->get_data();
			
			eval {
				$str .= " v\.".$s1->get_value("1")->get_data();
			};
			::rptMsg($str);
			eval {
				::rptMsg("Category   : ".$s1->get_value("6")->get_data());
			};
			
			eval {
				::rptMsg("UnInstall  : ".$s1->get_value("7")->get_data());
			};
				
#			::rptMsg("");
		}
	}
}


1;
