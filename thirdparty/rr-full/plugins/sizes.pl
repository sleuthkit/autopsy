#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# sizes.pl
# Plugin for RegRipper; traverses through a Registry hive,
# looking for values with binary data types, and checks their
# sizes; change $min_size value to suit your needs
#
# Change history
#    20150527 - Created
# 
# copyright 2015 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package sizes;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150527);

sub getConfig{return %config}
sub getShortDescr {
	return "Scans a hive file looking for binary value data of a min size";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $min_size = 50000;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching sizes v.".$VERSION);
	::rptMsg("sizes v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()."\n");  
	traverse($root_key);
}

sub traverse {
	my $key = shift;
#  my $ts = $key->get_timestamp();
  
  foreach my $val ($key->get_list_of_values()) {
  	my $type = $val->get_type();
  	if ($type == 0 || $type == 3) {
  		my $data = $val->get_data();
			my $len  = length($data);
			if ($len > $min_size) {
				
				my @name = split(/\\/,$key->get_path());
				$name[0] = "";
				$name[0] = "\\" if (scalar(@name) == 1);
				my $path = join('\\',@name);
				
				::rptMsg("Key  : ".$path);
				::rptMsg("Value: ".$val->get_name());
				::rptMsg("Size : ".$len." bytes.");
				::rptMsg("");
			}
  	}
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
  }
}

1;