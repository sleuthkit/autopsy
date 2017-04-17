#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# baseline.pl
#
# History
#    20130211 - Created
# 
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package baseline;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130211);

sub getConfig{return %config}
sub getShortDescr {
	return "Scans a hive file, checking sizes of binary value data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %vals;
my $count = 0;
my %data_len = ();

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching baseline v.".$VERSION);
	::rptMsg("baseline v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	traverse($root_key);
# Data structure containing findings is a hash of hashes	
	::rptMsg("Total values checked    : ".$count);
#	::rptMsg("");
	::rptMsg("Number of binary value lengths : ".scalar(keys %data_len));
	my @len = sort {$a <=> $b} keys %data_len;
#	::rptMsg("Value 0: ".$len[0]);
	::rptMsg("...");
	my $n = scalar @len - 1;
	for my $i (($n - 15)..$n) {
		::rptMsg("Value ".$i.": ".$len[$i]." bytes [# times: ".$data_len{$len[$i]}."]");
	}
}

sub traverse {
	my $key = shift;
#  my $ts = $key->get_timestamp();
  
  foreach my $val ($key->get_list_of_values()) {
  	my $type = $val->get_type();
  	if ($type == 0 || $type == 3) {
  		$count++;
  		my $data = $val->get_data();
  		if (exists $data_len{length($data)}) {
  			$data_len{length($data)}++;
  		}
  		else {
				$data_len{length($data)} = 1;
			}
  	}
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
  }
}

1;