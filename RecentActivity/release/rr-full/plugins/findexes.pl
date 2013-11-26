#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# findexes.pl
# Plugin for RegRipper; traverses through a Registry hive,
# looking for values with binary data types, and checks to see
# if they start with "MZ"; if so, records the value path, key 
# LastWrite time, and length of the data
#
# Change history
#    20090728 - Created
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package findexes;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090728);

sub getConfig{return %config}
sub getShortDescr {
	return "Scans a hive file looking for binary value data that contains MZ";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %vals;
my $bin_count = 0;
my $exe_count = 0;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching findexes v.".$VERSION);
	::rptMsg("findexes v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner 
	traverse($root_key);
# Data structure containing findings is a hash of hashes	
	foreach my $k (keys %vals) {
		::rptMsg("Key: ".$k."   LastWrite time: ".gmtime($vals{$k}{lastwrite}));
		foreach my $i (keys %{$vals{$k}}) {
			next if ($i eq "lastwrite");
			::rptMsg("  Value: ".$i."  Length: ".$vals{$k}{$i}." bytes");
		}
		::rptMsg("");
	}
	::rptMsg("Number of values w/ binary data types: ".$bin_count);
	::rptMsg("Number of values w/ MZ in binary data:   ".$exe_count);
}

sub traverse {
	my $key = shift;
#  my $ts = $key->get_timestamp();
  
  foreach my $val ($key->get_list_of_values()) {
  	my $type = $val->get_type();
  	if ($type == 0 || $type == 3) {
  		$bin_count++;
  		my $data = $val->get_data();
# This code looks for data that starts with MZ
#  		my $i    = unpack("v",substr($data,0,2)); 		
#  		if ($i == 0x5a4d) {
			if (grep(/MZ/,$data)) {
				$exe_count++;		
				my $path;
				my @p = split(/\\/,$key->get_path());
				if (scalar(@p) == 1) {
					$path = "root";
				}
				else {
					shift(@p);
					$path = join('\\',@p);
				}
				
				$vals{$path}{lastwrite} = $key->get_timestamp();
				$vals{$path}{$val->get_name()}    = length($data);			
			}
  	}
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
  }
}

1;