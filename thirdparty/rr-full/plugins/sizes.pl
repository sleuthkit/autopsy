#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# sizes.pl
# Plugin for RegRipper; traverses through a Registry hive,
# looking for values with binary data types, and checks their
# sizes; change $min_size value to suit your needs
#
# Change history
#    20230811 - added Regex to look for base64-encoded data
#    20201118 - updated to look for keys with a large num. of values
#             - OSINT indicates that Cobalt Strike is written to a random key with 760 Reg_Sz values
#    20201012 - MITRE update
#    20200517 - minor updates
#    20180817 - updated to include brief output, based on suggestion from J. Wood
#    20180607 - modified based on Meterpreter input from Mari DeGrazia
#    20150527 - Created
#
#    https://attack.mitre.org/techniques/T1112/
# 
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sizes;
use strict;

my $min_size    = 5000;
my $min_vals    = 100;
my $output_size = 64;

my %config = (hive          => "all",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1112",
              category      => "defense evasion",
              version       => 20230811);

sub getConfig{return %config}
sub getShortDescr {
	return "Scans hive for value data greater than ".$min_size." bytes, and keys with more than ".$min_vals." values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $count = 0;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching sizes v.".$VERSION);
	::rptMsg("sizes v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()."\n");  
  
  my $start = time;
    
	traverse($root_key);
	
	my $finish = time;
	
	::rptMsg("Scan completed: ".($finish - $start)." sec");
	::rptMsg("Total values  : ".$count);
	
	::rptMsg("");
	::rptMsg("Analysis Tip: Threat actors my hide \"fileless\" commands in Registry values.  This plugin sweeps through the Registry");
	::rptMsg("to look for values with data greater than ".$min_size." bytes in size.  It also looks for keys with more than ".$min_vals);
	::rptMsg("values; threat actors have been observed placing Cobalt Strike EXEs in up to 750 Registry values.");
	::rptMsg("");
	::rptMsg("As of 20230811, a regex to look for base64-encoding in string value data was added.");
}

sub traverse {
	my $key = shift;
#  my $ts = $key->get_timestamp();
  
  my @vals = ();
  if (@vals = $key->get_list_of_values()) {
  	
  	if (scalar @vals > $min_vals) {
  		my @name = split(/\\/,$key->get_path());
			$name[0] = "";
			$name[0] = "\\" if (scalar(@name) == 1);
			my $path = join('\\',@name);
  		::rptMsg("Key ".$path." [LastWrite time: ".::format8601Date($key->get_timestamp())."Z] has more than ".$min_vals." values [total values: ".(scalar @vals)."]");
  		::rptMsg("");
  	}
  	
  	foreach my $val (@vals) {
  		$count++;
  		my $type = $val->get_type();
  		if ($type == 0 || $type == 3 || $type == 1 || $type == 2) {
  			my $data = $val->get_data();
				my $len  = length($data);
				if ($len > $min_size) {
				
					my @name = split(/\\/,$key->get_path());
					$name[0] = "";
					$name[0] = "\\" if (scalar(@name) == 1);
					my $path = join('\\',@name);
					::rptMsg("Key  : ".$path."  Value: ".$val->get_name()."  Size: ".$len." bytes");

# Data type "none", "Reg_SZ", "Reg_Expand_SZ"				
					if ($type == 0 || $type == 1 || $type == 2) {
						::rptMsg("Data Sample (first ".$output_size." bytes) : ".substr($data,0,$output_size)."...");
# added 20230811
						if ($data =~ m/([A-Za-z0-9+\/]{4}){3,}([A-Za-z0-9+\/]{2}==|[A-Za-z0-9+\/]{3}=)?/) {
							::rptMsg("Value may contain base64-encoded data");
						}
# --------------
					}

# Binary data				
					if ($type == 3) {
						my $out = substr($data,0,$output_size);
						::probe($out);				
					}
					::rptMsg("");
				}
			}
  	}
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
  }
}

1;